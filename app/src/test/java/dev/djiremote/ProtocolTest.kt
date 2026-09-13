package dev.djiremote

import dev.djiremote.protocol.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ProtocolTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    // Public DJI documentation vector; these are published example bytes, not real pairing material.
    private val vector = hex("AA1B000100000000050057EE1D04000033FF0A01473936F4FAE1D0")
    @Test fun officialVectorDecodesAndReencodesExactly() {
        val f = requireNotNull(DjiProtocol.decode(vector))
        assertEquals(5, f.sequence); assertEquals(0x1d, f.set); assertEquals(4, f.id)
        assertArrayEquals(vector, DjiProtocol.encode(f))
    }
    @Test fun officialCrcValues() {
        assertEquals(0xee57, DjiProtocol.crc16(vector, 10))
        assertEquals(0xd0e1faf4.toInt(), DjiProtocol.crc32(vector, 23))
    }
    @Test fun everySingleByteCorruptionRejected() {
        vector.indices.forEach { i -> val b = vector.copyOf(); b[i] = (b[i].toInt() xor 1).toByte(); assertNull("byte $i", DjiProtocol.decode(b)) }
    }
    @Test fun emptyAndShortPacketsRejected() { for (size in 0..17) assertNull(DjiProtocol.decode(ByteArray(size))) }
    @Test fun pairingLayoutHasFixedSizeAndOffsets() {
        val p = DjiCommands.pair(0x12345678, byteArrayOf(2, 3, 4, 5, 6, 7), true, 4321)
        assertEquals(33, p.size); assertEquals(6, p.u8(4)); assertEquals(1, p.u8(26)); assertEquals(4321, p.u16(27))
        assertEquals(0x12345678, p.i32(0)); assertEquals(0, p.i32(21)); assertEquals(0, p.i32(29))
    }
    @Test fun rememberedPairingUsesNoForcedVerification() {
        assertEquals(0, DjiCommands.pair(1, ByteArray(6), false, 12).u8(26))
    }
    @Test fun recordingIsExplicitNotToggle() {
        assertEquals(0, DjiCommands.record(123, true).u8(4)); assertEquals(1, DjiCommands.record(123, false).u8(4))
        assertEquals(9, DjiCommands.record(123, false).size)
    }
    @Test fun ackCarriesSameSequenceAndIndex() {
        val f = DjiProtocol.Frame(65535, 0x20, 0, 0x19, DjiCommands.pairAck(123, 3))
        val decoded = requireNotNull(DjiProtocol.decode(DjiProtocol.encode(f)))
        assertTrue(decoded.response); assertEquals(65535, decoded.sequence); assertEquals(3, decoded.payload.i32(5))
    }
    @Test fun fragmentedFrameAtEveryBoundary() {
        for (i in 1 until vector.size) {
            val d = DjiProtocol.StreamDecoder()
            assertTrue(d.accept(vector.copyOf(i)).isEmpty())
            assertEquals(1, d.accept(vector.copyOfRange(i, vector.size)).size)
        }
    }
    @Test fun oneByteFragments() {
        val d = DjiProtocol.StreamDecoder(); var count = 0
        vector.forEach { count += d.accept(byteArrayOf(it)).size }; assertEquals(1, count)
    }
    @Test fun coalescedFrames() { assertEquals(3, DjiProtocol.StreamDecoder().accept(vector + vector + vector).size) }
    @Test fun streamResynchronizesAfterNoiseAndCorruptFrame() {
        val broken = vector.copyOf().also { it[18] = 99 }
        assertEquals(1, DjiProtocol.StreamDecoder().accept(byteArrayOf(0, 1, 0xaa.toByte()) + broken + vector).size)
    }
    @Test fun randomJunkDoesNotCrashDecoder() {
        val d = DjiProtocol.StreamDecoder(); val random = Random(42)
        repeat(500) { d.accept(random.nextBytes(random.nextInt(600))) }
        assertEquals(1, d.accept(vector).size)
    }
    @Test(expected = IllegalArgumentException::class) fun oversizeFrameRejected() { DjiProtocol.encode(DjiProtocol.Frame(1, 0, 0, 0, ByteArray(1006))) }
    @Test fun maximumFrameRoundTrips() {
        val f = DjiProtocol.Frame(1, 0, 0x1d, 2, ByteArray(1005) { it.toByte() })
        assertArrayEquals(f.payload, requireNotNull(DjiProtocol.decode(DjiProtocol.encode(f))).payload)
    }
}
