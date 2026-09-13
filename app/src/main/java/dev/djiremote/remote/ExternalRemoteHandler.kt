package dev.djiremote.remote

import android.os.SystemClock
import android.view.KeyEvent

class ExternalRemoteHandler {
    private var lastPress = -1000L
    fun handle(event: KeyEvent, learnedKey: Int, trigger: () -> Unit): Boolean {
        if (learnedKey < 0 || event.keyCode != learnedKey) return false
        if (event.action == KeyEvent.ACTION_UP) return true
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true
        val now = SystemClock.elapsedRealtime()
        if (now - lastPress >= 650) { lastPress = now; trigger() }
        return true
    }
}
