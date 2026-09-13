package dev.djiremote.protocol

/** DJI DUML framing, independent implementation from its public wire format. Not an SDK dependency. */
object DumlProtocol {
    data class Frame(val sequence: Int, val command: Int, val payload: ByteArray = byteArrayOf(),
        val flags: Int = 0x40, val sender: Int = 2, val receiver: Int = 1, val set: Int = 2) {
        val response get() = flags and 0x80 != 0
    }
    fun crc8(data: ByteArray, count: Int = data.size): Int {
        var crc = 0x77
        repeat(count) { i -> crc = crc xor data.u8(i); repeat(8) { crc = if (crc and 1 == 1) (crc ushr 1) xor 0x8c else crc ushr 1 } }
        return crc and 255
    }
    fun crc16(data: ByteArray, count: Int = data.size): Int {
        var crc = 0x3692
        repeat(count) { i -> crc = crc xor data.u8(i); repeat(8) { crc = if (crc and 1 == 1) (crc ushr 1) xor 0x8408 else crc ushr 1 } }
        return crc and 65535
    }
    fun encode(f: Frame): ByteArray {
        val size = f.payload.size + 13; require(size <= 1023)
        val b = DjiProtocol.buffer(size)
        b.put(0x55).putShort((size or 0x400).toShort()).put(crc8(b.array(), 3).toByte())
        b.put(f.sender.toByte()).put(f.receiver.toByte()).putShort(f.sequence.toShort())
        b.put(f.flags.toByte()).put(f.set.toByte()).put(f.command.toByte()).put(f.payload)
        b.putShort(crc16(b.array(), size - 2).toShort())
        return b.array()
    }
    fun decode(b: ByteArray): Frame? {
        if (b.size !in 13..1023 || b.u8(0) != 0x55 || b.u16(1) ushr 10 != 1 || b.u16(1) and 1023 != b.size) return null
        if (b.u8(8) and 7 != 0 || crc8(b, 3) != b.u8(3) || crc16(b, b.size - 2) != b.u16(b.size - 2)) return null
        return Frame(b.u16(6), b.u8(10), b.copyOfRange(11, b.size - 2), b.u8(8), b.u8(4), b.u8(5), b.u8(9))
    }
}
sealed interface CameraPacket {
    data class Rsdk(val frame: DjiProtocol.Frame) : CameraPacket
    data class Duml(val frame: DumlProtocol.Frame) : CameraPacket
}
/** Demultiplex complete validated frames; never scan one frame's payload as another protocol. */
class CameraPacketDecoder {
    private var pending = byteArrayOf()
    fun accept(chunk: ByteArray): List<CameraPacket> {
        if (chunk.size > 8192) { pending = byteArrayOf(); return emptyList() }
        pending += chunk
        val result = mutableListOf<CameraPacket>()
        while (pending.size >= 4) {
            val magic = pending.u8(0)
            if (magic != 0xaa && magic != 0x55) { pending = pending.drop(1).toByteArray(); continue }
            val headerSize = if (magic == 0xaa) 12 else 4
            if (pending.size < headerSize) break
            val size = if (magic == 0xaa) pending.u16(1) else pending.u16(1) and 1023
            val validHeader = if (magic == 0xaa) size in 18..1023 && pending.u8(4) == 0 && DjiProtocol.crc16(pending, 10) == pending.u16(10)
                else size in 13..1023 && pending.u16(1) ushr 10 == 1 && DumlProtocol.crc8(pending, 3) == pending.u8(3)
            if (!validHeader) { pending = pending.drop(1).toByteArray(); continue }
            if (pending.size < size) break
            val bytes = pending.copyOf(size)
            val packet = if (magic == 0xaa) DjiProtocol.decode(bytes)?.let { CameraPacket.Rsdk(it) }
                else DumlProtocol.decode(bytes)?.let { CameraPacket.Duml(it) }
            if (packet == null) pending = pending.drop(1).toByteArray()
            else { result += packet; pending = pending.drop(size).toByteArray() }
        }
        return result
    }
}
