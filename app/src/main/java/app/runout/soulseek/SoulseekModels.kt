package app.runout.soulseek

import java.security.MessageDigest

enum class SoulseekConnection { OFFLINE, CONNECTING, RECONNECTING, CONNECTED, PAUSED, ERROR }
enum class SoulseekDisconnectReason(val label: String) {
    NETWORK_LOST("Connection lost. Reconnecting…"), AUTH_REJECTED("Username or password rejected"),
    DISPLACED("This Soulseek ID connected in another client. Reconnect when you are ready."),
    MANUAL("Disconnected by you"), BACKGROUND_IDLE("Paused while the app is in the background"),
    SERVICE_TIMEOUT("Android stopped background activity. Open Runout to resume."), PROTOCOL_ERROR("Connection interrupted")
}
data class SoulseekSession(
    val state: SoulseekConnection = SoulseekConnection.OFFLINE,
    val username: String? = null,
    val error: String? = null,
    val authenticated: Boolean = state == SoulseekConnection.CONNECTED,
    val reason: SoulseekDisconnectReason? = null,
) {
    val connected: Boolean get() = state == SoulseekConnection.CONNECTED && !username.isNullOrBlank()
    val signedIn: Boolean get() = connected // Retain the strict transfer/authentication gate.
    val hasAccount: Boolean get() = authenticated && !username.isNullOrBlank()
}
enum class SearchPhase { IDLE, SEARCHING, COMPLETE, CANCELLED, FAILED, INTERRUPTED }
data class SoulseekSearchStatus(val id: String? = null, val query: String = "", val phase: SearchPhase = SearchPhase.IDLE, val limited: Boolean = false, val error: String? = null)
data class SoulseekFolderPreview(val requestId: String, val username: String, val directory: String, val files: List<SoulseekSearchResult> = emptyList(), val loading: Boolean = true, val error: String? = null)

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
    val queueLength: Long? = null,
) {
    private val normalizedPath = path.replace('\\', '/')
    val fileName: String = normalizedPath.substringAfterLast('/')
    val directory: String = normalizedPath.substringBeforeLast('/', "")
    val id: String = SoulseekPolicy.remoteId(username, path, size)
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
    /** The native downloader's read was cut by a signal (EINTR), as Android does to paused apps. */
    fun isInterruption(error: String?): Boolean =
        error != null && (error.contains("Interrupted system call", ignoreCase = true) || error.contains("os error 4)"))
    fun remoteId(username: String, path: String, size: Long): String = digest("$username\u0000$path\u0000$size")
    fun accountKey(owner: String, key: String): String = digest("$owner\u0000$key")
    fun albumId(username: String, directory: String): String = "soulseek-" + digest("$username\u0000$directory")
    fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        return CharArray(bytes.size * 2).also { hex ->
            bytes.forEachIndexed { index, byte ->
                val unsigned = byte.toInt() and 0xff
                hex[index * 2] = digits[unsigned ushr 4]
                hex[index * 2 + 1] = digits[unsigned and 0xf]
            }
        }.concatToString()
    }
    fun validUsername(value: String): Boolean = value.isNotBlank() && value.length <= 30 && value == value.trim() && value.all { it.code in 32..126 }
    fun safeName(value: String, fallback: String = "Music"): String = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').take(120).ifBlank { fallback }
    fun messageId(owner: String, serverId: Long): String = accountKey(owner, "incoming:$serverId")
}
