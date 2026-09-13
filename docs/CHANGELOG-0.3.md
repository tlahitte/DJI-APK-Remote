# 0.3.0-preview

- Branded 4×2 widget using the supplied banner gradient and separately rendered square badge, avoiding stretched artwork. Branding, group counts and two labeled controls are easier to read. Existing one-row widgets get a compact fallback until re-added.
- Record and Stop both stay visible throughout an active session. Stop is not removed just because recording status is delayed.
- Widget content reads versioned persisted Glance state instead of depending on an in-process Flow collector. Serialized updates read the latest service state, include a process token to reject stale process data, and refresh on changes plus a five-second heartbeat.
- R-SDK numeric status accepts response-flagged reports and the known seven-byte prefix. Partial status can confirm recording, but cannot invent battery/health readiness; extended status is re-requested when missing.
- Real experimental Action 4 DUML exposure client: read getters, explicit group Apply, manual mode/shutter/ISO setters, then strict readback verification. No setters if any group read preflight fails. Whole-stop ISO 100–12800.
- Additional mixed-frame, payload, timeout, mismatch, no-write-preflight, parallel-group and widget-state tests: 112 unit tests pass; Android lint has no errors.

Action 4 firmware acceptance still requires physical testing. The implementation can attempt supported commands and report real replies; it does not claim they work on every Action 4 session. No settings are sent by wheel changes alone.
