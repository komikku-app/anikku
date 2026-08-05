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
