package dev.djiremote.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

/** Injectable transport boundary for deterministic tests without pretending to test radio hardware. */
interface CameraTransport {
    val notifications: Channel<ByteArray>
    val disconnected: CompletableDeferred<Unit>
    suspend fun connect(address: String)
    suspend fun write(bytes: ByteArray)
    fun close()
}
