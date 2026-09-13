# Protocol reconnaissance

Read on 2026-09-13. Source trees were kept **outside** this repository. This is a new Kotlin implementation of the published protocol, not a fork or a copied application.

| Reference | Revision inspected | Use |
|---|---|---|
| https://github.com/dji-sdk/Osmo-GPS-Controller-Demo | `92fe23e5a749f189593f980a26a105c3bb66aa1c` | Authoritative framing, checksums, handshake and status layout |
| https://github.com/rhoenschrat/DJI-Remote | `b9d9b091e331cd9f7e5bdd5f2408a4e462a8c626` | Multicamera command dispatch, sender identity, advertisements |
| https://github.com/dimadesu/dji-remote | `c2012be6aca67d4882774cf5d9746f420a03e11f` | Android discovery and manufacturer/model hints |
| https://github.com/KonradIT/osmosis | `2fcdbc97e6dbefc875d425368be67cf32b50bb06` | R-SDK cross-check and Action 6 mode-only / subscription caveat |

DJI protocol documentation: https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol.md and https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol_data_segment.md . Android references are in the supplied project plan; implementation uses native BLE, connectedDevice FGS, Glance and MediaSession APIs.

## BLE

Bluetooth base UUID suffix: `-0000-1000-8000-00805f9b34fb`.

- Service `0000fff0`
- Notifications `0000fff4`
- Writes `0000fff5`
- CCCD `00002902`

Discovery uses hardware filters for manufacturer IDs `0x08AA` and `0xF7AA`, plus the service UUID. Model hints (`0x0014/15/18/17` or `0xFF33/44/55/66`) and DJI signature byte `0xFA` identify candidates. Advertising data is untrusted; the application handshake checks the returned model. A service-only/name-only unknown device is not enough to authorize control.

Each connection discovers characteristics, requests MTU 247, enables notifications/indications and waits for descriptor completion **before pairing**. Writes are serialized within that GATT, including frame fragments at MTU−3. GATT operations time out; a failed/timed-out operation closes that client's queue to prevent late callbacks satisfying later operations. MTU rejection currently triggers a bounded reconnect, rather than silently reusing a potentially ambiguous callback queue.

## Frame

Little-endian. `AA`, version/10-bit total length, command type, encryption byte (0 only), three reserved bytes, 16-bit sequence, CRC16, command set/id, payload, CRC32. Minimum 18 / maximum 1023 bytes. Header CRC16 uses seed `0x3AA3`, reflected polynomial `0xA001`. CRC32 uses seed `0x3AA3`, reflected polynomial `0xEDB88320`, no final XOR. Tests use DJI's public mode-switch frame vector, not real pairing data.

Stream decoding supports split/coalesced notifications, length/CRC validation, junk resynchronization and bounded buffering. Unknown encrypted/layout frames are not interpreted.

## Pairing (`00/19`)

33-byte request: random per-install sender ID; stable randomly generated locally-administered 6-byte controller identity; firmware=0; first pairing verify mode=1, remembered pairing=0; random four-digit verification code. Neither real phone MAC nor hard-coded shared credentials are used.

The immediate response is **not** pairing approval. Wait for the camera's command with verify mode=2 and result=0, then ACK using the camera's original sequence and a positive camera index. Reject nonzero approval and unsupported model IDs; do not auto-retry rejected pairing in the same session. Some firmware can omit the immediate ACK; the camera's approval command is still accepted.

Documented IDs: Action 4 `0xFF33`, Action 5 Pro `0xFF44`, Action 6 `0xFF55`, Osmo 360 `0xFF66`. No physical model has been certified by this project.

## Recording and state

- `1D/03`: 9-byte payload: sender controller ID, **0=start / 1=stop**, four reserved zero bytes. No toggle command is used. The official examples disagree on hard-coded ID values; this implementation follows rhoenschrat's use of the stable **sender** controller ID. Physical validation must confirm acceptance on each firmware.
- `1D/05`: subscribe using mode 3 (periodic + change), frequency 20 (2 Hz), reserved zeros.
- `1D/02`: at least 38 bytes. Mode@0, state@1, record seconds@5, remaining seconds@23, power@28, temperature@30, battery@37. Battery values outside 0–100 are unknown. Longer packets do not move the battery offset.
- Only **state 3 in a recognized video mode** means confirmed recording. State 5 is pre-recording, not an active confirmed take. Photo capture is not video recording. Ready additionally requires a usable mode, idle/pre-record state, awake, no critical overheat, remaining recording time and a fresh status.
- `1D/06`: text mode/parameters only. **Not evidence of recording.** Some Action 6 firmware emits only this frame. The app displays “unverified” and blocks Record rather than lying. Supporting such firmware needs further protocol research / device traces; blindly trusting ACKs would violate the plan.

No status for >2 seconds triggers a safe re-subscription, not a record retry. Numeric status is invalidated after >3.5 seconds on a 1.5-second watchdog tick (at most approximately 5 seconds). Record/Stop require a newer matching status within six seconds after the GATT write; negative ACKs must match command set/id/sequence. No automatic record retry occurs on timeout. Per-GATT writes have their own eight-second timeout.

## Deliberately not implemented

Broadcast wake (`WKP`), GPS, timecode, settings changes, photos, newer unknown status layouts, Wi-Fi credentials or media transfer. Power cameras on manually. The official wake procedure has a recent-pairing/30-minute sleep constraint and must be bench-tested before adoption.

The reference projects retain their own licenses. No source from their application implementations is vendored. Gradle wrapper scripts/JAR are standard Gradle tooling, distributed under Apache-2.0 notices included with Gradle/scripts. DJI's public test-vector bytes are attributed above.
