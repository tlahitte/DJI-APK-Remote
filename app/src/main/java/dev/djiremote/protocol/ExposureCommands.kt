package dev.djiremote.protocol

import dev.djiremote.camera.ExposurePreset

/** Wire payloads cross-checked against DJI's published Mobile SDK 4.18 class definitions. */
object ExposureCommands {
    const val SET_MODE = 0x1e
    const val SET_SHUTTER = 0x28
    const val GET_SHUTTER = 0x29
    const val SET_ISO = 0x2a
    const val GET_ISO = 0x2b
    val isoValues = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)
    fun manualMode() = byteArrayOf(4, 0)
    fun shutter(preset: ExposurePreset): ByteArray = DjiProtocol.buffer(4).put(1)
        .putShort((preset.shutterDenominator or 0x8000).toShort()).put(0).array()
    fun iso(preset: ExposurePreset): ByteArray {
        val index = isoValues.indexOf(preset.iso)
        require(index >= 0) { "Only whole-stop ISO values are supported by this driver" }
        return byteArrayOf((index + 3).toByte()) // absolute ISO: high (relative) bit stays clear
    }
    fun parseIso(payload: ByteArray): Int {
        require(payload.size == 2 && payload.u8(0) == 0 && payload.u8(1) in 0..11) { "Unsupported ISO reply" }
        return payload.u8(1)
    }
    fun parseShutter(payload: ByteArray): ShutterReadback {
        require(payload.size == 5 && payload.u8(0) == 0 && payload.u8(1) in 0..1) { "Unsupported shutter reply" }
        val value = payload.u16(2)
        return ShutterReadback(payload.u8(1) == 0, value and 0x8000 != 0, value and 0x7fff, payload.u8(4))
    }
}
data class ShutterReadback(val auto: Boolean, val reciprocal: Boolean, val integral: Int, val decimal: Int) {
    val label get() = if (auto) "Auto" else (if (reciprocal) "1/" else "") + integral + (if (decimal == 0) "" else ".$decimal")
}
data class ExposureReadback(val isoCode: Int, val shutter: ShutterReadback) {
    val isoLabel get() = when (isoCode) { 0, 1 -> "Auto"; 2 -> "50"; in 3..10 -> ExposureCommands.isoValues[isoCode - 3].toString(); 11 -> "25600"; else -> "Unknown" }
    fun matches(preset: ExposurePreset) = isoCode in 3..10 && ExposureCommands.isoValues[isoCode - 3] == preset.iso &&
        !shutter.auto && shutter.reciprocal && shutter.integral == preset.shutterDenominator && shutter.decimal == 0
}
