package dev.djiremote.camera

import dev.djiremote.protocol.CameraStatus
import dev.djiremote.protocol.ExposureReadback

enum class ConnectionState { OFFLINE, CONNECTING, PAIRING, READY, RECONNECTING, ERROR, REJECTED }
data class CameraState(
    val id: String, val name: String, val connection: ConnectionState = ConnectionState.OFFLINE,
    val status: CameraStatus? = null, val statusFresh: Boolean = false,
    val sessionReady: Boolean = false, val pending: Boolean? = null,
    val error: String? = null, val pairingCode: Int? = null,
    val sentAtMs: Long? = null, val confirmedAtMs: Long? = null,
    val reportVersion: Long = 0,
    val modelId: Int? = null,
    val exposure: ExposureReadback? = null,
    val exposureMessage: String = "Not checked",
    val exposureSupported: Boolean = false,
) {
    val recording get() = statusFresh && status?.recording == true
    val confirmedIdle get() = sessionReady && statusFresh && pending == null && status?.state in setOf(0, 1, 2, 5)
    val canCheckExposure get() = modelId == 0xff33 && confirmedIdle && status?.mode in dev.djiremote.protocol.DjiCommands.videoModes
    val possiblyRecording get() = recording || pending == true || (!statusFresh && status?.recording == true)
    val ready get() = sessionReady && statusFresh && status?.canStart == true && pending == null
    val uncertain get() = !statusFresh || pending != null
    val label: String get() = when {
        pending == true -> "STARTING · awaiting camera"
        pending == false -> "STOPPING · awaiting camera"
        recording -> "REC · ${status?.seconds ?: 0}s"
        error != null -> error
        ready -> "READY"
        sessionReady && !statusFresh -> "Waiting for verified camera status"
        sessionReady -> "Not ready · check mode / card / temperature"
        connection == ConnectionState.PAIRING -> "Confirm code on camera"
        else -> connection.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}
data class RemoteState(
    val active: Boolean = false, val cameras: List<CameraState> = emptyList(),
    val scanning: Boolean = false, val partialAllowed: Boolean = false,
    val busy: Boolean = false, val message: String? = null,
    val operationLabel: String? = null,
) {
    val canCheckExposure get() = active && !busy && cameras.isNotEmpty() && cameras.all { it.canCheckExposure }
    val canApplyExposure get() = canCheckExposure && cameras.all { it.exposureSupported }
    val readyCount get() = cameras.count { it.ready }
    val recordingCount get() = cameras.count { it.recording }
    val canRecord get() = active && !busy && cameras.isNotEmpty() &&
        !cameras.any { it.recording || it.pending != null } && readyCount > 0 &&
        (partialAllowed || readyCount == cameras.size)
    val canStop get() = active && !busy && cameras.any { it.sessionReady }
    val shouldStop get() = cameras.any { it.recording || it.uncertain }
    val hasRecordingActivity get() = cameras.any { it.possiblyRecording }
    fun canControlCamera(id: String, start: Boolean): Boolean {
        if (!active || busy) return false
        val camera = cameras.firstOrNull { it.id == id } ?: return false
        return if (start) camera.ready else camera.sessionReady && camera.pending == null
    }
    val headline get() = when {
        !active -> "Remote inactive"
        recordingCount > 0 -> "$recordingCount / ${cameras.size} REC" + if (cameras.any { it.uncertain }) " · check cameras" else ""
        busy -> "Waiting for camera confirmation"
        cameras.isEmpty() -> "Add cameras to begin"
        else -> "$readyCount / ${cameras.size} READY"
    }
}
