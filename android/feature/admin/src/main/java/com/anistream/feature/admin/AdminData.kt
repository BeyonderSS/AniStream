package com.anistream.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.core.account.AccountApi
import com.anistream.core.account.AccountRepository
import com.anistream.core.account.ApiException
import com.anistream.core.account.AuthRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject
import javax.inject.Singleton

// ---- JSON tree readers: fields are read by name, missing or null reads as empty/zero ----------------------------
fun JsonElement?.o(k: String): JsonElement? = (this as? JsonObject)?.get(k)
fun JsonElement?.s(k: String): String? = (o(k) as? JsonPrimitive)?.contentOrNull
fun JsonElement?.d(k: String): Double = (o(k) as? JsonPrimitive)?.doubleOrNull ?: 0.0
fun JsonElement?.i(k: String): Int = d(k).toInt()
fun JsonElement?.b(k: String): Boolean = (o(k) as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.doubleOrNull ?: 0.0).let { n -> n != 0.0 } } ?: false
fun JsonElement?.list(k: String): List<JsonElement> = (o(k) as? JsonArray) ?: emptyList()

// ---- formatting (ports of the web dashboard's helpers) --------------------------------------------------------
fun ago(t: Double, now: Long = System.currentTimeMillis()): String {
    if (t <= 0) return "never"
    val s = (now - t.toLong()) / 1000
    val a = Math.abs(s)
    fun p(n: Long, unit: String) = if (s >= 0) "$n $unit${if (n == 1L) "" else "s"} ago" else "in $n $unit${if (n == 1L) "" else "s"}"
    return when {
        a < 45 -> "just now"
        a < 3600 -> p((a + 30) / 60, "minute")
        a < 86400 -> p((a + 1800) / 3600, "hour")
        else -> p((a + 43200) / 86400, "day")
    }
}
fun JsonElement?.ago(k: String) = ago(d(k))

fun num(x: Double): String = when {
    x >= 1e6 -> "%.1fM".format(x / 1e6)
    x >= 1e4 -> "%.1fK".format(x / 1e3)
    else -> x.toLong().toString()
}
fun hours(minutes: Double): String = if (minutes >= 90) "${"%.1f".format(Math.round(minutes / 6) / 10.0)} h" else "${minutes.toLong()} min"
fun pct(x: Double) = "${Math.round(x * 100)}%"
fun rate(ok: Double, err: Double) = if (ok + err > 0) ok / (ok + err) else 1.0
fun clock(sec: Double): String {
    val s = sec.toLong()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun adminError(e: Throwable) =
    if ((e as? ApiException)?.code == 403) "Your account isn't an admin." else AuthRules.friendly(e)

// ---- data ---------------------------------------------------------------------------------------------------
/** Thin client over the admin endpoints (server/API.md). Answers stay as JSON trees. */
@Singleton
class AdminRepository @Inject constructor(private val api: AccountApi, private val account: AccountRepository) {
    suspend fun call(method: String, path: String, body: JsonObject? = null): JsonObject =
        api.call(method, "/v1/admin/$path", body, account.token) ?: JsonObject(emptyMap())
}

data class AdminUi(val path: String = "", val data: JsonObject? = null, val error: String? = null, val loading: Boolean = false)

/** One shared VM: each screen asks for one path; the latest [AdminUi] is for that path. Silent loads (auto-refresh) keep the spinner off. */
@HiltViewModel
class AdminViewModel @Inject constructor(private val repo: AdminRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AdminUi())
    val ui: StateFlow<AdminUi> = _ui.asStateFlow()
    private var job: Job? = null

    fun load(path: String, silent: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { if (it.path != path) AdminUi(path, loading = true) else it.copy(loading = !silent) }
            try {
                _ui.value = AdminUi(path, repo.call("GET", path))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = adminError(e), loading = false) }
            }
        }
    }

    /** For anything that is not the screen's main load: detail sheets, next pages, actions. Throws; callers show [adminError]. */
    suspend fun get(path: String) = repo.call("GET", path)
    suspend fun act(method: String, path: String, body: JsonObject? = null) = repo.call(method, path, body)
}
