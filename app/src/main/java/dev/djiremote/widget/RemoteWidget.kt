package dev.djiremote.widget

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

class RemoteWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as RemoteApp
        provideContent {
            val remote by app.remote.collectAsState()
            val prefs = currentState<Preferences>()
            Content(context, GroupWidgetState.from(remote), prefs[stringPreferencesKey("updated")] ?: "—")
        }
    }
    @Composable private fun Content(context: Context, state: GroupWidgetState, updated: String) {
        val open = actionStartActivity<MainActivity>()
        val ink = if (state.recording) Color(0xff261519) else Color(0xfff5f7fc)
        val secondary = if (state.recording) Color(0xff47242a) else Color(0xffb5c2d2)
        val icon = when (state.action) { GroupAction.CONNECT -> R.drawable.ic_bluetooth; GroupAction.RECORD -> R.drawable.ic_play; GroupAction.STOP -> R.drawable.ic_stop }
        val label = when (state.action) { GroupAction.CONNECT -> "Connect"; GroupAction.RECORD -> "Record all"; GroupAction.STOP -> "Stop all" }
        val serviceAction = when (state.action) { GroupAction.CONNECT -> RemoteService.CONNECT; GroupAction.RECORD -> RemoteService.RECORD; GroupAction.STOP -> RemoteService.STOP }
        val buttonAction = if (state.enabled && BlePermissions.granted(context)) command(context, serviceAction) else open
        // The whole remaining surface opens the app. The nested button's action takes precedence.
        Row(GlanceModifier.fillMaxSize().background(ImageProvider(if (state.recording) R.drawable.widget_recording else R.drawable.widget_surface))
            .clickable(open).padding(8.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Box(GlanceModifier.size(48.dp).background(ImageProvider(when {
                !state.enabled -> R.drawable.widget_button_disabled
                state.recording -> R.drawable.widget_button_recording
                else -> R.drawable.widget_button
            })).clickable(buttonAction), contentAlignment = Alignment.Center) {
                Image(ImageProvider(icon), contentDescription = if (state.enabled) label else "$label unavailable. Open app",
                    modifier = GlanceModifier.size(25.dp))
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(state.title, maxLines = 1, style = TextStyle(color = ColorProvider(ink), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(state.description, maxLines = 1, style = TextStyle(color = ColorProvider(secondary), fontSize = 12.sp))
                if (state.active) Text("Updated $updated", maxLines = 1, style = TextStyle(color = ColorProvider(secondary), fontSize = 10.sp))
            }
        }
    }
    private fun command(context: Context, action: String): Action = actionStartService(
        Intent(context, RemoteService::class.java).setAction(action), isForegroundService = true)
    companion object {
        suspend fun refresh(context: Context, state: RemoteState) {
            GlanceAppWidgetManager(context).getGlanceIds(RemoteWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { p ->
                    p[stringPreferencesKey("updated")] = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    p[booleanPreferencesKey("active")] = state.active
                    p[intPreferencesKey("expected")] = state.cameras.size
                    p[intPreferencesKey("ready")] = state.readyCount
                    p[intPreferencesKey("recording")] = state.recordingCount
                    p[booleanPreferencesKey("error")] = state.cameras.any { it.error != null || it.uncertain }
                }
            }
            RemoteWidget().updateAll(context)
        }
    }
}
class RemoteWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RemoteWidget() }
