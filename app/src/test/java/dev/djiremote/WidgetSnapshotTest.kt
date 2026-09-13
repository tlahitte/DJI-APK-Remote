package dev.djiremote

import dev.djiremote.camera.*
import dev.djiremote.protocol.*
import dev.djiremote.widget.*
import org.junit.Assert.*
import org.junit.Test

class WidgetSnapshotTest {
    private fun camera(recording: Boolean) = CameraState("test", "Not put in widget", ConnectionState.READY,
        CameraStatus(1,if(recording) 3 else 1,5,100,false,0,90),true,true)
    @Test fun persistedRecordingStateKeepsExplicitStop() {
        val s = WidgetSnapshot.from(RemoteState(active=true,cameras=listOf(camera(true))),100,"process","12:00")
        assertEquals(1,s.recording); assertTrue(s.canStop); assertFalse(s.canRecord); assertTrue(s.validFor("process",101))
    }
    @Test fun sameSessionCanTransitionReadyRecordingStopped() {
        val idle = RemoteState(active=true,cameras=listOf(camera(false)))
        val a=WidgetSnapshot.from(idle,100,"p","12:00")
        val b=WidgetSnapshot.from(idle.copy(cameras=listOf(camera(true))),200,"p","12:01")
        val c=WidgetSnapshot.from(idle,300,"p","12:02")
        assertTrue(a.canRecord); assertEquals(1,b.recording); assertTrue(c.canRecord); assertEquals(0,c.recording)
    }
    @Test fun oldProcessCannotResurrectRecording() {
        val s=WidgetSnapshot.from(RemoteState(active=true,cameras=listOf(camera(true))),100,"old","12:00")
        assertFalse(s.validFor("new",101))
    }
    @Test fun staleAndRebootedElapsedTimesAreRejected() {
        val s=WidgetSnapshot.from(RemoteState(active=true),100,"p","12:00")
        assertFalse(s.validFor("p",20101)); assertFalse(s.validFor("p",99))
    }
    @Test fun snapshotsContainNoCameraNamesOrAddresses() {
        val s=WidgetSnapshot.from(RemoteState(active=true,cameras=listOf(camera(true))),100,"p","12:00")
        assertFalse(s.toString().contains("Not put in widget")); assertFalse(s.toString().contains("id=test"))
    }
    @Test fun shortNumericStatusCanConfirmRecordingWithoutFakingHealth() {
        val p=byteArrayOf(1,3,0,0,0,5,0)
        val s=checkNotNull(CameraStatus.parse(p)); assertTrue(s.recording); assertFalse(s.complete); assertNull(s.battery); assertFalse(s.canStart)
    }
    @Test fun shortIdleStatusDoesNotPretendHealthReadiness() {
        val s=checkNotNull(CameraStatus.parse(byteArrayOf(1,1,0,0,0,0,0))); assertFalse(s.canStart)
    }
}
