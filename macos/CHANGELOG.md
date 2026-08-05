# Changelog — Anikku macOS Port

All notable changes to the macOS port of Anikku are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.8.1] — 2026-08-05

### Fixes — internet Watch Together actually works now

- **WebSocket handshake over the tunnel fixed.** The room server's 101
  response carried `Content-Length: 0`, which RFC 7230 forbids on 1xx —
  Cloudflare's edge treated it as end-of-response and dropped every
  tunneled WebSocket connection right after the handshake. Guests (browser
  or app) connected and immediately got "Disconnected". The server now
  serves a clean 101, verified end-to-end through a real Cloudflare tunnel.
- **One fresh tunnel per room.** Each new room spawns its own cloudflared
  tunnel (new random URL); ending the room drops it. Re-hosting the same
  episode generates a new tunnel every time.

## [1.8.0] — 2026-08-05

### Watch Together — over the internet, zero setup

**Hosting a room now works anywhere**
- Rooms are exposed to the internet through a bundled Cloudflare tunnel
  (`cloudflared` ships inside the app — no accounts, no domains, no port
  forwarding, nothing to install). Guests across the world join through a
  share link: `https://<tunnel>/room/<CODE>`.
- The share link is shown right in the room dialog — copy it to anyone,
  browser or Anikku guest, anywhere.
- Same-network behavior is untouched: LAN discovery by code still works, and
  if the tunnel can't start (offline, binary unavailable) rooms silently fall
  back to the exact same local flow as before.

**Joining is link-first**
- The room dialog accepts a full share link or a bare code; links join
  directly (TLS), codes still find the room on your network.

**Under the hood**
- The browser join page now mirrors the page scheme (wss over https), so
  tunnel-hosted browser guests aren't blocked by mixed-content rules.
- The tunnel is pinned and checksum-verified at build time like the other
  bundled native helpers; the process is watched and torn down at quit.

## [1.7.0] — 2026-08-05

### The polish release

**Fixes**
- Downloads search now actually filters the queue (typing did nothing).
- History and Updates refresh live while the app stays open — no more
  restarting to see what you just watched.
- Episode selector in the player is windowed for long series: no more
  overflow on One Piece-length shows, and pills are finally clickable.
- About screens show the real version instead of a hardcoded "1.0.0".
- The "⋯" menu on library cards is visible in light mode again.
- Errors no longer leak raw exception text / gradle commands / "demo mode"
  language; failed downloads say why.

**Onboarding is real setup now**
- Pick a color scheme and Light/Dark/Auto on the Appearance step — applied
  instantly, not just described.
- The Sources step shows your live installed-source count and jumps straight
  to Browse.

**Discoverability**
- Anime pages: ⌘D library · ⌘E share · ⌘⇧C copy URL · ⌘⇧O open in browser
  (previously advertised but not wired).
- The app restores your last tab on launch.
- "What's New" dialog after every update — release notes ship in the app.
- Tooltips on every icon-only row button (downloads, episodes, queue).
- Playback menu greys out when no video is open.

**Features surfaced**
- "Download on Wi-Fi only" setting (existed, had no UI).
- Episode rows show real download state: queued / downloading with progress
  bar / paused / done / error.
- Folder scans show live progress with a Cancel button.
- Library section in Settings is no longer an empty shell; settings search
  matches setting names, not just section headings.
- Consistent empty states across source browse and the source filter.

**Housekeeping**
- Dead code removed (mock data moved to tests, unused i18n/scroll components,
  duplicate imports), emoji replaced with proper icons, mobile "tap" wording
  replaced with desktop copy.

## [1.6.1] — 2026-08-05

### Watch Together — real sync, real guests

- **Join where the host is**: the room server now replays the host's current
  position + play state to every new member the instant they join, so a guest
  lands exactly at the host's spot instead of flashing from 0:00. The macOS
  app guest also starts its first load at that position.
- **Guests stay connected**: the server's per-socket read timeout (5s) was
  silently killing any member who stopped sending — a guest who's just
  watching got disconnected seconds after joining. Timeout raised to 120s and
  both clients heartbeat (OkHttp pings / browser JSON ping) — the room stays
  alive for the whole watch.
- **Working browser controls**: the join page got custom play/pause, ±10s
  skip buttons and a seek slider backed by the host's real duration (sent in
  every sync), so the timeline works even when the stream itself reports no
  duration. Seeks are queued until the video metadata loads and retried until
  they stick (browsers silently drop early seeks).
- **Name popup on join**: opening the link assigns a random name instantly,
  then prompts for your own (any name, emojis welcome); ✕/Esc keeps the
  random one. The macOS dialog has a name field too.
- **No more message storms**: the browser page no longer echoes media events
  back as play/pause/seek (some engines mark programmatic events as
  user-initiated, which looped the room at 1 Hz).

### PiP window

- Seek slider fixed: it now spans the full episode duration with the current
  position as the thumb, so you can click/drag anywhere ahead to skip there
  (previously the bar always rendered full and dragging only sought within
  0–1 seconds).


## [1.6.0] — 2026-08-02

### Polish: tab state & window persistence

- Switching tabs no longer destroys anything: every tab keeps its search,
  filters, scroll position and pushed screens, and Discover/Torrents load once
  at startup instead of re-fetching on every switch (all 9 tabs stay composed).
- Window size and position are remembered across launches.

### Polish: first-run & empty states

- Onboarding gains a Skip button, accurate shortcut tips (⌘1–⌘9, F/G/S/M…),
  and a real "Add Sources" step (replacing the placeholder storage screen).
- Every empty state now leads somewhere: Library → Browse sources, Downloads →
  Find something to watch, Browse/Torrents → Open Extensions.
- Discover got a proper empty state, and Discover/Torrents/AnimeDetail errors
  now have Retry buttons.

### Polish: player feel

- Buffering mid-playback shows a spinner; volume changes show a transient OSD.
- Updated shortcut hint bar; new keyboard-shortcuts reference dialog (`?` in
  the player and Help > Keyboard Shortcuts…).
- End-of-episode shows Replay / Next episode; Esc exits fullscreen or goes back.
- The window title follows the playing episode.

### Polish: PiP controls

- The PiP window now has its own play/pause, seek slider and mute — fully
  controllable while another app is frontmost.

### Polish: Now Playing + capture

- The anime cover shows in Control Center's Now Playing widget.
- GIF clip length is settable (3/5/10s in Settings) and capture runs off the
  UI thread with a "Capturing…" indicator (no more freeze).

### Polish: visual consistency

- History, Updates and Downloads rows now show cover art (with initials
  fallback, including on failed image loads).
- Hover tooltips on all player icon buttons; AnimeDetail share moved to ⌘E
  (⌘S belongs to the sidebar).

### Polish: downloads power tools

- Search in the Downloads tab, Pause all / Resume all batch actions, and a
  working Play button from Settings > View Downloads.

### Polish: settings cleanup

- Removed 5 dead toggles and "Download on Wi-Fi only" (a desktop app).
- Settings sections are collapsible with a search field that filters and
  auto-expands matches.

### Watch Together: real sync + richer UX

- **Fixed**: pause/play/seek now actually propagate to everyone. Two root
  causes: guests never had their role set (so their actions were silently
  dropped — the big one), and the host's periodic sync could fight a just-made
  action. Guests now join as real members and a local-action guard prevents
  stale sync from undoing your pause/seek.
- Member names are shown in the room dialog, with Copy code / Copy link
  buttons; guests see "The host closed the room" instead of a silent
  disconnect; the host gets a warning before ending a room with watchers.

