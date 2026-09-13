package dev.djiremote

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import dev.djiremote.camera.*
import dev.djiremote.protocol.CameraStatus
import dev.djiremote.storage.*
import dev.djiremote.ui.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking

/** Emulator-only visual/UX smoke harness. This test APK is NEVER part of the shipped app. */
class UiSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Run only on a test emulator" }
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync(); Thread.sleep(600)
            val app = activity.application as RemoteApp
            fun state(recording: Boolean): RemoteState {
                val cameras = (1..2).map { i -> CameraState("test-$i", if (i == 1) "Action · Main angle" else "Action · Wide angle",
                    ConnectionState.READY, CameraStatus(1, if (recording && i == 1) 3 else 1, 12, 1000, false, 0, 80), true, true) }
                return RemoteState(active = true, cameras = cameras)
            }
            runOnMainSync {
                app.settings.value = Settings(cameras = state(false).cameras.map { KnownCamera(it.id, it.name, true) })
                app.remote.value = state(false)
            }
            awaitText("Record all"); screenshot("ready")
            check(find("Local Only - No Account - No Internet Permissions") == null)
            check(find("Record only this camera") != null)
            runOnMainSync { app.remote.value = state(true) }
            awaitText("Stop this camera"); screenshot("partial-recording")
            check(find("Record only this camera") != null)
            click("Button"); awaitText("Volume Up"); screenshot("button")
            click("Settings"); awaitText("Home-Screen Widget"); screenshot("settings")
            check(find("Session Diagnostics") != null)
            check(find("Long-press your home screen → Widgets → DJI Multi Remote. The widget and notification control this same session.") == null)
            var scrolls = 0
            while (find("Local Only - No Account - No Internet Permissions") == null && scrolls++ < 5) {
                findScrollable(uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(400)
            }
            awaitText("Local Only - No Account - No Internet Permissions"); screenshot("settings-info")
            runOnMainSync { app.remote.value = RemoteState(); app.settings.value = Settings() }
            runBlocking { app.repository.experimentalExposure(true) }
            click("Remote")
            var wheelScrolls = 0
            while (find("Not sent to cameras") == null && wheelScrolls++ < 4) {
                findScrollable(uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(400)
            }
            awaitText("Not sent to cameras"); screenshot("experimental-wheels")
            click("1/250")
            click("160")
            repeat(30) {
                if (app.settings.value.exposurePreset.shutterDenominator == 250 && app.settings.value.exposurePreset.iso == 160) return@repeat
                Thread.sleep(100)
            }
            check(app.settings.value.exposurePreset.shutterDenominator == 250)
            check(app.settings.value.exposurePreset.iso == 160)
            check(!app.remote.value.active)
            screenshot("experimental-selected")
            runBlocking { app.repository.experimentalExposure(false); app.repository.stageShutter(200); app.repository.stageIso(100) }
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", "PASS: ready, per-camera, partial recording, default key, settings, privacy placement, staged wheel selection") })
        } catch (e: Exception) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("failure", e.toString()) })
        }
    }
    private fun find(text: String, node: AccessibilityNodeInfo? = uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (i in 0 until node.childCount) find(text, node.getChild(i))?.let { return it }
        return null
    }
    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) findScrollable(node.getChild(i))?.let { return it }
        return null
    }
    private fun awaitText(text: String) {
        repeat(30) { if (find(text) != null) return; Thread.sleep(100) }
        error("Text missing: $text")
    }
    private fun click(text: String) {
        awaitText(text)
        var node = find(text)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        waitForIdleSync(); Thread.sleep(400)
    }
    private fun screenshot(name: String) {
        waitForIdleSync(); Thread.sleep(300)
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        File(targetContext.cacheDir, "smoke-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
