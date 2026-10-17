#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ "$(uname -s)" == Darwin ]] || { echo "Run on macOS." >&2; exit 1; }
MODE="${1:---verify}"
case "$MODE" in run|--verify|--logs|--debug) ;; *) echo "Use run, --verify, --logs or --debug"; exit 2 ;; esac
pkill -x 'Smart Checker' >/dev/null 2>&1 || true
if [[ -f ./gradlew ]]; then GRADLE=(bash ./gradlew); else GRADLE=(gradle); fi
"${GRADLE[@]}" :desktopApp:createDistributable --console=plain
APP="$PWD/desktopApp/build/compose/binaries/main/app/Smart Checker.app"
if [[ "$MODE" == --debug ]]; then
  lldb -- "$APP/Contents/MacOS/Smart Checker"
else
  open -n "$APP"
  if [[ "$MODE" == --verify ]]; then
    sleep 5
    pgrep -x 'Smart Checker' >/dev/null
    echo "Process is running. Check the visible window manually."
  elif [[ "$MODE" == --logs ]]; then
    /usr/bin/log stream --level info --predicate 'process == "Smart Checker"'
  fi
fi
