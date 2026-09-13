# 0.2 validation — 2026-09-13

- Android debug APK builds successfully with the existing isolated JDK/SDK and external signing key.
- **78 unit tests pass, 0 failures/errors.** Includes idle/repeated/stale Stop, identical-status delivery, per-camera isolation/gates, group widget actions/colors, default key and experimental preset validation.
- Android lint: **0 errors**. Platform-version/backward-compatible attribute/tool-update warnings remain non-fatal.
- Emulator-only `UiSmokeInstrumentation` passes: ready group, per-camera action, partial-recording UI, Volume Up default, Settings placement, privacy statement only in Settings, wheel snapping/selection and local DataStore persistence without starting a camera session.
- Test-only camera fixtures live in `androidTest`, never in the installed application APK. Screenshots with sample camera names are UI fixtures, NOT evidence of camera radio operation.
- APK manifest no longer requests POST_NOTIFICATIONS; no notification prompt is launched. Android 13+ retains the required foreground-service Active apps visibility. Android 8–12 still need a quiet notification.
- No INTERNET permission; no private signing key in source. Secret-pattern/key-file checks pass.

The camera protocol, BLE timing, actual exposure/ISO control, background physical Volume-Up remote routing, and measured filming reliability are not established by emulator or unit tests. See `HARDWARE_TEST_PLAN.md` and `EXPOSURE_RESEARCH.md`. The wheels do not transmit commands.

Run the UI harness only on the test emulator (it checks `Build.HARDWARE`). Build the separate Android test APK with `assembleDebugAndroidTest`, install both APKs on the emulator, then run `adb shell am instrument -w dev.djiremote.test/dev.djiremote.UiSmokeInstrumentation`. The test APK is not distributed to the phone.

Additional launcher/system checks on Android 16: the picker reports 4×1; the new single-row widget renders with its button on the left; tapping Connect starts the foreground session without opening the app; tapping the text/body opens the main app. The notification shade contains no app session notification while Android reports the connected-device foreground service running.
