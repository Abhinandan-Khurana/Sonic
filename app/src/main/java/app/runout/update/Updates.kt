package app.runout.update

import android.content.Context
import app.runout.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Where Sonic's builds are published: the project's GitHub releases. */
const val UPDATE_REPO = "Abhiroop1122/Sonic"

/** The page with the newest release and its APK, opened in the browser. */
const val RELEASES_PAGE = "https://github.com/$UPDATE_REPO/releases/latest"

/**
 * A version as the releases tag it: "v1.0.1", "v1.1.0-dev.2". A development build comes before
 * its release ("1.1.0-dev.2" < "1.1.0"), and later development builds after earlier ones.
 */
data class Version(val major: Int, val minor: Int, val patch: Int, val pre: Int? = null) : Comparable<Version> {
    override fun compareTo(other: Version): Int = compareValuesBy(
        this, other, { it.major }, { it.minor }, { it.patch },
        // A release (no pre number) outranks any of its development builds.
        { it.pre ?: Int.MAX_VALUE },
    )

    override fun toString() = "$major.$minor.$patch" + (pre?.let { "-dev.$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-[a-z]+\.?(\d+)?)?""", RegexOption.IGNORE_CASE)

        fun parse(tag: String): Version? = PATTERN.find(tag.trim())?.let { m ->
            val (a, b, c, d) = m.destructured
            Version(a.toInt(), b.toInt(), c.ifEmpty { "0" }.toInt(), if (m.value.contains('-')) d.ifEmpty { "0" }.toInt() else null)
        }
    }
}

/** A published release newer than the build on the phone. */
data class Release(val version: Version, val name: String, val page: String)

/** Settings › About › updates, and the notice on Settings when there's one. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val at: Long) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * Asks the project's GitHub releases for the latest stable build and compares it with
 * [installed]. Nothing is downloaded or installed here: a newer release is offered as its page,
 * opened in the browser, where its APK and checksum are. Once a day on launch when [auto] is on
 * (Settings › Flags), or whenever you ask in Settings › About. Only the request itself reaches
 * GitHub; no account, nothing about you or your music.
 */
class Updates(
    context: Context,
    private val http: Http,
    private val installed: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(prefs.getLong(CHECKED, 0L).takeIf { it > 0 }?.let { UpdateState.UpToDate(it) } ?: UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val _auto = MutableStateFlow(prefs.getBoolean(AUTO, true))
    val auto: StateFlow<Boolean> = _auto.asStateFlow()

    fun setAuto(on: Boolean) {
        _auto.value = on
        prefs.edit().putBoolean(AUTO, on).apply()
    }

    /** The daily check: quiet, and only when the last one is a day old. */
    suspend fun checkIfDue() {
        if (!_auto.value || clock() - prefs.getLong(CHECKED, 0L) < DAY_MS) return
        check(quiet = true)
    }

    /** A check you asked for; a failure says why. A quiet one keeps what was there on failure. */
    suspend fun check(quiet: Boolean = false) {
        if (_state.value == UpdateState.Checking) return
        val before = _state.value
        _state.value = UpdateState.Checking
        val body = withContext(Dispatchers.IO) { http.get("https://api.github.com/repos/$UPDATE_REPO/releases/latest") }
        val result = when {
            body == null -> UpdateState.Failed("couldn't reach github. check your connection and try again.")
            else -> runCatching { latest(String(body), installed) }.fold(
                onSuccess = { release ->
                    val now = clock()
                    prefs.edit().putLong(CHECKED, now).apply()
                    release?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate(now)
                },
                onFailure = { UpdateState.Failed("github sent something sonic couldn't read.") },
            )
        }
        _state.value = if (quiet && result is UpdateState.Failed) before else result
    }

    companion object {
        private const val CHECKED = "checked_at"
        private const val AUTO = "auto"
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** GitHub's latest release, when it's a newer stable build than [installed]; else null. */
        fun latest(json: String, installed: String): Release? {
            val current = Version.parse(installed) ?: return null
            val r = JSONObject(json)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) return null
            val version = Version.parse(r.optString("tag_name")) ?: return null
            if (version <= current) return null
            return Release(
                version = version,
                name = r.optString("name").ifBlank { "Sonic $version" },
                page = r.optString("html_url").ifBlank { RELEASES_PAGE },
            )
        }
    }
}
