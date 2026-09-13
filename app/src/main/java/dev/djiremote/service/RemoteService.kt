package dev.djiremote.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.djiremote.R
import dev.djiremote.RemoteApp
import dev.djiremote.ble.BlePermissions
import dev.djiremote.camera.CameraManager
import dev.djiremote.ui.MainActivity
import dev.djiremote.widget.GroupWidgetState
import dev.djiremote.widget.RemoteWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RemoteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: RemoteApp
    private lateinit var manager: CameraManager
    private var media: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        app = application as RemoteApp
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Background camera connection", NotificationManager.IMPORTANCE_MIN))
        manager = CameraManager(app, scope)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BlePermissions.granted(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            ServiceCompat.startForeground(this, 1, notification(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        } catch (_: SecurityException) { stopSelf(); return START_NOT_STICKY }
        val wasActive = app.remote.value.active
        manager.start()
        if (!wasActive) {
            scope.launch {
                app.remote.map { GroupWidgetState.from(it) }
                    .distinctUntilChanged().collect {
                        // Android 13+ deliberately has no notification permission: only the required
                        // system Active apps indicator remains. Older versions need a quiet notification.
                        if (Build.VERSION.SDK_INT < 33) getSystemService(NotificationManager::class.java).notify(1, notification())
                        runCatching { RemoteWidget.refresh(this@RemoteService, app.remote.value) }
                    }
            }
            scope.launch {
                while (isActive) {
                    delay(15_000)
                    runCatching { RemoteWidget.refresh(this@RemoteService, app.remote.value) }
                }
            }
            scope.launch { app.settings.map { it.mediaEnabled }.distinctUntilChanged().collect { enabled ->
                media?.release(); media = null
                if (enabled) enableMedia()
            } }
        }
        when (intent?.action) {
            SCAN -> manager.scan()
            CANCEL_SCAN -> manager.cancelScan()
            CONNECT -> manager.reconnect()
            RECORD -> manager.command(true)
            STOP -> manager.command(false)
            RECORD_ONE, STOP_ONE -> intent.getStringExtra(CAMERA_ID)?.let {
                manager.command(intent.action == RECORD_ONE, it)
            }
            TOGGLE -> manager.toggle()
            END -> { stopSelf(); return START_NOT_STICKY }
        }
        return START_NOT_STICKY
    }
    private fun enableMedia() {
        media = MediaSession(this, "DJI Remote button").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val event = if (Build.VERSION.SDK_INT >= 33) mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        else mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    return event != null && app.button.handle(event, app.settings.value.keyCode) { manager.toggle() }
                }
            }, Handler(Looper.getMainLooper()))
            setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
            isActive = true
        }
    }
    private fun notification(): Notification {
        val state = app.remote.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DJI Multi Remote").setContentText(state.headline)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setSilent(true).setShowWhen(false).setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (state.canRecord) b.addAction(0, "Record", pending(RECORD, 1))
        if (state.canStop) b.addAction(0, "Stop all", pending(STOP, 2))
        b.addAction(0, "Open", open)
        return b.build()
    }
    private fun pending(action: String, code: Int) = PendingIntent.getForegroundService(this, code,
        Intent(this, RemoteService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    override fun onDestroy() {
        manager.close(); media?.release(); scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        app.scope.launch { runCatching { RemoteWidget.refresh(this@RemoteService, app.remote.value) } }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "remote_session_quiet"
        const val CONNECT = "dev.djiremote.CONNECT"
        const val SCAN = "dev.djiremote.SCAN"
        const val CANCEL_SCAN = "dev.djiremote.CANCEL_SCAN"
        const val RECORD = "dev.djiremote.RECORD"
        const val STOP = "dev.djiremote.STOP"
        const val RECORD_ONE = "dev.djiremote.RECORD_ONE"
        const val STOP_ONE = "dev.djiremote.STOP_ONE"
        private const val CAMERA_ID = "camera_id"
        const val TOGGLE = "dev.djiremote.TOGGLE"
        const val END = "dev.djiremote.END"
        fun send(context: Context, action: String, cameraId: String? = null) {
            context.startForegroundService(Intent(context, RemoteService::class.java).setAction(action).apply {
                if (cameraId != null) putExtra(CAMERA_ID, cameraId)
            })
        }
    }
}
