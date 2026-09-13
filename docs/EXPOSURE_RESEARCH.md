# Experimental exposure and ISO: staged UI only

The two wheels are local group presets. “Exposure” in this UI means shutter time (1/N seconds), not exposure-value compensation. ISO choices span 100–12800; neither the ranges nor each value are asserted to be supported by every camera/firmware/frame-rate combination.

**There is no transmit/apply path.** Wheel callbacks only edit non-sensitive integer values in private on-device DataStore. Experimental mode is opt-in; changing a wheel does not start a service or connect to a camera. No settings are shown as camera-confirmed.

## Evidence and limits

- DJI's documented R-SDK (`AA` framing, our pairing and recording connection) does not document complete manual shutter/ISO setters/readback: https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/92fe23e5a749f189593f980a26a105c3bb66aa1c/docs/protocol_data_segment.md .
- Osmosis's separate DUML research (`55` framing) lists BLE/datalink camera parameters `02/8E`, including PID `000F` ISO *limit* values 04=100–800 and 05=100–1600: https://github.com/KonradIT/osmosis/blob/2fcdbc97e6dbefc875d425368be67cf32b50bb06/MEDIA_PROTOCOL.md#14-camera-parameters .
- The same research lists `camcap_iso`, `camcap_shutter` and exposure-mode capability topics, but a topic name is not a verified setter, range table or readback implementation.

We do not send DUML packets over an existing R-SDK session speculatively, conflate ISO limits with fixed manual ISO, sweep undocumented values or infer success from a wheel position.

## Gate before enabling real controls

1. Identify camera model/firmware and a supported session/protocol for reads and writes.
2. Establish capability/range queries, manual/auto mode behavior, exact shutter/ISO setters, status decoding and error handling.
3. On a test camera, apply one reversible setting and read it back. Record whether changing exposure mode is also required. Restore the prior setting after testing.
4. Repeat with a second camera; then fan out independent per-camera writes and display per-camera confirmations/failures.
5. Do not promise atomic or frame-synchronized changes: BLE delivery and camera application happen independently.

Parallel dispatch is supported by the architecture; successful exposure/ISO control on actual cameras is **not yet established**.
