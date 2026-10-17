#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ "$(uname -s)" == Darwin ]] || { echo "Run this script on macOS." >&2; exit 1; }
command -v java >/dev/null || { echo "Install JDK 21 first." >&2; exit 1; }
if [[ -f ./gradlew ]]; then
  GRADLE=(bash ./gradlew)
elif command -v gradle >/dev/null; then
  GRADLE=(gradle)
else
  echo "Install Gradle 8.9 or generate the Gradle wrapper first." >&2
  exit 1
fi
"${GRADLE[@]}" :shared:desktopTest :desktopApp:createDistributable :desktopApp:packageDmg --console=plain
APP="desktopApp/build/compose/binaries/main/app/Smart Checker.app"
test -f "$APP/Contents/Info.plist"
test -x "$APP/Contents/MacOS/Smart Checker"
/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Contents/Info.plist"
plutil -lint "$APP/Contents/Info.plist"
ARCH="$(uname -m)"
mkdir -p releases/macos
find desktopApp/build/compose/binaries/main/dmg -maxdepth 1 -name '*.dmg' -exec cp {} "releases/macos/Smart Checker-6.26.2-${ARCH}.dmg" \;
test -f "releases/macos/Smart Checker-6.26.2-${ARCH}.dmg"
hdiutil verify "releases/macos/Smart Checker-6.26.2-${ARCH}.dmg"
shasum -a 256 "releases/macos/Smart Checker-6.26.2-${ARCH}.dmg"
echo "Built for ${ARCH}. This script does not perform Developer ID signing or notarization."
