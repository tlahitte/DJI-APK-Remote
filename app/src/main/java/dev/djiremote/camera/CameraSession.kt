package dev.djiremote.camera

import android.os.SystemClock
import dev.djiremote.ble.CameraTransport
import dev.djiremote.protocol.*
import dev.djiremote.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

class PairingRejected : IllegalStateException("Pairing rejected on camera. Reconnect manually to try again.")
class UnsupportedCamera : IllegalStateException("Camera model is not supported by this R-SDK remote")
class CameraSession(
    private val client: CameraTransport, private val scope: CoroutineScope, private val known: KnownCamera,
    private val identity: ControllerIdentity, private val index: Int,
    private val onChange: (CameraState) -> Unit, private val log: (String) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ExposureTarget {
    val state = MutableStateFlow(CameraState(known.address, known.name))
    private val exposureClient = ExposureClient(client)
    private var sequence = SecureRandom().nextInt(65535)
    private val handshake = CompletableDeferred<DjiProtocol.Frame>()
    private var reader: Job? = null
    private var poller: Job? = null
    private val commandLock = Mutex()
    private var lastStatusAt = 0L
    private var lastFullStatusAt = 0L
    private var statusVersion = 0L
    private var recordSequence: Int? = null
    private var recordFailure: String? = null
    private var pairSequence: Int? = null
    private fun update(change: (CameraState) -> CameraState) { state.value = change(state.value); onChange(state.value) }
    private fun nextSequence(): Int { sequence = (sequence + 1) and 65535; return sequence }
    private suspend fun send(set: Int, id: Int, payload: ByteArray, type: Int = 2, seq: Int = nextSequence()) {
        client.write(DjiProtocol.encode(DjiProtocol.Frame(seq, type, set, id, payload)))
    }
    suspend fun connect() {
        update { it.copy(connection = ConnectionState.CONNECTING, error = null) }
        log("CONNECT_STARTED")
        client.connect(known.address)
        log("GATT_CONNECTED")
        reader = scope.launch {
            val decoder = CameraPacketDecoder()
            for (chunk in client.notifications) {
                for (packet in decoder.accept(chunk)) when (packet) {
                    is CameraPacket.Rsdk -> receive(packet.frame)
                    is CameraPacket.Duml -> exposureClient.receive(packet.frame)
                }
            }
        }
        val code = SecureRandom().nextInt(10_000)
        update { it.copy(connection = ConnectionState.PAIRING, pairingCode = code) }
        log("PAIR_STARTED")
        pairSequence = nextSequence()
        send(0, 0x19, DjiCommands.pair(identity.id, identity.address, !known.paired, code), seq = pairSequence!!)
        val approval = withTimeout(35_000) {
            select<DjiProtocol.Frame> {
                handshake.onAwait { it }
                client.disconnected.onAwait { error("Camera disconnected during pairing") }
            }
        }
        if (approval.payload.u16(27) != 0) throw PairingRejected()
        val model = approval.payload.i32(0)
        if (model !in DjiCommands.supportedModels) throw UnsupportedCamera()
        send(0, 0x19, DjiCommands.pairAck(identity.id, index), type = 0x20, seq = approval.sequence)
        update { it.copy(connection = ConnectionState.READY, sessionReady = true, pairingCode = null, modelId = model) }
        log("PAIR_SUCCESS")
        send(0x1d, 5, DjiCommands.subscribe(), type = 0)
        poller = scope.launch {
            while (isActive) {
                delay(1500)
                val age = clock() - lastStatusAt
                if (age > 3500 && state.value.statusFresh) update { it.copy(statusFresh = false) }
                if (age > 2000 || clock() - lastFullStatusAt > 3500) {
                    try { send(0x1d, 5, DjiCommands.subscribe(), type = 0) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { client.close(); break }
                }
            }
        }
    }
    private fun receive(frame: DjiProtocol.Frame) {
        if (frame.set == 0 && frame.id == 0x19) {
            if (!frame.response && frame.payload.size >= 29 && frame.payload.u8(26) == 2) handshake.complete(frame)
            else if (frame.response && frame.sequence == pairSequence && frame.payload.size >= 5 && frame.payload.u8(4) != 0)
                handshake.completeExceptionally(PairingRejected())
        }
        if (frame.set == 0x1d && frame.id == 2 && state.value.sessionReady) {
            val status = CameraStatus.parse(frame.payload) ?: return
            lastStatusAt = clock(); statusVersion++
            if (status.complete) lastFullStatusAt = lastStatusAt
            update { it.copy(status = status, statusFresh = true, reportVersion = statusVersion) }
        }
        if (frame.set == 0x1d && frame.id == 6 && !frame.response && state.value.sessionReady && !state.value.statusFresh) {
            // Text mode (1D06) is NOT evidence of recording. Some Action 6 firmware only emits this.
            update { it.copy(error = "Firmware sent mode only; recording state is unverified") }
        }
        if (frame.set == 0x1d && frame.id == 3 && frame.response && frame.sequence == recordSequence && frame.payload.isNotEmpty()) {
            if (frame.payload.u8(0) != 0) {
                recordFailure = "Camera rejected command (${frame.payload.u8(0)})"
                update { it.copy(error = recordFailure) }
            }
        }
    }
    suspend fun record(start: Boolean, operationAt: Long): Boolean = commandLock.withLock {
        if (!state.value.sessionReady || (start && !state.value.ready)) return@withLock false
        // An idempotent STOP must not wait for a state transition that has already happened.
        // Only a fresh numeric idle report qualifies; stale/unknown status still sends STOP.
        if (!start && state.value.confirmedIdle && clock() - lastStatusAt <= 3500) {
            update { it.copy(error = null, sentAtMs = null, confirmedAtMs = clock() - operationAt) }
            log("STOP_ALREADY_IDLE")
            return@withLock true
        }
        recordFailure = null
        val baseline = statusVersion
        val seq = nextSequence(); recordSequence = seq
        update { it.copy(pending = start, error = null, sentAtMs = null, confirmedAtMs = null) }
        log(if (start) "REC_REQUESTED" else "STOP_REQUESTED")
        try {
            val sent = clock()
            update { it.copy(sentAtMs = sent - operationAt) }
            send(0x1d, 3, DjiCommands.record(identity.id, start), seq = seq)
            log(if (start) "REC_PACKET_SENT" else "STOP_PACKET_SENT")
            withTimeout(6_000) {
                state.first { recordFailure != null || (it.reportVersion > baseline && it.statusFresh && it.status?.recording == start) }
            }
            check(recordFailure == null) { recordFailure ?: "Command failed" }
            update { it.copy(pending = null, confirmedAtMs = clock() - operationAt, error = null) }
            log(if (start) "REC_CONFIRMED" else "STOP_CONFIRMED")
            true
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            update { it.copy(pending = null, error = "Confirmation timed out. Check camera; no automatic retry.") }
            log("COMMAND_TIMEOUT"); false
        } catch (_: Exception) {
            update { it.copy(pending = null, error = recordFailure ?: "Command failed. Check camera before retrying.") }
            log("PROTOCOL_ERROR"); false
        } finally { recordSequence = null }
    }
    private fun checkExposureIdle() {
        check(state.value.canCheckExposure && clock() - lastStatusAt <= 3500) { "Exposure requires a connected, freshly confirmed idle Action 4" }
    }
    override suspend fun readExposure(): Boolean = commandLock.withLock {
        try {
            checkExposureIdle()
            update { it.copy(exposureMessage = "Reading camera settings…", exposureSupported = false) }
            val value = exposureClient.read()
            update { it.copy(exposure = value, exposureSupported = true, exposureMessage = "Camera read: ${value.shutter.label} · ISO ${value.isoLabel}") }
            log("EXPOSURE_READ_CONFIRMED"); true
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            update { it.copy(exposureSupported = false, exposureMessage = "No setting-query response on this BLE session. No settings written.") }
            log("EXPOSURE_READ_TIMEOUT"); false
        } catch (e: Exception) {
            val reason = if (e is ExposureCommandRejected) e.message + ". No settings written." else "Setting replies unsupported, or camera not idle. No settings written."
            update { it.copy(exposureSupported = false, exposureMessage = reason) }
            log("EXPOSURE_READ_FAILED"); false
        }
    }
    override suspend fun applyExposure(preset: ExposurePreset): Boolean = commandLock.withLock {
        var writesStarted = false
        try {
            checkExposureIdle()
            check(state.value.exposureSupported) { "Read camera settings first" }
            ExposureCommands.iso(preset)
            update { it.copy(exposureMessage = "Applying manual shutter / ISO…") }
            val actual = exposureClient.apply(preset, state.value.exposure) { checkExposureIdle(); writesStarted = true }
            update { it.copy(exposure = actual, exposureMessage = "Camera confirmed: ${actual.shutter.label} · ISO ${actual.isoLabel}") }
            log("EXPOSURE_APPLIED_CONFIRMED"); true
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            update { it.copy(exposureSupported = false, exposureMessage = if (writesStarted) "Apply timed out: settings may be partially changed. Read camera / check screen." else "No settings written; camera no longer ready.") }
            log("EXPOSURE_APPLY_TIMEOUT"); false
        } catch (e: Exception) {
            val reason = (e as? ExposureCommandRejected)?.message ?: "Apply rejected or readback differs"
            update { it.copy(exposureSupported = false, exposureMessage = if (writesStarted) "$reason. Some settings may have changed; check camera." else "No settings written; read support / idle check failed.") }
            log("EXPOSURE_APPLY_FAILED"); false
        }
    }
    suspend fun awaitDisconnect() { client.disconnected.await() }
    fun close() {
        poller?.cancel(); reader?.cancel(); exposureClient.close(); client.close()
        update { it.copy(connection = ConnectionState.OFFLINE, sessionReady = false, statusFresh = false, pending = null, pairingCode = null, exposureSupported = false, exposureMessage = "Disconnected — settings unverified") }
    }
}
