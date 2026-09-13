package dev.djiremote

import dev.djiremote.ble.CameraTransport
import dev.djiremote.camera.*
import dev.djiremote.protocol.*
import dev.djiremote.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraSessionTest {
    private class FakeTransport : CameraTransport {
        override val notifications = Channel<ByteArray>(64)
        override val disconnected = CompletableDeferred<Unit>()
        val writes = mutableListOf<DjiProtocol.Frame>()
        var emitStatus = true
        var confirmRecord = true
        var rejected = false
        var commandError = false
        var ackSequenceOffset = 0
        var model = 0xff33
        var recording = false
        var handshakeSequence = 456
        override suspend fun connect(address: String) {}
        fun emit(frame: DjiProtocol.Frame) { check(notifications.trySend(DjiProtocol.encode(frame)).isSuccess) }
        fun status() {
            val p = ByteArray(38); p[0] = 1; p[1] = if (recording) 3 else 1; p[23] = 100; p[37] = 80
            emit(DjiProtocol.Frame(99, 0, 0x1d, 2, p))
        }
        override suspend fun write(bytes: ByteArray) {
            val f = requireNotNull(DjiProtocol.decode(bytes)); writes += f
            when {
                f.set == 0 && f.id == 0x19 && !f.response -> {
                    val p = DjiProtocol.buffer(33).putInt(model).array(); p[26] = 2; p[27] = if (rejected) 1 else 0
                    emit(DjiProtocol.Frame(handshakeSequence, 2, 0, 0x19, p))
                }
                f.set == 0x1d && f.id == 5 && emitStatus -> status()
                f.set == 0x1d && f.id == 3 -> {
                    emit(DjiProtocol.Frame((f.sequence + ackSequenceOffset) and 65535, 0x20, 0x1d, 3,
                        byteArrayOf(if (commandError) 2 else 0, 0, 0, 0, 0)))
                    if (confirmRecord && !commandError) { recording = f.payload.u8(4) == 0; status() }
                }
            }
        }
        override fun close() { disconnected.complete(Unit); notifications.close() }
    }
    private fun TestScope.session(t: FakeTransport, index: Int = 1) = CameraSession(t, backgroundScope,
        KnownCamera("test-$index", "Test camera"), ControllerIdentity(123, ByteArray(6)), index, {}, {}, { testScheduler.currentTime })
    @Test fun handshakeAcknowledgesCameraSequenceAndIndex() = runTest {
        val t = FakeTransport(); val s = session(t, 3); s.connect(); runCurrent()
        val ack = t.writes.single { it.response }
        assertEquals(t.handshakeSequence, ack.sequence); assertEquals(3, ack.payload.i32(5))
        assertTrue(s.state.value.ready); s.close()
    }
    @Test fun requiresStatusAfterHandshake() = runTest {
        val t = FakeTransport().apply { emitStatus = false }; val s = session(t); s.connect(); runCurrent()
        assertTrue(s.state.value.sessionReady); assertFalse(s.state.value.ready); s.close()
    }
    @Test fun rejectedPairingDoesNotSubscribeOrBecomeReady() = runTest {
        val t = FakeTransport().apply { rejected = true }; val s = session(t)
        try { s.connect(); fail("Expected rejected pairing") } catch (_: PairingRejected) {}
        assertFalse(s.state.value.sessionReady); assertFalse(t.writes.any { it.set == 0x1d }); s.close()
    }
    @Test fun unknownCameraRejected() = runTest {
        val t = FakeTransport().apply { model = 0x9999 }; val s = session(t)
        try { s.connect(); fail("Expected unsupported camera") } catch (_: UnsupportedCamera) {}
        assertFalse(s.state.value.ready); s.close()
    }
    @Test fun confirmedRecordAndStop() = runTest {
        val t = FakeTransport(); val s = session(t); s.connect(); runCurrent()
        assertTrue(s.record(true, 0)); assertTrue(s.state.value.recording)
        assertTrue(s.record(false, 0)); assertFalse(s.state.value.recording); assertTrue(s.state.value.ready); s.close()
    }
    @Test fun successfulAckWithoutStatusDoesNotBecomeRecordingOrRetry() = runTest {
        val t = FakeTransport().apply { confirmRecord = false }; val s = session(t); s.connect(); runCurrent()
        assertFalse(s.record(true, 0)); assertFalse(s.state.value.recording)
        assertEquals(1, t.writes.count { it.set == 0x1d && it.id == 3 })
        assertTrue(s.state.value.error!!.contains("timed out")); s.close()
    }
    @Test fun commandRejectionDisplayed() = runTest {
        val t = FakeTransport().apply { commandError = true }; val s = session(t); s.connect(); runCurrent()
        assertFalse(s.record(true, 0)); assertTrue(s.state.value.error!!.contains("rejected")); s.close()
    }
    @Test fun mismatchedAckDoesNotAffectCurrentCommand() = runTest {
        val t = FakeTransport().apply { commandError = true; ackSequenceOffset = 1 }; val s = session(t); s.connect(); runCurrent()
        assertFalse(s.record(true, 0)); assertTrue(s.state.value.error!!.contains("timed out")); s.close()
    }
    @Test fun missingFeedExpiresReadiness() = runTest {
        val t = FakeTransport(); val s = session(t); s.connect(); runCurrent(); assertTrue(s.state.value.ready)
        t.emitStatus = false; advanceTimeBy(5000); runCurrent()
        assertFalse(s.state.value.ready); assertFalse(s.state.value.statusFresh); s.close()
    }
    @Test fun modeOnlyFeedNeverReportsReady() = runTest {
        val t = FakeTransport().apply { emitStatus = false }; val s = session(t); s.connect(); runCurrent()
        t.emit(DjiProtocol.Frame(99, 0, 0x1d, 6, ByteArray(46))); runCurrent()
        assertFalse(s.state.value.ready); assertTrue(s.state.value.error!!.contains("unverified")); s.close()
    }
    @Test fun commandsAreQueuedToBothCamerasBeforeFirstConfirmation() = runTest {
        val a = FakeTransport().apply { confirmRecord = false }; val b = FakeTransport().apply { confirmRecord = false }
        val sa = session(a, 1); val sb = session(b, 2); sa.connect(); sb.connect(); runCurrent()
        val jobs = listOf(sa, sb).map { async { it.record(true, 0) } }; runCurrent()
        assertTrue(a.writes.any { it.set == 0x1d && it.id == 3 }); assertTrue(b.writes.any { it.set == 0x1d && it.id == 3 })
        assertTrue(jobs.all { !it.isCompleted })
        a.recording = true; b.recording = true; a.status(); b.status(); runCurrent()
        assertTrue(jobs.awaitAll().all { it }); sa.close(); sb.close()
    }
    @Test fun closeInvalidatesConfirmedStateButRetainsLastReport() = runTest {
        val t = FakeTransport(); val s = session(t); s.connect(); runCurrent(); s.record(true, 0); s.close()
        assertFalse(s.state.value.recording); assertFalse(s.state.value.sessionReady); assertTrue(s.state.value.status!!.recording)
    }
    @Test fun stoppingAlreadyIdleCameraSendsNothingAndSucceedsImmediately() = runTest {
        val t = FakeTransport().apply { confirmRecord = false }; val s = session(t)
        s.connect(); runCurrent(); t.emitStatus = false
        val before = testScheduler.currentTime
        assertTrue(s.record(false, before))
        assertEquals(before, testScheduler.currentTime)
        assertFalse(t.writes.any { it.set == 0x1d && it.id == 3 })
        assertNull(s.state.value.error); assertNull(s.state.value.pending); s.close()
    }
    @Test fun repeatedStopOnIdleCameraDoesNotCreateTimeouts() = runTest {
        val t = FakeTransport(); val s = session(t); s.connect(); runCurrent()
        repeat(20) { assertTrue(s.record(false, 0)) }
        assertFalse(t.writes.any { it.set == 0x1d && it.id == 3 }); assertNull(s.state.value.error); s.close()
    }
    @Test fun staleIdleCameraStillReceivesExplicitStop() = runTest {
        val t = FakeTransport().apply { confirmRecord = false }; val s = session(t); s.connect(); runCurrent()
        t.emitStatus = false; advanceTimeBy(5000); runCurrent()
        assertFalse(s.record(false, 0))
        assertEquals(1, t.writes.count { it.set == 0x1d && it.id == 3 })
        assertTrue(s.state.value.error!!.contains("timed out")); s.close()
    }
    @Test fun stopAfterSuccessfulStopIsNoOp() = runTest {
        val t = FakeTransport(); val s = session(t); s.connect(); runCurrent()
        assertTrue(s.record(true, 0)); assertTrue(s.record(false, 0))
        val commands = t.writes.count { it.set == 0x1d && it.id == 3 }
        assertTrue(s.record(false, 0)); assertEquals(commands, t.writes.count { it.set == 0x1d && it.id == 3 }); s.close()
    }
    @Test fun confirmedIdleStopClearsOldCommandTimeout() = runTest {
        val t = FakeTransport().apply { confirmRecord = false }; val s = session(t); s.connect(); runCurrent()
        assertFalse(s.record(true, 0)); assertNotNull(s.state.value.error)
        t.status(); runCurrent(); assertTrue(s.record(false, 0)); assertNull(s.state.value.error); s.close()
    }
    @Test fun individualControlDoesNotWriteToTheOtherCamera() = runTest {
        val a = FakeTransport(); val b = FakeTransport()
        val sa = session(a, 1); val sb = session(b, 2); sa.connect(); sb.connect(); runCurrent()
        assertTrue(sa.record(true, 0))
        assertFalse(b.writes.any { it.set == 0x1d && it.id == 3 }); assertTrue(sb.state.value.ready)
        sa.close(); sb.close()
    }

    @Test fun identicalIdleReportStillUnblocksAStopAwaiter() = runTest {
        val t = FakeTransport().apply { confirmRecord = false }; val s = session(t); s.connect(); runCurrent()
        t.emitStatus = false
        // Stale by the no-op cutoff, but just before the next freshness-watchdog tick.
        advanceTimeBy(3600); runCurrent()
        val op = async { s.record(false, testScheduler.currentTime) }; runCurrent()
        assertFalse(op.isCompleted)
        t.status(); runCurrent()
        assertTrue(op.await()); assertNull(s.state.value.error); s.close()
    }

    @Test fun responseFlaggedNumericStatusIsAccepted() = runTest {
        val t = FakeTransport(); val s = session(t); s.connect(); runCurrent()
        val p = ByteArray(38); p[0] = 1; p[1] = 3; p[23] = 100
        t.emit(DjiProtocol.Frame(30, 0x20, 0x1d, 2, p)); runCurrent()
        assertTrue(s.state.value.recording); s.close()
    }
    @Test fun shortRecordingPushIsAccepted() = runTest {
        val t = FakeTransport(); val s = session(t); s.connect(); runCurrent()
        t.emit(DjiProtocol.Frame(30, 0, 0x1d, 2, byteArrayOf(1,3,0,0,0,4,0))); runCurrent()
        assertTrue(s.state.value.recording); assertEquals(4,s.state.value.status?.seconds); s.close()
    }

}
