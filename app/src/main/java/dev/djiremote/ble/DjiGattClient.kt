package dev.djiremote.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** One independent serialized ATT operation queue per camera, never one global queue. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION") // Legacy GATT overloads are required for the supported minimum SDK.
class DjiGattClient(private val context: Context, private val scope: CoroutineScope) : CameraTransport {
    private data class Event(val kind: String, val status: Int, val mtu: Int = 23)
    private val events = Channel<Event>(32)
    override val notifications = Channel<ByteArray>(64)
    override val disconnected = CompletableDeferred<Unit>()
    private val operations = Mutex()
    private var gatt: BluetoothGatt? = null
    private var writer: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private var closed = false
    private fun uuid(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
    private fun deliver(g: BluetoothGatt, event: Event) {
        scope.launch { if (!closed && gatt === g && events.trySend(event).isFailure) close() }
    }
    private fun notification(g: BluetoothGatt, c: BluetoothGattCharacteristic, data: ByteArray) {
        if (c.uuid != uuid("fff4")) return
        val copy = data.copyOf()
        scope.launch { if (!closed && gatt === g && notifications.trySend(copy).isFailure) close() }
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            scope.launch {
                if (closed || gatt !== g) return@launch
                if (status != BluetoothGatt.GATT_SUCCESS || state == BluetoothProfile.STATE_DISCONNECTED) close()
                else if (state == BluetoothProfile.STATE_CONNECTED) deliver(g, Event("connected", status))
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = deliver(g, Event("services", status))
        override fun onMtuChanged(g: BluetoothGatt, value: Int, status: Int) = deliver(g, Event("mtu", status, value))
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = deliver(g, Event("descriptor", status))
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = deliver(g, Event("write", status))
        @Deprecated("Used on Android 12 and below")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) notification(g, c, c.value ?: byteArrayOf())
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = notification(g, c, value)
    }
    private suspend fun await(kind: String, timeout: Long = 8_000): Event = withTimeout(timeout) {
        val event = events.receive()
        check(event.kind == kind) { "Unexpected Bluetooth callback" }
        check(event.status == BluetoothGatt.GATT_SUCCESS) { "Bluetooth operation failed (${event.status})" }
        event
    }
    override suspend fun connect(address: String) = operations.withLock {
        check(!closed)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        check(adapter?.isEnabled == true) { "Turn on Bluetooth" }
        val device = adapter.getRemoteDevice(address)
        val link = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: error("Bluetooth connection could not start")
        gatt = link
        try {
            await("connected", 15_000)
            check(link.discoverServices()) { "Service discovery could not start" }; await("services")
            val service = link.getService(uuid("fff0")) ?: error("DJI remote service not found")
            writer = service.getCharacteristic(uuid("fff5")) ?: error("DJI write characteristic missing")
            val notify = service.getCharacteristic(uuid("fff4")) ?: error("DJI status characteristic missing")
            // A negotiated MTU avoids splitting 51-byte pairing frames on modern phones.
            if (link.requestMtu(247)) mtu = await("mtu").mtu.coerceAtLeast(23)
            check(link.setCharacteristicNotification(notify, true)) { "Cannot enable camera notifications" }
            val descriptor = notify.getDescriptor(uuid("2902")) ?: error("DJI notification descriptor missing")
            val value = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            val accepted = if (Build.VERSION.SDK_INT >= 33) link.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
                else { descriptor.value = value; link.writeDescriptor(descriptor) }
            check(accepted) { "Notification subscription could not start" }; await("descriptor")
            link.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            Unit
        } catch (e: Exception) { close(); throw e }
    }
    override suspend fun write(bytes: ByteArray) = operations.withLock {
        val link = gatt ?: error("Camera disconnected")
        val characteristic = writer ?: error("Camera not initialized")
        val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        try {
            for (part in bytes.toList().chunked(mtu - 3)) {
                val value = part.toByteArray()
                val accepted = if (Build.VERSION.SDK_INT >= 33)
                    link.writeCharacteristic(characteristic, value, type) == BluetoothStatusCodes.SUCCESS
                else {
                    characteristic.value = value
                    characteristic.writeType = type
                    link.writeCharacteristic(characteristic)
                }
                check(accepted) { "Bluetooth write rejected" }
                await("write")
            }
        } catch (e: Exception) {
            // A timed-out operation invalidates this queue; never consume its late callback for a later command.
            close(); throw e
        }
    }
    override fun close() {
        if (closed) return
        closed = true
        val link = gatt; gatt = null; writer = null
        runCatching { link?.disconnect() }; runCatching { link?.close() }
        events.close(IllegalStateException("Camera disconnected")); notifications.close()
        disconnected.complete(Unit)
    }
}
