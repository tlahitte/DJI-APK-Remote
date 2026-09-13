package dev.djiremote.camera

import kotlinx.coroutines.*

interface ExposureTarget {
    suspend fun readExposure(): Boolean
    suspend fun applyExposure(preset: ExposurePreset): Boolean
}
enum class ExposureOutcome { READ, APPLIED, READ_FAILED, PARTIAL }
object ExposureBatch {
    private suspend fun safe(action: suspend () -> Boolean): Boolean = try { action() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    suspend fun run(targets: List<ExposureTarget>, preset: ExposurePreset?, applying: () -> Unit = {}): ExposureOutcome {
        if (targets.isEmpty()) return ExposureOutcome.READ_FAILED
        val reads = supervisorScope { targets.map { async { safe { it.readExposure() } } }.awaitAll() }
        if (!reads.all { it }) return ExposureOutcome.READ_FAILED
        if (preset == null) return ExposureOutcome.READ
        applying()
        val results = supervisorScope { targets.map { async { safe { it.applyExposure(preset) } } }.awaitAll() }
        return if (results.all { it }) ExposureOutcome.APPLIED else ExposureOutcome.PARTIAL
    }
}
