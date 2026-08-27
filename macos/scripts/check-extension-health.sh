#!/bin/bash
#
# Anikku Extension Health Check
# =============================
# Runs the ExtensionCompatibilityTest and generates a timestamped health report.
# Reports which sources are working and which are failing, with specific errors.
#
# Usage:
#   ./macos/scripts/check-extension-health.sh            # Run once
#   ./macos/scripts/check-extension-health.sh --watch     # Run every hour
#   ./macos/scripts/check-extension-health.sh --interval 300  # Run every 5 min
#
# Output:
#   - Terminal: Color-coded summary table
#   - HTML report: /tmp/anikku_health_report_TIMESTAMP.html
#   - JSON report: /tmp/anikku_health_report_TIMESTAMP.json

set -euo pipefail

# ── Config ──────────────────────────────────────────────────────────────
PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
REPORT_DIR="/tmp"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_HTML="${REPORT_DIR}/anikku_health_${TIMESTAMP}.html"
REPORT_JSON="${REPORT_DIR}/anikku_health_${TIMESTAMP}.json"
JAVA_HOME="${JAVA_HOME:-$(brew --prefix openjdk@17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)}"
WATCH_MODE=false
INTERVAL=3600  # default: 1 hour

# ── Parse args ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --watch|-w) WATCH_MODE=true; shift ;;
        --interval) INTERVAL="$2"; shift 2 ;;
        --help|-h) echo "Usage: $0 [--watch] [--interval SECONDS]"; exit 0 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ── Colors ──────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ── Functions ───────────────────────────────────────────────────────────

