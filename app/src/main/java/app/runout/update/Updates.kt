package app.runout.update

import android.content.Context
import app.runout.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Where Sonic's builds are published: the project's GitHub releases. */
const val UPDATE_REPO = "Abhiroop1122/runout"

/**
 * A version as the releases tag it: "v0.8.0", "v0.8.0-dev.2". A development build comes before
 * its release ("0.8.0-dev.2" < "0.8.0"), and later development builds after earlier ones.
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

/** A published build newer than the one installed. */
data class Release(
    val version: Version,
    val name: String,
    val development: Boolean,
    /** The release's first lines, as its notes start. */
    val notes: String,
    val page: String,
    /** The APK to download, when the release carries one. */
    val apk: String?,
    val apkBytes: Long?,
)

/** Settings › Flags › updates. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val at: Long) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * Checks the project's GitHub releases on request (no background polling, no account). The newest
 * non-draft release, development builds included since that's how Sonic ships for now, is offered
 * when it's newer than [installed]. Android only installs an update signed with the same key as
 * the app on the phone, so a download from anywhere else can't replace it.
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

    suspend fun check() {
        if (_state.value == UpdateState.Checking) return
        _state.value = UpdateState.Checking
        val body = withContext(Dispatchers.IO) { http.get("https://api.github.com/repos/$UPDATE_REPO/releases?per_page=30") }
        _state.value = when {
            body == null -> UpdateState.Failed("couldn't reach github. check your connection and try again.")
            else -> runCatching { newest(String(body), installed) }.fold(
                onSuccess = { release ->
                    release?.let { UpdateState.Available(it) } ?: clock().also { prefs.edit().putLong(CHECKED, it).apply() }.let { UpdateState.UpToDate(it) }
                },
                onFailure = { UpdateState.Failed("github sent something sonic couldn't read.") },
            )
        }
    }

    companion object {
        private const val CHECKED = "checked_at"

        /** The newest release in GitHub's list that's newer than [installed], or null. */
        fun newest(json: String, installed: String): Release? {
            val current = Version.parse(installed) ?: return null
            val list = JSONArray(json)
            return (0 until list.length()).map { list.getJSONObject(it) }
                .filter { !it.optBoolean("draft") }
                .mapNotNull { r -> Version.parse(r.optString("tag_name"))?.let { it to r } }
                .filter { (v, _) -> v > current }
                .maxByOrNull { (v, _) -> v }
                ?.let { (v, r) ->
                    val assets = r.optJSONArray("assets") ?: JSONArray()
                    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    Release(
                        version = v,
                        name = r.optString("name").ifBlank { "Sonic $v" },
                        development = r.optBoolean("prerelease"),
                        notes = r.optString("body").lineSequence().map { it.trim().trimStart('#', '-', '*', ' ') }
                            .filter { it.isNotEmpty() }.take(3).joinToString(" "),
                        page = r.optString("html_url"),
                        apk = apk?.optString("browser_download_url")?.takeIf { it.isNotBlank() },
                        apkBytes = apk?.optLong("size")?.takeIf { it > 0 },
                    )
                }
        }
    }
}
