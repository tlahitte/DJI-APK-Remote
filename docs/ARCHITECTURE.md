# Architecture / safety model

`Compose activity / Glance widget / notification / external key → RemoteService → CameraManager → N CameraSession → N DjiGattClient`

- Only the foreground `connectedDevice` service creates the camera manager, scanner and connections. UI/widget never owns BLE. Service is not exported, not sticky, and not started at boot.
- The process-wide application exposes observable snapshots/settings; it does not keep a disconnected background BLE loop alive. Ending the session cancels its scope and closes clients. Starting again uses remembered cameras. Android UI/notification/widget interactions explicitly start the session.
- Discovery runs only when requested, for at most 20 seconds. Known cameras reconnect directly by their saved BLE address. Connection loops are independent and bounded (initial attempt plus three retries, 2/4/8-second backoff); manual Reconnect is required after exhaustion. Pairing rejection is terminal until manual retry. Rotating device addresses may require removing/re-adding the camera.
- A generation guard prevents callbacks from an old/replaced connection overwriting the replacement's state.
- Record gates on paired session + fresh numeric status + appropriate camera state. A BLE link alone is never READY. Default all-camera readiness is checked before fan-out. Connections can still drop after the gate: this cannot be atomic across independent radios, so partial failures are explicit.
- Independent coroutine commands queue to every eligible GATT before waiting for the other cameras' confirmations. GATT operations remain serial within each client. Whole-group operations are guarded against repeated taps.
- STOP is explicit, sent to every initialized session even if status is stale or Record is blocked. Disconnected cameras cannot be stopped; UI warnings retain their last reported recording status as historical, not current.
- State is confirmed from numeric status, not successful writes or ACKs. Timeouts never cause blind Record retries. If state is uncertain, generic single-button toggle chooses Stop, not Start.
- Settings and pairing identity live in the app's private no-backup DataStore; diagnostics are a bounded in-memory ring, discarded with the process. Only generic event labels, session camera indices and monotonic times are logged.
- Glance persists only aggregate counts/flags/update time. Recreated processes default to OFF, not a persisted green/REC flag. Widget updates on summary changes and every 15 seconds while active. Android can retain a rendered widget after process death: the visible timestamp must be checked, and tapping status opens the app. There is no claim that a static widget can detect its own dead process instantaneously.
- Learning consumes a key only while learning. Saved keys are intercepted while the activity is focused; an opt-in active MediaSession can receive matching media keys while Android routes them here. No accessibility service, keylogger, fake audio, or unsupported global volume interception is used. Debounce is 650 ms; repeat events are ignored.

## Remaining limitations

No genlock / hardware synchronization, no guaranteed locked-screen media routing, no radio tests, no measured >99% reliability, no confirmed compatibility matrix. Firmware may not expose the numeric status needed for safe remote control. End session deliberately **does not issue Stop**; the confirmation dialog explains this, since terminating a connection must not masquerade as successful camera shutdown.

## 0.2 additions

Group and individual commands share the same operation mutex. An explicit selected-camera ID filters the eligible sessions to that one camera and bypasses only the group-readiness requirement. Widget state remains group-only.

Freshly confirmed idle Stop is a no-op; a monotonically increasing report revision ensures repeated identical status notifications can still wake a confirmation waiter. Stale/unknown status never takes the no-op shortcut.

Experimental exposure wheels persist a local preset only, without calling the service/manager/transport. Modern Android notification permission is deliberately absent; foreground-service registration remains mandatory and visible in Active apps.

## 0.3 additions

Widget rendering now consumes a serialized persisted aggregate snapshot, not a live service Flow inside Glance. A per-process token and elapsed-time age guard reject stale snapshots; writes re-read the latest service state to avoid older queued OFF snapshots overwriting a newer state. A five-second service heartbeat complements immediate change updates. Record and Stop remain separate controls.

ExposureBatch performs all read-only preflights before setting writes, and dispatches independent per-camera apply operations in parallel. ExposureClient shares each camera's serialized transport but has separate DUML sequence/command matching. The combined stream decoder demultiplexes R-SDK and DUML without scanning inside payloads. Only getter readback matching the chosen manual shutter and ISO is reported as confirmed. Frame encoding research is not a guarantee of Action 4 firmware support.
