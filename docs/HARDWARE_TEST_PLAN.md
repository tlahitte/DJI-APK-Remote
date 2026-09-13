# Physical acceptance checklist — NOT EXECUTED

Required bench: Android BLE phone, two supported DJI cameras, expendable memory cards and an optional generic remote. Record phone model/Android version and camera model/firmware in **private test notes outside the repository**. Never commit BLE addresses, pairing codes, raw traffic or personal recordings.

## Single camera

- [ ] Fresh install; permission denied and later granted; Bluetooth off/on.
- [ ] Scan identifies intended device; pair code matches; camera approval completes readiness.
- [ ] Reject pairing: no repeated automatic prompts during the session.
- [ ] Supported model/firmware emits numeric status; battery matches the camera.
- [ ] Video+card ready. Photo, playback, sleeping, full/missing card, overheat each block Record.
- [ ] Start/stop 50 consecutive times; compare every app state with the camera.
- [ ] Manually start/stop on camera; app catches up without pressing the remote.
- [ ] Pre-record is not shown as an actual take before capture begins.
- [ ] End/start session and restart app: no repeated manual approval with saved pairing history.

## Two or more cameras

- [ ] Pair independently; both must be READY before default Record enables.
- [ ] Start/stop 50+ takes; inspect actual files and record all failures.
- [ ] Verify dispatch timing has no acknowledgement serialization between cameras.
- [ ] One missing camera blocks default Record; partial override affects only ready subset and warns.
- [ ] One disconnects mid-start/mid-stop; healthy sessions survive; no false all-camera success.
- [ ] Disconnected recording camera is UNKNOWN / last report, never assumed stopped.
- [ ] Reconnect recovers actual camera state and never sends automatic Record.
- [ ] Rapid taps/key repeats do not enqueue multiple group operations.
- [ ] In-place APK update with same signing key preserves settings.

## Android background/widget

- [ ] Background app; lock phone for 10+ minutes; operate via widget/notification when accessible.
- [ ] Test under default phone battery policy before requesting any exemption.
- [ ] Process killed/force-stopped: next open is OFF; widget timestamp visibly stops until refreshed.
- [ ] Notification denied: no crash, foreground-task visibility still follows Android policy.
- [ ] Revoke Bluetooth permission during use; no crash; open/grant permissions/reconnect recovers.
- [ ] Widget CONNECT after reboot or process recreation; multiple widget instances; resize.
- [ ] End session warning: disconnect does not imply cameras stopped.

## External remote

- [ ] Learn actual key, persist mapping, cancel learning without replacing saved mapping.
- [ ] Test volume/HID with app foreground only.
- [ ] Test media keys foreground/background/locked, with and without a music app.
- [ ] Confirm key events stop with the session; no global interception when inactive.

Only claim a supported firmware/hardware combination after these pass. Calculate reliability as successful physically verified operations / attempted operations; unit tests and emulator checks do not count toward >99% radio reliability.
