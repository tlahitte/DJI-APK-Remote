# Experimental Action 4 exposure control — 0.3

## What changed

0.2 only staged wheel choices. 0.3 has a real DUML request/response client sharing the serialized BLE GATT transport with R-SDK. It sends **only read-only getters** first. All saved cameras must be idle Action 4 sessions and must answer both getters before any group setting writes. The user must explicitly confirm Apply.

**Physical Action 4 behavior has not been verified by the assistant.** Wire-format verification, mocked transport tests and successful APK compilation do not establish firmware compatibility. If the current R-SDK-paired BLE link does not answer DUML queries, this build blocks setting writes. No extra pairing/session wake, Wi-Fi or password query is performed automatically.

## Sources inspected

- DJI published `com.dji:dji-sdk-provided:4.18`, from https://repo.maven.apache.org/maven2/com/dji/dji-sdk-provided/4.18/ . Inspected the wire-packing/getter offsets in `DataCameraSetShutterSpeed`, `DataCameraGetShutterSpeed`, `DataCameraSetIso`, `DataCameraGetIso`, `DataCameraSetExposureMode` and command/ISO enums. The SDK binary is **not** bundled or linked into the application.
- `o-gs/dji-firmware-tools`, revision `195692263c2684cf1ddc4995f2736be6c0fb135e`: https://github.com/o-gs/dji-firmware-tools/blob/195692263c2684cf1ddc4995f2736be6c0fb135e/comm_dissector/wireshark/dji-dumlv1-camera.lua . Cross-checks command IDs and ISO/mode enums.
- Osmosis BLE DUML framing and BLE/datalink camera-parameter research: https://github.com/KonradIT/osmosis/blob/2fcdbc97e6dbefc875d425368be67cf32b50bb06/MEDIA_PROTOCOL.md . ISO-limit PID 000F is **not** used as a fixed-ISO setter.

These are protocol references, not an official SDK support statement for Action 4. No reference SDK/source implementation is vendored.

## Wire path

DUML SOF `55`; version 1; 10-bit length; header CRC8 (init 77, reflected poly 8C); App sender 02 → Camera receiver 01; sequence echoed by camera; request flags 40; command set 02; trailing CRC16 (init 3692, reflected poly 8408). Camera replies must match sequence, command set/id and endpoints, carry response flag, and return success code 00. The existing R-SDK `AA` frames are independently decoded by a bounded, CRC-checked mixed-protocol stream decoder, not searched inside DUML payloads.

| Operation | Cmd ID | Wire payload / readback |
|---|---|---|
| Get ISO | 2B | Empty request; response `00` + one ISO enum byte |
| Get shutter | 29 | Empty request; response `00` + mode byte + little-endian reciprocal/integral u16 + decimal byte |
| Set manual mode | 1E | `04 00` |
| Set shutter | 28 | `01`, then `denominator OR 8000` as little-endian u16, then decimal 00. 1/400 is `01 90 81 00` |
| Set fixed ISO | 2A | One absolute enum byte: ISO100=03 through ISO12800=0A; the relative-change high bit remains clear |

The mode setter is skipped when both preflight getters already report manual settings. After the required setters succeed, both getters are read again; valid but different readings may be re-polled briefly, without repeating setters. Readback must show manual reciprocal shutter, the requested integer denominator/zero decimal, and the requested fixed ISO enum. ACK-only success is not sufficient.

## Safety / failure semantics

- Read is read-only. All group preflights finish before any setter is sent.
- Whole-stop ISO values only; invalid stored fractional-stop presets normalize locally, never by sending a camera change.
- Before each setter, recheck the session is an idle Action 4 with fresh status.
- No writes while app-reported recording is active. Camera state can still change independently; this is not an atomic transaction.
- Three setters can partially succeed. Failure says settings may have changed; read/check the camera before trying again. No blind retries or rollback.
- Each query has bounded waiting and strict reply matching. Unsupported/rejected layouts are not guessed.
- Exposure readback is labeled as last read, not a continuously observed live sensor value.
- No shutter/ISO commands are emitted merely by moving the wheels.
