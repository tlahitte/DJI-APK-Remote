package dev.djiremote.camera

import android.os.SystemClock
import dev.djiremote.RemoteApp
import dev.djiremote.ble.DjiBleScanner
import dev.djiremote.ble.DjiGattClient
import dev.djiremote.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CameraManager(private val app: RemoteApp, private val scope: CoroutineScope) {
    private val scanner = DjiBleScanner(app, scope)
    private val sessions = mutableMapOf<String, CameraSession>()
    private val jobs = mutableMapOf<String, Job>()
    private val generations = mutableMapOf<String, Int>()
    private val rows = mutableMapOf<String, CameraState>()
    private var settings = app.settings.value
    private var active = false
    private var busy = false
    private var operationLabel: String? = null
    private var message: String? = null
    private val operation = Mutex()
    fun start() {
        if (active) return
        active = true; publish()
        scope.launch { app.repository.settings.collect { next ->
            settings = next
            val ids = next.cameras.map { it.address }.toSet()
            jobs.keys.toList().filter { it !in ids }.forEach { id ->
                generations[id] = (generations[id] ?: 0) + 1
                jobs.remove(id)?.cancel(); sessions.remove(id)?.close(); rows.remove(id)
            }
            for ((i, camera) in next.cameras.withIndex()) {
                if (camera.address !in jobs) connect(camera, i + 1)
            }
            publish()
        } }
        scope.launch { scanner.found.collect { app.discovered.value = it } }
        scope.launch { scanner.scanning.collect { publish() } }
        scope.launch { scanner.error.filterNotNull().collect { message = it; publish() } }
    }
    fun scan() { message = null; scanner.start(); app.log("SCAN_STARTED"); publish() }
    fun cancelScan() { scanner.stop(); publish() }
    private fun connect(camera: KnownCamera, index: Int) {
        val generation = (generations[camera.address] ?: 0) + 1
        generations[camera.address] = generation
        jobs[camera.address] = scope.launch(start = CoroutineStart.LAZY) {
            for (attempt in 0..3) {
                if (!isActive || !active) break
                if (attempt > 0) {
                    rows[camera.address] = (rows[camera.address] ?: CameraState(camera.address, camera.name)).copy(
                        connection = ConnectionState.RECONNECTING, sessionReady = false, statusFresh = false,
                        error = "Reconnecting · attempt $attempt / 3")
                    publish(); delay(2_000L shl (attempt - 1))
                }
                val currentKnown = settings.cameras.firstOrNull { it.address == camera.address } ?: break
                lateinit var session: CameraSession
                session = CameraSession(DjiGattClient(app, scope), scope, currentKnown, app.repository.identity(), index,
                    onChange = { if (active && generations[camera.address] == generation && sessions[camera.address] === session) { rows[camera.address] = it; publish() } }, log = { app.log("C$index $it") })
                sessions[camera.address] = session
                var terminal = false
                var failure: String? = null
                try {
                    session.connect()
                    app.repository.paired(camera.address)
                    session.awaitDisconnect()
                    failure = "Connection lost. Camera may still be recording."
                } catch (e: CancellationException) {
                    if (e !is TimeoutCancellationException) throw e
                    failure = "Connection or pairing timed out. Check the camera screen."
                } catch (e: PairingRejected) { terminal = true; failure = e.message }
                  catch (e: UnsupportedCamera) { terminal = true; failure = e.message }
                  catch (_: SecurityException) { terminal = true; failure = "Bluetooth permission revoked. Open app settings." }
                  catch (_: Exception) { failure = "Bluetooth/session failed. Check camera power and Bluetooth." }
                finally { session.close() }
                if (generations[camera.address] != generation || !active) return@launch
                app.log("C$index DISCONNECTED")
                rows[camera.address] = session.state.value.copy(
                    connection = if (terminal) ConnectionState.REJECTED else ConnectionState.ERROR,
                    error = failure ?: "Camera disconnected")
                publish()
                if (terminal) break
            }
        }.also { it.start() }
    }
    fun reconnect() {
        message = null
        settings.cameras.forEachIndexed { i, camera ->
            if (sessions[camera.address]?.state?.value?.sessionReady != true && jobs[camera.address]?.isActive != true) {
                jobs.remove(camera.address)?.cancel(); sessions.remove(camera.address)?.close()
                connect(camera, i + 1)
            }
        }
        publish()
    }
    fun command(start: Boolean, cameraId: String? = null) {
        if (operation.isLocked) return
        scope.launch {
            operation.withLock {
                val snapshot = app.remote.value
                val allowed = if (cameraId == null) {
                    if (start) snapshot.canRecord else snapshot.canStop
                } else snapshot.canControlCamera(cameraId, start)
                if (!allowed) {
                    message = if (start) "Record blocked: check readiness or enable partial recording explicitly." else "No connected camera can receive Stop. Check cameras manually."
                    publish(); return@withLock
                }
                busy = true; message = null; publish()
                val at = SystemClock.elapsedRealtime()
                try {
                    val targets = sessions.values.filter {
                        (cameraId == null || it.state.value.id == cameraId) &&
                            (if (start) it.state.value.ready else it.state.value.sessionReady)
                    }
                    val results = supervisorScope { targets.map { session -> async { session.record(start, at) } }.awaitAll() }
                    message = if (results.all { it } && targets.size == (if (cameraId == null) settings.cameras.size else 1)) null
                        else if (cameraId != null) "Camera command could not be confirmed. Check that camera."
                        else "Partial result: check every camera. Missing cameras may still be recording."
                } finally { busy = false; publish() }
            }
        }
    }
    fun exposure(preset: ExposurePreset? = null) {
        if (operation.isLocked) return
        scope.launch {
            operation.withLock {
                if (!app.remote.value.canCheckExposure || sessions.size != settings.cameras.size) {
                    message = "Exposure requires every saved camera to be an idle, connected Action 4. No settings sent."
                    publish(); return@withLock
                }
                busy = true; message = null; operationLabel = "Checking exposure…"; publish()
                try {
                    val targets = settings.cameras.map { sessions.getValue(it.address) }
                    // All read-only preflights complete before ANY setting writes.
                    message = when (ExposureBatch.run(targets, preset) { operationLabel = "Applying exposure…"; publish() }) {
                        ExposureOutcome.READ_FAILED -> "Exposure queries failed on one or more cameras. No settings written."
                        ExposureOutcome.PARTIAL -> "Exposure only partly confirmed. Check each camera; no automatic retry."
                        else -> null // per-camera readback is the confirmation surface
                    }
                } finally { busy = false; operationLabel = null; publish() }
            }
        }
    }
    fun toggle() {
        // Unknown status never means “not recording”; prefer explicit Stop over an unsafe start.
        command(!app.remote.value.shouldStop)
    }
    private fun publish() {
        app.remote.value = RemoteState(active, settings.cameras.map { camera ->
            (rows[camera.address] ?: CameraState(camera.address, camera.name)).copy(name = camera.name)
        }, scanner.scanning.value, settings.partial, busy, message, operationLabel)
    }
    fun close() {
        active = false
        scanner.stop()
        jobs.values.forEach { it.cancel() }; jobs.clear()
        sessions.values.forEach { it.close() }; sessions.clear()
        rows.clear(); app.discovered.value = emptyList(); busy = false
        message = "Session ended. Disconnecting does not stop cameras."
        publish()
    }
}
