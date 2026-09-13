package dev.djiremote.widget

import dev.djiremote.camera.ConnectionState
import dev.djiremote.camera.RemoteState

enum class GroupAction { CONNECT, RECORD, STOP }
/** Widget presentation is based only on the whole group, never a selected/individual camera. */
data class GroupWidgetState(
    val action: GroupAction, val enabled: Boolean, val title: String, val description: String,
    val recording: Boolean = false, val active: Boolean = false,
) {
    companion object {
        fun from(s: RemoteState): GroupWidgetState {
            val total = s.cameras.size
            if (!s.active) return GroupWidgetState(GroupAction.CONNECT, true, "DJI Multi Remote",
                if (total == 0) "Connect your camera group" else "$total cameras · session off")
            val recording = s.recordingCount
            val action = if (s.hasRecordingActivity) GroupAction.STOP else GroupAction.RECORD
            val enabled = if (action == GroupAction.STOP) s.canStop else s.canRecord
            val errors = s.message != null || s.cameras.any { it.error != null || it.connection in setOf(ConnectionState.ERROR, ConnectionState.REJECTED) }
            val title = when {
                s.busy -> s.operationLabel ?: if (s.cameras.any { it.pending == true }) "Starting take…" else "Stopping…"
                recording > 0 -> if (recording == total) "All cameras recording" else "Partial recording"
                s.hasRecordingActivity -> "Recording status unknown"
                total == 0 -> "No cameras added"
                s.canRecord -> "Ready to record"
                s.scanning -> "Searching for cameras…"
                s.cameras.any { it.connection == ConnectionState.PAIRING } -> "Approve camera pairing"
                s.cameras.any { it.connection in setOf(ConnectionState.CONNECTING, ConnectionState.RECONNECTING) } -> "Connecting cameras…"
                else -> "Camera group not ready"
            }
            val description = when {
                total == 0 -> "Tap here to add cameras"
                recording > 0 -> "$recording / $total recording" + if (recording < total || errors) " · check group" else " · tap stop to finish"
                s.hasRecordingActivity -> "Check cameras · stop remains available"
                s.busy -> "Waiting for camera confirmation"
                errors -> "${s.readyCount} / $total ready · check group"
                s.canRecord && s.readyCount < total -> "${s.readyCount} / $total ready · partial enabled"
                else -> "${s.readyCount} / $total ready"
            }
            return GroupWidgetState(action, enabled, title, description, recording > 0, true)
        }
    }
}
