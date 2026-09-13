package dev.djiremote.protocol

object DjiCommands {
    fun pair(controllerId: Int, identity: ByteArray, firstPair: Boolean, code: Int): ByteArray {
        require(identity.size == 6)
        val b = DjiProtocol.buffer(33)
        b.putInt(controllerId).put(6).put(identity).put(ByteArray(10))
        b.putInt(0).put(0).put(if (firstPair) 1.toByte() else 0.toByte())
        b.putShort(code.toShort()).putInt(0)
        return b.array()
    }
    fun pairAck(controllerId: Int, index: Int) = DjiProtocol.buffer(9)
        .putInt(controllerId).put(0).putInt(index).array()
    // device_id identifies the sending remote, as in the multicamera reference implementation.
    fun record(controllerId: Int, start: Boolean) = DjiProtocol.buffer(9)
        .putInt(controllerId).put(if (start) 0.toByte() else 1.toByte()).putInt(0).array()
    fun subscribe() = byteArrayOf(3, 20, 0, 0, 0, 0)
    val supportedModels = setOf(0xff33, 0xff44, 0xff55, 0xff66)
    val videoModes = setOf(0x00, 0x01, 0x02, 0x0a, 0x28, 0x34, 0x38, 0x3a, 0x3c, 0x41, 0x43, 0x44, 0x4a)
}
data class CameraStatus(
    val mode: Int, val state: Int, val seconds: Int, val remainingSeconds: Long,
    val sleeping: Boolean, val temperature: Int, val battery: Int?, val complete: Boolean = true
) {
    val recording get() = state == 3 && mode in DjiCommands.videoModes
    val canStart get() = complete && mode in DjiCommands.videoModes && state in setOf(0, 1, 5) &&
        !sleeping && temperature < 2 && remainingSeconds > 0
    companion object {
        fun parse(payload: ByteArray): CameraStatus? {
            // The common 7-byte prefix can report recording even when extended health fields are omitted.
            if (payload.size < 7 || payload.u8(1) !in setOf(0, 1, 2, 3, 5)) return null
            if (payload.size < 38) return CameraStatus(payload.u8(0), payload.u8(1), payload.u16(5), 0, false, 0, null, complete = false)
            return CameraStatus(payload.u8(0), payload.u8(1), payload.u16(5),
                payload.i32(23).toLong() and 0xffffffffL, payload.u8(28) == 3,
                payload.u8(30), payload.u8(37).takeIf { it <= 100 })
        }
    }
}
