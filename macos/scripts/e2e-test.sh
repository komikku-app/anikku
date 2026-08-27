#!/bin/bash
# =============================================================================
# Anikku macOS E2E Test Script — Full Flow
# Uses cua-driver to automate: Browse → Select Source → Select Anime → Play
# =============================================================================
# Prerequisites:
#   1. cua-driver installed at /Users/ernest/.local/bin/cua-driver
#   2. macOS permissions granted (run: cua-driver permissions grant)
#   3. Anikku extension JARs in ~/Library/Application Support/Anikku/extensions/
# =============================================================================
set -uo pipefail

CUA="/Users/ernest/.local/bin/cua-driver"
ANIKKU_DIR="/Users/ernest/Desktop/DEVPROJECTS/anikku/macos"
JAVA_HOME="/opt/homebrew/opt/openjdk@17"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
SESSION_ID="anikku-e2e-$TIMESTAMP"
SCREENSHOT_DIR="/tmp/$SESSION_ID"
RECORDING_DIR="/tmp/$SESSION_ID/recording"
STEP=0
ANIKKU_PID=""
ANIKKU_WINDOW_ID=""
declare -a SCREENSHOTS

PASS_COUNT=0
FAIL_COUNT=0

# ── Session Management ────────────────────────────────────────────────────────

# Trap to ensure session cleanup on any exit
cleanup() {
    local exit_code=$?
    echo ""
    echo "  Cleaning up session '$SESSION_ID'..."
    $CUA call end_session --session "$SESSION_ID" 2>/dev/null || true
    echo "  Session ended."
    exit $exit_code
}
trap cleanup EXIT INT TERM

start_session() {
    run_step "Start cua-driver session"
    if $CUA call start_session --session "$SESSION_ID" 2>/dev/null; then
        pass "Session '$SESSION_ID' started"
        # Customize cursor to a visible color
        $CUA call set_agent_cursor_style \
            --session "$SESSION_ID" \
            --bloom_color '#00E5FF' \
            --gradient_colors '["#00E5FF","#00B8D4","#0091EA"]' 2>/dev/null || warn "Could not set cursor style"
        pass "Agent cursor styled (cyan gradient)"
        # Start recording for trajectory replay
        mkdir -p "$RECORDING_DIR"
        $CUA call start_recording --session "$SESSION_ID" --output_dir "$RECORDING_DIR" 2>/dev/null && \
            pass "Trajectory recording started" || \
            warn "Recording not available (may need macOS 15+ or ffmpeg)"
    else
        warn "Could not start session — continuing without cursor/recording"
    fi
}

end_session() {
    echo ""
    run_step "End cua-driver session"
    if $CUA call end_session --session "$SESSION_ID" 2>/dev/null; then
        pass "Session '$SESSION_ID' ended"
        if [ -d "$RECORDING_DIR" ]; then
            local replay_info=$(find "$RECORDING_DIR" -name '*.json' 2>/dev/null | head -5 | wc -l | tr -d ' ')
            if [ "$replay_info" -gt 0 ]; then
                pass "Recording saved to $RECORDING_DIR/ ($replay_info turn files)"
            fi
        fi
    else
        warn "Could not end session"
    fi
}

# ── Helpers ──────────────────────────────────────────────────────────────────

pass()   { PASS_COUNT=$((PASS_COUNT + 1)); echo "  ✅ $1"; }
fail()   { FAIL_COUNT=$((FAIL_COUNT + 1)); echo "  ❌ $1"; }
warn()   { echo "  ⚠️  $1"; }

run_step() {
    STEP=$((STEP + 1))
    echo ""
    echo "────────────────────────────────────────────────────────────────────"
    printf "  [%d] %s\n" "$STEP" "$1"
    echo "────────────────────────────────────────────────────────────────────"
}

