# 0.2.0-preview

- User-provided icon; density-specific and adaptive launcher assets with metadata stripped.
- Dark navy/coral (#FF5D64) redesign, icon-based bottom navigation and per-camera controls.
- 4×1 widget: one left-side Connect/Record/Stop button, aggregate group title/description on the right, coral only for camera-confirmed recording. The rest of the surface opens the main app. Unknown last-recording state retains Stop without claiming confirmed recording.
- Settings houses widget pinning (button only), diagnostics, version/info, safety and the privacy statement. Old global explanatory clutter removed.
- Volume Up default; preserve explicit custom/disabled mappings and offer Reset to Volume Up.
- Individual camera Record/Stop does not require the rest of the group to be ready. The all-camera safety gate remains on group Record.
- Stop on a freshly verified idle camera succeeds without sending/waiting for a redundant command. Stale/unknown cameras still receive explicit Stop and require verification. Numeric report revisions prevent StateFlow from suppressing identical reports needed by a waiting command.
- Android 13+: no POST_NOTIFICATIONS permission or prompt. Background BLE remains a foreground service with the required Active apps visibility. Older versions retain a minimal silent notification.
- Opt-in snapping wheels for global shutter-time and ISO presets. Locally saved only; no unsupported Bluetooth writes and no fake applied status.
- 78 unit tests, Android lint with no errors, and emulator-only visual/UX smoke checks. Real camera validation is still required for this update.

Existing widgets may need removing/re-adding to pick up the new default 4×1 dimensions. The update uses the same development signing key and does not clear saved cameras.
