# DJI Multi Remote 0.2.0-preview

An Android engineering **prerelease**, development/debug-signed, for Android 8.0+ with Bluetooth LE.

## Highlights

- Supplied app icon and coral/navy redesign with icon-based navigation.
- 4×1 aggregate-status widget: Connect → Record → Stop, button left/text right, coral while recording; tapping the rest opens the app.
- Individual camera Record/Stop alongside group controls.
- Stop on freshly confirmed idle cameras is a successful no-op; identical status reports no longer leave confirmation waiters stuck.
- Volume Up default, custom remote mappings, and Settings for widget pinning, diagnostics and app info.
- No ordinary session notification on Android 13+; Android's mandatory Active apps entry remains. Older Android uses a silent minimal notification.
- Experimental shutter-speed and ISO wheels. **Local presets only—not sent to cameras.**

## Update

End the camera session before installing. Install this APK over the existing app to preserve settings; do not uninstall first. Remove/re-add existing widgets to adopt the 4×1 default size. Updating/disconnecting the app does not stop a camera's recording.

## Validation and limitations

78 unit tests pass; Android lint has no errors; emulator UI/widget and quiet-background checks pass. In-place installation and app launch were verified on a connected Android phone. Physical camera/firmware acceptance and filming reliability remain unverified for this build.

R-SDK is implemented for Action 4, Action 5 Pro, Action 6 and Osmo 360, not a certified compatibility list. Some Action 6 firmware lacks the numeric status needed to enable Record safely. Exposure/ISO setters and readback are **not implemented**; parallel transport capability is not proof of camera support or frame synchronization.

This APK uses the retained development key for in-place updates. It is not a production-signed build; an independently/CI-generated debug key will not necessarily update it. No private signing keys or camera credentials are distributed.

## Assets

- `DJI-Multi-Remote-0.2.0-preview.apk`
- `SHA256SUMS.txt`
