# DJI Multi Remote

**0.3.0-preview · Native Android · Kotlin / Compose / Glance**

A small Bluetooth remote for a group of DJI cameras: connect once, record together, or trigger a single camera. The app uses the supplied coral icon and a dark navy / **#FF5D64** interface.

> **Engineering preview:** camera compatibility, inter-camera timing and filming reliability still need physical-camera validation. Experimental Action 4 exposure controls now send actual commands only after read-only queries succeed; physical Action 4 compatibility is not yet validated. No >99% reliability or frame-synchronization claim is made.

## What's in 0.3

- **Group controls:** Record All / Stop All, concurrent per-camera dispatch, and camera-reported confirmation.
- **Individual controls:** record or stop one linked camera without triggering the others.
- **Safe readiness:** group Record requires every saved camera ready by default; partial recording is an explicit option. Per-camera controls require only that camera ready.
- **4×2 widget:** your banner, an undistorted square logo, clear DJI Multi Remote branding and separate Record / Stop controls throughout an active session. Group status comes from a persisted Glance snapshot, updated on changes and every five seconds. Tap the rest of the widget to open the app. Legacy small widgets use a compact layout; re-add to adopt 4×2.
- **Cleaner app:** icon-based Remote, Cameras, Button and Settings tabs. Widget pinning, diagnostics, version/info and the privacy statement live in Settings.
- **Volume Up default:** common shutter remotes work while the app is focused. Custom key learning and optional media-button routing are available.
- **Idempotent Stop:** a camera freshly confirmed idle is already stopped; no redundant command or false confirmation timeout. Identical status reports can also complete an awaiting command.
- **Quiet background operation:** Android 13+ has no notification-permission prompt or ordinary session notification; the required Android **Active apps** entry remains. Android 8–12 require a silent minimal notification.
- **Experimental Action 4 exposure:** select shutter time and whole-stop ISO 100–12800, Read cameras, then explicitly Apply to all. Manual mode/shutter/ISO writes require matching camera readback; unsupported queries cause no setting writes.

## Install or update

**Requirements:** Android 8.0+ (API 26), Bluetooth LE, and supported camera firmware.

1. Stop filming and end the remote session before updating.
2. Install `DJI-Multi-Remote-0.3.0-preview.apk`. Install over the existing app—**do not uninstall first** if you want to retain saved cameras/settings.
3. Open **DJI Multi Remote** and grant Nearby devices permission. On Android 8–11, BLE discovery also requires Location permission and the system Location switch. No notification permission is requested.
4. Power cameras on, enable Bluetooth, choose Video mode and check memory cards. Disconnect DJI Mimo or other controllers.
5. **Cameras → Find cameras → Add to group**. Match the pairing code and approve on each camera screen.
6. Wait for group readiness, then use **Record all** or an individual camera's **Record only this camera** button.
7. Add the widget from **Settings → Home-Screen Widget**. Remove/re-add older widgets once to adopt the new 4×2 default dimensions.

Android may ask you to allow APK installation from your file manager. Keep Play Protect enabled and review its warnings; do not disable device-wide security protections.

**Disconnecting, force-stopping, updating or uninstalling the app does not stop cameras.** Verify that each camera stopped before ending a take. A stale/disconnected camera is unknown, not assumed stopped. The widget's update time helps identify stale rendered state after Android kills the process.

## Camera and remote compatibility

| Camera | Implementation status |
|---|---|
| Osmo Action 4 | R-SDK implemented; firmware/hardware validation required |
| Osmo Action 5 Pro | R-SDK implemented; firmware/hardware validation required |
| Osmo Action 6 | R-SDK implemented; numeric recording-status support varies by firmware |
| Osmo 360 | R-SDK implemented; firmware/hardware validation required |
| Action 2/3, Pocket, Nano | Not supported by this build |

Some Action 6 firmware emits text-mode status (`1D06`) without numeric recording status (`1D02`). The app stays unverified and blocks Record rather than pretending an ACK proves recording. See [protocol notes](docs/PROTOCOL.md).

Volume/keyboard shutter buttons work **while the app is focused**. Background media keys depend on Android routing and competing media apps. The app does not use an accessibility service, fake audio playback or global key interception. Existing explicit custom/disabled mappings are retained; **Reset to Volume Up** restores the default.

## Experimental Action 4 shutter / ISO controls

1. Connect all saved cameras. This first driver allows **Action 4 only**, idle in a video mode, with fresh status. Mixed/offline/recording groups are blocked.
2. Enable **Settings → Experimental exposure & ISO → Show global control wheels**.
3. Select the desired shutter speed (for example 1/400) and whole-stop ISO (100, 200, 400, 800, 1600, 3200, 6400, 12800).
4. Tap **Read cameras**. Every camera must answer both setting getters before Apply becomes available.
5. Tap **Apply to all** and confirm the change to **manual exposure**. The service reads every camera again before sending any group writes. Each camera receives shutter/ISO setters and, if needed, a manual-mode request, then getter readback.
6. Check each camera's result. “Camera confirmed” means getter values match the request—not merely that a write or ACK succeeded. A failed setter/readback can leave partial changes; check the camera and use Read again. There is no blind retry or automatic rollback.

