# Validation — 2026-09-13

## Passed

- `scripts/build.sh`: unit tests, lint and debug APK assembly successful with JDK 17, Gradle 9.6.0, AGP 9.4.0, Kotlin 2.4.20, SDK 37.2 / Build Tools 36.0.0.
- **48 unit tests; 0 failures/errors**: 15 protocol/CRC/stream tests, 21 status/readiness tests, 12 fake-transport session/concurrency tests.
- Android lint: **0 errors, 5 warnings**. Warnings are the deliberate target API 36 (compile API 37.2), backward-compatible manifest/widget attributes, and a newer optional Gradle release. No blanket lint baseline was used.
- APK signature: verified with `apksigner`; APK Signature Scheme v2, Android development/debug certificate. This is an engineering-preview build, not a production release key.
- APK manifest: minimum API 26, target API 36, **no INTERNET permission**. Glance/WorkManager adds wake-lock, network-state and boot-reschedule permissions; these do not add Internet access or boot-start camera connections.
- Secret hygiene scan passed. No signing-key files or recognized token patterns found in checked source; actual key outside repository. Checked repository has no GitHub remote / no embedded remote credentials.
- Source whitespace check: `git diff --check` passed.

## Emulator smoke checks actually performed

Google APIs Android 16 / API 36, ARM64 Pixel 6 profile:

- APK installs, launches and survives an in-place same-key APK update.
- Dark Compose screens render and scroll; status/navigation bars remain readable.
- Nearby devices and notification permission dialogs appear and grant correctly.
- Scan starts a foreground service; Android reports `connectedDevice` type `0x10` and a persistent private notification.
- Foreground service remains active when the activity goes to the launcher.
- Learn Button detects an injected `KEYCODE_VOLUME_UP` event. This is not a physical Bluetooth remote test.
- Glance widget can be pinned through the launcher and renders its inactive state.
- Tapping widget CONNECT from the launcher starts the connected-device foreground service and updates the widget to the active empty-group state. Record/Stop remain disabled with no configured cameras.
- Restarting/updating the process returns UI to inactive rather than reviving old confirmed recording state.
- No app crash in the emulator crash buffer during these checks.

## Not validated / not claimed

No real Android phone, DJI camera or generic Bluetooth remote was connected. Therefore pairing persistence with camera firmware, 50-cycle tests, actual record/stop success, radio reconnect behavior, battery accuracy, inter-camera timing and locked-screen remote behavior remain **untested**. Emulator BLE scaffolding and fake transports do not validate those. No >99% reliability or model compatibility claim is made. The GitHub Actions workflow is supplied but has not been run remotely.

Use `HARDWARE_TEST_PLAN.md` before trusting the app on a real shoot. Action 6 mode-only status firmware remains a known protocol limitation, not a successfully controlled configuration.