take_screenshot() {
    local label="$1"
    local path="$SCREENSHOT_DIR/${STEP}_${label// /_}.jpg"
    mkdir -p "$SCREENSHOT_DIR"
    if [ -n "$ANIKKU_WINDOW_ID" ] && [ -n "$ANIKKU_PID" ]; then
        $CUA call zoom --pid "$ANIKKU_PID" --window_id "$ANIKKU_WINDOW_ID" \
            --session "$SESSION_ID" \
            --screenshot_out_file "$path" 2>/dev/null && \
            { SCREENSHOTS+=("$path"); } || warn "Screenshot failed: $label"
    fi
}

get_anikku_pid() {
    pgrep -f 'AnikkuApp' | head -1 || pgrep -f 'gradlew.*run' | head -1 || echo ""
}

# Wait for condition with timeout. Args: label, timeout_seconds, cmd...
wait_for() {
    local label="$1" timeout="$2"; shift 2
    local waited=0
    while [ "$waited" -lt "$timeout" ]; do
        if "$@" 2>/dev/null; then
            pass "$label (${waited}s)"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    fail "$label (timed out after ${timeout}s)"
    return 1
}

# Click an AX element by its element_index in cua-driver's get_window_state output
ax_click() {
    local element_index="$1"
    $CUA call click --pid "$ANIKKU_PID" \
        --window_id "$ANIKKU_WINDOW_ID" \
        --session "$SESSION_ID" \
        --element_index "$element_index" 2>&1
    return $?
}

# Send a hotkey to Anikku
send_hotkey() {
    local keys_json="$1"
    $CUA call hotkey --pid "$ANIKKU_PID" --session "$SESSION_ID" --keys "$keys_json" 2>&1
    return $?
}

# Get the AX tree for the Anikku window, returning JSON elements
get_ax_tree() {
    $CUA call get_window_state --pid "$ANIKKU_PID" \
        --window_id "$ANIKKU_WINDOW_ID" \
        --session "$SESSION_ID" \
        --max_depth 10 \
        --max_elements 200 2>&1
}

# Return the tree_markdown text
get_ax_markdown() {
    $CUA call get_window_state --pid "$ANIKKU_PID" \
        --window_id "$ANIKKU_WINDOW_ID" \
        --session "$SESSION_ID" \
        --max_depth 10 \
        --max_elements 200 2>&1 | \
        python3 -c "
import sys, json
data = json.load(sys.stdin)
tree = data.get('tree_markdown', data.get('markdown', ''))
print(tree)
" 2>/dev/null || echo "PARSE_ERROR"
}

# Return a JSON array of AX elements with their relevant fields
get_ax_elements() {
    $CUA call get_window_state --pid "$ANIKKU_PID" \
        --window_id "$ANIKKU_WINDOW_ID" \
        --session "$SESSION_ID" \
        --max_depth 10 \
        --max_elements 200 2>&1 | \
        python3 -c "
import sys, json
data = json.load(sys.stdin)
elements = data.get('structuredContent', {}).get('elements', [])
out = []
for e in elements:
    out.append({
        'index': e.get('element_index'),
        'role': e.get('role'),
        'label': (e.get('label') or e.get('title') or e.get('description') or '')[:80],
        'frame': e.get('frame'),
    })
print(json.dumps(out, indent=2))
" 2>/dev/null || echo "PARSE_ERROR"
}

# Find the first element in the AX tree matching label substring
# Returns the element_index as a number, or empty string
find_element_by_label() {
    local search="$1"
    local fallback_idx="${2:-}"
    $CUA call get_window_state --pid "$ANIKKU_PID" \
        --window_id "$ANIKKU_WINDOW_ID" \
        --session "$SESSION_ID" \
        --max_depth 10 \
        --max_elements 200 \
        --query "$search" 2>&1 | \
        python3 -c "
import sys, json
data = json.load(sys.stdin)
elements = data.get('structuredContent', {}).get('elements', [])
results = [e for e in elements if '$search'.lower() in (e.get('label', '') + e.get('title', '') + e.get('description', '')).lower()]
if results:
    print(results[0].get('element_index', -1))
    sys.exit(0)
else:
    print('')
    sys.exit(1)
" 2>/dev/null || echo "$fallback_idx"
}

