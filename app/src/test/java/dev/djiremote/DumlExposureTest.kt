package dev.djiremote

import dev.djiremote.ble.CameraTransport
import dev.djiremote.camera.*
import dev.djiremote.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DumlExposureTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun documentedDumlVectorRoundTrips() {
        val b = hex("551304030201020440028e01010f000104bec8")
        val f = requireNotNull(DumlProtocol.decode(b))
        assertEquals(2, f.set); assertEquals(0x8e, f.command); assertArrayEquals(b, DumlProtocol.encode(f))
    }
    @Test fun everyCorruptedByteIsRejected() {
        val b = hex("551304030201020440028e01010f000104bec8")
        b.indices.forEach { i -> val corrupt = b.copyOf(); corrupt[i] = (corrupt[i].toInt() xor 1).toByte(); assertNull(DumlProtocol.decode(corrupt)) }
    }
    @Test fun setShutterPayloadUsesManualReciprocalLayout() {
        assertArrayEquals(hex("01908100"), ExposureCommands.shutter(ExposurePreset(400, 100)))
        assertArrayEquals(hex("01c88000"), ExposureCommands.shutter(ExposurePreset(200, 100)))
    }
    @Test fun isoUsesAbsoluteEnumNotNumericLittleEndian() {
        assertArrayEquals(byteArrayOf(3), ExposureCommands.iso(ExposurePreset(400, 100)))
        assertArrayEquals(byteArrayOf(10), ExposureCommands.iso(ExposurePreset(400, 12800)))
    }
    @Test fun manualExposureModeUsesTwoBytes() { assertArrayEquals(byteArrayOf(4, 0), ExposureCommands.manualMode()) }
    @Test fun repliesHaveExplicitStatusAndValidatedLength() {
        assertEquals(3, ExposureCommands.parseIso(byteArrayOf(0, 3)))
        val s = ExposureCommands.parseShutter(hex("0001908100"))
        assertFalse(s.auto); assertTrue(s.reciprocal); assertEquals(400, s.integral)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectedIsoIsNotAValue() { ExposureCommands.parseIso(byteArrayOf(0xe0.toByte(), 3)) }
    @Test(expected = IllegalArgumentException::class) fun malformedShutterIsNotAValue() { ExposureCommands.parseShutter(byteArrayOf(0, 1, 2)) }
    @Test fun automaticSettingsDoNotMatchManualPreset() {
        assertFalse(ExposureReadback(0, ShutterReadback(true, true, 400, 0)).matches(ExposurePreset(400,100)))
    }
    @Test fun dualStreamHandlesEveryFragmentBoundary() {
        val a = DjiProtocol.encode(DjiProtocol.Frame(1, 0, 0x1d, 2, ByteArray(38)))
        val b = DumlProtocol.encode(DumlProtocol.Frame(3, 0x29, byteArrayOf(0, 1, 2, 3, 0), flags = 0xc0, sender = 1, receiver = 2))
        val combined = a + b + a
        for (split in 1 until combined.size) {
            val d = CameraPacketDecoder()
            val frames = d.accept(combined.copyOf(split)) + d.accept(combined.copyOfRange(split, combined.size))
            assertEquals(3, frames.size); assertTrue(frames[0] is CameraPacket.Rsdk); assertTrue(frames[1] is CameraPacket.Duml)
        }
    }
    @Test fun embeddedProtocolBytesAreNotAnotherFrame() {
        val nested = DjiProtocol.encode(DjiProtocol.Frame(3, 0, 0x1d, 2, ByteArray(38)))
        val outer = DumlProtocol.encode(DumlProtocol.Frame(7, 1, nested))
        val frames = CameraPacketDecoder().accept(outer)
        assertEquals(1, frames.size); assertTrue(frames.single() is CameraPacket.Duml)
    }
    private class Fake : CameraTransport {
        override val notifications = Channel<ByteArray>(64)
        override val disconnected = CompletableDeferred<Unit>()
        lateinit var client: ExposureClient
        val commands = mutableListOf<Int>()
        var isoCode = 0
        var shutter = byteArrayOf(0, 0, 0, 0)
        var ignoreWrites = false
        var rejectCommand = -1
        var silent = false
        var wrongSequence = false
        override suspend fun connect(address: String) {}
        override suspend fun write(bytes: ByteArray) {
            val f = requireNotNull(DumlProtocol.decode(bytes)); commands += f.command
            if (silent) return
            if (!ignoreWrites && f.command != rejectCommand) when (f.command) {
                ExposureCommands.SET_ISO -> isoCode = f.payload.u8(0)
                ExposureCommands.SET_SHUTTER -> shutter = f.payload.copyOf()
            }
            val result = if (f.command == rejectCommand) byteArrayOf(0xe0.toByte()) else when (f.command) {
                ExposureCommands.GET_ISO -> byteArrayOf(0, isoCode.toByte())
                ExposureCommands.GET_SHUTTER -> byteArrayOf(0) + shutter
                else -> byteArrayOf(0)
            }
            client.receive(DumlProtocol.Frame(if (wrongSequence) f.sequence xor 1 else f.sequence, f.command, result, 0xc0, 1, 2))
        }
        override fun close() { client.close() }
    }
    private fun fake() = Fake().also { it.client = ExposureClient(it) }
    @Test fun readOnlyQueriesNeverSendSettingWrites() = runTest {
        val f = fake(); val value = f.client.read()
        assertEquals(listOf(ExposureCommands.GET_ISO, ExposureCommands.GET_SHUTTER), f.commands)
        assertEquals("Auto", value.isoLabel)
    }
    @Test fun appliesAndReadsBackActualValues() = runTest {
        val f = fake(); f.client.read()
        assertTrue(f.client.apply(ExposurePreset(400, 800)) {}.matches(ExposurePreset(400, 800)))
        assertEquals(listOf(0x2b,0x29,0x1e,0x28,0x2a,0x2b,0x29), f.commands)
    }
    @Test fun successAcksWithUnchangedSettingsFailVerification() = runTest {
        val f = fake().apply { ignoreWrites = true }
        try { f.client.apply(ExposurePreset(400, 800)) {}; fail("Must not call unchanged settings applied") } catch (_: IllegalStateException) {}
    }
    @Test fun rejectionStopsSubsequentSettingWrites() = runTest {
        val f = fake().apply { rejectCommand = ExposureCommands.SET_SHUTTER }
        try { f.client.apply(ExposurePreset(400, 800)) {}; fail("Expected rejection") } catch (_: IllegalStateException) {}
        assertFalse(ExposureCommands.SET_ISO in f.commands)
    }
    @Test fun wrongSequenceCannotSatisfyGetter() = runTest {
        val f = fake().apply { wrongSequence = true }
        try { f.client.read(); fail("Expected timeout") } catch (_: TimeoutCancellationException) {}
        assertEquals(listOf(ExposureCommands.GET_ISO), f.commands)
    }
    @Test fun getterTimeoutIsNotRetried() = runTest {
        val f = fake().apply { silent = true }
        try { f.client.read(); fail("Expected timeout") } catch (_: TimeoutCancellationException) {}
        assertEquals(1, f.commands.size)
    }
    @Test fun idleGuardRunsBeforeEverySetter() = runTest {
        val f = fake(); var checks = 0
        try { f.client.apply(ExposurePreset(400,800)) { checks++; check(checks < 2) }; fail("Expected guard") } catch (_: IllegalStateException) {}
        assertEquals(listOf(ExposureCommands.SET_MODE), f.commands)
    }
    @Test fun delayedReadbackDoesNotResendSetters() = runTest {
        val f = fake().apply { ignoreWrites = true }
        val job = async { f.client.apply(ExposurePreset(400,800)) {} }; runCurrent()
        assertFalse(job.isCompleted)
        f.isoCode = 6; f.shutter = ExposureCommands.shutter(ExposurePreset(400,800))
        advanceTimeBy(300); runCurrent()
        assertTrue(job.await().matches(ExposurePreset(400,800)))
        assertEquals(1,f.commands.count { it==ExposureCommands.SET_SHUTTER })
        assertEquals(1,f.commands.count { it==ExposureCommands.SET_ISO })
    }

    @Test fun alreadyManualCameraDoesNotNeedModeWrite() = runTest {
        val f = fake().apply { isoCode=3; shutter=ExposureCommands.shutter(ExposurePreset()); rejectCommand=ExposureCommands.SET_MODE }
        val current=f.client.read()
        assertTrue(f.client.apply(ExposurePreset(400,800),current) {}.matches(ExposurePreset(400,800)))
        assertFalse(ExposureCommands.SET_MODE in f.commands)
    }

}
