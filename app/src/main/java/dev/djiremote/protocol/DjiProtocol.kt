package dev.djiremote.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** DJI R-SDK, independently implemented from its public wire-format specification. */
object DjiProtocol {
    data class Frame(val sequence: Int, val type: Int, val set: Int, val id: Int, val payload: ByteArray) {
        val response get() = type and 0x20 != 0
    }
    fun crc16(bytes: ByteArray, count: Int = bytes.size): Int {
        var crc = 0x3aa3
        repeat(count) { i ->
            crc = crc xor (bytes[i].toInt() and 255)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xa001 else crc ushr 1 }
        }
        return crc and 0xffff
    }
    fun crc32(bytes: ByteArray, count: Int = bytes.size): Int {
        var crc = 0x3aa3
        repeat(count) { i ->
            crc = crc xor (bytes[i].toInt() and 255)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xedb88320.toInt() else crc ushr 1 }
        }
        return crc
    }
    fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    fun encode(frame: Frame): ByteArray {
        val size = frame.payload.size + 18
        require(size <= 1023)
        val b = buffer(size)
        b.put(0xaa.toByte()).putShort(size.toShort()).put(frame.type.toByte())
        b.put(0).put(byteArrayOf(0, 0, 0)).putShort(frame.sequence.toShort())
        b.putShort(crc16(b.array(), 10).toShort())
        b.put(frame.set.toByte()).put(frame.id.toByte()).put(frame.payload)
        b.putInt(crc32(b.array(), size - 4))
        return b.array()
    }
    fun decode(bytes: ByteArray): Frame? {
        if (bytes.size !in 18..1023 || bytes.u8(0) != 0xaa) return null
        if (bytes.u16(1) != bytes.size || bytes.u8(4) != 0) return null
        if (crc16(bytes, 10) != bytes.u16(10)) return null
        if (crc32(bytes, bytes.size - 4) != bytes.i32(bytes.size - 4)) return null
        return Frame(bytes.u16(8), bytes.u8(3), bytes.u8(12), bytes.u8(13), bytes.copyOfRange(14, bytes.size - 4))
    }
    /** GATT notifications may split a frame, or coalesce multiple frames. Bounded, CRC-checked. */
    class StreamDecoder {
        private var pending = byteArrayOf()
        fun accept(chunk: ByteArray): List<Frame> {
            if (chunk.size > 8192) { pending = byteArrayOf(); return emptyList() }
            pending += chunk
            val frames = mutableListOf<Frame>()
            while (pending.size >= 12) {
                val size = pending.u16(1)
                if (pending.u8(0) != 0xaa || size !in 18..1023 ||
                    pending.u8(4) != 0 || crc16(pending, 10) != pending.u16(10)) {
                    pending = pending.drop(1).toByteArray(); continue
                }
                if (pending.size < size) break
                val frame = decode(pending.copyOf(size))
                if (frame == null) pending = pending.drop(1).toByteArray()
                else { frames += frame; pending = pending.drop(size).toByteArray() }
            }
            return frames
        }
    }
}
fun ByteArray.u8(offset: Int) = this[offset].toInt() and 255
fun ByteArray.u16(offset: Int) = u8(offset) or (u8(offset + 1) shl 8)
fun ByteArray.i32(offset: Int) = ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
