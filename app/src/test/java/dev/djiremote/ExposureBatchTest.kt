package dev.djiremote

import dev.djiremote.camera.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExposureBatchTest {
    private class Target(val reads: Boolean = true, val applies: Boolean = true) : ExposureTarget {
        var writes = 0
        override suspend fun readExposure() = reads
        override suspend fun applyExposure(preset: ExposurePreset): Boolean { writes++; return applies }
    }
    @Test fun oneFailedQueryPreventsAllGroupWrites() = runTest {
        val a=Target(); val b=Target(reads=false)
        assertEquals(ExposureOutcome.READ_FAILED,ExposureBatch.run(listOf(a,b),ExposurePreset()))
        assertEquals(0,a.writes); assertEquals(0,b.writes)
    }
    @Test fun readButtonNeverAppliesSettings() = runTest {
        val a=Target();assertEquals(ExposureOutcome.READ,ExposureBatch.run(listOf(a),null));assertEquals(0,a.writes)
    }
    @Test fun partialResultIsNotGlobalSuccess() = runTest {
        assertEquals(ExposureOutcome.PARTIAL,ExposureBatch.run(listOf(Target(),Target(applies=false)),ExposurePreset()))
    }
    @Test fun allVerifiedIsApplied() = runTest {
        assertEquals(ExposureOutcome.APPLIED,ExposureBatch.run(listOf(Target(),Target()),ExposurePreset()))
    }
    @Test fun writesDispatchWithoutWaitingForOtherCameraConfirmation() = runTest {
        val confirmation=CompletableDeferred<Boolean>();var started=0
        fun target() = object:ExposureTarget {
            override suspend fun readExposure()=true
            override suspend fun applyExposure(preset:ExposurePreset):Boolean { started++;return confirmation.await() }
        }
        val job=async { ExposureBatch.run(listOf(target(),target()),ExposurePreset()) };runCurrent()
        assertEquals(2,started);assertFalse(job.isCompleted);confirmation.complete(true)
        assertEquals(ExposureOutcome.APPLIED,job.await())
    }
}
