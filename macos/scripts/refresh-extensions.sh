#!/usr/bin/env bash
#
# refresh-extensions.sh — rebuild/install new sources, force-test the fleet,
# and report which sources work from this IP.
#
# Why --rerun-tasks: Gradle's test task does NOT track the extensions directory
# as an input, so after installing new jars `check-extension-health.sh` silently
# reuses the previous JUnit XML (41s instead of ~11 min). --rerun-tasks forces
# a real run every time.
#
# Usage:
#   BUILD_LIST="animexin anizone" ./refresh-extensions.sh   # build some, then test
#   ./refresh-extensions.sh                                  # just force-test the fleet
#
# Output: per-extension table on stdout, JSON at /tmp/anikku_refresh_<ts>.json,
# full gradle log at /tmp/anikku-refresh-<ts>.log
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MACOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$MACOS_DIR/.." && pwd)"
TS=$(date +%Y%m%d_%H%M%S)
LOG="/tmp/anikku-refresh-${TS}.log"
BUILD_LIST="${BUILD_LIST:-}"

echo "== Anikku extension refresh $TS ==" | tee "$LOG"

# 1) Optional: build + auto-install requested extensions from yuzono source
if [ -n "$BUILD_LIST" ]; then
  for pkg in $BUILD_LIST; do
    echo ">> building $pkg ..."
    rm -rf /tmp/anikku-source-build/extensions-source /tmp/anikku-source-build/classes
    if bash "$SCRIPT_DIR/build-keiyoushi-from-source.sh" --pkg "$pkg" --lang all --keep-temp >> "$LOG" 2>&1; then
      echo "   built+installed: $pkg"
    else
      echo "   BUILD FAILED: $pkg (reason in $LOG)"
    fi
  done
fi

# 2) Force a real fleet test
echo ">> running fleet test (--rerun-tasks, ~11-16 min) ..."
cd "$PROJECT_ROOT"
./macos/gradlew -p macos test \
  --tests "app.anikku.macos.platform.extension.ExtensionCompatibilityTest" \
  --rerun-tasks --no-daemon --console=plain >> "$LOG" 2>&1
GRADLE_EXIT=$?
if [ "$GRADLE_EXIT" -ne 0 ]; then
  echo "!! fleet test failed (exit $GRADLE_EXIT) — see $LOG" | tee -a "$LOG"
  tail -20 "$LOG"
  exit "$GRADLE_EXIT"
fi

# 3) Parse the JUnit XML into a report
XML="$MACOS_DIR/build/test-results/test/TEST-app.anikku.macos.platform.extension.ExtensionCompatibilityTest.xml"
if [ ! -f "$XML" ]; then
  echo "!! test XML not found: $XML" | tee -a "$LOG"
  exit 1
fi
XML="$XML" JSON="/tmp/anikku_refresh_${TS}.json" python3 - <<'PYEOF'
import os, re, json, html

xml = open(os.environ["XML"]).read()
# unescape entities
out = (xml.replace("&quot;", '"').replace("&apos;", "'")
          .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&"))
out = out.replace("<![CDATA[", "").replace("]]>", "")

rows = []
pat = re.compile(r'\[([✅⏱❌⚠⏭])\]\s*([^\|]+?)\s*\|\s*Browse:\s*([✅⏱❌⚠])\s*(\d+)?\s*\|\s*Episodes:\s*([✅⏱❌⚠])\s*(\d+)?\s*\|\s*Video:\s*([✅⏱❌⚠])\s*(\d+)?\s*\|?\s*(.*)')
for line in out.splitlines():
    m = pat.search(line)
    if m:
        rows.append({
            "name": m.group(2).strip(),
            "load": m.group(1),
            "browse": m.group(3), "browse_count": int(m.group(4) or 0),
            "episodes": m.group(5), "episode_count": int(m.group(6) or 0),
            "video": m.group(7), "video_count": int(m.group(8) or 0),
            "first": m.group(9).strip(),
        })

summary = {}
sm = re.search(r'Total extensions:\s*(\d+).*?Loaded:\s*(\d+).*?Browse \(popular\):\s*(\d+).*?Episodes:\s*(\d+).*?Video URLs:\s*(\d+).*?Elapsed:\s*(\d+)s', out, re.S)
if sm:
    summary = {"total": int(sm.group(1)), "loaded": int(sm.group(2)),
               "browsed": int(sm.group(3)), "episodes": int(sm.group(4)),
               "videos": int(sm.group(5)), "elapsed_seconds": int(sm.group(6))}

working = [r for r in rows if r["episodes"] == "✅" and r["video"] == "✅"]
no_video = [r for r in rows if r["episodes"] == "✅" and r["video"] != "✅"]
no_episodes = [r for r in rows if r["browse"] != "✅"]

print()
print("=" * 78)
print(f"FLEET REPORT — {len(rows)} extensions | working (episodes+video): {len(working)}")
if summary: print(f"summary: {summary}")
print("=" * 78)
print("\n✅ WORKING (episodes + video URLs):")
for r in sorted(working, key=lambda x: -x["episode_count"]):
    print(f"   {r['name']:<26} ep={r['episode_count']:<5} vid={r['video_count']}")
print("\n⚠️  EPISODES BUT NO VIDEO:")
for r in sorted(no_video, key=lambda x: x["name"].lower()):
    print(f"   {r['name']:<26} ep={r['episode_count']:<5} video={r['video']}")
print("\n❌ NO EPISODES (browse failed):")
for r in sorted(no_episodes, key=lambda x: x["name"].lower()):
    print(f"   {r['name']:<26} browse={r['browse']}")

json.dump({"summary": summary, "extensions": rows,
           "working": [r["name"] for r in working]},
          open(os.environ["JSON"], "w"), indent=2)
print(f"\nJSON: {os.environ['JSON']}")
print(f"Full log: {os.environ.get('ANIKKU_LOG', '')}")
PYEOF
