package dev.djiremote.ui

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.djiremote.BuildConfig
import dev.djiremote.R
import dev.djiremote.RemoteApp
import dev.djiremote.ble.BlePermissions
import dev.djiremote.camera.*
import dev.djiremote.service.RemoteService
import dev.djiremote.storage.KnownCamera
import dev.djiremote.storage.Settings
import dev.djiremote.widget.RemoteWidgetReceiver
import kotlinx.coroutines.launch

private val Coral = Color(0xffFF5D64)
private val Night = Color(0xff101721)
private val Panel = Color(0xff1b2532)
private val Muted = Color(0xffadbdcf)
private val Mint = Color(0xff83dfc3)
private val Amber = Color(0xffffca87)
private val Ink = Color(0xff28161a)

class MainActivity : ComponentActivity() {
    private val app get() = application as RemoteApp
    private var learning by mutableStateOf(false)
    private var learned by mutableStateOf<Int?>(null)
    private var localMessage by mutableStateOf<String?>(null)
    private var permitted by mutableStateOf(false)
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = BlePermissions.granted(this)
        localMessage = if (permitted) null else "Allow Nearby devices in Android app settings to connect cameras."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Coral, onPrimary = Ink, primaryContainer = Color(0xff482b33), onPrimaryContainer = Color(0xffffdadd),
                secondary = Mint, onSecondary = Night, secondaryContainer = Color(0xff263447), onSecondaryContainer = Color.White,
                background = Night, surface = Panel, surfaceContainer = Panel, surfaceContainerHigh = Color(0xff263447),
                onSurface = Color(0xfff2f6fc), onSurfaceVariant = Muted, outline = Color(0xff435167))) {
                Screen()
            }
        }
    }
    override fun onPause() { learning = false; learned = null; super.onPause() }
    override fun onResume() { super.onResume(); permitted = BlePermissions.granted(this) }
    private fun permissions() { permissionRequest.launch(BlePermissions.required()) }
    private fun command(action: String, cameraId: String? = null) {
        if (!BlePermissions.granted(this)) { permissions(); return }
        try { RemoteService.send(this, action, cameraId); localMessage = null }
        catch (_: Exception) { localMessage = "Couldn't start the session. Check Bluetooth and try again." }
    }
    // Public Android Activity hook, intercepted before focused widgets consume learned HID keys.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (learning && event.keyCode !in setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) learned = event.keyCode
            return true
        }
        if (app.remote.value.active && app.button.handle(event, app.settings.value.keyCode) { command(RemoteService.TOGGLE) }) return true
        return super.dispatchKeyEvent(event)
    }
    @Composable private fun Screen() {
        val remote by app.remote.collectAsStateWithLifecycle()
        val settings by app.settings.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var endConfirmation by remember { mutableStateOf(false) }
        var renaming by remember { mutableStateOf<KnownCamera?>(null) }
        var name by remember { mutableStateOf("") }
        val labels = listOf("Remote", "Cameras", "Button", "Settings")
        val icons = listOf(R.drawable.ic_play, R.drawable.ic_camera, R.drawable.ic_remote_button, R.drawable.ic_settings)
        Scaffold(containerColor = Night, bottomBar = {
            NavigationBar(containerColor = Panel) {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index; learning = false },
                        icon = { Glyph(icons[index], null) }, label = { Text(label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Coral, selectedIconColor = Ink,
                            selectedTextColor = Coral, unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }) { padding ->
            key(tab) {
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.brand_icon), "App logo", Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)))
                        Column(Modifier.weight(1f)) { Text("Multi Remote", fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("All your cameras. One take.", color = Muted, fontSize = 12.sp) }
                        Box(Modifier.size(10.dp).background(if (remote.active) Mint else Color(0xff556171), CircleShape))
                    }
                    (localMessage ?: remote.message)?.let { Notice(it) }
                    if (!permitted && tab in 0..1) Section("Nearby devices", R.drawable.ic_bluetooth) {
                        Text("Allow Bluetooth access to link your cameras.", color = Muted, fontSize = 14.sp)
                        ActionButton("Allow access", R.drawable.ic_bluetooth, onClick = { permissions() })
                    }
                    when (tab) {
                        0 -> RemotePage(remote, settings, { tab = 1 }, { endConfirmation = true })
                        1 -> CamerasPage(remote, settings) { camera -> renaming = camera; name = camera.name }
                        2 -> ButtonPage(settings)
                        3 -> SettingsPage(remote, settings)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        if (endConfirmation) AlertDialog(onDismissRequest = { endConfirmation = false }, icon = { Glyph(R.drawable.ic_disconnect, null, Coral) },
            title = { Text("End this session?") }, text = { Text("Disconnecting does not stop the cameras. Stop your take first, or check each camera manually.") },
            confirmButton = { TextButton(onClick = { command(RemoteService.END); endConfirmation = false }) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { endConfirmation = false }) { Text("Keep connected") } })
        if (renaming != null) AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Camera name") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renaming?.let { c -> lifecycleScope.launch { app.repository.rename(c.address, name) } }; renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
    @Composable private fun RemotePage(remote: RemoteState, settings: Settings, addCameras: () -> Unit, endSession: () -> Unit) {
        val recording = remote.recordingCount > 0
        val stopAction = remote.hasRecordingActivity
        val textColor = if (recording) Ink else Color.White
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (recording) Coral else Panel)) {
            Column(Modifier.padding(22.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (recording) "TAKE IN PROGRESS" else if (remote.active) "CAMERA GROUP" else "YOUR NEXT TAKE", fontSize = 11.sp,
                        letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Glyph(if (recording) R.drawable.ic_camera else R.drawable.ic_bluetooth, null, textColor)
                }
                Text(when {
                    recording -> "${remote.recordingCount} / ${remote.cameras.size}\nrecording"
                    !remote.active -> "Ready when\nyou are."
                    remote.cameras.isEmpty() -> "Let's add\nyour cameras."
                    remote.busy -> "One moment…"
                    else -> "${remote.readyCount} / ${remote.cameras.size}\ncameras ready"
                }, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text(when {
                    remote.cameras.isEmpty() -> "Build your camera group to get started."
                    !remote.active -> "${remote.cameras.size} saved cameras · session off"
                    recording -> "Confirmed by the cameras" + if (remote.recordingCount < remote.cameras.size) " · partial take" else ""
                    remote.busy -> "Waiting for camera confirmation"
                    settings.partial -> "Partial recording enabled"
                    else -> "All-camera safety gate on"
                }, color = if (recording) Ink else Muted, fontSize = 13.sp)
                if (remote.cameras.isEmpty()) ActionButton("Add cameras", R.drawable.ic_add, onClick = addCameras)
                else if (!remote.active) ActionButton("Connect group", R.drawable.ic_bluetooth, enabled = permitted, onClick = { command(RemoteService.CONNECT) })
                else ActionButton(if (stopAction) "Stop all" else "Record all", if (stopAction) R.drawable.ic_stop else R.drawable.ic_play,
                    enabled = if (stopAction) remote.canStop else remote.canRecord, dark = recording,
                    onClick = { command(if (stopAction) RemoteService.STOP else RemoteService.RECORD) })
            }
        }
        if (settings.experimentalExposure) ExposureWheels(settings.exposurePreset,
            onShutter = { lifecycleScope.launch { app.repository.stageShutter(it) } },
            onIso = { lifecycleScope.launch { app.repository.stageIso(it) } })
        if (remote.active && remote.cameras.any { it.uncertain }) Notice("Some camera states are unverified. Check the group before your take.")
        if (remote.cameras.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Your cameras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = addCameras) { Glyph(R.drawable.ic_add, null, Coral, 17); Spacer(Modifier.width(6.dp)); Text("Manage") }
            }
            remote.cameras.forEachIndexed { index, camera -> CameraCard(camera, index + 1, remote) }
        }
        if (remote.active) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { command(RemoteService.CONNECT) }, enabled = !remote.busy) { Glyph(R.drawable.ic_refresh, null, size = 18); Spacer(Modifier.width(6.dp)); Text("Reconnect") }
                TextButton(onClick = endSession) { Glyph(R.drawable.ic_disconnect, null, size = 18); Spacer(Modifier.width(6.dp)); Text("End session") }
            }
            if (!stopAction) OutlinedButton(onClick = { command(RemoteService.STOP) }, enabled = remote.canStop, modifier = Modifier.fillMaxWidth()) {
                Glyph(R.drawable.ic_stop, null, size = 17); Spacer(Modifier.width(8.dp)); Text("Stop all")
            }
        }
    }
    @Composable private fun CameraCard(camera: CameraState, number: Int, remote: RemoteState) {
        val color = when { camera.recording -> Coral; camera.error != null -> Amber; camera.ready -> Mint; else -> Muted }
        Card(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (camera.recording) Coral else Color(0xff303d4e)),
            colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).background(color.copy(alpha = .13f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Glyph(R.drawable.ic_camera, null, color) }
                    Column(Modifier.weight(1f)) {
                        Text(camera.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("CAM $number", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    Text(if (camera.statusFresh) camera.status?.battery?.let { "$it%" } ?: "—" else "—", color = Muted, fontSize = 12.sp)
                }
                Text(camera.label, color = color, fontSize = 13.sp)
                camera.pairingCode?.let { Text("%04d".format(it), fontSize = 28.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Bold, color = Coral) }
                if (!camera.statusFresh && camera.status?.recording == true) Text("Last seen recording · current state unknown", color = Amber, fontSize = 12.sp)
                if (camera.recording && camera.error != null) Text(camera.error, color = Amber, fontSize = 12.sp)
                if (remote.active) {
                    val stop = camera.possiblyRecording
                    OutlinedButton(onClick = { command(if (stop) RemoteService.STOP_ONE else RemoteService.RECORD_ONE, camera.id) },
                        enabled = remote.canControlCamera(camera.id, !stop), modifier = Modifier.fillMaxWidth()) {
                        Glyph(if (stop) R.drawable.ic_stop else R.drawable.ic_play, null, size = 18)
                        Spacer(Modifier.width(8.dp)); Text(if (stop) "Stop this camera" else "Record only this camera")
                    }
                }
            }
        }
    }
    @Composable private fun CamerasPage(remote: RemoteState, settings: Settings, rename: (KnownCamera) -> Unit) {
        val discovered by app.discovered.collectAsStateWithLifecycle()
        PageTitle("Link your angles", "Power cameras on, then approve pairing on each screen.")
        ActionButton(if (remote.scanning) "Stop scanning" else "Find cameras", if (remote.scanning) R.drawable.ic_stop else R.drawable.ic_bluetooth,
            enabled = permitted, onClick = { command(if (remote.scanning) RemoteService.CANCEL_SCAN else RemoteService.SCAN) })
        if (remote.scanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral)
        val available = discovered.filter { d -> settings.cameras.none { it.address == d.address } }
        if (available.isNotEmpty()) Text("Nearby", fontWeight = FontWeight.Bold, color = Mint)
        available.forEach { camera ->
            Section(camera.name, R.drawable.ic_camera) {
                Text("${camera.model} · ${camera.rssi} dBm", color = Muted, fontSize = 12.sp)
                FilledTonalButton(onClick = { lifecycleScope.launch { app.repository.add(KnownCamera(camera.address, camera.name)) } }) {
                    Glyph(R.drawable.ic_add, null, size = 18); Spacer(Modifier.width(8.dp)); Text("Add to group")
                }
            }
        }
        if (settings.cameras.isNotEmpty()) Text("Saved cameras", fontWeight = FontWeight.Bold)
        settings.cameras.forEachIndexed { index, camera ->
            val current = remote.cameras.firstOrNull { it.id == camera.address } ?: CameraState(camera.address, camera.name)
            CameraCard(current, index + 1, remote)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { rename(camera) }) { Text("Rename") }
                TextButton(onClick = { lifecycleScope.launch { app.repository.remove(camera.address) } }, enabled = !remote.active) { Text("Remove") }
            }
        }
        Text(if (remote.active) "End the session before removing cameras." else "Action 4, Action 5 Pro, Action 6 and Osmo 360 · firmware support varies.", color = Muted, fontSize = 12.sp)
        TextButton(onClick = { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
            Glyph(R.drawable.ic_bluetooth, null, size = 18); Spacer(Modifier.width(8.dp)); Text("Android Bluetooth settings")
        }
    }
    @Composable private fun ButtonPage(settings: Settings) {
        PageTitle("Your remote, remapped", "One press to record. Press again to stop.")
        Section("Saved button", R.drawable.ic_volume) {
            Text(keyName(settings.keyCode), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Coral)
            Text(if (settings.keyCode == KeyEvent.KEYCODE_VOLUME_UP) "Default for volume-up shutter remotes" else "Custom mapping", fontSize = 12.sp, color = Muted)
            if (learning) {
                Text("Press your remote now…", color = Mint)
                learned?.let { Text("Detected: ${keyName(it)}", fontWeight = FontWeight.Bold) }
                Row {
                    Button(onClick = { learned?.let { code -> lifecycleScope.launch { app.repository.key(code) } }; learning = false }, enabled = learned != null) { Text("Save button") }
                    TextButton(onClick = { learning = false; learned = null }) { Text("Cancel") }
                }
            } else ActionButton("Learn another button", R.drawable.ic_remote_button, onClick = { learning = true; learned = null })
            Row {
                TextButton(onClick = { lifecycleScope.launch { app.repository.key(KeyEvent.KEYCODE_VOLUME_UP) } }) { Text("Reset to Volume Up") }
                TextButton(onClick = { lifecycleScope.launch { app.repository.key(-1) } }) { Text("Disable") }
            }
        }
        Section("Media remotes", R.drawable.ic_remote_button) {
            SettingSwitch("Enable media-button session", settings.mediaEnabled) { value -> lifecycleScope.launch { app.repository.media(value) } }
            Text("Media keys can work in the background when Android routes them here. Music apps may take priority.", color = Muted, fontSize = 13.sp)
        }
        Notice("Volume-up shutter buttons work while this app is open. Android doesn't deliver those keys to background apps.")
    }
    @Composable private fun SettingsPage(remote: RemoteState, settings: Settings) {
        val diagnostics by app.diagnostics.collectAsStateWithLifecycle()
        var showDiagnostics by rememberSaveable { mutableStateOf(false) }
        PageTitle("Make it yours", "Session preferences and app information.")
        Section("Recording safety", R.drawable.ic_check) {
            SettingSwitch("Require every camera ready", !settings.partial, enabled = !remote.busy) { requireAll -> lifecycleScope.launch { app.repository.partial(!requireAll) } }
            Text(if (settings.partial) "Group Record can start with missing cameras." else "Group Record waits for every saved camera. Individual camera buttons control only that camera.", color = Muted, fontSize = 12.sp)
        }
        Section("Home-Screen Widget", R.drawable.ic_widget) {
            ActionButton("Add home-screen widget", R.drawable.ic_add, onClick = {
                val widgets = getSystemService(AppWidgetManager::class.java)
                if (widgets.isRequestPinAppWidgetSupported) widgets.requestPinAppWidget(
                    ComponentName(this@MainActivity, RemoteWidgetReceiver::class.java), null, null)
                else localMessage = "This launcher doesn't support direct widget pinning."
            })
        }
        Section("Session Diagnostics", R.drawable.ic_diagnostics) {
            TextButton(onClick = { showDiagnostics = !showDiagnostics }) { Text(if (showDiagnostics) "Hide diagnostics" else "View diagnostics") }
            if (showDiagnostics) {
                remote.cameras.forEachIndexed { index, camera -> Text("Camera ${index + 1} · queued +${camera.sentAtMs ?: "—"} ms · confirmed +${camera.confirmedAtMs ?: "—"} ms", fontSize = 12.sp) }
                Text("Dispatch / status timing, not frame synchronization.", fontSize = 12.sp, color = Muted)
                Text(diagnostics.takeLast(30).joinToString("\n").ifEmpty { "No session events yet." }, fontSize = 11.sp, color = Muted)
            }
        }
        Section("Experimental exposure & ISO", R.drawable.ic_iso) {
            SettingSwitch("Show global control wheels", settings.experimentalExposure) { value ->
                lifecycleScope.launch { app.repository.experimentalExposure(value) }
            }
            Text("Choose a shutter speed and ISO preset for the group. Saved locally only; Bluetooth setting commands and readback are not verified.", color = Muted, fontSize = 12.sp)
        }
        if (settings.experimentalExposure) ExposureWheels(settings.exposurePreset,
            onShutter = { lifecycleScope.launch { app.repository.stageShutter(it) } },
            onIso = { lifecycleScope.launch { app.repository.stageIso(it) } })
        Section("App info", R.drawable.ic_info) {
            Text("DJI Multi Remote", fontWeight = FontWeight.Bold)
            Text("Version ${BuildConfig.VERSION_NAME}", color = Coral)
            Text("Independent engineering preview. Verify camera behavior before critical takes. Not affiliated with DJI.", color = Muted, fontSize = 12.sp)
            Text("Background sessions stay out of the normal notification shade on Android 13+. Android still shows an Active apps entry. Older versions require a silent notification.", color = Muted, fontSize = 12.sp)
            HorizontalDivider(color = Color(0xff344152))
            Text("Local Only - No Account - No Internet Permissions", color = Mint, fontSize = 12.sp)
        }
    }
    private fun keyName(code: Int) = when (code) {
        -1 -> "Disabled"
        KeyEvent.KEYCODE_VOLUME_UP -> "Volume Up"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}

@Composable private fun Glyph(@DrawableRes id: Int, description: String?, color: Color = LocalContentColor.current, size: Int = 24) {
    Icon(painterResource(id), contentDescription = description, tint = color, modifier = Modifier.size(size.dp))
}
@Composable private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}
@Composable private fun Section(title: String, @DrawableRes icon: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Glyph(icon, null, Coral, 20); Text(title, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}
@Composable private fun ActionButton(label: String, @DrawableRes icon: Int, enabled: Boolean = true, dark: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(
            containerColor = if (dark) Ink else Coral, contentColor = if (dark) Color.White else Ink)) {
        Glyph(icon, null, size = 20); Spacer(Modifier.width(10.dp)); Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
@Composable private fun Notice(text: String) {
    Row(Modifier.fillMaxWidth().background(Color(0xff302b25), RoundedCornerShape(14.dp)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Glyph(R.drawable.ic_warning, null, Amber, 18); Text(text, color = Amber, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}
@Composable private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked, onChanged, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Coral, checkedThumbColor = Ink))
    }
}