run_health_check() {
    local check_start=$(date +%s)
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║     🩺 Anikku Extension Health Check                        ║"
    echo "║     $(date)                  ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    # Run the ExtensionCompatibilityTest
    echo -e "${BLUE}🔍 Running Extension Compatibility Test...${NC}"
    echo ""
    
    cd "$PROJECT_DIR"
    export JAVA_HOME="$JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    
    # Capture the test output and results
    local test_start=$(date +%s%N)
    local gradle_output=$(./macos/gradlew -p macos test --tests "app.anikku.macos.platform.extension.ExtensionCompatibilityTest" --no-daemon 2>&1 || true)
    local test_end=$(date +%s%N)
    local test_duration_ns=$((test_end - test_start))
    local test_duration_ms=$((test_duration_ns / 1000000))
    
    # Check if test passed
    if echo "$gradle_output" | grep -q "BUILD SUCCESSFUL"; then
        echo -e "${GREEN}✅ Test completed successfully${NC}"
    else
        echo -e "${RED}❌ Test FAILED${NC}"
        echo "$gradle_output" | tail -20
    fi
    
    # Read the XML test report to extract results
    local xml_report="${PROJECT_DIR}/macos/build/test-results/test/TEST-app.anikku.macos.platform.extension.ExtensionCompatibilityTest.xml"
    
    if [[ ! -f "$xml_report" ]]; then
        echo -e "${RED}❌ No test report found at $xml_report${NC}"
        return 1
    fi
    
    # Parse the XML report using Python
    local results_json=$(python3 << PYEOF
import xml.etree.ElementTree as ET
import json, re, sys

tree = ET.parse('$xml_report')
root = tree.getroot()

# Find system-out — it's a direct child of <testsuite>, NOT inside <testcase>
system_out = ''
so_elem = root.find('system-out')
if so_elem is not None and so_elem.text:
    system_out = so_elem.text

if not system_out:
    # Fallback: search anywhere in the tree
    for elem in root.iter('system-out'):
        if elem.text:
            system_out = elem.text
            break

if not system_out:
    print(json.dumps({'error': 'No system-out found'}))
    sys.exit(0)

# Parse the extension result lines
# Format: [✅] Aniyomi: extension_name | Browse: ✅ 26 | Episodes: ✅ 14 | Video: ✅ 8 | Title
results = []
line_count = 0
for line in system_out.split(chr(10)):
    # Match: optional whitespace, [icon], " Aniyomi: ", name, spaces, "| Browse: ", icon, count, ...
    # The name can contain spaces (e.g. "One Two Three" formats) so we match non-greedily up to " | Browse:"
    match = re.match(
        r'\s*\[(.)\]\s+Aniyomi:\s+(.+?)\s+\|\s+Browse:\s+(.)\s+(\d+)\s+'
        r'\|\s+Episodes:\s+(.)\s+(\d+)\s+'
        r'\|\s+Video:\s+(.)\s+(\d+)\s+'
        r'\|\s*(.*)',
        line
    )
    if match:
        line_count += 1
        load_status = match.group(1)
        name = match.group(2).strip()
        browse_status = match.group(3)
        try:
            browse_count = int(match.group(4))
        except ValueError:
            browse_count = 0
        episode_status = match.group(5)
        try:
            episode_count = int(match.group(6))
        except ValueError:
            episode_count = 0
        video_status = match.group(7)
        try:
            video_count = int(match.group(8))
        except ValueError:
            video_count = 0
        first_title = match.group(9).strip()
        
        # Determine overall health
        if browse_status == '✅' and video_status == '✅':
            health = 'working'
        elif browse_status == '✅' and episode_status == '✅':
            health = 'partial'
        elif browse_status == '✅':
            health = 'browse_only'
        elif browse_status == '⏱':
            health = 'timeout'
        elif browse_status in ('❌', '⚠'):
            health = 'error'
        else:
            health = 'unknown'
        
        results.append({
            'name': name,
            'load': load_status,
            'browse': browse_status,
            'browse_count': browse_count,
            'episodes': episode_status,
            'episode_count': episode_count,
            'video': video_status,
            'video_count': video_count,
            'first_title': first_title,
            'health': health,
        })

# Parse summary from the last lines of system-out
summary = {}
for line in system_out.split(chr(10)):
    s = line.strip()
    if 'Total extensions:' in s:
        try:
            summary['total'] = int(s.split(':')[1].strip())
        except (ValueError, IndexError):
            pass
    elif 'Loaded:' in s and 'Total extensions' not in s:
        try:
            summary['loaded'] = int(s.split(':')[1].strip())
        except (ValueError, IndexError):
            pass
    elif 'Browse' in s and ':' in s:
        try:
            summary['browsed'] = int(s.split(':')[1].strip())
        except (ValueError, IndexError):
            pass
    elif 'Episodes' in s and ':' in s:
        try:
            summary['episodes'] = int(s.split(':')[1].strip())
        except (ValueError, IndexError):
            pass
    elif 'Video URLs' in s and ':' in s:
        try:
            summary['videos'] = int(s.split(':')[1].strip())
        except (ValueError, IndexError):
            pass
    elif 'Elapsed' in s and ':' in s:
        try:
            val = s.split(':')[1].strip().rstrip('s')
            summary['elapsed_seconds'] = int(val)
        except (ValueError, IndexError):
            pass

output = {
    'timestamp': '$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u +"%Y-%m-%dT%H:%M:%S")',
    'test_duration_ms': $test_duration_ms,
    'results': results,
    'summary': summary,
}
print(json.dumps(output, indent=2))
PYEOF
)
    
    # Save JSON report
    echo "$results_json" > "$REPORT_JSON"
    echo -e "${GREEN}📄 JSON report saved: $REPORT_JSON${NC}"
    
    # Parse and display terminal report
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  📊 Extension Health Summary                                ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    
    local total=$(echo "$results_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('summary',{}).get('total', 0))" 2>/dev/null || echo "0")
    local loaded=$(echo "$results_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('summary',{}).get('loaded', 0))" 2>/dev/null || echo "0")
    local browsed=$(echo "$results_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('summary',{}).get('browsed', 0))" 2>/dev/null || echo "0")
    local episodes=$(echo "$results_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('summary',{}).get('episodes', 0))" 2>/dev/null || echo "0")
    local videos=$(echo "$results_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('summary',{}).get('videos', 0))" 2>/dev/null || echo "0")
    local elapsed=$(echo "$results_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('summary',{}).get('elapsed_seconds', 0))" 2>/dev/null || echo "0")
    
    echo -e "  ${CYAN}Extensions tested:${NC}    $total"
    echo -e "  ${GREEN}✅ Loaded:${NC}            $loaded"
    echo -e "  ${GREEN}✅ Browse works:${NC}      $browsed"
    echo -e "  ${GREEN}✅ Episodes work:${NC}     $episodes"
    echo -e "  ${GREEN}✅ Video URLs work:${NC}   $videos"
    echo -e "  ${BLUE}⏱  Test duration:${NC}     ${elapsed}s"
    echo ""
    
    # Working end-to-end
    echo -e "${GREEN}━━━ ✅ WORKING (Browse + Episodes + Video) ━━━${NC}"
    echo "$results_json" | python3 -c "
import json,sys
d=json.load(sys.stdin)
working = [r for r in d['results'] if r['browse'] == '✅' and r['video'] == '✅']
for r in sorted(working, key=lambda x: x['name']):
    print(f\"  ✅ {r['name']:<25} {r['first_title'][:35]} ({r['video_count']} videos)\")
print(f\"  Total: {len(working)}\")
" 2>/dev/null || echo "  (parse error)"
    echo ""
    
    # Partial (browse + episodes, no video)
    echo -e "${YELLOW}━━━ ⚠️  PARTIAL (Browse + Episodes, No Video) ━━━${NC}"
    echo "$results_json" | python3 -c "
import json,sys
d=json.load(sys.stdin)
partial = [r for r in d['results'] if r['browse'] == '✅' and r['episodes'] == '✅' and r['video'] != '✅']
for r in sorted(partial, key=lambda x: x['name']):
    print(f\"  ⚠️  {r['name']:<25} episodes={r['episode_count']:<4} video_status={r['video']}\")
print(f\"  Total: {len(partial)}\")
" 2>/dev/null || echo "  (parse error)"
    echo ""
    
    # Failing (browse failed)
    echo -e "${RED}━━━ ❌ FAILING (Browse failed) ━━━${NC}"
    echo "$results_json" | python3 -c "
import json,sys
d=json.load(sys.stdin)
failing = [r for r in d['results'] if r['browse'] in ('❌', '⏱', '⚠')]
for r in sorted(failing, key=lambda x: x['name']):
    print(f\"  ❌ {r['name']:<25} browse={r['browse']}  episodes={r['episodes']}\")
print(f\"  Total: {len(failing)}\")
" 2>/dev/null || echo "  (parse error)"
    echo ""
    
    # Generate HTML report
    python3 << PYEOF
import json, html

with open('$REPORT_JSON') as f:
    data = json.load(f)

results = data.get('results', [])
summary = data.get('summary', {})

html_parts = []
html_parts.append('''<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Anikku Extension Health Report</title>
<style>
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 20px; background: #1a1a2e; color: #e0e0e0; }
h1 { color: #e94560; }
h2 { color: #0f3460; margin-top: 30px; }
table { border-collapse: collapse; width: 100%; margin-top: 16px; font-size: 13px; }
th { background: #16213e; color: #e94560; padding: 10px 8px; text-align: left; font-weight: 600; }
td { padding: 6px 8px; border-bottom: 1px solid #0f3460; }
tr:hover { background: #16213e; }
.working { color: #4ecca3; font-weight: bold; }
.partial { color: #ffc107; font-weight: bold; }
.failing { color: #e94560; font-weight: bold; }
.timeout { color: #ff9800; font-weight: bold; }
.summary { background: #16213e; padding: 16px; border-radius: 8px; margin: 16px 0; }
.summary td { border: none; padding: 4px 16px; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: bold; }
.badge-working { background: #1a4a3a; color: #4ecca3; }
.badge-partial { background: #4a3a1a; color: #ffc107; }
.badge-failing { background: #4a1a1a; color: #e94560; }
</style>
</head>
<body>
<h1>🩺 Anikku Extension Health Report</h1>
<p>Generated: ''' + data.get('timestamp', 'unknown') + '''</p>
<div class="summary">
<table>
<tr><td><strong>Total Extensions:</strong></td><td>''' + str(summary.get('total', 0)) + '''</td><td><strong>Working:</strong></td><td><span class="badge badge-working">''' + str(len([r for r in results if r.get('health') == 'working'])) + '''</span></td></tr>
<tr><td><strong>Test Duration:</strong></td><td>''' + str(data.get('test_duration_ms', 0) // 1000) + '''s</td><td><strong>Partial:</strong></td><td><span class="badge badge-partial">''' + str(len([r for r in results if r.get('health') == 'partial'])) + '''</span></td></tr>
<tr><td></td><td></td><td><strong>Failing:</strong></td><td><span class="badge badge-failing">''' + str(len([r for r in results if r.get('health') in ('error', 'timeout')])) + '''</span></td></tr>
</table>
</div>
<h2>All Extensions</h2>
<table>
<tr><th>#</th><th>Extension</th><th>Health</th><th>Browse</th><th>#</th><th>Episodes</th><th>#</th><th>Video</th><th>#</th><th>First Anime</th></tr>''')

for i, r in enumerate(results, 1):
    health_class = r.get('health', 'unknown')
    css = 'working' if health_class == 'working' else 'partial' if health_class == 'partial' else 'failing'
    browse_css = 'working' if r.get('browse') == '✅' else 'failing'
    ep_css = 'working' if r.get('episodes') == '✅' else 'failing'
    vid_css = 'working' if r.get('video') == '✅' else 'failing'
    
    row = f'''<tr>
<td>{i}</td>
<td>{html.escape(r.get('name', ''))}</td>
<td class="{css}">{health_class}</td>
<td class="{browse_css}">{r.get('browse', '')}</td>
<td>{r.get('browse_count', 0)}</td>
<td class="{ep_css}">{r.get('episodes', '')}</td>
<td>{r.get('episode_count', 0)}</td>
<td class="{vid_css}">{r.get('video', '')}</td>
<td>{r.get('video_count', 0)}</td>
<td>{html.escape(r.get('first_title', ''))[:30]}</td>
</tr>'''
    html_parts.append(row)

html_parts.append('''
</table>
<p style="color: #666; margin-top: 20px;">Generated by Anikku Health Checker</p>
</body>
</html>''')

with open('$REPORT_HTML', 'w') as f:
    f.write(chr(10).join(html_parts))

print('HTML report generated')
PYEOF
    
    echo -e "${GREEN}📄 HTML report: $REPORT_HTML${NC}"
    echo -e "${GREEN}📄 JSON report:  $REPORT_JSON${NC}"
    
    local check_end=$(date +%s)
    local check_duration=$((check_end - check_start))
    echo ""
    echo -e "${BLUE}⏱  Health check completed in ${check_duration}s${NC}"
    echo ""
}

# ── Main ────────────────────────────────────────────────────────────────

if [[ "$WATCH_MODE" == "true" ]]; then
    echo -e "${CYAN}🔄 Watch mode enabled — checking every ${INTERVAL}s${NC}"
    echo -e "${CYAN}   Press Ctrl+C to stop${NC}"
    echo ""
    while true; do
        run_health_check
        echo -e "${BLUE}⏰ Next check in ${INTERVAL}s...${NC}"
        sleep "$INTERVAL"
    done
else
    run_health_check
fi
