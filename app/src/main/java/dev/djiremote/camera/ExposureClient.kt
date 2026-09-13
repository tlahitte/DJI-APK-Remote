package dev.djiremote.camera

import dev.djiremote.ble.CameraTransport
import dev.djiremote.protocol.*
import kotlinx.coroutines.*
import java.security.SecureRandom

class ExposureCommandRejected(val command: Int, val code: Int) : IllegalStateException(
    "Camera rejected command 02/%02X (0x%02X)".format(command, code))

/** Experimental, deliberately limited to a session that already answers both read-only getters. */
class ExposureClient(private val transport: CameraTransport) {
    private data class Pending(val command: Int, val response: CompletableDeferred<DumlProtocol.Frame>)
    private val pending = mutableMapOf<Int, Pending>()
    private var sequence = SecureRandom().nextInt(65536)
    fun receive(f: DumlProtocol.Frame) {
        val p = pending[f.sequence] ?: return
        if (f.response && f.sender == 1 && f.receiver == 2 && f.set == 2 && f.command == p.command) p.response.complete(f)
    }
    private suspend fun request(command: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        sequence = (sequence + 1) and 65535
        val seq = sequence; val reply = CompletableDeferred<DumlProtocol.Frame>()
        pending[seq] = Pending(command, reply)
        try {
            return withTimeout(3500) {
                transport.write(DumlProtocol.encode(DumlProtocol.Frame(seq, command, payload)))
                val data = reply.await().payload
                check(data.isNotEmpty()) { "Empty setting-command reply" }
                if (data.u8(0) != 0) throw ExposureCommandRejected(command, data.u8(0))
                data
            }
        } finally { pending.remove(seq) }
    }
    suspend fun read(): ExposureReadback {
        val iso = ExposureCommands.parseIso(request(ExposureCommands.GET_ISO))
        val shutter = ExposureCommands.parseShutter(request(ExposureCommands.GET_SHUTTER))
        return ExposureReadback(iso, shutter)
    }
    suspend fun apply(preset: ExposurePreset, current: ExposureReadback? = null, beforeWrite: () -> Unit): ExposureReadback {
        ExposureCommands.iso(preset) // reject unsupported selection before any setting write
        // Avoid an unnecessary mode write when both getters already report manual settings.
        if (current == null || current.shutter.auto || current.isoCode !in 3..11) {
            beforeWrite(); request(ExposureCommands.SET_MODE, ExposureCommands.manualMode())
        }
        beforeWrite(); request(ExposureCommands.SET_SHUTTER, ExposureCommands.shutter(preset))
        beforeWrite(); request(ExposureCommands.SET_ISO, ExposureCommands.iso(preset))
        // ACKs are not enough. Only matching readback is called confirmed.
        repeat(4) { attempt ->
            val confirmed = read()
            if (confirmed.matches(preset)) return confirmed
            // Allow asynchronous camera application, but retry GETs only, never the setters.
            if (attempt < 3) delay(250)
        }
        error("Readback differs from the requested shutter/ISO")
    }
    fun close() { pending.values.forEach { it.response.completeExceptionally(IllegalStateException("Camera disconnected")) }; pending.clear() }
}
