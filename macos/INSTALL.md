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
# Output: build/compose/binaries/main/dmg/Anikku-1.0.0.dmg
```

There is currently no supported Homebrew formula.

## Post-Installation Setup

### 1. Verify bundled playback support

The packaged app includes `libmpv`; no separate mpv installation is required.
Developers running from Gradle should install it through Homebrew:

```bash
brew install mpv
```

To verify installation:

```bash
mpv --version
# mpv 0.41.0 Copyright © 2000-2024 mpv/MPlayer/mplayer2 projects
```

Without libmpv, a development run starts in mock mode and cannot display video.

### 2. First Launch

1. Open `Anikku.app` from your Applications folder
2. Grant any permission prompts (notifications, file access)
3. Browse the extension sources and find anime to watch

### 3. (Optional) Tracker Login

To sync your watch history with MyAnimeList, AniList, or Kitsu:

1. Go to Settings → Tracking
2. Select your tracker
3. Click "Login" — your default browser will open the OAuth page
4. Authorize the app and return to Anikku

Discord Rich Presence is not available in this build because a native Discord
IPC transport has not been implemented. Its failure cannot block startup or
playback.

## Updating

### Automatic Update Check

The app checks for updates on launch by querying the GitHub Releases API. When an update is available, a notification appears with a link to the release page.

### Manual Update

1. Download the latest `.dmg` from the [Releases page](https://github.com/komikku-app/anikku/releases)
2. Replace `Anikku.app` in your Applications folder
3. Your data (library, downloads, settings) is preserved in `~/Library/Application Support/Anikku/`

## Migrating from Android

If you have an existing Anikku installation on Android, you can migrate your data:

1. **On Android:** Settings → Data & Storage → Backup → Create Backup
2. Transfer the `.tachibk` file to your Mac (AirDrop, iCloud, USB)
3. **On macOS:** File → Open Backup... → Select your `.tachibk` file
4. Your library, categories, and settings will be restored

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

### "mpv: command not found"

Install libmpv:

```bash
brew install mpv
```

### "Cannot connect to sources"

Check your internet connection. If you're behind a firewall, ensure outbound HTTPS (port 443) is allowed. Some source websites may be blocked depending on your region.

### Player shows black screen

1. Verify libmpv is installed: `mpv --version`
2. Try a different video format or source
3. Check the logs at `~/Library/Logs/Anikku/` for error details

### App crashes on launch

1. Clear the app data: `rm -rf ~/Library/Application\ Support/Anikku`
2. Check crash logs: `cat ~/Library/Logs/Anikku/crash-*.log`
3. File an issue on [GitHub](https://github.com/komikku-app/anikku/issues)

## Getting Help

- **GitHub Issues:** [Report a bug](https://github.com/komikku-app/anikku/issues)
- **Documentation:** See the `macos/docs/` directory
- **Build Guide:** [BUILDING.md](BUILDING.md)
