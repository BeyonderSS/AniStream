package com.anistream.core.account

import android.content.Context
import com.anistream.core.database.ActiveProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class PairCode(val code: String, val expiresAt: Long)

/**
 * Account state for the whole app. Token, user, profile cache and active profile live in plain SharedPreferences
 * (ponytail: not encrypted; EncryptedSharedPreferences isn't a dependency and the token only opens this user's watch history).
 * When [enabled] is false (blank REELM_API_URL) nothing here is ever reached and the app stays local-only.
 */
@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val api: AccountApi
) {
    val enabled: Boolean get() = api.enabled
    val baseUrl: String get() = api.baseUrl

    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** "Who's watching?" is due: set at launch/sign-in when the account has more than one profile, cleared on pick. */
    private val _pickerPending = MutableStateFlow(false)
    val pickerPending: StateFlow<Boolean> = _pickerPending.asStateFlow()

    val token: String? get() = _session.value?.token
    /** one id per install, shown to the user's other devices in the hub */
    val deviceId: String = prefs.getString("deviceId", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }

    init {
        val token = prefs.getString("token", null)
        val user = prefs.getString("user", null)?.let { runCatching { Json.parseToJsonElement(it).jsonObject.toUser() }.getOrNull() }
        if (enabled && token != null && user != null) {
            _profiles.value = prefs.getString("profiles", null)?.let { runCatching { Json.parseToJsonElement(it).jsonArray.map { p -> p.jsonObject.toProfile() } }.getOrNull() }.orEmpty()
            publish(token, user, prefs.getString("activeProfile", null))
            _pickerPending.value = _profiles.value.size > 1
        }
    }

    private fun publish(token: String, user: User, activeId: String?) {
        val active = _profiles.value.firstOrNull { it.id == activeId }
        _session.value = Session(token, user, active)
        ActiveProfile.id = active?.id ?: ""
    }

    suspend fun login(username: String, password: String) = auth("login", username, password)
    suspend fun register(username: String, password: String) = auth("register", username, password)

    private suspend fun auth(path: String, username: String, password: String) {
        val res = api.call("POST", "/v1/auth/$path", buildJsonObject { put("username", username.trim().lowercase()); put("password", password) })!!
        signIn(str(res, "token"), res.userObj())
    }

    /** Also the TV path: a claimed pairing code hands back the same token + user. */
    suspend fun signIn(token: String, user: User) {
        prefs.edit().putString("token", token).putString("user", buildJsonObject { put("id", user.id); put("username", user.username) }.toString()).remove("activeProfile").apply()
        _profiles.value = emptyList()
        publish(token, user, null)
        refreshProfiles()
        // a single profile needs no picker; with several the user picks
        if (_profiles.value.size == 1) selectProfile(_profiles.value[0].id) else _pickerPending.value = _profiles.value.size > 1
    }

    suspend fun logout() {
        token?.let { runCatching { api.call("POST", "/v1/auth/logout", token = it) } }
        prefs.edit().clear().putString("deviceId", deviceId).apply()
        _profiles.value = emptyList()
        _session.value = null
        _pickerPending.value = false
        ActiveProfile.id = ""
    }

    suspend fun refreshProfiles() {
        val t = token ?: return
        val list = api.call("GET", "/v1/profiles", token = t)!!["profiles"]!!.jsonArray.map { it.jsonObject.toProfile() }
        setProfiles(list)
    }

    private fun setProfiles(list: List<Profile>) {
        _profiles.value = list
        prefs.edit().putString("profiles", JsonArray(list.map { buildJsonObject { put("id", it.id); put("name", it.name); put("avatar", it.avatar); put("kids", it.kids) } }).toString()).apply()
        val s = _session.value ?: return
        publish(s.token, s.user, s.activeProfile?.id)
    }

    fun selectProfile(id: String) {
        val s = _session.value ?: return
        prefs.edit().putString("activeProfile", id).apply()
        publish(s.token, s.user, id)
        _pickerPending.value = false
    }

    suspend fun createProfile(name: String, avatar: String, kids: Boolean) {
        val p = api.call("POST", "/v1/profiles", buildJsonObject { put("name", name.trim()); put("avatar", avatar); put("kids", kids) }, token)!!.toProfile()
        setProfiles(_profiles.value + p)
    }

    suspend fun updateProfile(id: String, name: String, avatar: String, kids: Boolean) {
        val p = api.call("PATCH", "/v1/profiles/$id", buildJsonObject { put("name", name.trim()); put("avatar", avatar); put("kids", kids) }, token)!!.toProfile()
        setProfiles(_profiles.value.map { if (it.id == id) p else it })
    }

    suspend fun deleteProfile(id: String) {
        api.call("DELETE", "/v1/profiles/$id", token = token)
        val rest = _profiles.value.filter { it.id != id }
        setProfiles(rest)
        if (_session.value?.activeProfile == null && rest.isNotEmpty()) _pickerPending.value = rest.size > 1
    }

    // --- TV pairing (device-code flow) ---

    /** TV, signed out: asks the server for a code to show as a QR. */
    suspend fun startPair(): PairCode {
        val r = api.call("POST", "/v1/pair")!!
        return PairCode(str(r, "code"), r["expiresAt"]?.toString()?.toLongOrNull() ?: 0L)
    }

    /** TV: one poll. Signs in when the phone has claimed the code; true = signed in. */
    suspend fun pollPair(code: String): Boolean {
        val r = api.call("GET", "/v1/pair/$code")!!
        if (str(r, "status") != "claimed") return false
        signIn(str(r, "token"), r.userObj())
        return true
    }

    /** Phone, signed in: approves the TV that shows [code]. */
    suspend fun claimPair(code: String) {
        api.call("POST", "/v1/pair/${code.trim().uppercase()}/claim", token = token)
    }
}