### Tests
- New end-to-end Watch Together session tests (pause propagation, seek relay,
  baseline sync, stale-sync guard, member names, host-leave notice) — the
  guard test caught the guest-role bug. Full suite: 755 green.

## [1.5.0] — 2026-08-02

### New: media keys + Now Playing (macOS-native)

- Playback is controllable from the keyboard's media keys, AirPods remote, and
  the Control Center / lock screen — play, pause, toggle, next/previous
  episode, and seek.
- The Now Playing widget shows "Anime — Ep N" with the artist, live position
  (updated ~2s) and correct play/pause state; it clears when you leave the
  player.
- Built on the public MediaPlayer framework via a new JNA Objective-C block
  bridge (`MPRemoteCommandCenter` + `MPNowPlayingInfoCenter`) — no private
  frameworks, no accessibility permissions.

### New: screenshot + 5-second GIF clip capture

- New GIF button in the player top bar (and the `G` key) captures the last
  ~5 seconds of playback as an animated GIF: decoded frames are downscaled to
  480px and assembled with javax.imageio — no external tools. Saved to
  `~/Pictures/Anikku/Anikku-Clip-<stamp>.gif`, with Open/Share buttons.
- The existing PNG screenshot button (camera icon / `S`) is unchanged.

### New: Watch Together rooms (LAN)

- Start a room from the player — guests on the same network join with a
  6-character code (auto-discovered via UDP beacon, no configuration).
- Play/pause/seek stay in sync for everyone; the host broadcasts its position
  once per second and guests reconcile drift. Anyone can control.
- The host's episode streams to guests through the room server — local
  downloads/files play directly, online streams are proxied with byte-range
  support — and a browser page at `http://<host>:18234/room/<code>` works for
  friends without the app (mp4/HLS; MKV/torrents need the app).
- Room code + watcher count show as a chip in the player top bar.

### Tests
- 19 new tests: animated-GIF writer/recorder, Watch Together protocol
  round-trips, room server (relay, membership, late-joiner episode push,
  unknown-room rejection, join page, local-file ranges, upstream proxy with
  Range/header passthrough), UDP discovery, and the Now Playing bridge
  lifecycle. Full suite: 749 green.

## [1.4.3] — 2026-08-02

### Fix: download tracker didn't update live (stuck at 0%)

- **Root cause**: the chunk loop updated the repository's `downloadedBytes` /
  `progress` on every 32 KB chunk, but never re-published the `downloads`
  StateFlow the UI collects — the value only appeared once a status change
  (like pause) triggered a refresh. The tracker showed 0% the whole time.
- **Fix**: the chunk loop now re-publishes the downloads list on a ~200 ms
  throttle (plus a final publish when the stream ends), so the percentage and
  MB remaining tick up live in the Downloads tab. The HLS/DASH manifest path
  got the same live-progress publish.

### Fix: pause → resume restarted the download from 0%

- **Root cause**: `pause()` deleted the in-flight partial file, so `resume()`
  always started over from the beginning — even if you'd downloaded 50%.
- **Fix**: pausing now preserves the partial file (`.name.resume.part`) and
  records its path on the entry. Resuming reuses it and continues with an
  HTTP `Range: bytes=<offset>-` request:
  - `206` → append the remainder to the partial (resume works);
  - `200` → the server ignored the Range, so the file is restarted cleanly;
  - `416` → the source file changed and no longer covers our offset → error.
  A cancellation race was also closed: a late-arriving cancel after pause can
  no longer delete the preserved partial before the resume uses it.

### Tests
- New resume test: seeds a PAUSED entry with a preserved partial, serves a
  `206 Partial Content` remainder, and asserts the resume request carried the
  correct `Range` header, the final file equals the full content, and the
  recorded partial is cleared. Full suite: 732 green.

## [1.4.2] — 2026-08-02

### Fix: streaming downloads saved a 12 KB text file, not the episode

- **Root cause**: most streaming sources expose HLS/DASH manifests (`.m3u8` /
  `.mpd`), and the download manager saved the manifest URL's response raw — a
  small text playlist, not the video. Logged as "Download complete (12.7 KB)"
  and the file wouldn't play ("couldn't resolve video URL").
- **Fix**: the download manager now (1) prefers a source's direct media URL
  (`.mkv`/`.mp4`/…) over a manifest when both are offered, and (2) when a
  manifest is all a source provides, downloads it properly:
  - **HLS**: follows the master playlist → picks the highest-bandwidth variant →
    downloads every segment (plus AES-128 keys and fMP4 init segments) →
    writes a local playlist.
  - **DASH**: parses the MPD, picks the best video representation, downloads the
    init + all numbered segments → writes a local playlist.
  - Verified end-to-end with a real episode: the download manager produced a
    genuine 78 MB Matroska file, and the DASH path decoded a full 23:46 episode
    (1080p AV1 10-bit) and the HLS path a 1080p stream — both via mpv.

### Fix: player arrows (←/→ and J/L) sometimes didn't seek

- Seeking was gated on `totalSeconds > 0`, so the keys did nothing during the
  first moments of playback before mpv reported the duration.
- Clicking player control buttons also stole keyboard focus, after which the
  arrows stopped working until the video was clicked again. Clicking the video
  now returns focus to the player, and absolute seeking works immediately.

### Fix: window X (close) sometimes didn't quit the app

- `onCloseRequest` ran `app.onShutdown()` and only then `exitApplication()` —
  if any teardown step threw (Discord RPC, Sparkle, TorrServer…), the close
  request died and the window stayed stuck. Shutdown is now best-effort and
  the app always exits (Dock/⌘Q quit fixed the same way).

### Tests
- 5 new HlsDashDownloader tests (MockWebServer: HLS master/variant/segments,
  encrypted keys, DASH fMP4, token-directory resolution, manifest detection).
  Full suite: 729 green.

## [1.4.1] — 2026-08-02

### Fix: downloads failed instantly (all attempts were torrent episodes)

- **Root cause**: the Nyaa/torrent sources resolve every episode to a `magnet:`
  link, but the download manager tried to fetch it with OkHttp, which rejected
  the scheme (`Expected URL scheme 'http' or 'https' but was 'magnet'`) —
  every download errored immediately, and retry failed the same way.
- **Fix**: magnet downloads now route through the torrent engine (bundled
  TorrServer, WebTorrent fallback): the engine produces a local HTTP stream
  that the normal download loop saves with live progress, then the torrent is
  torn down — the file stays on disk and plays offline like any download.
  Verified end-to-end with a real magnet (TorrServer stream → 200 → bytes
  flowing → clean stop).
- **Also**: downloads of stream URLs now send a fallback User-Agent (parity
  with the player) so CDNs that reject header-less requests stop 403ing.

### Fix: Picture-in-Picture icon toggled but no window appeared

- **Root cause**: `MacOSPipHandler.isPipVisible` was a plain field — flipping
  it never invalidated Compose, so the `PipWindow` composable never re-entered
  composition (the icon changed via a stale param, the window never existed).
- **Fix**: it's now backed by Compose state; toggling opens/closes the PiP
  window for real.

### Fix: skip-intro button never appeared for some episodes

- Hardened the AniSkip fetch (try/catch + diagnostic log) and, for torrent
  playback, the title is now cleaned before the AniList lookup
  ("[SubsPlease] Frieren - 01 (1080p) [ABC]" → "Frieren"), which previously
  made the MAL-ID resolution fail for anything watched through the Torrents
  tab's detail flow. If a show genuinely has no AniSkip data, the button stays
  hidden by design (per-episode times are real, never hardcoded).