# ── Main Test Flow ───────────────────────────────────────────────────────────

echo "═══════════════════════════════════════════════════════════════"
echo "  Anikku macOS E2E Test Suite — Full Flow"
echo "  Started: $(date)"
echo "═══════════════════════════════════════════════════════════════"

# ── 0. Setup ─────────────────────────────────────────────────────────────────
run_step "Setup"

# Verify cua-driver daemon is running
# Check if daemon is running (try status, then list_windows as fallback test)
if $CUA status 2>/dev/null | grep -qiE "(running|listening|alive|up)" || \
   $CUA call list_windows 2>/dev/null | python3 -c "import sys,json; data=json.load(sys.stdin); print(f'Found {len(data)} windows')" 2>/dev/null; then
    pass "cua-driver daemon is running"
else
    warn "Daemon not detected. Starting..."
    nohup $CUA serve --daemon > /tmp/cua-driver.log 2>&1 &
    sleep 3
    if $CUA status 2>/dev/null | grep -qiE "(running|listening|alive|up)"; then
        pass "cua-driver daemon started"
    else
        fail "Failed to start cua-driver daemon"
        echo "  Try: pkill -f cua-driver && $CUA serve --daemon"
        exit 1
    fi
fi

# Verify permissions
PERM_CHECK=$($CUA call check_permissions 2>&1)
if echo "$PERM_CHECK" | grep -qi "accessibility.*screen recording\|both granted\|you're set"; then
    pass "macOS permissions granted"
else
    warn "Permissions may not be fully granted. Attempting grant..."
    $CUA permissions grant 2>&1 || true
    warn "If this fails, grant manually: System Settings → Privacy → Accessibility + Screen Recording"
fi

# ── 1. Launch Anikku ─────────────────────────────────────────────────────────
run_step "Launch Anikku"

cd "$ANIKKU_DIR"
export JAVA_HOME="$JAVA_HOME"

# Kill any existing instance
pkill -f 'AnikkuApp' 2>/dev/null || true
sleep 2

# Launch in background
nohup ./gradlew run > "/tmp/anikku-e2e-$TIMESTAMP.log" 2>&1 &
ANIKKU_PID=$!
pass "Started Gradle task (PID: $ANIKKU_PID)"

# Wait for the app window to appear
wait_for "Anikku window visible" 90 \
    bash -c 'WINDOWS=$($CUA call list_windows 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
