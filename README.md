# DJI Multi Remote

**0.2.0-preview · Native Android · Kotlin / Compose / Glance**

A small Bluetooth remote for a group of DJI cameras: connect once, record together, or trigger a single camera. The app uses the supplied coral icon and a dark navy / **#FF5D64** interface.

> **Engineering preview:** camera compatibility, inter-camera timing and filming reliability still need physical-camera validation. Exposure/ISO wheels are **local presets only**—they do not change camera settings. No >99% reliability or frame-synchronization claim is made.

## What's in 0.2

- **Group controls:** Record All / Stop All, concurrent per-camera dispatch, and camera-reported confirmation.
- **Individual controls:** record or stop one linked camera without triggering the others.
- **Safe readiness:** group Record requires every saved camera ready by default; partial recording is an explicit option. Per-camera controls require only that camera ready.
- **4×1 widget:** one row, four columns. A single left-side button changes from Connect to Record to Stop; aggregate group status sits on the right. The widget turns coral when cameras report recording. Tap elsewhere to open the app.
- **Cleaner app:** icon-based Remote, Cameras, Button and Settings tabs. Widget pinning, diagnostics, version/info and the privacy statement live in Settings.
- **Volume Up default:** common shutter remotes work while the app is focused. Custom key learning and optional media-button routing are available.
- **Idempotent Stop:** a camera freshly confirmed idle is already stopped; no redundant command or false confirmation timeout. Identical status reports can also complete an awaiting command.
- **Quiet background operation:** Android 13+ has no notification-permission prompt or ordinary session notification; the required Android **Active apps** entry remains. Android 8–12 require a silent minimal notification.
- **Experimental wheels:** shutter times including 1/200 and 1/400, and ISO 100–12800. Choices are saved only on the phone and explicitly marked **Not sent to cameras**.

## Install or update

**Requirements:** Android 8.0+ (API 26), Bluetooth LE, and supported camera firmware.

1. Stop filming and end the remote session before updating.
2. Install `DJI-Multi-Remote-0.2.0-preview.apk`. Install over the existing app—**do not uninstall first** if you want to retain saved cameras/settings.
3. Open **DJI Multi Remote** and grant Nearby devices permission. On Android 8–11, BLE discovery also requires Location permission and the system Location switch. No notification permission is requested.
4. Power cameras on, enable Bluetooth, choose Video mode and check memory cards. Disconnect DJI Mimo or other controllers.
5. **Cameras → Find cameras → Add to group**. Match the pairing code and approve on each camera screen.
6. Wait for group readiness, then use **Record all** or an individual camera's **Record only this camera** button.
7. Add the widget from **Settings → Home-Screen Widget**. Remove/re-add older widgets once to adopt the new 4×1 default dimensions.

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

## Experimental shutter / ISO wheels

Enable **Settings → Experimental exposure & ISO → Show global control wheels**. The wheels also appear on the Remote page. Scroll or tap to choose values.

- “Exposure” here means **shutter time**, not EV compensation.
- The selectable ranges are presets, not a capability claim for every camera/frame rate.
- **No Bluetooth apply/transmit path exists for these settings yet.** The UI and stored values must not be interpreted as camera readback.
- Concurrent dispatch is feasible, but exact setters, capability queries and readback need validation on one camera, then two. Separate DUML research about ISO *limits* does not establish manual shutter/ISO support in our R-SDK sessions.

See [exposure research and implementation gate](docs/EXPOSURE_RESEARCH.md).

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

**78 unit tests pass**, Android lint has **0 errors**, and emulator UI checks cover group/per-camera displays, default key mapping, Settings placement and local wheel selection/persistence. Launcher checks verify 4×1 sizing and separate button/body behavior. Android 16 checks verify the background service without a normal session notification. In-place APK installation and launch on a connected Android phone have been verified.

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

Release tag: **`v0.2.0-preview`**. Expected assets: the APK and `SHA256SUMS.txt`. The APK is a **prerelease**; retain the engineering-preview and experimental-control limitations in its release notes.

- [0.2 changes](docs/CHANGELOG-0.2.md)
- [Release notes](docs/RELEASE-0.2.0.md)
- [Architecture and state/safety model](docs/ARCHITECTURE.md)
- [Protocol reconnaissance](docs/PROTOCOL.md)
- [Exposure / ISO research](docs/EXPOSURE_RESEARCH.md)
- [Hardware acceptance tests](docs/HARDWARE_TEST_PLAN.md)
- [Validation](docs/VALIDATION.md)

Independent project; not affiliated with or endorsed by DJI. Product names identify intended compatibility only.