## [1.4.0] — 2026-08-02

### New: Skip intro/outro (AniSkip)

- The player now fetches real intro/outro/recap skip windows from the free
  AniSkip API (keyed by the MyAnimeList ID, resolved automatically from the
  episode via AniList — no login or API key needed).
- A **Skip Intro / Skip Outro / Skip Recap** button appears while playback is
  inside a skip window (with a live countdown) and seeks past it on click.
- The existing **"Skip intro"** setting now means auto-skip: with it on, intros
  are skipped automatically once per episode (outros never auto-skip).
- Works identically for stream, torrent, download, and local playback.

### New: Downloads tab

- In-app downloads now have their own sidebar tab (Library, Updates, History,
  Stats, Browse, Torrents, **Downloads**, Discover, More — ⌘7) instead of being
  buried under Settings > Downloads.
- Completed downloads get a **Play** button; pause/resume/retry/clear-all work
  exactly like before (the Settings screen still shows the same queue).
- Playback resolves the downloaded file locally (no re-fetch), falling back to
  the source stream if the file was deleted.

### New: Local video collection

- **Add Folder…** in the Library toolbar imports any folder of video files.
  Filenames are parsed with the same release-name parser as torrents
  ("Show - 01 (1080p).mkv", "Show S01E05.mkv", batch ranges) and grouped into
  anime with all seasons/episodes.
- A **Local Videos** row appears in the Library; opening a show lists every
  episode by file, with sizes, and plays it offline through the local HTTP
  server — with full resume, history, and tracker progress sync.
- Remove an anime from the collection with the trash action on its screen.

### New: Airing schedule + Discover feed

- **Discover** tab (⌘8): an **Airing This Week** schedule from AniList's public
  API — every show airing in the next week grouped by day, with live
  "in 3h 24m" countdowns for today's episodes.
- **Trending Now** and **Current Season** charts, plus **Because You Watched…**
  recommendations when you're logged into AniList.
- Tapping any entry opens the anime detail screen, which auto-finds it in your
  installed sources so you can play it immediately.

### Tests
- 29 new tests (AniSkip client, local repo/scanner/grouper, Discover season
  math, AniList schedule/trending/seasonal/recommendations). Full suite: 724 green.

## [1.3.3] — 2026-08-02

### Fix: torrent episodes wouldn't play ("failed to fetch episodes: lateinit property title has not been initialized")

- **Root cause**: the player's load effect built the request anime with only its
  URL set. The Nyaa extension reads `anime.title` inside `getEpisodeList`
  (it becomes the episode name), so the unset Kotlin `lateinit` property threw
  `UninitializedPropertyAccessException` and playback died with the toast above
  — the episode list rendered on the detail screen, but the player's own
  re-fetch crashed.
- **Fix**: the player now sets `title` on the request anime (falls back to
  "Unknown"), matching what the detail screen already did. Torrent magnets now
  play from both the Torrents tab and the Nyaa Browse extension.

### New: grouped torrent browsing (Nyaa → AniList → seasons/episodes)

Searching Nyaa returns one result per release file, so "Death Note" used to
show dozens of single-episode cards. The Torrents tab now:

- **Parses every torrent filename** (group tag, quality, `S01E05`/`Season 2`/
  `2nd Season`, `Ep. 12`, `01-24` and `01 ~ 37` batch ranges, `v2`
  suffixes, file extensions, multi-title rows, fansub descriptors).
- **Groups releases by anime** (all seasons of one show fold into a single
  group) and sorts groups by how active they are.
- **Matches the top groups against AniList's public API** (no login needed) for
  canonical title, cover art, year/format, and synopsis — best-effort; unmatched
  titles still work from their parsed names.
- **Opens a per-anime menu**: header (cover + synopsis), each season as a
  section, one row per episode playing the best-quality release (4K > 1080p >
  720p > 480p), with the other releases of that episode one tap away. Batch
  releases and unparseable entries get their own sections, so nothing is hidden.
- Clicking any row streams the magnet through the existing TorrServer/WebTorrent
  engine as before.

### Tests
- 31 new tests: Nyaa filename parser (16), torrent grouping (8), AniList public
  search client (8, MockWebServer). Full suite: 695 green.

## [1.3.2] — 2026-08-04

### Fix: Browse/Extensions crash ("Companion") from an incompatible extension

- **Root cause**: the prebuilt Miruro extension was compiled against a Kotlin
  build of org.json and accesses `JSONObject.NULL` as
  `JSONObject$Companion.getNULL()`. The app's bundled org.json (stock JSON-java)
  has no such companion, so the extension threw `NoSuchFieldError: Companion`
  while the Browse tab was health-checking installed sources — an uncaught
  Error that crashed the app.
- **Fix**: org.json is now vendored into the app (public-domain JSON-java
  20231013) with a small Kotlin-companion shim (`JSONObject$Companion.getNULL()`
  returning the real `NULL` sentinel), so Miruro (and any future extension using
  the Kotlin org.json API) works. Verified against the packaged runtime's module
  set: `JSONObject.Companion.getNULL()` resolves and returns `JSONObject.NULL`.
- **Hardening**: the Browse-tab source health check now catches `Throwable`, so
  an incompatible or broken extension shows "Incompatible/Error" instead of
  killing the app.

---

## [1.3.1] — 2026-08-04

### Fix: Torrents tab crash ("java/net/http/HttpClient")

- **Root cause**: the packaged app's Java runtime is a minimized jlink image
  that does not include the `java.net.http` module. The Nyaa torrent extension
  used `java.net.http.HttpClient`, so opening the Torrents tab threw
  `NoClassDefFoundError: java/net/http/HttpClient` — which crashed the app.
- **Fix**: the Nyaa extension now fetches pages with `java.net.HttpURLConnection`
  (java.base — always present). Verified against the packaged runtime's exact
  module set: the extension loads and returns Nyaa search/popular results.
  The new extension jar is deployed automatically to your extensions folder.
- **Hardening**: the Torrents tab (and search) now catch `Throwable` around
  extension calls, so a failing extension shows an error message instead of
  killing the app. Also applied to the extension's popular/search loaders.

---

## [1.3.0] — 2026-08-04

### New Episode tracking, Watch stats, Torrents tab, MAL + Kitsu sync

**New Episode tracking + notifications**
- The background library check now feeds a **New Episodes** row at the top of
  the Library tab: shows which followed shows gained episodes since the last
  check (baseline-gated, so a first-ever scan never floods the feed).
- Newly discovered episodes fire **per-anime macOS notifications** (capped, and
  deduped against the feed) and set a **dock badge** with the feed count.
  Notifications can be toggled off in Settings > Data & Storage
  ("New episode notifications"); the cadence reuses the Library update
  schedule. Clicking a feed card opens the show (auto source-linking makes
  it playable); the 3-dot menu dismisses it from the feed.

**Watch stats dashboard**
- New **Stats** tab (and the existing Settings > Stats screen): total episodes,
  hours watched, current + longest daily streaks, most-watched anime, and a
  last-14-days activity bar chart — all derived from watch history.

**Torrents tab**
- New **Torrents** tab: popular + searchable catalogue from torrent-flagged
  extensions (e.g. Nyaa), streaming through the built-in engine (bundled
  TorrServer with WebTorrent fallback). The player now shows
  "Fetching torrent metadata…" while a magnet is being prepared.

