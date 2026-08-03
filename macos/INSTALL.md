# Installing Anikku macOS

## System Requirements

- **macOS 12.0+** (Monterey, Ventura, Sonoma, Sequoia)
- **Apple Silicon** for the currently verified packaged artifact
- **4GB RAM minimum** (8GB+ recommended for smooth video playback)

Intel source builds may be possible with matching native dependencies, but an
Intel package has not been produced or verified by the current release process.

## Installation Methods

### Option 1: Download DMG (Recommended)

1. Download the latest `Anikku-*.dmg` from the [Releases page](https://github.com/komikku-app/anikku/releases)
2. Open the `.dmg` file
3. Drag `Anikku.app` to your `Applications` folder
4. First launch: Right-click → Open (to bypass Gatekeeper for unsigned builds)

### Option 2: Build from Source

See [BUILDING.md](BUILDING.md) for full build instructions.

```bash
git clone https://github.com/komikku-app/anikku.git
cd anikku/macos
./gradlew packageDmg
# Output: build/compose/binaries/main/dmg/Anikku-1.0.2.dmg
```

There is currently no supported Homebrew formula.

## Post-Installation Setup

### 1. Verify bundled playback support

The packaged app includes `libmpv`, native TorrServer, the Touch ID helper, and
its Java runtime; no separate player or torrent helper installation is required.
Developers running from Gradle should install mpv through Homebrew:

```bash
brew install mpv
```

To verify installation:

```bash
mpv --version
# mpv 0.41.0 Copyright © 2000-2024 mpv/MPlayer/mplayer2 projects
```

Without libmpv, a development run remains usable but playback is disabled and
shows an error. Watch progress is never simulated.

### 2. First Launch

1. Open `Anikku.app` from your Applications folder
2. Grant any permission prompts (notifications, file access)
3. Browse the extension sources and find anime to watch

Touch ID is offered when LocalAuthentication reports enrolled biometrics. A
Keychain-backed PIN remains available as the fallback.

### 3. (Optional) Tracker Login

To sync your watch history with MyAnimeList, AniList, or Kitsu:

1. Go to Settings → Tracking
2. Select your tracker
3. Click "Login" — your default browser will open the OAuth page
4. Authorize the app and return to Anikku

### 4. Automatic English Subtitles

No setup required — the app ships with baked-in free-tier credentials for the
subtitle providers:

- **Jimaku** (anime-native database) is used automatically when a source serves
  no subtitle track: the player resolves the anime via AniList, fetches the
  exact episode's English subs, and attaches them.
- **OpenSubtitles.com** is a fallback: open the player's subtitle menu →
  "Search OpenSubtitles…" → pick a result (English first) → it attaches with
  correct timing.

You can override the built-in keys in **Settings → Subtitles** (values are
stored in your macOS Keychain and take precedence). Note that all users of a
given build share the developer account's free-tier download quotas.

### 5. Resume Playback

The player saves your position every few seconds during playback and seeks back
to it when you reopen an episode — pause at 10:00, quit, reopen, and it resumes
at 10:00. Toggle this off in Settings → Player → "Resume from last position".

Discord Rich Presence is available as an opt-in setting. It connects only to a
locally running Discord Desktop client through its Unix-domain IPC socket; a
missing Discord client cannot block startup or playback.

Google Drive backup requires your own Google Desktop OAuth client ID. The
browser flow uses PKCE and stores the resulting session only in Keychain.

## Updating

### Automatic Update Check

Packaged builds initialize Sparkle against the validated HTTPS appcast.
Development runs, or builds without the native helper, fall back to an
informational GitHub Releases check that opens the release page; the fallback
does not install an unverified artifact.

### Manual Update

1. Download the latest `.dmg` from the [Releases page](https://github.com/komikku-app/anikku/releases)
2. Replace `Anikku.app` in your Applications folder
3. Your data (library, downloads, settings) is preserved in `~/Library/Application Support/Anikku/`

## Migrating from Android

If you have an existing Anikku installation on Android, you can migrate your data:

1. **On Android:** Settings → Data & Storage → Backup → Create Backup
2. Transfer the `.tachibk` file to your Mac (AirDrop, iCloud, USB)
3. **On macOS:** File → Open Backup... → Select your `.tachibk` file
4. The import is merged into the current macOS data

The importer restores Android library entries, category membership, episode
history/progress, and custom anime metadata. Android app/source preferences and
credentials are intentionally not imported. macOS-created backups use the
lossless `.anikku_backup.json` format and can be restored from the same menu.

## File Locations

| Data | Location |
|---|---|
| App data | `~/Library/Application Support/Anikku/` |
| Preferences | `~/Library/Application Support/Anikku/preferences.json` |
| Database | `~/Library/Application Support/Anikku/data/anime.db` |
| Downloads | `~/Library/Application Support/Anikku/downloads/` |
| Backups | `~/Library/Application Support/Anikku/backups/` |
| Extensions | `~/Library/Application Support/Anikku/extensions/` |
| Logs | `~/Library/Logs/Anikku/` |
| Crash reports | `~/Library/Logs/Anikku/crash-*.log` |

## Uninstalling

Move `Anikku.app` from Applications to the Trash. To remove local data too,
delete only the `Anikku` folders listed under **File Locations** above. Library,
download, and preference data cannot be recovered after the Trash is emptied.

## Troubleshooting

### "Anikku.app is damaged and can't be opened"

This can occur because development builds are not Developer ID signed or
notarized. First use Finder's **Right-click → Open** flow. For a DMG you built
yourself from trusted source, quarantine can also be removed explicitly:

```bash
xattr -rd com.apple.quarantine /Applications/Anikku.app
```

### "mpv: command not found" during development

Install libmpv:

```bash
brew install mpv
```

### "Cannot connect to sources"

Check your internet connection. If you're behind a firewall, ensure outbound HTTPS (port 443) is allowed. Some source websites may be blocked depending on your region.

### Player shows black screen

1. For a Gradle development run, verify libmpv is installed: `mpv --version`
2. For an installed build, run `./gradlew verifyPackage` against the built app
   to confirm bundled native components
3. Try a different video format or source
4. Check the logs at `~/Library/Logs/Anikku/` for error details

### App crashes on launch

1. Quit Anikku and move its data aside so it remains recoverable:
   `mv "$HOME/Library/Application Support/Anikku" "$HOME/Desktop/Anikku-data-backup"`
2. Reopen the app and check crash logs under `~/Library/Logs/Anikku/`
3. Restore the moved folder if the reset did not help
4. File an issue on [GitHub](https://github.com/komikku-app/anikku/issues)

## Getting Help

- **GitHub Issues:** [Report a bug](https://github.com/komikku-app/anikku/issues)
- **Documentation:** See the `macos/docs/` directory
- **Build Guide:** [BUILDING.md](BUILDING.md)
