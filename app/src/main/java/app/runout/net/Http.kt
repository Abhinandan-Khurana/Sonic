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
    const val USER_AGENT = "Runout/0.4.0 ( https://github.com/Somchandra17/runout )"

    private fun open(url: String): HttpURLConnection? {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location") ?: return null
                conn.disconnect()
                current = URL(URL(current), next).toString()
            } else {
                return if (code == 200) conn else { conn.disconnect(); null }
            }
        }
        return null
    }

    override fun get(url: String): ByteArray? = runCatching {
        val c = open(url) ?: return null
        try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
    }.getOrNull()

    override fun download(url: String, to: File, onBytes: (Long) -> Unit): Boolean = runCatching {
        val c = open(url) ?: return false
        try {
            to.parentFile?.mkdirs()
            val tmp = File(to.parentFile, to.name + ".part")
            c.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        onBytes(total)
                    }
                }
            }
            tmp.renameTo(to)
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)
}
