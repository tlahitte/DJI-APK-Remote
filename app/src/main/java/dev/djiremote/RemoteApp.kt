package dev.djiremote

import android.app.Application
import android.os.SystemClock
import dev.djiremote.ble.DiscoveredCamera
import dev.djiremote.camera.*
import dev.djiremote.storage.*
import dev.djiremote.remote.ExternalRemoteHandler
import dev.djiremote.widget.RemoteWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RemoteApp : Application() {
    val widgetProcessToken: String = java.util.UUID.randomUUID().toString()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repository: CameraRepository
        private set
    val settings = MutableStateFlow(Settings())
    val remote = MutableStateFlow(RemoteState())
    val discovered = MutableStateFlow<List<DiscoveredCamera>>(emptyList())
    val diagnostics = MutableStateFlow<List<String>>(emptyList())
    val button = ExternalRemoteHandler()
    override fun onCreate() {
        super.onCreate()
        repository = CameraRepository(this)
        scope.launch {
            repository.settings.collect {
                settings.value = it
                if (!remote.value.active) remote.value = RemoteState(cameras = it.cameras.map { c -> CameraState(c.address, c.name) }, partialAllowed = it.partial)
            }
        }
        scope.launch { runCatching { RemoteWidget.refresh(this@RemoteApp, remote.value) } }
    }
    fun log(event: String) {
        // Whitelisted event names and a session-local camera number only. No raw BLE or identity logging.
        diagnostics.value = (diagnostics.value + "${SystemClock.elapsedRealtime()} $event").takeLast(120)
    }
}
