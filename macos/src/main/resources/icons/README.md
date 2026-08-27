# Anikku macOS App Icon

## Requirements

The build expects `app.icns` in this directory. An `.icns` file is a macOS icon
container that holds multiple resolutions (16×16 through 1024×1024).

## Generate from PNG (1024×1024 base)

### Option 1: Using `iconutil` (built into macOS)

```bash
# 1. Create an iconset directory
mkdir AppIcon.iconset

# 2. Place your 1024×1024 PNG at each required size
#    (or use sips to resize from a single source)
sips -z 16 16   icon_1024.png --out AppIcon.iconset/icon_16x16.png
sips -z 32 32   icon_1024.png --out AppIcon.iconset/icon_16x16@2x.png
sips -z 32 32   icon_1024.png --out AppIcon.iconset/icon_32x32.png
sips -z 64 64   icon_1024.png --out AppIcon.iconset/icon_32x32@2x.png
sips -z 128 128 icon_1024.png --out AppIcon.iconset/icon_128x128.png
sips -z 256 256 icon_1024.png --out AppIcon.iconset/icon_128x128@2x.png
sips -z 256 256 icon_1024.png --out AppIcon.iconset/icon_256x256.png
sips -z 512 512 icon_1024.png --out AppIcon.iconset/icon_256x256@2x.png
sips -z 512 512 icon_1024.png --out AppIcon.iconset/icon_512x512.png
sips -z 1024 1024 icon_1024.png --out AppIcon.iconset/icon_512x512@2x.png

# 3. Convert to .icns
iconutil -c icns AppIcon.iconset

# 4. Move to this directory
mv AppIcon.icns app.icns
```

### Option 2: Using Image2Icon (GUI)

Download [Image2Icon](https://img2icns.com/) and export as `.icns`.

## Design Guidelines

- **Shape:** macOS uses rounded-rectangle masks (squircle). Design with
  ~40px corner radius padding on a 1024×1024 canvas.
- **Style:** Use the Anikku brand colour (#1a1a2e deep blue) with the
  app's star/play emblem.
- **Format:** Save as PNG (lossless) before converting to `.icns`.
