#! /usr/bin/env bash

#
# macos-bundle.sh v1.0
# 
# Create a "bundle" for the Autopsy 4 application.
#
# 2025.11.02.- Eduardo René Rodríguez Ávila, creation.

#
# Application name and path
#

echo "Creating app bundle..."
APP="$HOME/Applications/Autopsy.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources/app"

#
# Info.plist
#

echo "Creating Info.plist..."
cat > "$APP/Contents/Info.plist" << EOT
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Autopsy</string>
  <key>CFBundleDisplayName</key><string>Autopsy</string>
  <key>CFBundleIdentifier</key><string>mx.example.autopsy</string>
  <key>CFBundleExecutable</key><string>autopsy-launcher</string>
  <key>CFBundleIconFile</key><string>autopsy.icns</string>  
  <key>LSMinimumSystemVersion</key><string>13.0</string>
  <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
EOT

#
# Launcher
#

echo "Creating launcher script..."
cat > "$APP/Contents/MacOS/autopsy-launcher" << EOT
#! /usr/bin/env bash
set -euo pipefail

# Resolve bundle paths
BUNDLE_DIR="\$(cd "\$(dirname "\$0")/.." && pwd)"
RESOURCES="\$BUNDLE_DIR/Resources"
APP_ROOT="\$RESOURCES/app"

# Prefer a JDK 17 if present; fall back to default
if [ -z "${JAVA_HOME:-}" ]; then
  if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    export JAVA_HOME="$("/usr/libexec/java_home" -v 17)"
  else
    export JAVA_HOME="$("/usr/libexec/java_home" 2>/dev/null || true)"
  fi
  [ -n "${JAVA_HOME:-}" ] && export PATH="\$JAVA_HOME/bin:\$PATH"
fi

# Start from the app's root so relative paths work
cd "\$APP_ROOT"
exec "\$APP_ROOT/bin/autopsy"
EOT

chmod u+x "$APP/Contents/MacOS/autopsy-launcher"

#
# Autopsy is copied
#

echo "Copyng application..."
SRC="$PWD"  # Change if necessary
rsync -a --delete   --exclude=".git"  --exclude="target" \
  "$SRC/${autopsy,bin,etc}" "$APP/Contents/Resources/app/"

#
# Icon set
#

echo "Creating icon set..."
mkdir -p tmp/autopsy.iconset
( 
sips -z 16 16     autopsy.png --out tmp/autopsy.iconset/icon_16x16.png
sips -z 32 32     autopsy.png --out tmp/autopsy.iconset/icon_16x16@2x.png
sips -z 32 32     autopsy.png --out tmp/autopsy.iconset/icon_32x32.png
sips -z 64 64     autopsy.png --out tmp/autopsy.iconset/icon_32x32@2x.png
sips -z 128 128   autopsy.png --out tmp/autopsy.iconset/icon_128x128.png
sips -z 256 256   autopsy.png --out tmp/autopsy.iconset/icon_128x128@2x.png
sips -z 256 256   autopsy.png --out tmp/autopsy.iconset/icon_256x256.png
sips -z 512 512   autopsy.png --out tmp/autopsy.iconset/icon_256x256@2x.png
sips -z 512 512   autopsy.png --out tmp/autopsy.iconset/icon_512x512.png 
) > /dev/null 2>&1
cp autopsy.png tmp/autopsy.iconset/icon_512x512@2x.png
iconutil -c icns tmp/autopsy.iconset -o "$APP/Contents/Resources/autopsy.icns"

#
# Sign
#

echo -n "Signing application..."
codesign -s - --force --deep "$APP"
xattr -dr com.apple.quarantine "$APP"

# 
# Final instrucctions
#

echo ""
echo "First time could be necessary to launch from terminal with:"
echo ""
echo "\$ open $APP"
echo ""
echo "after that it will appear in Spotlight"
