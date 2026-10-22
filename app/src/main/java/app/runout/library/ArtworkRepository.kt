package app.runout.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max

/**
 * Finds and caches album covers, in this order:
 * 1. the picture embedded in the audio files,
 * 2. an image in the album folder (cover.jpg, folder.jpg, ...),
 * 3. MusicBrainz + the Cover Art Archive, looked up by artist and album title.
 *
 * MusicBrainz asks for at most one request a second and an identifying User-Agent, so lookups are
 * serialised through a mutex with a gap between them.
 */
class ArtworkRepository(
    private val context: Context,
    private val http: app.runout.net.Http? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val dir = File(context.filesDir, "covers").apply { mkdirs() }
    private val mbMutex = Mutex()
    private var lastMbRequest = 0L

    fun cachedFile(albumId: String): File? = File(dir, "$albumId.jpg").takeIf { it.exists() && it.length() > 0 }

    suspend fun saveBytes(albumId: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        val bmp = decodeScaled(bytes) ?: return@withContext null
        write(albumId, bmp)
    }

    suspend fun copyFromUri(albumId: String, uri: Uri): File? = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext null
        val bmp = decodeScaled(bytes) ?: return@withContext null
        write(albumId, bmp)
    }

    private val misses = context.getSharedPreferences("cover-misses", Context.MODE_PRIVATE)

    /** Cache failed lookups for a week, keyed by metadata rather than a reusable record id. */
    suspend fun lookupOnline(albumId: String, artist: String, title: String): File? = withContext(Dispatchers.IO) {
        cachedFile(albumId)?.let { return@withContext it }
        if (artist.isBlank() || artist == "Unknown artist" || title.isBlank()) return@withContext null
        val key = LibraryScanner.stableId("$artist\u0000$title")
        mbMutex.withLock {
            val timestamp = now()
            val expiry = misses.getLong(key, 0L)
            if (expiry > timestamp && expiry - timestamp <= MISS_TTL_MS) return@withLock null
            val wait = MB_GAP_MS - (System.currentTimeMillis() - lastMbRequest)
            if (wait > 0) delay(wait)
            lastMbRequest = System.currentTimeMillis()
            val groupId = findReleaseGroup(artist, title)
            val file = groupId?.let { httpGet("https://coverartarchive.org/release-group/$it/front-500") }
                ?.let { decodeScaled(it) }?.let { write(albumId, it) }
            misses.edit {
                if (file == null) putLong(key, timestamp + MISS_TTL_MS) else remove(key)
            }
            file
        }
    }

    private fun findReleaseGroup(artist: String, title: String): String? {
        fun clean(s: String) = s.replace("\"", " ").replace("\\", " ").trim()
        val query = "releasegroup:\"${clean(title)}\" AND artist:\"${clean(artist)}\""
        val url = "https://musicbrainz.org/ws/2/release-group/?query=" +
            URLEncoder.encode(query, "UTF-8") + "&fmt=json&limit=3"
        val body = httpGet(url)?.toString(Charsets.UTF_8) ?: return null
        val groups = runCatching { JSONObject(body).getJSONArray("release-groups") }.getOrNull() ?: return null
        for (i in 0 until groups.length()) {
            val g = groups.getJSONObject(i)
            if (g.optInt("score", 0) >= MIN_SCORE) return g.optString("id").takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun httpGet(url: String): ByteArray? {
        if (http != null) return http.get(url)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, image/*")
            }
            // Cover Art Archive redirects to archive.org; follow manually if the protocol changes.
            var code = conn.responseCode
            var hops = 0
            while (code in 300..399 && hops < 5) {
                val next = conn!!.getHeaderField("Location") ?: break
                conn.disconnect()
                conn = (URL(URL(url), next).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                code = conn.responseCode
                hops++
            }
            if (code != 200) null else conn!!.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun decodeScaled(bytes: ByteArray, maxPx: Int = 1000): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun write(albumId: String, bmp: Bitmap): File? = runCatching {
        val file = File(dir, "$albumId.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        file
    }.getOrNull()

    companion object {
        val USER_AGENT = app.runout.net.DefaultHttp.USER_AGENT
        private const val MB_GAP_MS = 1100L
        private const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val MIN_SCORE = 90

        /** Average colour of a cover, nudged to be usable as a vinyl colour. */
        fun averageColor(file: File): Int? = runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            val small = Bitmap.createScaledBitmap(bmp, 8, 8, true)
            var r = 0L; var g = 0L; var b = 0L
            for (x in 0 until 8) for (y in 0 until 8) {
                val c = small.getPixel(x, y)
                r += Color.red(c); g += Color.green(c); b += Color.blue(c)
            }
            val hsv = FloatArray(3)
            Color.RGBToHSV((r / 64).toInt(), (g / 64).toInt(), (b / 64).toInt(), hsv)
            hsv[1] = (hsv[1] * 1.35f).coerceIn(0.25f, 0.9f)
            hsv[2] = hsv[2].coerceIn(0.35f, 0.85f)
            Color.HSVToColor(hsv)
        }.getOrNull()
    }
}
