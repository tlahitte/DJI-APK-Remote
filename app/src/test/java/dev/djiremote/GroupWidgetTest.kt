package dev.djiremote

import dev.djiremote.camera.*
import dev.djiremote.protocol.CameraStatus
import dev.djiremote.storage.Settings
import dev.djiremote.widget.*
import org.junit.Assert.*
import org.junit.Test

class GroupWidgetTest {
    private fun camera(id: String = "a", recording: Boolean = false) = CameraState(id, "Test camera", ConnectionState.READY,
        CameraStatus(1, if (recording) 3 else 1, 0, 1000, false, 0, 80), true, true)
    @Test fun offOnlyOffersConnect() {
        val v = GroupWidgetState.from(RemoteState())
        assertEquals(GroupAction.CONNECT, v.action); assertTrue(v.enabled); assertFalse(v.recording)
    }
    @Test fun idleOnlyOffersRecord() {
        val v = GroupWidgetState.from(RemoteState(active = true, cameras = listOf(camera())))
        assertEquals(GroupAction.RECORD, v.action); assertTrue(v.enabled); assertFalse(v.recording)
    }
    @Test fun recordingSwitchesToStopAndCoral() {
        val v = GroupWidgetState.from(RemoteState(active = true, cameras = listOf(camera(recording = true))))
        assertEquals(GroupAction.STOP, v.action); assertTrue(v.enabled); assertTrue(v.recording)
    }
    @Test fun partialRecordingStillOffersGroupStop() {
        val v = GroupWidgetState.from(RemoteState(active = true, cameras = listOf(camera(recording = true), camera("b"))))
        assertEquals(GroupAction.STOP, v.action); assertEquals("Partial recording", v.title)
        assertTrue(v.description.contains("1 / 2 recording")); assertTrue(v.recording)
    }
    @Test fun unknownLastRecordingKeepsStopButNeverClaimsConfirmedRecording() {
        val c = camera(recording = true).copy(statusFresh = false)
        val v = GroupWidgetState.from(RemoteState(active = true, cameras = listOf(c)))
        assertEquals(GroupAction.STOP, v.action); assertFalse(v.recording)
        assertEquals("Recording status unknown", v.title)
    }
    @Test fun disconnectedPreviouslyRecordingDoesNotEnableCommands() {
        val c = camera(recording = true).copy(statusFresh = false, sessionReady = false)
        val v = GroupWidgetState.from(RemoteState(active = true, cameras = listOf(c)))
        assertEquals(GroupAction.STOP, v.action); assertFalse(v.enabled)
    }
    @Test fun startingIsNotOptimisticallyRed() {
        val c = camera().copy(pending = true)
        val v = GroupWidgetState.from(RemoteState(active = true, busy = true, cameras = listOf(c)))
        assertEquals(GroupAction.STOP, v.action); assertFalse(v.enabled); assertFalse(v.recording)
    }
    @Test fun missingCameraDisablesGroupRecord() {
        val v = GroupWidgetState.from(RemoteState(active = true, cameras = listOf(camera(), CameraState("b", "B"))))
        assertEquals(GroupAction.RECORD, v.action); assertFalse(v.enabled)
    }
    @Test fun partialOverrideEnablesGroupRecordExplicitly() {
        val v = GroupWidgetState.from(RemoteState(active = true, partialAllowed = true, cameras = listOf(camera(), CameraState("b", "B"))))
        assertTrue(v.enabled); assertTrue(v.description.contains("partial enabled"))
    }
    @Test fun individualRecordBypassesOnlyGroupGate() {
        val s = RemoteState(active = true, cameras = listOf(camera(), CameraState("b", "B")))
        assertFalse(s.canRecord); assertTrue(s.canControlCamera("a", true)); assertFalse(s.canControlCamera("b", true))
    }
    @Test fun individualCanRecordWhileOtherCameraRecords() {
        val s = RemoteState(active = true, cameras = listOf(camera(recording = true), camera("b")))
        assertFalse(s.canRecord); assertTrue(s.canControlCamera("b", true)); assertFalse(s.canControlCamera("a", true))
    }
    @Test fun invalidSelectionOrBusyCannotTriggerIndividualCamera() {
        val s = RemoteState(active = true, cameras = listOf(camera()))
        assertFalse(s.canControlCamera("not-added", true)); assertFalse(s.copy(busy = true).canControlCamera("a", true))
        assertFalse(s.copy(active = false).canControlCamera("a", false))
    }
    @Test fun defaultMappingIsVolumeUp() { assertEquals(24, Settings().keyCode) }
    @Test fun explicitMappingIsPreserved() { assertEquals(85, Settings(keyCode = 85).keyCode); assertEquals(-1, Settings(keyCode = -1).keyCode) }
    @Test fun widgetContainsNoIndividualIdentity() {
        val s = RemoteState(active = true, cameras = listOf(camera().copy(name = "PRIVATE CAMERA NAME")))
        assertFalse(GroupWidgetState.from(s).toString().contains("PRIVATE CAMERA NAME"))
    }
}