**MyAnimeList + Kitsu tracker sync**
- Full 2-way library sync for **MyAnimeList** and **Kitsu**, mirroring the
  AniList model: pull imports the remote list (title-matched entries get
  `malId`/`kitsuId` attached instead of duplicating), push writes history-derived
  watched progress + status. Per-tracker sync controls + auto-sync intervals
  appear in Settings > Tracking when logged in.
- Kitsu search/update/username implemented (progress updates PATCH the library
  entry, per Kitsu's API); MAL scrobbling already worked and now pairs with a
  real library sync. Login needs your own app credentials (Manage Trackers >
  MyAnimeList/Kitsu — register at myanimelist.net/apiconfig and kitsu.io).

---

## [1.2.0] — 2026-08-04

### Auto source linking + playback fallback, player depth, season downloads + Library filters

**Auto source linking — AniList imports become playable on their own**
- After an AniList sync (manual or periodic), unlinked library entries are
  automatically searched across the installed extensions and linked to the
  best high-confidence title match — no manual source picker needed. Manual
  "Sync library now" reports how many were linked in the result toast.
- Opening an AniList-imported anime in the library now auto-matches a source
  on the detail screen ("Searching for a streaming source…"); if none is
  found, the manual "Link to a source" flow is offered as before.
- Matching is conservative (normalized-title equality or containment) so a
  wrong anime is never silently linked.

**Playback fallback — a dead source no longer ends the episode**
- When a source fails to resolve a video or mpv errors out mid-play, the
  player automatically tries the next installed extension that has the same
  anime (up to 2 auto-switches), relinking the library entry to the working
  source. A "Try another source" button continues manually after that.
- Episode matching across sources uses episode numbers (per-source episode
  URLs differ).

**Player depth**
- Default playback speed from Settings is now actually applied on open.
- Keyboard shortcuts: `[`/`]` speed presets, `,`/`.` subtitle-delay ±0.5s,
  `S` screenshot (hint bar updated).
- Screenshots now save to `~/Pictures/Anikku/` (subtitles included) and the
  toast reports the file name.
- Audio-delay and subtitle-delay panels gained −/+0.5s nudge buttons.

**Season downloads + Library filters**
- "Download all" button on the anime detail screen queues every episode
  (skips blank URLs, idempotent per episode).
- Download queue items that are finished now have a per-item Remove button
  (deletes the local file + entry).
- Library gained new sort modes — **Last Watched, Date Added, Progress** — and
  a progress filter row: All / In progress / Not started / Finished. The
  "Last Watched" sort is driven by watch history, so it updates as you watch.

---

## [1.1.6] — 2026-08-04

### AniList-synced entries are playable + Library layout rework

**Link a synced anime to a source (now playable)**
- Clicking an AniList-imported anime (no streaming source) now opens a **source
  picker**: the installed extensions are listed, picking one searches it for
  the anime's title, and the user picks the right match — the library entry is
  updated with that source's id/URL and the detail screen opens so episodes
  stream from there. Also reachable via "Link to a source" on the anime detail
  error state, and from Continue Watching cards for synced shows.

**Library layout**
- Continue Watching is now a **compact strip** (smaller covers, tighter
  spacing) so the saved-anime grid gets the full remaining height and is easy
  to scroll through — no more half-and-half fixed panes fighting for space.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.1.5] — 2026-08-04

### AniList sync fixed + 3-dot remove menus

**AniList library sync actually works now**
- The GraphQL request body had a **double-quoted username** (`JSONObject.quote`
  already adds quotes; the template wrapped them again), producing invalid JSON
  that AniList rejected with "No query or mutation provided" — sync silently
  imported nothing. Query construction is now a tested pure function
  (`TrackerManager.buildAniListLibraryQuery`).
- "Sync library now" (Settings → Tracking) now imports your AniList lists into
  the Library with covers + categories; auto-sync (12h/daily/weekly) pushes
  progress back.

**3-dot (⋯) remove menus across the app**
- **Library** grid cards + list rows: Remove from library, Remove from continue
  watching.
- **Continue Watching** row cards: Remove from continue watching, Remove from
  library.
- **History** entries: Delete from history, Remove from library.
- **Episode** rows (anime detail): Remove from continue watching, Remove download.
- History gained `removeForEpisode`/`removeForAnime`; a shared `OverflowMenu`
  component drives all of them.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.1.4] — 2026-08-04

### Keychain write bug fixed — OAuth sessions now actually persist

- Root cause of "logged in but app says not connected": `MacOSKeychain.store`
  passed the secret to `security add-generic-password` via **stdin** with a
  trailing `-w`. On macOS that makes `security` INTERACTIVELY PROMPT for the
  password ("password data for new item:") — the piped value only answers the
  first prompt, the retype prompt fails, and the keychain item is created with
  an **EMPTY password** while still exiting 0 (the app logged "stored (1846
  chars)" and believed it succeeded). Reads then returned null, so login
  statuses stayed "Not connected" and sessions never survived a restart.
- Secrets are now passed as the `-w` argument (verified round-trip via
  `security find-generic-password -w`). This fixes persistence for AniList
  tokens AND the same latent bug in every other keychain write (proxy
  password, subtitle credential overrides, Google Drive / SyncYomi tokens,
  app-lock PIN hash).
- Note: entries written by earlier builds stored empty values — re-do the
  affected action once (e.g. AniList login) to store the real secret.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.1.3] — 2026-08-04

### AniList OAuth callback fixed (Safari "can't connect to server")

- The authorize step worked, but the callback redirect (`localhost:8080/callback`)
  hit a dead port: NanoHTTPD binds the port from its **constructor**, not the
  `start()` argument, so the server was actually listening on a random port
  while the redirect claimed 8080. The server is now constructed with port 8080
  to match AniList's registered redirect.
- OAuth callback timeout raised from 120s to 300s so a slow authorize-page read
  doesn't shut the server down before the redirect lands.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.1.2] — 2026-08-04

### AniList OAuth fix + reliable resume playback

- **AniList login fixed**: the OAuth flow used a random loopback port
  (`http://127.0.0.1:<random>/callback`) while AniList's registered redirect is
  `http://localhost:8080/callback` — the mismatch was rejected as
  `invalid_client` / "Client authentication failed". AniList now uses the exact
  registered redirect (fixed port 8080) for both the authorize URL and the
  token exchange.
- **Resume playback made reliable**: history was matched only by the hashed
  episode URL, which can change between sessions (signed URLs, query params),
  silently breaking "continue from where I left off" for some shows. The player
  now falls back to matching by episode number when the id lookup misses.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.1.1] — 2026-08-04

### Launch crash fix + baked AniList client

- Fixed a crash on app launch: `Key "-1730808111" was already used` in the
  Library tab's Continue Watching row. Episode IDs are URL hashes that collide
  across anime; the LazyRow now keys on the unique `(animeId, episodeId)` pair.
- Baked in the developer's AniList OAuth client id/secret
  (`gradle.properties` → `anilist.clientId`/`anilist.clientSecret`), so tracker
  login is one click with no credential setup.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.1.0] — 2026-08-04

### Tier-1: AniList sync, downloads, auto-play, player polish

**AniList tracker — full 2-way library sync**
- OAuth login stays one click (baked client id/secret supported via
  `anilist.clientId`/`anilist.clientSecret` in `gradle.properties`; falls back
  to manual credential entry when unset).
- **Sync library now** in Settings → Tracking: imports your AniList lists
  (Watching/Completed/Plan to Watch/Dropped) into the Library with matching
  categories and covers.
- **Auto-sync** (Off / 12h / Daily / Weekly) runs a pull + push cycle in the
  background. Merge policy: progress is always the max of local and remote
  (never loses watch progress); status follows AniList on import and is pushed
  only for commit states (Completed/Dropped) or Watching when remote is unset.
