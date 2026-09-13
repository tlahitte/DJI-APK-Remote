package dev.djiremote.camera

/** Local staging only. No assumption that a particular firmware accepts any of these values. */
data class ExposurePreset(val shutterDenominator: Int = 200, val iso: Int = 100) {
    init { require(shutterDenominator in shutterDenominators); require(iso in isoValues) }
    val shutterLabel get() = "1/$shutterDenominator"
    companion object {
        val shutterDenominators = listOf(25, 30, 40, 50, 60, 80, 100, 120, 160, 200, 240, 250, 320, 400,
            500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000)
        val isoValues = listOf(100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600,
            2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800)
        fun normalized(shutter: Int?, iso: Int?) = ExposurePreset(
            shutter?.takeIf { it in shutterDenominators } ?: 200,
            iso?.takeIf { it in isoValues } ?: 100)
    }
}