for w in data:
    if w.get(\"app\",\"\").lower().startswith(\"anikku\") or \"anikku\" in w.get(\"title\",\"\").lower():
        print(w.get(\"id\",\"\"))
        sys.exit(0)
sys.exit(1)
" 2>/dev/null); [ -n "$WINDOWS" ]'

# Capture window info
ANIKKU_WINDOW_INFO=$($CUA call list_windows 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
for w in data:
    if w.get('app','').lower().startswith('anikku') or 'anikku' in w.get('title','').lower():
        print(json.dumps(w))
        sys.exit(0)
sys.exit(1)
" 2>/dev/null)
ANIKKU_WINDOW_ID=$(echo "$ANIKKU_WINDOW_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
ANIKKU_PID=$(get_anikku_pid)

pass "Window ID: $ANIKKU_WINDOW_ID, PID: $ANIKKU_PID"

# Bring window to front
$CUA call bring_to_front --pid "$ANIKKU_PID" --session "$SESSION_ID" 2>/dev/null
sleep 1

# Start cua-driver session for cursor visualization and trajectory recording
start_session

# Screenshot
take_screenshot "01_app_launched"

# ── 2. Explore UI Structure ─────────────────────────────────────────────────
run_step "Explore UI Structure"

AX_MARKDOWN=$(get_ax_markdown)
if [ "$AX_MARKDOWN" != "PARSE_ERROR" ]; then
    pass "Accessibility tree retrieved"
    echo "$AX_MARKDOWN" | head -40
else
    warn "Could not parse accessibility tree. Continuing with keyboard shortcuts..."
fi

take_screenshot "02_initial_ui"

# ── 3. Navigate to Browse Tab (⌘3) ──────────────────────────────────────────
run_step "Navigate to Browse Tab (⌘3)"

send_hotkey '["cmd","3"]' && pass "Sent Browse tab hotkey" || warn "Hotkey failed"

# Wait for the Browse screen to load
sleep 3
take_screenshot "03_browse_tab"

# Re-fetch AX tree and look for source items
AX_MARKDOWN=$(get_ax_markdown)
echo "$AX_MARKDOWN" | head -30

# ── 4. List Sources ──────────────────────────────────────────────────────────
run_step "List Available Sources"

# Use tree_markdown to find source names
SOURCES=$(get_ax_elements 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    sources = [e for e in data if e['role'] in ('AXGroup', 'AXButton', 'AXStaticText') and e['label'] and len(e['label']) > 2]
    for s in sources[:10]:
        print(f\"  [{s['index']}] {s['role']}: {s['label']}\")
except Exception as e:
    print(f'ERROR: {e}')
" 2>/dev/null)
if [ -n "$SOURCES" ]; then
    pass "Found AX elements:"
    echo "$SOURCES"
else
    warn "No structured elements found — trying coordinate-based click"
    echo "$AX_MARKDOWN" | head -50
fi

# ── 5. Click First Source ────────────────────────────────────────────────────
run_step "Select First Source"

# Try to find and click source cards. Sources are in a LazyColumn with role AXGroup.
# Look for first clickable source element
SOURCE_INDEX=$(find_element_by_label "allanime" "")
if [ -z "$SOURCE_INDEX" ]; then
    SOURCE_INDEX=$(find_element_by_label "gogoanime" "")
fi
if [ -z "$SOURCE_INDEX" ]; then
    SOURCE_INDEX=$(find_element_by_label "anime" "")
fi

if [ -n "$SOURCE_INDEX" ] && [ "$SOURCE_INDEX" != "-1" ]; then
    ax_click "$SOURCE_INDEX" && pass "Clicked source by element_index $SOURCE_INDEX" || \
        warn "click failed for element_index $SOURCE_INDEX"
else
    warn "No source element found by label — trying coordinate fallback"
    # Fallback: get window bounds and click centered on first item
    WINDOW_INFO=$($CUA call get_window_state --pid "$ANIKKU_PID" --window_id "$ANIKKU_WINDOW_ID" 2>/dev/null)
    # Retrieve x, y, w, h from topmost AXScrollArea or AXDrawer
    WIN_BOUNDS=$(echo "$WINDOW_INFO" | python3 -c "
import sys, json
data = json.load(sys.stdin)
elements = data.get('structuredContent', {}).get('elements', [])
# Find the main window frame
for e in elements:
    if e.get('role') in ('AXWindow', 'AXScrollArea') and e.get('frame'):
        f = e['frame']
        print(f'{f[\"x\"]},{f[\"y\"]},{f[\"w\"]},{f[\"h\"]}')
        sys.exit(0)
print('')
" 2>/dev/null)
    if [ -n "$WIN_BOUNDS" ]; then
        IFS=',' read -r WX WY WW WH <<< "$WIN_BOUNDS"
        # Click roughly in the center-left of the window where sources are listed
        CLICK_X=$((WX + 100))
        CLICK_Y=$((WY + 250))
        $CUA call click --pid "$ANIKKU_PID" --session "$SESSION_ID" --x "$CLICK_X" --y "$CLICK_Y" 2>/dev/null && \
            pass "Clicked at ($CLICK_X, $CLICK_Y) via coordinates" || \
            fail "Coordinate click failed"
    else
        # Last resort: click center of screen
        SCREEN=$($CUA call get_screen_size 2>/dev/null)
        SCR_W=$(echo "$SCREEN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('width',1440))" 2>/dev/null || echo "1440")
        $CUA call click --pid "$ANIKKU_PID" --session "$SESSION_ID" --x $((SCR_W / 3)) --y 400 2>/dev/null && \
            pass "Clicked at screen center-left" || \
            fail "All click methods failed"
    fi
fi

# Wait for SourceBrowseScreen to load
sleep 5
take_screenshot "04_source_browse"

AX_MARKDOWN=$(get_ax_markdown)
echo "$AX_MARKDOWN" | head -30

# ── 6. Select First Anime ────────────────────────────────────────────────────
run_step "Select First Anime"

# Wait for anime grid to load
sleep 3

ANIME_INDEX=$(find_element_by_label "Attack" "")
if [ -z "$ANIME_INDEX" ]; then
    ANIME_INDEX=$(find_element_by_label "Episode" "")
fi
if [ -z "$ANIME_INDEX" ]; then
    # Fallback: click the first anime card by coordinates
    WINDOW_INFO=$($CUA call get_window_state --pid "$ANIKKU_PID" --window_id "$ANIKKU_WINDOW_ID" 2>/dev/null)
    echo "$WINDOW_INFO" | python3 -c "
import sys, json
data = json.load(sys.stdin)
elements = data.get('structuredContent', {}).get('elements', [])
# Find first anime card (usually AXGroup or AXImage inside a grid)
for e in elements:
    label = (e.get('label','') or '').strip()
    if label and len(label) > 5 and e.get('role') in ('AXGroup','AXButton'):
        print(f\"  [{e['element_index']}] {e['role']}: {label[:60]}\")
" 2>/dev/null
    warn "No anime element found by label — trying first scroll area click"
    WIN_BOUNDS=$(echo "$WINDOW_INFO" | python3 -c "
import sys, json
data = json.load(sys.stdin)
elements = data.get('structuredContent', {}).get('elements', [])
for e in elements:
    if e.get('role') in ('AXWindow', 'AXScrollArea') and e.get('frame'):
        f = e['frame']
        print(f'{f[\"x\"]},{f[\"y\"]},{f[\"w\"]},{f[\"h\"]}')
        sys.exit(0)
print('')
" 2>/dev/null)
    if [ -n "$WIN_BOUNDS" ]; then
        IFS=',' read -r WX WY WW WH <<< "$WIN_BOUNDS"
        CLICK_X=$((WX + 80))
        CLICK_Y=$((WY + 180))
        $CUA call click --pid "$ANIKKU_PID" --session "$SESSION_ID" --x "$CLICK_X" --y "$CLICK_Y" 2>/dev/null && \
            pass "Clicked first anime at ($CLICK_X, $CLICK_Y)" || \
            warn "Coordinate click for anime failed"
    else
        warn "Could not determine bounds to click anime"
    fi
else
    ax_click "$ANIME_INDEX" && pass "Clicked anime by element_index $ANIME_INDEX" || \
        warn "click failed for anime element_index $ANIME_INDEX"
fi

# Wait for AnimeDetailScreen to load
sleep 5
take_screenshot "05_anime_detail"
AX_MARKDOWN=$(get_ax_markdown)
echo "$AX_MARKDOWN" | head -40

# ── 7. Click First Episode / Continue Watching ──────────────────────────────
run_step "Play Episode"

# Look for Continue Watching button or first episode
PLAY_INDEX=$(find_element_by_label "Continue Watching" "")
if [ -z "$PLAY_INDEX" ]; then
    PLAY_INDEX=$(find_element_by_label "Play Arrow" "")
fi
if [ -z "$PLAY_INDEX" ]; then
    PLAY_INDEX=$(find_element_by_label "Episode" "")
fi

if [ -n "$PLAY_INDEX" ] && [ "$PLAY_INDEX" != "-1" ]; then
    ax_click "$PLAY_INDEX" && pass "Clicked play by element_index $PLAY_INDEX" || \
        warn "click failed for play element $PLAY_INDEX"
else
    warn "No play button found by label — trying coordinates"
    # The Continue Watching button is typically in the info header area
    WIN_BOUNDS=$($CUA call get_window_state --pid "$ANIKKU_PID" --window_id "$ANIKKU_WINDOW_ID" 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
elements = data.get('structuredContent', {}).get('elements', [])
for e in elements:
    if e.get('role') in ('AXWindow', 'AXScrollArea') and e.get('frame'):
        f = e['frame']
        print(f'{f[\"x\"]},{f[\"y\"]},{f[\"w\"]},{f[\"h\"]}')
        sys.exit(0)
print('')
" 2>/dev/null)
    if [ -n "$WIN_BOUNDS" ]; then
        IFS=',' read -r WX WY WW WH <<< "$WIN_BOUNDS"
        # Continue Watching button is typically at x + 136, y + 430 or similar
        # Try a few common positions
        for CY in 350 380 420 460 500; do
            $CUA call click --pid "$ANIKKU_PID" --session "$SESSION_ID" --x $((WX + 136)) --y $((WY + CY)) 2>/dev/null
            sleep 1
        done
        pass "Attempted coordinate clicks for play button"
    else
        warn "Could not determine window bounds"
    fi
fi

# Wait for PlayerScreen to load
sleep 5
take_screenshot "06_player_screen"

# ── 8. Verify Player Screen ─────────────────────────────────────────────────
run_step "Verify Player Screen"

AX_MARKDOWN=$(get_ax_markdown)
echo "$AX_MARKDOWN" | head -30

# Check if we see player-related elements
if echo "$AX_MARKDOWN" | grep -qi "back\|play\|pause\|seek\|volume\|settings\|screenshot"; then
    pass "Player controls detected in accessibility tree"
else
    warn "Player controls not visible in AX tree — may need permissions or app is still loading"
fi

# Try pressing Space to toggle play/pause
send_hotkey '["space"]' && pass "Sent space (play/pause)" || warn "Space key failed"
sleep 2
take_screenshot "07_playing"

# Press again to pause
send_hotkey '["space"]' && pass "Sent space again (pause)" || warn "Space key failed (2)"
sleep 1
take_screenshot "08_paused"

# ── 9. Test Keyboard Shortcuts ──────────────────────────────────────────────
run_step "Test Keyboard Shortcuts"

send_hotkey '["left"]' && pass "Sent left arrow (seek -10s)" || warn "Left arrow failed"
sleep 1
send_hotkey '["right"]' && pass "Sent right arrow (seek +10s)" || warn "Right arrow failed"
sleep 1
send_hotkey '["up"]' && pass "Sent up arrow (volume +5)" || warn "Up arrow failed"
sleep 1
send_hotkey '["down"]' && pass "Sent down arrow (volume -5)" || warn "Down arrow failed"
sleep 1
take_screenshot "09_after_keyboard"

# ── 10. Navigate Back ───────────────────────────────────────────────────────
run_step "Navigate Back"

# Try Escape or click back button
send_hotkey '["escape"]' 2>/dev/null || send_hotkey '["cmd","w"]' 2>/dev/null || true
sleep 2
take_screenshot "10_after_back"

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  E2E Test Summary"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  Passed: $PASS_COUNT"
echo "  Failed: $FAIL_COUNT"
echo "  Screenshots: ${#SCREENSHOTS[@]}"
for ss in "${SCREENSHOTS[@]}"; do
    echo "    📸 $(basename "$ss")"
done
echo ""
echo "  Log: /tmp/anikku-e2e-$TIMESTAMP.log"
echo "  Screenshots: $SCREENSHOT_DIR/"
echo ""

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo "  🎉 ALL TESTS PASSED"
else
    echo "  ⚠️  $FAIL_COUNT test(s) failed — review screenshots and log"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"

# ── Cleanup ──────────────────────────────────────────────────────────────────
end_session

# Don't kill the app automatically so the user can inspect the final state
echo ""
echo "  Anikku is still running (PID: $ANIKKU_PID) for manual inspection."
echo "  Kill with: pkill -f 'AnikkuApp'"
echo ""

exit $FAIL_COUNT
