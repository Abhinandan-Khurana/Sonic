package app.runout.soulseek

import java.security.MessageDigest

/** Offline is an intentional, fully usable local playback state. */
enum class SoulseekConnection { OFFLINE, CONNECTING, CONNECTED, ERROR }
data class SoulseekSession(val state: SoulseekConnection = SoulseekConnection.OFFLINE, val username: String? = null, val error: String? = null) {
    val signedIn: Boolean get() = state == SoulseekConnection.CONNECTED && !username.isNullOrBlank()
}

data class SoulseekSearchResult(
    val username: String,
    val path: String,
    val size: Long,
    val durationSec: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val bitrate: Int? = null,
    val freeSlots: Int = 0,
    val uploadSpeed: Long = 0,
) {
    val fileName: String get() = path.replace('\\', '/').substringAfterLast('/')
    val directory: String get() = path.replace('\\', '/').substringBeforeLast('/', "")
    val id: String get() = SoulseekPolicy.remoteId(username, path, size)
}

enum class SoulseekTransferState { QUEUED, DOWNLOADING, PAUSED, VALIDATING, PUBLISHING, COMPLETED, CANCELLED, FAILED }
data class SoulseekTransfer(
    val id: String,
    val username: String,
    val path: String,
    val size: Long,
    val bytes: Long = 0,
    val state: SoulseekTransferState = SoulseekTransferState.QUEUED,
    val queuePosition: Long? = null,
    val speed: Double = 0.0,
    val error: String? = null,
    val provider: String = "Soulseek",
) {
    val fileName: String get() = path.replace('\\', '/').substringAfterLast('/')
    val progress: Float get() = if (size <= 0) 0f else (bytes.toDouble() / size).toFloat().coerceIn(0f, 1f)
}

data class SoulseekContact(val username: String, val online: Boolean = false)
data class SoulseekUpload(val username: String, val path: String, val bytes: Long, val size: Long, val state: String) {
    val fileName: String get() = path.replace('\\', '/').substringAfterLast('/')
    val progress: Float get() = if (size > 0) (bytes.toDouble() / size).toFloat().coerceIn(0f, 1f) else 0f
}
enum class SoulseekMessageState { PENDING, SENT, FAILED, RECEIVED }
data class SoulseekMessage(val id: String, val username: String, val text: String, val timestamp: Long, val outgoing: Boolean, val state: SoulseekMessageState, val read: Boolean = false)
data class SoulseekConversation(val username: String, val messages: List<SoulseekMessage>, val unread: Int)

/** Stable identities include the complete remote path, expected size and exact peer name. */
object SoulseekPolicy {
    fun remoteId(username: String, path: String, size: Long): String = digest("$username\u0000$path\u0000$size")
    fun accountKey(owner: String, key: String): String = digest("$owner\u0000$key")
    fun albumId(username: String, directory: String): String = "soulseek-" + digest("$username\u0000$directory")
    fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun validUsername(value: String): Boolean = value.isNotBlank() && value.length <= 30 && value == value.trim() && value.all { it.code in 32..126 }
    fun safeName(value: String, fallback: String = "Music"): String = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').take(120).ifBlank { fallback }
    fun messageId(owner: String, serverId: Long): String = accountKey(owner, "incoming:$serverId")
}
