package dev.djiremote.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

object BlePermissions {
    fun required() = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun granted(context: Context) = required().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
data class DiscoveredCamera(val address: String, val name: String, val model: String, val rssi: Int)
@SuppressLint("MissingPermission")
class DjiBleScanner(private val context: Context, private val scope: CoroutineScope) {
    val found = MutableStateFlow<List<DiscoveredCamera>>(emptyList())
    val scanning = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var scanner: BluetoothLeScanner? = null
    private var deadline: Job? = null
    private var callback: ScanCallback? = null
    fun start() {
        if (scanning.value) return
        if (!BlePermissions.granted(context)) { error.value = "Allow Nearby devices permission first"; return }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter?.isEnabled != true) { error.value = "Turn on Bluetooth first"; return }
        val le = adapter.bluetoothLeScanner ?: run { error.value = "Bluetooth LE scanner unavailable"; return }
        found.value = emptyList(); error.value = null
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val owner = this
                scope.launch {
                    if (callback !== owner) return@launch
                    val record = result.scanRecord ?: return@launch
                    val mfg = record.getManufacturerSpecificData(0x08aa)
                        ?: record.getManufacturerSpecificData(0xf7aa)
                    val modelId = if (mfg != null && mfg.size >= 2)
                        (mfg[0].toInt() and 255) or ((mfg[1].toInt() and 255) shl 8) else -1
                    val model = when (modelId) {
                        0x0014, 0xff33 -> "Osmo Action 4"
                        0x0015, 0xff44 -> "Osmo Action 5 Pro"
                        0x0018, 0xff55 -> "Osmo Action 6"
                        0x0017, 0xff66 -> "Osmo 360"
                        else -> null
                    }
                    val djiSignature = mfg != null && mfg.size >= 3 && mfg[2] == 0xfa.toByte()
                    if (model == null && !djiSignature) return@launch
                    // Advertisements are hints only; the handshake validates the actual model.
                    val address = result.device.address
                    val item = DiscoveredCamera(address, record.deviceName?.take(60) ?: model ?: "DJI camera",
                        model ?: "Model checked during pairing", result.rssi)
                    found.value = (found.value.filterNot { it.address == address } + item).sortedBy { it.name }
                }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
            override fun onScanFailed(errorCode: Int) { scope.launch { error.value = "Scan failed ($errorCode). Wait, then retry."; stop() } }
        }
        callback = cb; scanner = le; scanning.value = true
        try {
            // Hardware filtering also permits results while the phone's display is off.
            val filters = listOf(0x08aa, 0xf7aa).map {
                ScanFilter.Builder().setManufacturerData(it, byteArrayOf()).build()
            } + ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"))).build()
            le.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
            deadline = scope.launch { delay(20_000); stop() }
        } catch (_: SecurityException) { error.value = "Bluetooth permission was revoked"; stop() }
          catch (_: IllegalStateException) { error.value = "Bluetooth is unavailable"; stop() }
    }
    fun stop() {
        deadline?.cancel(); deadline = null
        callback?.let { runCatching { scanner?.stopScan(it) } }
        callback = null; scanner = null; scanning.value = false
    }
}
