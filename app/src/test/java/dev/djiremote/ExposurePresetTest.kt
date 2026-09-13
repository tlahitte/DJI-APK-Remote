package dev.djiremote

import dev.djiremote.camera.ExposurePreset
import dev.djiremote.storage.Settings
import org.junit.Assert.*
import org.junit.Test

class ExposurePresetTest {
    @Test fun experimentalControlsAreOptIn() { assertFalse(Settings().experimentalExposure) }
    @Test fun isoLimitsAre100To12800() { assertEquals(100, ExposurePreset.isoValues.first()); assertEquals(12800, ExposurePreset.isoValues.last()) }
    @Test fun requestedShutterValuesExist() { assertTrue(200 in ExposurePreset.shutterDenominators); assertTrue(400 in ExposurePreset.shutterDenominators) }
    @Test fun shutterLabelIsReciprocalNotExposureCompensation() { assertEquals("1/400", ExposurePreset(400, 12800).shutterLabel) }
    @Test fun invalidStoredValuesFallBackSafely() { assertEquals(ExposurePreset(), ExposurePreset.normalized(0, 999)) }
    @Test fun validStoredValuesRoundTrip() { assertEquals(ExposurePreset(400, 3200), ExposurePreset.normalized(400, 3200)) }
    @Test(expected = IllegalArgumentException::class) fun invalidIsoCannotBeStaged() { ExposurePreset(400, 64) }
    @Test fun listsAreUniqueAndOrdered() {
        assertEquals(ExposurePreset.isoValues.sorted().distinct(), ExposurePreset.isoValues)
        assertEquals(ExposurePreset.shutterDenominators.sorted().distinct(), ExposurePreset.shutterDenominators)
    }
}
