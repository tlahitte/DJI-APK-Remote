package dev.djiremote.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import dev.djiremote.R
import dev.djiremote.RemoteApp
import dev.djiremote.ble.BlePermissions
import dev.djiremote.camera.RemoteState
import dev.djiremote.service.RemoteService
import dev.djiremote.ui.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RemoteWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as RemoteApp
        provideContent {
            // Read the persisted state Glance actually invalidates, not a captured process StateFlow.
            val snapshot = decode(currentState<Preferences>())
            Content(context, snapshot, snapshot.validFor(app.widgetProcessToken, SystemClock.elapsedRealtime()))
        }
    }
    @Composable private fun Content(context: Context, s: WidgetSnapshot, live: Boolean) {
        val open = actionStartActivity<MainActivity>()
        // Preserve the supplied banner's approximate 459:195 proportions inside the 4x2 host.
        val contentHeight = minOf(LocalSize.current.height, (LocalSize.current.width.value * 195f / 459f).dp.coerceAtLeast(132.dp))
        val compact = contentHeight < 170.dp
        val recording = live && s.recording > 0
        val title = when {
            !live && s.active -> "Status unavailable"
            !live -> "Ready when you are"
            recording -> "${s.recording} / ${s.expected} recording"
            s.busy -> "Waiting for cameras…"
            else -> "${s.ready} / ${s.expected} cameras ready"
        }
        if (LocalSize.current.height < 132.dp) {
            Row(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_banner)).clickable(open).padding(8.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("DJI Multi Remote", maxLines = 1, style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Text(if (recording) "${s.recording}/${s.expected} REC" else if (live) "${s.ready}/${s.expected} READY" else "Session off", maxLines = 1,
                        style = TextStyle(color = ColorProvider(if (recording) Color(0xffFF5D64) else Color.White), fontSize = 15.sp))
                }
                if (!s.active) MiniControl(context, "Connect", R.drawable.ic_bluetooth, RemoteService.CONNECT, true)
                else {
                    MiniControl(context, "Record all", R.drawable.ic_play, RemoteService.RECORD, live && s.canRecord)
                    Spacer(GlanceModifier.width(8.dp))
                    MiniControl(context, "Stop all", R.drawable.ic_stop, RemoteService.STOP, s.canStop || !live)
                }
            }
            return
        }
        Box(GlanceModifier.fillMaxSize().clickable(open), contentAlignment = Alignment.Center) {
        Column(GlanceModifier.fillMaxWidth().height(contentHeight).background(ImageProvider(R.drawable.widget_banner)).clickable(open).padding(if (compact) 8.dp else 12.dp)) {
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("DJI MULTI REMOTE", maxLines = 1, style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(if (compact) 2.dp else 4.dp))
                    Text(title, maxLines = 2, style = TextStyle(color = ColorProvider(if (recording) Color(0xffFF5D64) else Color.White), fontSize = if (compact) 16.sp else 19.sp, fontWeight = FontWeight.Bold))
                    if (!compact) Text(if (live) s.detail else "Open app to check your camera group", maxLines = 2,
                        style = TextStyle(color = ColorProvider(Color(0xffc5d1df)), fontSize = 12.sp))
                }
                Spacer(GlanceModifier.width(8.dp))
                Image(ImageProvider(R.drawable.widget_badge), "DJI Multi Remote icon", modifier = GlanceModifier.size(if (compact) 48.dp else 60.dp))
            }
            Spacer(GlanceModifier.defaultWeight())
            if (s.updatedLabel != "—") Text("Updated ${s.updatedLabel}" + if (live) "" else " · verify status in app", maxLines = 1,
                style = TextStyle(color = ColorProvider(Color(0xffc5d1df)), fontSize = 10.sp))
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                if (!s.active) {
                    Control(context, "CONNECT", R.drawable.ic_bluetooth, RemoteService.CONNECT, true, GlanceModifier.defaultWeight())
                } else {
                    // Both controls stay visible: STOP never disappears just because a status update is late.
                    Control(context, "RECORD", R.drawable.ic_play, RemoteService.RECORD, live && s.canRecord, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(10.dp))
                    // If the snapshot is stale, allow an explicit STOP request. Service rechecks live sessions.
                    Control(context, "STOP", R.drawable.ic_stop, RemoteService.STOP, s.canStop || !live, GlanceModifier.defaultWeight())
                }
            }
        }
        }
    }
    @Composable private fun Control(context: Context, label: String, icon: Int, action: String, enabled: Boolean, modifier: GlanceModifier) {
        val target = if (enabled && BlePermissions.granted(context)) command(context, action) else actionStartActivity<MainActivity>()
        Box(modifier.height(48.dp).background(ImageProvider(if (enabled) R.drawable.widget_button else R.drawable.widget_button_disabled)).clickable(target), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(18.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(label, style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
    @Composable private fun MiniControl(context: Context, label: String, icon: Int, action: String, enabled: Boolean) {
        val target = if (enabled && BlePermissions.granted(context)) command(context, action) else actionStartActivity<MainActivity>()
        Box(GlanceModifier.size(44.dp).background(ImageProvider(if (enabled) R.drawable.widget_button else R.drawable.widget_button_disabled)).clickable(target), contentAlignment = Alignment.Center) {
            Image(ImageProvider(icon), label, modifier = GlanceModifier.size(22.dp))
        }
    }
    private fun command(context: Context, action: String): Action = actionStartService(Intent(context, RemoteService::class.java).setAction(action), isForegroundService = true)
    companion object {
        private val writer = Mutex()
        private val active = booleanPreferencesKey("v3_active")
        private val expected = intPreferencesKey("v3_expected")
        private val ready = intPreferencesKey("v3_ready")
        private val recording = intPreferencesKey("v3_recording")
        private val canRecord = booleanPreferencesKey("v3_can_record")
        private val canStop = booleanPreferencesKey("v3_can_stop")
        private val busy = booleanPreferencesKey("v3_busy")
        private val title = stringPreferencesKey("v3_title")
        private val detail = stringPreferencesKey("v3_detail")
        private val elapsed = longPreferencesKey("v3_elapsed")
        private val token = stringPreferencesKey("v3_process")
        private val timeLabel = stringPreferencesKey("v3_time")
        private fun decode(p: Preferences) = WidgetSnapshot(p[active] ?: false, p[expected] ?: 0, p[ready] ?: 0, p[recording] ?: 0,
            p[canRecord] ?: false, p[canStop] ?: false, p[busy] ?: false, p[title] ?: "Remote inactive", p[detail] ?: "",
            p[elapsed] ?: 0, p[token] ?: "", p[timeLabel] ?: "—")
        @Suppress("UNUSED_PARAMETER")
        suspend fun refresh(context: Context, state: RemoteState) = writer.withLock {
            val app = context.applicationContext as RemoteApp
            // Re-read at write time: a queued OFF update must not overwrite a newer live session.
            val s = WidgetSnapshot.from(app.remote.value, SystemClock.elapsedRealtime(), app.widgetProcessToken,
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
            GlanceAppWidgetManager(context).getGlanceIds(RemoteWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { p ->
                    p[active] = s.active; p[expected] = s.expected; p[ready] = s.ready; p[recording] = s.recording
                    p[canRecord] = s.canRecord; p[canStop] = s.canStop; p[busy] = s.busy
                    p[title] = s.title; p[detail] = s.detail; p[elapsed] = s.updatedElapsed; p[token] = s.processToken; p[timeLabel] = s.updatedLabel
                }
                RemoteWidget().update(context, id)
            }
        }
    }
}
class RemoteWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RemoteWidget() }
