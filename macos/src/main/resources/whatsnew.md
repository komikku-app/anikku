## v1.10.0 — sources you can trust

- Browse sorts working sources first, greys out dead ones, and shows a
  Recommended row of known-good sources; re-check health anytime
- Onboarding recommends real sources — tap Browse to open one directly
- The app now responds instantly after launch (health checks wait for Browse)
- Cloudflare bypass fixed after a force-quit (stale Chrome cleanup)
- ⌘K command palette — jump to any tab, screen or setting by typing
- Undo for Clear History, Clear Completed downloads and Backup delete
- History rows with missing titles show the episode name instead of ", , 01"
- Empty states everywhere, with a next action on every tab
- Watch Together rooms have live chat (app and browser)
- Per-source settings gear on installed extensions — values actually save now
- Player: pick an audio output device (DAC/headphones/HDMI), remembered

## v1.9.2 — one consistent look

- Watch Stats opened from Settings has a back arrow now
- Backup & Restore uses the standard top bar (title + back) like every other
  screen
- Onboarding got the app's usual top bar: the step's title is in the title
  bar and Skip moved to the top-right corner

## v1.9.1 — crash fix + back navigation

- Fixed the crash when starting playback from an anime page (Now Playing
  artwork loaded an image incorrectly)
- Every screen has a visible back button now — Downloads and Extensions got
  proper top bars
- Escape / ⌘[ go back from anywhere, and View ▸ Go Back (⌘[) works too
- Re-clicking the current tab in the sidebar returns you to that tab's home —
  no more getting stuck on the Downloads screen from Settings

## v1.9.0 — the big polish update

- Escape / ⌘[ goes back from any screen — no more clicking arrows
- Quality switcher in the player (your pick is remembered)
- Resume straight from History with one click
- Theme mode (System/Light/Dark) in Settings
- Watch Together: hosts can lock controls and kick members, guests can
  change playback speed, and magnet rooms deep-link into the app
- Auto-download new episodes (global switch + per-anime toggle)
- Choose your download folder; live torrent progress in the Torrents tab
- Extension repos persist; update channels (Stable/Beta); in-app release notes
- Tracker sessions that expire now tell you to sign in again

## v1.8.5 — trust fixes

- Volume and playback speed now persist between sessions
- "Simultaneous downloads" actually limits concurrent downloads now
- Extension repo failures show a real error with Retry, not a fake empty list
- Global search tells you when every source failed and why
- Updates tab shows real progress — no more phantom "Checking…" state
- Corrupted databases are detected and repaired at startup, with a backup

## v1.8.4 — settings layout fixed

- The settings screen was rendering its rows on top of each other — squashed,
  text over text. Every section now lays out correctly; collapse/expand and
  search still work.

## v1.8.3 — fullscreen for browser guests

- The join page (phone or desktop) has a fullscreen button — maximize the
  watch party player on any device

## v1.8.2 — browser guests can finally see the video

**Fixes the black screen on Android/browser guests**
- HLS streams (what most anime sources serve) now play in the browser —
  bundled hls.js + playlists routed through the tunnel so segments load
  with the host's headers
- If the host plays a format browsers can't decode (MKV/HEVC), the join
  page says so clearly — join from the Anikku app to watch those

## v1.8.1 — internet Watch Together fixed

**This is the fix that makes cross-network rooms actually work**
- Guests (browser or app) were connecting and instantly getting
  "Disconnected" — a handshake incompatibility with the tunnel. Fixed
  and verified through a real Cloudflare tunnel end-to-end.
- Each room now gets its own fresh tunnel link; ending the room drops it.

## v1.8.0 — Watch Together goes worldwide

**Watch together from anywhere**
- Hosting a room now works across the internet — no accounts, no setup
- The room dialog shows a share link: anyone with it joins, from any country
- Browser friends open the link; Anikku friends paste it (or the code)
- Still works on your local network exactly like before — it falls back
  automatically when there's no internet

**Details**
- Share links are TLS-secured through a bundled Cloudflare tunnel
- Browser guests connect over wss — no more mixed-content blocks

## v1.7.0 — The polish release

**Fixes**
- Downloads search now actually filters your queue
- History and Updates refresh live while the app stays open
- Episode selector in the player no longer overflows on long series
- About screen shows the real version instead of "1.0.0"

**Better first run**
- Onboarding now applies your theme instantly and shows your installed sources

**Discoverability**
- Anime pages: ⌘D library · ⌘E share · ⌘⇧C copy URL · ⌘⇧O open in browser
- The app remembers which tab you were on and restores it next launch
- Tooltips on every icon button

**Features surfaced**
- Download on Wi-Fi only setting
- Download status (queued / progress / error) on episode rows
- Folder-scan progress with cancel
- Live progress while checking for updates
