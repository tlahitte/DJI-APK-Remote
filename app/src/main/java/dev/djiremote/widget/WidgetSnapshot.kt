package dev.djiremote.widget

import dev.djiremote.camera.RemoteState

/** A versioned, durable GROUP-only view. Never owns BLE or depends on a live composition collector. */
data class WidgetSnapshot(
    val active: Boolean = false, val expected: Int = 0, val ready: Int = 0, val recording: Int = 0,
    val canRecord: Boolean = false, val canStop: Boolean = false, val busy: Boolean = false,
    val title: String = "Remote inactive", val detail: String = "Tap Connect to start a session",
    val updatedElapsed: Long = 0, val processToken: String = "", val updatedLabel: String = "—",
) {
    fun validFor(currentToken: String, now: Long): Boolean = active && processToken == currentToken &&
        now >= updatedElapsed && now - updatedElapsed <= 20_000
    companion object {
        fun from(state: RemoteState, now: Long, token: String, label: String): WidgetSnapshot {
            val group = GroupWidgetState.from(state)
            return WidgetSnapshot(state.active, state.cameras.size, state.readyCount, state.recordingCount,
                state.canRecord, state.canStop, state.busy, group.title, group.description, now, token, label)
        }
    }
}
