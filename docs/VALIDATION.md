# 0.3 validation — 2026-09-13

## Passed

- Debug APK build, 112 unit tests (0 failures/errors), Android lint (0 errors).
- Published DUML example frame roundtrip/CRC corruption checks; manual shutter and fixed ISO payload layout checks against inspected DJI SDK definitions.
- Mixed R-SDK / DUML framing: fragmented/coalesced frames, separate CRC checks, no interpretation of one frame's payload as another frame.
- Fake-transport exposure tests: read-only getters, setters + readback, mismatched sequences, query timeout without retry, negative ACK, unchanged readback after success ACK, idle checks before each setter.
- Group exposure tests: failed read on one camera prevents all setting writes; setters dispatch independently; partial success is not reported as global success.
- Emulator UI harness: group and per-camera controls, Settings/privacy placement, wheel selection. Test-only camera fixtures are not included in the installed app.
- Visible launcher-widget integration: ready → recording → stopped updates from persisted snapshots, Record and Stop remaining present, and tapping branding/body opens the main app. Screenshots with recording cameras are simulated test states.

## Not established

Actual Action 4 exposure setter acceptance and physical readback have not been tested by the assistant. Camera firmware may reject or ignore the DUML getters on an R-SDK-paired session; the driver stops before settings writes if so. No generic-camera compatibility, frame synchronization, or >99% filming-reliability claim is made.

A phone/camera test must: stop filming, install this preview over the prior app, reconnect the Action 4, tap Read cameras, inspect the response, then explicitly Apply a reversible setting and confirm the displayed camera screen + app readback. Do not treat mocked tests as a physical success report.
