package dev.djiremote

import dev.djiremote.camera.*
import dev.djiremote.protocol.*
import org.junit.Assert.*
import org.junit.Test

class ReadinessTest {
    private fun status(state: Int = 1, mode: Int = 1) = CameraStatus(mode, state, 0, 1000, false, 0, 80)
    private fun camera(id: String = "sample") = CameraState(id, "Test camera", ConnectionState.READY, status(), true, true)
    @Test fun zeroCamerasCannotRecord() { assertFalse(RemoteState(active = true).canRecord) }
    @Test fun allReadyCanRecord() { assertTrue(RemoteState(active = true, cameras = listOf(camera("a"), camera("b"))).canRecord) }
    @Test fun missingCameraBlocksByDefault() { assertFalse(RemoteState(active = true, cameras = listOf(camera(), CameraState("b", "B"))).canRecord) }
    @Test fun explicitPartialOverrideAllowsReadySubset() { assertTrue(RemoteState(active = true, cameras = listOf(camera(), CameraState("b", "B")), partialAllowed = true).canRecord) }
    @Test fun connectedWithoutStatusIsNotReady() { assertFalse(camera().copy(statusFresh = false).ready) }
    @Test fun pairingWithoutSessionIsNotReady() { assertFalse(camera().copy(sessionReady = false).ready) }
    @Test fun staleRecordingIsUnknownNotGreenOrConfirmedRec() {
        val c = camera().copy(status = status(3), statusFresh = false)
        assertFalse(c.ready); assertFalse(c.recording); assertTrue(c.uncertain)
        assertTrue(RemoteState(active = true, cameras = listOf(c)).shouldStop)
    }
    @Test fun preRecordingIsNotConfirmedRecording() { assertFalse(status(5).recording); assertTrue(status(5).canStart) }
    @Test fun photoCaptureDoesNotCountAsVideoRecording() { assertFalse(status(3, 5).recording); assertFalse(status(1, 5).canStart) }
    @Test fun playbackBlocksReadiness() { assertFalse(status(2).canStart) }
    @Test fun hotCameraBlocksReadiness() { assertFalse(status().copy(temperature = 2).canStart) }
    @Test fun fullCardBlocksReadiness() { assertFalse(status().copy(remainingSeconds = 0).canStart) }
    @Test fun sleepingCameraBlocksReadiness() { assertFalse(status().copy(sleeping = true).canStart) }
    @Test fun pendingCommandBlocksStart() { assertFalse(camera().copy(pending = true).ready) }
    @Test fun stopAvailableEvenIfReadinessFails() {
        val s = RemoteState(active = true, cameras = listOf(camera().copy(statusFresh = false)))
        assertFalse(s.canRecord); assertTrue(s.canStop)
    }
    @Test fun busyBlocksDuplicateCommands() {
        val s = RemoteState(active = true, busy = true, cameras = listOf(camera()))
        assertFalse(s.canRecord); assertFalse(s.canStop)
    }
    @Test fun recordingOneCameraPreventsAnotherRecordAll() {
        assertFalse(RemoteState(active = true, cameras = listOf(camera(), camera("b").copy(status = status(3))), partialAllowed = true).canRecord)
    }
    @Test fun shortStatusRejected() { for (size in 0..37) assertNull(CameraStatus.parse(ByteArray(size))) }
    @Test fun batteryUsesDocumentedOffsetNotLastByte() {
        val p = ByteArray(44); p[0] = 1; p[1] = 1; p[37] = 80; p[43] = 20
        assertEquals(80, CameraStatus.parse(p)?.battery)
    }
    @Test fun invalidBatteryIsUnknown() {
        val p = ByteArray(38); p[1] = 1; p[37] = 0xff.toByte(); assertNull(CameraStatus.parse(p)?.battery)
    }
    @Test fun unknownStatusCodeRejected() {
        val p = ByteArray(38); p[1] = 7; assertNull(CameraStatus.parse(p))
    }
}