Wheel movements alone do not send settings. Queries that time out or return an unrecognized/rejected response cause **no setting writes**. Some firmware/session combinations may not accept DUML setting queries alongside the R-SDK recording session; this driver does not perform a speculative extra pairing, wake or Wi-Fi handshake. A query failure is not proof that all Bluetooth exposure control is impossible, but this build will not pretend it worked.

The wire encodings were cross-checked against DJI Mobile SDK 4.18 class definitions and open protocol research. That does **not** certify Action 4 firmware compatibility: this build still needs a read/apply test on your actual camera. It is not frame-synchronized or atomic across cameras. See [research and wire details](docs/EXPOSURE_RESEARCH.md).

## Build / IDE

Use **Android Studio compatible with Android Gradle Plugin 9.4**, **JDK 17**, SDK **Android 37.2** (`platforms;android-37.2`) and **Build Tools 36.0.0**. Open the repository root and sync Gradle. Android Studio is not required just to install the APK.

The project uses Gradle 9.6.0, Kotlin 2.4.20, Compose, Glance 1.2.0, Coroutines/Flow, private DataStore and native Android BLE. It compiles against API 37.2, targets API 36 and supports API 26+.

```sh
# All installations and private build material must be outside this repository.
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="/path/to/android-sdk"
export DJI_PRIVATE_DIR="/path/outside/repository/dji-build-private"
./scripts/build.sh
```

The script checks for secrets, runs unit tests/lint, and builds `app/build/outputs/apk/debug/app-debug.apk`.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Signing:** this is a development/debug-signed preview, not a production-signed release. Keep the private key outside Git and retain it for future in-place updates. `DJI_DEBUG_KEYSTORE` can select the original external key. A different key cannot update an existing installation without uninstalling it. CI uses an ephemeral debug key; CI builds are not guaranteed to update the release APK in place.

## Validation

**112 unit tests pass**, Android lint has **0 errors**, and emulator UI checks cover group/per-camera displays, default key mapping, Settings placement and wheel selection. DUML tests cover packet CRCs, setter payloads, reply correlation, read-only behavior, readback mismatch and failure handling. Launcher tests verify ready → recording → stopped changes, both controls remaining available, and body taps opening the app. Android 16 checks verify the background service without a normal session notification. In-place installation/launch of the prior 0.2 preview was verified on a connected Android phone. The 0.3 camera-control path has not yet been tested on that phone/camera.

These are not physical-camera acceptance tests. Radio reliability, actual camera commands and multi-camera timing need the [hardware test checklist](docs/HARDWARE_TEST_PLAN.md). UI screenshots with test cameras are simulated fixtures, not evidence of real recording. See [validation details](docs/VALIDATION.md).

## Security and repository hygiene

- No account, telemetry, cloud or **Internet permission**.
- Camera addresses and random controller identity live only in the phone's private `noBackupFilesDir` DataStore. Backup/device transfer is disabled/excluded.
- Diagnostics contain generic event labels, session camera numbers and monotonic times—not camera names, addresses, pairing codes, raw packets or credentials.
- The widget stores only counts/flags/update time.
- Build/signing scripts reject an in-repository private key. Never place credentials or signing keys here, even if Git would ignore them.
- `.gitignore` excludes build/IDE metadata, binaries, key files, local configuration, `.claude` workspaces, and Claude-related `.md` / `.markdown` files with case-insensitive names at any depth. Ordinary project documentation remains tracked.
- `python3 scripts/check-secrets.py` is a heuristic check, not a proof; inspect the staged diff before publishing.

## Release and documentation

Current working preview: **`0.3.0-preview`**. The previously prepared local release remains tagged **`v0.2.0-preview`**; this update does not create or publish a new release. Expected assets: the APK and `SHA256SUMS.txt`. The APK is a **prerelease**; retain the engineering-preview and experimental-control limitations in its release notes.

- [0.3 changes](docs/CHANGELOG-0.3.md)
- [0.2 changes](docs/CHANGELOG-0.2.md)
- [Release notes](docs/RELEASE-0.2.0.md)
- [Architecture and state/safety model](docs/ARCHITECTURE.md)
- [Protocol reconnaissance](docs/PROTOCOL.md)
- [Exposure / ISO research](docs/EXPOSURE_RESEARCH.md)
- [Hardware acceptance tests](docs/HARDWARE_TEST_PLAN.md)
- [Validation](docs/VALIDATION.md)

Independent project; not affiliated with or endorsed by DJI. Product names identify intended compatibility only.