- Auto-scrobble (existing) + manual "Link to tracker" still work; token
  refresh now uses the stored client credentials (was empty, so AniList
  tokens could never refresh).

**Downloads / offline**
- **Fix: downloads now send the source's stream headers** (Referer, User-Agent
  — many sources 403'd bare requests). The download resolves the same
  preferred-first `Video` the player uses and persists its headers.
- **"Download next 3"** button on the anime detail episode header (queues the
  next N undownloaded episodes).
- **Download queue storage summary**: downloaded size + free space, plus
  "Clear completed" (deletes files + entries).

**Auto-play next + Continue Watching**
- Auto-play next episode is now actually wired (was a dormant setting): on
  natural end, after 0.8s it loads the next episode. Toggle lives in Settings
  → Player.
- **Continue Watching row** on the Library tab: in-progress episodes (per
  anime, most recent first) with covers and a progress bar; click resumes
  playback.

**Player polish**
- Shortcuts: **K** play/pause, **J/L** −/+10s, **F** fullscreen, **M** mute
  (restores previous volume). Space/arrows unchanged.
- **Double-click video → fullscreen**; **mouse-wheel scroll → volume**.
- **Picture-in-Picture**: PiP button in the transport controls opens an
  always-on-top floating mini window mirroring the live playback.
- **Subtitle appearance**: font size + vertical position sliders in the
  player's Subtitles panel and in Settings → Player.
- **Load subtitle file…** in the Subtitles panel (`.srt`/`.ass`/`.ssa`/`.vtt`
  from disk).
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.0.5] — 2026-08-04

### History filter/sort + subtitle matching

- **History filters**: search box (title / episode) plus a Sort menu —
  Recently viewed, Oldest first, Title A→Z, Title Z→A, Episode number.
  Result count updates live; empty-search shows "no matches".
- **Season-aware subtitle matching tightened**: the episode matcher now
  understands Netflix-style continuation numbering — S2E1 (app reports "1")
  also matches files named "13" when Season 1 had 12 episodes (offset derived
  from AniList). It also stops false positives: "Season 2" markers and years
  like "(2025)" are no longer treated as episode numbers.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.0.4] — 2026-08-04

### Crash fixes

- Fixed History crash: `Key "-1730808111" was already used` when scrolling.
  Episode IDs are URL hashes that can collide across anime; the list now keys
  on the unique `(animeId, episodeId)` pair.
- Fixed "layouts are not part of the same hierarchy" crash when opening video
  settings (Subtitles) during playback. The root-level `SelectionContainer`
  made every `Text` selectable, including inside popups and the player's
  animated panels; a press across layout roots crashed Compose's selection
  manager. Copy actions already use explicit clipboard buttons, so the
  container was removed.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.0.3] — 2026-08-04

### Season-aware subtitle matching

- AniList resolution is now season-aware: the player picks the season whose
  episode range covers the requested episode (e.g. "Solo Leveling" episode 13
  resolves to Season 2, not Season 1). Falls back to the top title match for
  airing seasons and unknown episode counts.
- Removed the hardcoded `season_number=1` from OpenSubtitles searches, which
  would always match season 1 for multi-season shows.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.0.2] — 2026-08-04

### Subtitle credentials + resume fix

- Baked the developer's free Jimaku + OpenSubtitles credentials into the app
  (`gradle.properties` → generated `SubtitleDefaults.kt`) so automatic English
  subtitles work out of the box with zero end-user configuration.
- **Fixed resume playback**: the player now saves the watch position every 5s
  during playback (previously only on screen dispose, which app quit / force-quit
  could skip) and seeks back to the saved position when reopening an episode.
  Pause at 10:00, exit, reopen → resumes at 10:00.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

## [1.0.1] — 2026-08-03

### Maintenance release

