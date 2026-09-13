package dev.djiremote.storage

import android.content.Context
import android.view.KeyEvent
import dev.djiremote.camera.ExposurePreset
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class KnownCamera(val address: String, val name: String, val paired: Boolean = false)
data class Settings(val cameras: List<KnownCamera> = emptyList(), val partial: Boolean = false,
    val keyCode: Int = KeyEvent.KEYCODE_VOLUME_UP, val mediaEnabled: Boolean = false,
    val experimentalExposure: Boolean = false, val exposurePreset: ExposurePreset = ExposurePreset())
data class ControllerIdentity(val id: Int, val address: ByteArray)
class CameraRepository(context: Context) {
    private val remoteData = PreferenceDataStoreFactory.create(
        produceFile = { File(context.noBackupFilesDir, "remote_settings.preferences_pb") })
    private val camerasKey = stringPreferencesKey("cameras")
    private val partialKey = booleanPreferencesKey("partial")
    private val keyKey = intPreferencesKey("remote_key")
    private val mediaKey = booleanPreferencesKey("media_enabled")
    private val experimentalKey = booleanPreferencesKey("experimental_exposure")
    private val shutterKey = intPreferencesKey("staged_shutter_denominator")
    private val isoKey = intPreferencesKey("staged_iso")
    private val identityKey = stringPreferencesKey("controller_identity")
    private val idKey = intPreferencesKey("controller_id")
    val settings: Flow<Settings> = remoteData.data.map { p ->
        val a = JSONArray(p[camerasKey] ?: "[]")
        Settings((0 until a.length()).map { i -> a.getJSONObject(i).let {
            KnownCamera(it.getString("address"), it.getString("name"), it.optBoolean("paired"))
        } }, p[partialKey] ?: false, p[keyKey] ?: KeyEvent.KEYCODE_VOLUME_UP, p[mediaKey] ?: false,
            p[experimentalKey] ?: false, ExposurePreset.normalized(p[shutterKey], p[isoKey]))
    }
    suspend fun identity(): ControllerIdentity {
        val random = SecureRandom()
        val prefs = remoteData.updateData { old -> old.toMutablePreferences().apply {
            if (this[idKey] == null) this[idKey] = random.nextInt().let { if (it == 0) 1 else it }
            if (this[identityKey] == null) {
                val address = ByteArray(6).also(random::nextBytes)
                address[0] = ((address[0].toInt() or 2) and 0xfe).toByte()
                this[identityKey] = address.joinToString("") { "%02x".format(it) }
            }
        } }
        val hex = requireNotNull(prefs[identityKey])
        return ControllerIdentity(requireNotNull(prefs[idKey]), hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    }
    private suspend fun mutateCameras(change: (List<KnownCamera>) -> List<KnownCamera>) {
        remoteData.edit { p ->
            val a = JSONArray(p[camerasKey] ?: "[]")
            val old = (0 until a.length()).map { i -> a.getJSONObject(i).let {
                KnownCamera(it.getString("address"), it.getString("name"), it.optBoolean("paired"))
            } }
            val next = JSONArray()
            change(old).forEach { next.put(JSONObject().put("address", it.address).put("name", it.name).put("paired", it.paired)) }
            p[camerasKey] = next.toString()
        }
    }
    suspend fun add(camera: KnownCamera) = mutateCameras { if (it.any { c -> c.address == camera.address }) it else it + camera }
    suspend fun remove(address: String) = mutateCameras { it.filterNot { c -> c.address == address } }
    suspend fun paired(address: String) = mutateCameras { it.map { c -> if (c.address == address) c.copy(paired = true) else c } }
    suspend fun rename(address: String, name: String) = mutateCameras { it.map { c -> if (c.address == address) c.copy(name = name.trim().take(40).ifEmpty { c.name }) else c } }
    suspend fun partial(value: Boolean) { remoteData.edit { it[partialKey] = value } }
    suspend fun key(code: Int) { remoteData.edit { it[keyKey] = code } }
    suspend fun experimentalExposure(value: Boolean) { remoteData.edit { it[experimentalKey] = value } }
    suspend fun stageShutter(value: Int) {
        require(value in ExposurePreset.shutterDenominators)
        remoteData.edit { it[shutterKey] = value }
    }
    suspend fun stageIso(value: Int) {
        require(value in ExposurePreset.isoValues)
        remoteData.edit { it[isoKey] = value }
    }
    suspend fun media(value: Boolean) { remoteData.edit { it[mediaKey] = value } }
}
