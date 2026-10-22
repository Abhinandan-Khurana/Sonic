package app.runout.net

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** The little HTTP the app needs. An interface so tests can serve recorded responses. */
interface Http {
    fun get(url: String): ByteArray?
    fun download(url: String, to: File, onBytes: (Long) -> Unit = {}): Boolean
}

object DefaultHttp : Http {
    const val USER_AGENT = "Sonic/0.7.0 ( https://github.com/Abhiroop1122/runout )"

    private fun open(url: String, rangeFrom: Long = 0L): HttpURLConnection? {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                if (rangeFrom > 0) setRequestProperty("Range", "bytes=$rangeFrom-")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location") ?: return null
                conn.disconnect()
                current = URL(URL(current), next).toString()
            } else {
                return if (code == 200 || code == 206) conn else { conn.disconnect(); null }
            }
        }
        return null
    }

    override fun get(url: String): ByteArray? = runCatching {
        val c = open(url) ?: return null
        try { if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null } finally { c.disconnect() }
    }.getOrNull()

    override fun download(url: String, to: File, onBytes: (Long) -> Unit): Boolean = runCatching {
        to.parentFile?.mkdirs()
        val tmp = File(to.parentFile, to.name + ".part")
        val existing = tmp.takeIf { it.isFile }?.length() ?: 0L
        val c = open(url, existing) ?: return false
        try {
            val code = c.responseCode
            val append = code == 206 && existing > 0
            if (!append && tmp.exists()) tmp.delete()
            val start = if (append) existing else 0L
            c.inputStream.use { input ->
                FileOutputStream(tmp, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = start
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        onBytes(total)
                    }
                }
            }
            if (tmp.length() <= start && start > 0) return false
            if (to.exists()) to.delete()
            tmp.renameTo(to)
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)
}
