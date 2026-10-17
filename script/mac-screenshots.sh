#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p releases/macos/evidence
APP="$PWD/desktopApp/build/compose/binaries/main/app/Smart Checker.app"
cleanup() { pkill -x 'Smart Checker' >/dev/null 2>&1 || true; }
trap cleanup EXIT
for theme in light dark; do
  cleanup
  if [[ "$theme" == dark ]]; then flag=true; else flag=false; fi
  osascript -e "tell application \"System Events\" to tell appearance preferences to set dark mode to $flag"
  open -n "$APP"
  sleep 10
  pgrep -x 'Smart Checker' > "releases/macos/evidence/${theme}-process.txt"
  osascript -e 'tell application "Smart Checker" to activate'
  sleep 2
  screencapture -x "releases/macos/evidence/${theme}.png"
done
sw_vers > releases/macos/evidence/environment.txt
uname -m >> releases/macos/evidence/environment.txt
echo "Screenshots are evidence only; inspect the images to confirm window and theme rendering."