- Added an `android.webkit.URLUtil` stub so more Keiyoushi extensions (e.g. StreamingCommunity) compile and load.
- Extended the extension build pipeline: okruextractor overload fix, per-extractor retry, cached-classpath fix, and support for newer `src/all` extensions (strips unsupported source-api overrides).
- Added new sources to the installed fleet: animexin, lmanime, subsplease, rouvideo, chineseanime, animeonsen, mkissa (allanime successor), animetsu, streamingcommunity.
- Added `scripts/refresh-extensions.sh` for repeatable fleet refresh + reporting.
- Fixed Sparkle auto-update startup: `SUPublicEDKey` now embeds the raw 32-byte
  Ed25519 key (not the DER/SPKI form Sparkle can't decode), which was causing
  "Unable to Check For Updates — the update checker failed to start correctly".
- Enabled Sparkle's `automaticallyChecksForUpdates` so silent background checks
  run on their own schedule instead of a manual `checkForUpdatesInBackground`.
- App version string now comes from `gradle.properties` (generated `AppInfo.kt`)
  instead of a hardcoded `1.0.0`, so internal version reporting matches the bundle.
- **Automatic English subtitles**: when a source serves no subtitle track, Anikku
  resolves the anime via AniList and fetches the exact episode's subs from Jimaku
  (anime-native database), cached per episode. OpenSubtitles.com is the fallback:
  the player's subtitle panel has a "Search OpenSubtitles…" entry that lists
  candidates English-first for the user to pick. Provider keys are baked in from
  `gradle.properties` (free tiers) and can be overridden in Settings (Keychain).
- **Smart offset**: manual subtitle-delay adjustments are remembered per anime and
  auto-applied to subsequent episodes.
- Rebuilt the DMG (ad-hoc signed; Developer ID/notarization still not applied).

---

## [Unreleased] — 2026-08-02

### Completed desktop architecture

- Completed and live-tested the real extension browse/search → details →
  episodes → video resolution → libmpv play/seek flow, including playback past
  30 seconds, deliberate failure/retry, and switching to a second episode on
  the same player.
- Added versioned startup migrations, secure SyncYomi synchronization, full
  Google Drive desktop OAuth/backup restore, native Discord Desktop IPC,
  LocalAuthentication/Touch ID, actionable Dock controls, and Keychain-only
  proxy credentials.
- Added Android gzip/protobuf `.tachibk` migration import for library,
  categories, history/progress, and custom anime metadata while retaining the
  lossless versioned macOS JSON format.
- Added ADRs and a detailed libmpv JNA ABI/threading/ownership reference.

### Reliability and security

- Removed JVM-global insecure TLS behavior and retained normal certificate and
  hostname verification.
- Hardened extension package/signature validation, duplicate-source handling,
  atomic replacement/rollback, trust state, and classloader cleanup.
- Added atomic persistence and malformed-data recovery across preferences,
  repositories, downloads, backups, and tracker tokens.
- Hardened the local media server against traversal, unauthorized methods,
  invalid ranges, stale routes, and shutdown races.

### Playback and controls

- Corrected libmpv ABI/render parameters and made renderer callbacks, native
  memory, resize/disposal, and repeated player lifecycle safe.
- Added authoritative playback-state handling, stale end-event rejection,
  exact local seek behavior, and click/drag/keyboard transport tests.
- Revalidated real extension-to-libmpv playback plus HTTP, HLS, DASH, and
  magnet/torrent stream paths.
- Enabled safe hardware decoding with `hwdec=auto-copy-safe`, bounded image
  caches, adaptive lazy library rendering, and high-bitrate playback/seek/heap
  regression coverage.
- Removed the old no-libmpv simulated watch-progress path; playback now fails
  visibly instead of reporting progress without rendered media.

### Native torrent streaming

- Pinned TorrServer `MatriX.141.1` for Apple Silicon and Intel, with HTTPS
  download, architecture-specific SHA-256 verification, executable packaging,
  localhost API integration, and a native launch/API regression task.
- Select the largest recognized video file from torrent metadata and retain a
  bounded WebTorrent fallback when the native helper cannot prepare a stream.

### Updates, packaging, and validation

- Restricted update checks and release links to authenticated HTTPS, added
  semantic version ordering and bounded retries, and made fallback behavior
  informational rather than an unverified installer.
- Added deterministic Sparkle helper shutdown and fixed its feed-string
  lifetime; active signed-enclosure verification remains pending owner signing
  credentials and a release artifact.
- Produced and structurally verified an unsigned Apple-silicon DMG containing
  the runtime, libmpv, Sparkle framework/helper, and application icon.
- Made the live extension fleet test enforce its documented per-stage timeout,
  preventing a blocking third-party provider from hanging Gradle.

This remains a development preview only because Developer ID signing,
notarization, and an active Sparkle-signed release enclosure require release
owner credentials/artifacts. Those release operations were not fabricated.

## [1.0.0-beta.2] — 2026-07-29

### Extension System — Major Compatibility Milestone

- **58 extension JARs pre-installed** (up from 20), 33 verified end-to-end
- **Extension compatibility test** exercises all 58 extensions through Load → Browse → Episodes → Video URL stages with Chrome CDP Cloudflare bypass
- **Core module fix**: Preferences.kt context!! null-safety — unblocked extensions depending on core utilities
- **ACTUAL_PKG detection fix**: Ported dot-sort algorithm from single-build script to batch script — fixes extensions picking up subpackage names (e.g., `.dto` instead of base package)

### Chrome CDP Cloudflare Bypass

- **ChromeCDPClient**: Auto-launches headless Chrome with anti-detection (hidden `navigator.webdriver`, real User-Agent, hidden `navigator.plugins`)
- **Referer pass-through**: CDP `Page.navigate` now accepts and forwards `Referer` header — critical for WCO embed domains
- **CloudflareInterceptor**: Detects hard WAF blocks vs JS challenges, 3-attempt retry with cookie caching
- **FallbackDns**: Included in shared-libs.jar for CDP bypass at runtime
- **CDP diagnostics**: ExtensionCompatibilityTest captures CDP debug logs, WAF detection summaries

### WCO Video Extraction — Fixed

- **Root cause found**: WCO embed pages serve a 10-second announcement interstitial (not the video player)
- **Fix**: URL rewrite `index.php` → `video-js.php` bypasses the countdown and fetches the actual player page
- **Content-based extraction**: Domain-agnostic `iframeParse` with 3 fallback paths (getJSON/ajax API, universal m3u8, atob/base64 obfuscation)
- **Error-resilient debug dump**: HTML dumps to `/tmp/wco-iframe-dump-*.html` even when HTTP/CDP fails
- **Result**: All 6 WCO extensions (wcoanimedub, wcoanimesub, wcoforever, wcofun, wcostream, wcotv) now return 16 video URLs each

### Build Pipeline Improvements

- **Batch build**: Auto-discovers and compiles extractors individually (3-pass) — avoids single broken extractor blocking all others
- **lib-multisrc themes**: Compiled individually instead of as one blob — fixes multisrc extensions producing 1-9KB JARs
- **Extractor webview patches**: Non-null `onPageFinished`/`shouldInterceptRequest` overrides for JVM compatibility
- **Extension-specific patches**: superstream (CloudflareInterceptor injection), miruro (JVM stubs), animekhor (vidhideextractor), animenosub (autoUnpacker), luciferdonghua (okruextractor)
- **Python patching system**: `patch-wco-video-extraction.py`, `patch-miruro-sources.py`, `patch-hanime-source.py`, `patch-kissanime-source.py`

### JVM Compatibility Fixes

- `android.net.Uri.decode()` — Lenient URL decoding
- `android.util.LruCache` — Added import for CinebyExtractor
- `OkruExtractor` — Explicit lambda params for `extractFromDash` overload resolution
- `AniDB.kt` — `sortVideos` override with preserved `setDefaultValue`
- `DopeFlix.kt` — `MutableSet<String>` via `.toMutableSet()`
- `keiyoushi-utils Json.kt` — `parseAs` uses `body.string()` instead of `body.source()` for JVM
- `NextJs.kt` — Non-exhaustive `when` fix
- `Preference.kt` stub — Added `setDefaultValue` + `setEnabled`

### Other

- CDP Cloudflare bypass enabled for video CDN domains (fxpy7.watching.onl, etc.)
- `shared-libs.jar` rebuilt with ChromeCDPClient referer + FallbackDns + CloudflareInterceptor
- Backup & Restore panel with create/timeline/restore UI
- Menu bar actions wired (undo/redo, file operations)
- Extension test updated from 45 to 52 (now 58) extensions

---

## [1.0.0-beta.1] — 2026-07-11

### Phase 12 — Documentation & Final Polish

- Created `macos/CHANGELOG.md` documenting full development history
- Updated `macos/README.md` with honest status table (Phase 10 marked ❌ Not started)
- Updated project root `README.md` with macOS port section
- Updated `macos/INSTALL.md` with references to CHANGELOG and architecture plan
- All `macos/docs/` guides reviewed and updated
- Staged and committed all outstanding changes (both modified and untracked files)

### Phase 10 — Packaging (Not Started)

- App icon `.icns` exists at `macos/src/main/resources/icons/`
- `entitlements.plist` created with proper Hardened Runtime permissions
- Sparkle appcast template exists at `macos/src/main/resources/Sparkle/`
- Sparkle Ed25519 public key exists at `macos/src/main/resources/Sparkle/`
- `libmpv.2.dylib` bundled at `macos/src/main/resources/dist/`
- jpackage DMG/PKG packaging NOT yet configured in `build.gradle.kts`
- Code signing and notarization NOT yet set up

### Phase 11 — Testing

- 40+ test files created across all subsystems (UI, platform, player, auth, etc.)
- Extension loading integration tests (SampleExtension, AllAnime, Keiyoushi)
- End-to-end flow tests NOT yet run (Browse → Search → Play)

### Phase 9 — macOS Native Integration

- **Menu bar**: Full macOS menu bar (File, Edit, View, Playback, Window, Help) with
  ⌘ keyboard shortcuts — `MacOSMenuBarFactory.kt`
- **Keyboard shortcuts**: Global shortcuts handler for app-wide hotkeys —
  `GlobalKeyboardShortcuts.kt`
- **File picker**: AWT-based file/directory picker — `MacOSFilePicker.kt`
- **PiP**: Picture-in-Picture support via secondary always-on-top window —
  `MacOSPipHandler.kt`
- **Dock**: Badge count and dock menu (Play/Pause, Next Episode) —
  `MacOSDockManager.kt`
- **Full screen**: macOS fullscreen toggle — `MacOSFullScreen.kt`
- **Share**: macOS native share via clipboard — `MacOSShareUtil.kt`
- **Dark mode**: Automatic detection via `isSystemInDarkTheme()`
- **Touch Bar**: Not implemented

### Phase 8 — WebView Replacement

- Replaced Android WebView with system browser launch via
  `java.awt.Desktop.browse(URI)` — `BrowserLauncher.kt`

### Phase 7 — Advanced Features

- **Tracker sync**: OAuth login flows with local HTTP server for callback handling —
  `TrackerManager.kt`, `TrackerOAuthManager.kt`, `TrackerTokenStore.kt`,
  `OAuthServer.kt`
- **Google Drive sync**: REST API v3 client — `GoogleDriveRestClient.kt`
- **Discord Rich Presence**: WebSocket-based Discord RPC —
  `DiscordRPC.kt`
- **Biometric auth**: Touch ID via `LocalAuthentication.framework` with PIN
  fallback — `MacOSBiometricAuth.kt`
- **Torrent support**: TorrentServer API client — `TorrentServerBridge.kt`
- **Notifications**: macOS Notification Center integration —
  `MacOSNotificationManager.kt`
- **App update checker**: GitHub Releases API — `AppUpdateChecker.kt`
- **Crash reporting**: Local crash log with uncaught exception handler —
  `CrashReporter.kt`
- **Download manager**: Queue-based download system — `MacOSDownloadManager.kt`
- **UI Action Logger**: Development logging for UI interactions —
  `UIActionLogger.kt`

### Phase 6 — Video Player (mpv via JNA)

- **MPVLib**: Full JNA bindings to libmpv C API — `MPVLib.kt`
  - Core: `create()`, `destroy()`, `initialize()`, `command()`
  - Options: `setOptionString()`, `setPropertyString/Int/Double()`
  - Reads: `getPropertyString/Int/Double/Flag()`
  - Events: `observeProperty()`, `event()`, `waitEvent()`
  - Render API: `mpv_render_context_create()`, `render()`, `free()`
- **MPVEventLoop**: Coroutine-based event processing for mpv property changes
  and events — `MPVEventLoop.kt`
- **MPVSoftwareRenderer**: Offscreen FBO render context pulling RGBA frames for
  Compose rendering — `MPVSoftwareRenderer.kt`
- **MPVVideoSurface**: Compose composable wrapping the software renderer for
  video display — `MPVVideoSurface.kt`
- **PlayerViewModel**: Central player state machine managing:
  - Playback lifecycle (IDLE → LOADING → PLAYING → PAUSED → ENDED → ERROR)
  - Position tracking with periodic updates
  - Volume, speed (0.25x–4.0x), fullscreen
  - Audio/subtitle track selection and delay
  - Video equalizer (brightness, contrast, saturation, gamma)
  - Aspect ratio, rotation, horizontal/vertical flip
  - Screenshot capture
  - mpv initialization with locale-safe configuration
- **Utils**: Time formatting utilities — `Utils.kt`
- **PlayerPreferences**: mpv config file management — `PlayerPreferences.kt`
- **MacOSHttpServer**: NanoHTTPd-based local HTTP server for streaming video to
  mpv — `MacOSHttpServer.kt`
- **FFmpegBridge**: FFmpeg binary wrapper for screenshot/transcoding —
  `FFmpegBridge.kt`

### Phase 5 — Screen-by-Screen UI

- **Library screen**: Grid/list view with category filtering, search, sort —
  `LibraryTab.kt`
- **Updates screen**: Grouped updates by date, mark all seen, refresh —
  `UpdatesTab.kt`
- **History screen**: Chronological history list with clear and resume —
  `HistoryTab.kt`
- **Browse/Sources screen**: Source list with language icons, search, extension
  management — `BrowseTab.kt`, `SourceBrowseScreen.kt`, `ExtensionsScreen.kt`
- **Anime Detail screen**: Cover image, info header, episode list with seen/
  bookmark/download/play — `AnimeDetailScreen.kt`
- **Player screen**: Full player UI with controls, settings panels, keyboard
  shortcuts — `PlayerScreen.kt`, `PlayerControls.kt`, `PlayerSettings.kt`
- **Settings screen**: 15 sub-screens (Appearance, Library, Downloads, Player
  Advanced, Data & Storage, Security, Tracking, Connections, etc.) —
  `SettingsScreen.kt`, `SettingsState.kt`
- **Tracker settings**: MAL/AniList/Kitsu login panels — `TrackerSettingsPanel.kt`
- **Download queue**: LazyColumn-based queue with download management —
  `DownloadQueueScreen.kt`
- **Stats screen**: Anime watching statistics — `StatsScreen.kt`
- **Onboarding**: First-launch setup flow — `OnboardingScreen.kt`
- **About dialog**: Version info, update check, credits — `AboutDialog.kt`

### Phase 4 — UI Framework & Navigation

- **Theme system**: Material 3 with 20 color schemes:
  Base, Cottoncandy, Cloudflare, Doom, GreenApple, Lavender, Matrix,
  MidnightDusk, Mocha, Monet, Nord, Sapphire, Strawberry, Tachiyomi, Tako,
  TealTurqoise, TidalWave, YinYang, Yotsuba, CustomColorScheme
- **Voyager navigation**: Tab navigator with per-tab inner navigators preventing
  ClassCastException on tab switch when non-Tab screens are on stack
- **NavigationRail**: Desktop left sidebar with 5 tabs (Library, Updates,
  History, Browse, More)
- **AnimatedTabFade**: Fade transition when switching tabs
- **Components**: Scrollbar, VerticalFastScroller, SettingsItems, AdaptiveSheet,
  AnimeCoverImage, CommonAnimeItem, MacOSToast, VideoQualityBadge,
  PlaybackStateBadge, OfflineBadge, ErrorUi, OfflineCheckmarkAnimation

### Phase 3 — Networking & Source API

- **MacOSNetworkHelper**: OkHttp client with cache, brotli, DoH, logging
  interceptors
- **MacOSCookieJar**: Persistent cookie store via `java.net.CookieManager`
- **MacOSExtensionLoader**: URLClassLoader-based extension loading with:
  - META-INF/extension.json parsing
  - SHA-256 trust verification
  - Shared dependency JAR injection
  - Per-source class instantiation with resilient error handling
- **MacOSExtensionManager**: Extension lifecycle management:
  - Installed extension scanning and loading
  - Trust store management (auto-trust on first launch, persistent
    `trusted_extensions.json`)
  - Source enumeration and deduplication
  - Extension install from repo URLs
  - Load state tracking (`StateFlow<ExtensionState>`)
- **ReflectiveSourceProxy**: Reflection-based source proxy for calling extension
  methods with hoster-based fallback for getVideoList
- **Source API stubs**: Source, CatalogueSource, ConfigurableSource, SAnime,
  SEpisode, Video, AnimesPage, Hoster, AnimeFilterList, etc.
- **DexClassLoader**: APK-to-JAR compatibility layer (d2j-dex2jar-based)
- **Android stubs for extension compatibility**:
  - `android.content.Context` — File-based directory access
  - `android.util.Base64` — Delegates to `java.util.Base64`
  - `android.util.Log` — Delegates to SLF4J
  - `android.os.Bundle`, `android.os.Looper`, `android.os.Handler` —
    JVM-compatible stubs
  - `android.graphics.Bitmap`, `android.graphics.BitmapFactory` —
    Delegates to AWT/Apache Commons
  - `android.net.Uri` — Delegates to `java.net.URI`
  - `android.annotation.SuppressLint` — With `AnnotationTarget.CLASS`
  - `android.text.InputType` — Common constants
  - `org.json.JSONObject`, `org.json.JSONArray` — Full JVM stubs
  - `eu.kanade.tachiyomi.animeextension.BuildConfig` — TMDB_API stub

### Phase 2 — Domain & Data Layer

- **MacOSStorageManager**: Download/backup management with file system
- **MacOSCustomAnimeRepository**: File-backed anime CRUD
- **LibraryRepository**: JSON-backed library persistence
- **HistoryRepository**: JSON-backed watch history
- **DownloadRepository**: Download tracking with state management

### Phase 1 — Core Infrastructure

- **Koin DI**: 3 modules (Platform, Domain, App) replacing Android's Injekt
- **MacOSPreferenceStore**: JSON-file backed preferences
- **MacOSDatabaseDriver**: SQLDelight JDBC SQLite driver
- **MacOSLogger**: SLF4J/Logback logging to file and console
- **AnikkuApplication**: Full app lifecycle (init, focus, blur, shutdown)
- **BackgroundTaskScheduler**: Coroutine-based replacement for Android WorkManager
- **CrashReporter**: Thread.setDefaultUncaughtExceptionHandler + Sentry log
- **Injekt bridge**: Koin delegation for extension compatibility

### Phase 0 — Project Scaffolding

- Created `macos/` directory structure as standalone Compose Desktop Gradle project
- Configured Gradle build with JVM toolchain 17, Compose Multiplatform 1.11.1
- Version catalog (`libs.versions.toml`) with all desktop-compatible deps
- Settings management with JitPack, Maven Central, Google repositories
- Built source-api-jvm.jar and common-jvm.jar from Android KMP modules
- Entry point (`AnikkuApp.kt`) with Window management

---

## [0.9.0] — 2026-07-10

### Build pipeline

- Created `batch-build-keiyoushi-from-source.sh` — builds 51 English extensions
  from yuzono/anime-extensions source as JVM JARs
- Created `patch-hanime-source.py` — removes Chicory/WASM/WebView files,
  patches Hanime.kt to use NativeSignatureProvider exclusively
- Created `patch-kissanime-source.py` — adds hosterListSelector/hosterFromElement
  stubs, fixes putString null-safety
- Created `patch-hoster-stubs.py` — generalized hoster stubs for all extensions
- Created `copy-extension-deps.sh` — copies extension runtime dependencies
- Created `e2e-test.sh` — basic end-to-end test script
- Extension compilation tracking: 10/51 English extensions build from source

### Extension fixes

- Fixed `SuppressLint` annotation for `object` declarations (hanime Base64Helper)
- Added `InputType` constants stub for hanime edit-text preferences
- Added `addEditTextPreference` / `getSwitchPreference` to keiyoushi-utils
- Created `BuildConfig.kt` stub for mapple extension
- Created `JSONObject.kt` / `JSONArray.kt` stubs for miruro extension
- Created `org.json` stubs for extensions needing JSON parsing
- Fixed `PreferenceScreenExt.kt` with both addSwitchPreference and
  getSwitchPreference stubs
- Hosted stubs generalized for all multisrc extensions

## [0.8.0] — 2026-07-09

### Extension system overhaul

- Rebuilt `MacOSExtensionLoader.kt` with proper shared dependency JAR injection
- Fixed class loader hierarchy to properly resolve source-api classes
- Added `findSharedLibsDir()` with multi-path search (dev vs bundled .app)
- Made source loading resilient — individual class failures skip rather than
  fail the entire extension
- Fixed `SourceProxy.kt` with hoster-based getVideoList fallback
- Fixed `MacOSExtensionManager.kt` with auto-trust on first launch
- Fixed `deployBundledExtensions` to compare by pkgName, not filename
- Deployed 20 extension JARs to extensions directory

## [0.7.0] — 2026-07-08

### Player & mpv

- Created `MPVLib.kt` — Complete JNA bindings to libmpv C API
- Created `MPVEventLoop.kt` — Coroutine-based mpv event processing
- Created `MPVSoftwareRenderer.kt` — Offscreen FBO render context
- Created `MPVVideoSurface.kt` — Compose composable for video display
- Created `PlayerViewModel.kt` — Full player state machine (1200+ lines)
- Created `PlayerScreen.kt` — Complete player UI with controls, settings panels
- Created `PlayerControls.kt` — Play/pause, seek, volume, speed controls
- Created `PlayerSettings.kt` — Advanced player settings panels
- Created `Utils.kt` — Time formatting utilities
- Created `PlayerPreferences.kt` — mpv config file management
- Created `MacOSHttpServer.kt` — NanoHTTPd local video streaming server
- Created `FFmpegBridge.kt` — FFmpeg binary wrapper

## [0.6.0] — 2026-07-07

### Screen-by-screen UI porting

- Ported all main screens: Library, Updates, History, Browse, More
- Created `AnimeDetailScreen.kt` with full source API integration
- Created all settings screens (15 sub-screens)
- Created `SourceBrowseScreen.kt` with search/browse
- Created `ExtensionsScreen.kt` with extension management
- Created `DownloadQueueScreen.kt`
- Created `StatsScreen.kt`
- Created `OnboardingScreen.kt`
- Created `AboutDialog.kt`
- Created all UI components

## [0.5.0] — 2026-07-06

### UI framework & navigation

- Set up Voyager tab navigator with per-tab inner navigators
- Created `MainWindow.kt` with desktop NavigationRail sidebar
- Ported 20 Material 3 color schemes
- Created `AnikkuTheme.kt` with dynamic theming
- Created all custom UI components

## [0.4.0] — 2026-07-05

### Network & extension loading

- Created `MacOSNetworkHelper.kt` with OkHttp client
- Created `MacOSCookieJar.kt` with persistent cookie storage
- Created `MacOSExtensionLoader.kt` — URLClassLoader-based loading
- Created `MacOSExtensionManager.kt` — extension lifecycle management
- Created `SourceProxy.kt` — reflection-based source proxy
- Created all Android stubs for extension compatibility

## [0.3.0] — 2026-07-04

### Core infrastructure

- Set up Koin DI (Platform, Domain, App modules)
- Created `MacOSPreferenceStore.kt` (JSON-backed)
- Created `MacOSDatabaseDriver.kt` (SQLDelight JDBC)
- Created `MacOSLogger.kt` (SLF4J/Logback)
- Created `BackgroundTaskScheduler.kt` (WorkManager replacement)
- Created `AnikkuApplication.kt` (app lifecycle)
- Created storage layer (`MacOSStorageProvider`, `MacOSStorageManager`)

## [0.2.0] — 2026-07-03

### Project scaffolding

- Created `macos/` directory as standalone Compose Desktop Gradle project
- Configured build system with version catalog
- Built source-api and common JVM JARs from Android KMP modules
- Set up Compose Multiplatform Desktop plugin
- Created minimal entry point (`AnikkuApp.kt`) with empty window
- Created `macos/settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`

## [0.1.0] — 2026-07-02

### Initial planning

- Created `architectural_rework_for_macos.md` — comprehensive 12-phase port plan
- Analyzed 1,071 Kotlin files across all Android modules
- Catalogued Android import usage across 17 Android packages (~800 import lines)
- Documented architecture decisions (Koin, mpv JNA, Compose Desktop, etc.)
- Created risk register and migration map (Appendix C, D)
- Designed build approach: standalone `macos/` module + shared source references

---

## Upcoming

### Phase 10 — Packaging & Distribution (planned)

- jpackage DMG packaging configuration
- App code signing and notarization
- Sparkle auto-updater wiring
- GitHub Actions CI for macOS builds
- Homebrew formula

### Phase 11 — Testing & Polish (planned)

- Run full end-to-end test: Browse → Search → Anime Detail → Play
- Fix runtime extension loading errors
- Fix mpv video rendering bugs
- Performance optimization for large libraries
- Memory profiling during extended playback

---

*For the full architecture plan and migration guide, see
[architectural_rework_for_macos.md](../architectural_rework_for_macos.md).*
