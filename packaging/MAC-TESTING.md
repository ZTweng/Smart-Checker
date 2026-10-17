# macOS build and interactive checks

Status: prepared on Windows; no macOS build or launch has been performed.

## Environment

Use a Mac, or an interactive cloud Mac on Apple hardware with desktop access.
Install JDK 21 and use the included Gradle 8.9 wrapper.
Do not purchase a build-only service if you need to inspect a visible window.
Do not expose SSH or VNC directly to the public internet; use the provider's secure access.

## Commands

Open Terminal in the extracted project folder:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
bash packaging/build-macos.sh
bash script/build_and_run.sh --verify
```

The DMG is copied to releases/macos with its architecture in the filename.
Build separately on Apple silicon (arm64) and Intel (x86_64); neither is universal.
The runtime is bundled; end users do not need Java.
Install by dragging Smart Checker.app to Applications; uninstall by removing the app.
User-created Excel files and logs are not removed by uninstalling.

## Manual verification

- Launch the installed app from Applications, not only the build folder.
- Check light/dark startup, manual switching and system theme following.
- Add multiple XLSX files, remove/clear entries, select both activity types.
- Check progress and window responsiveness while checking workbooks.
- Verify results, notes, timestamps, output opening and log opening.
- Verify check-updates success and offline/error behavior.
- Repeat after closing and reopening the application.

No Developer ID signing or notarization is configured. A locally tested DMG is
not automatically ready for friction-free public distribution.
Do not remove quarantine or disable Gatekeeper to conceal verification failures.
Never include .signing, credentials, private keys, personal spreadsheets or logs
in a GitHub source upload.
