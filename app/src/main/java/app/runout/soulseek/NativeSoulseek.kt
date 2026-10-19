package app.runout.soulseek

import androidx.annotation.Keep

/** Owns one native session. Commands and events use the contract in native/README.md. */
@Keep
class NativeSoulseek(
    private val sharedFileOpener: (String) -> Int = { -1 },
) : AutoCloseable {
    companion object {
        private val loading = runCatching { System.loadLibrary("runout_soulseek") }
        val isAvailable: Boolean get() = loading.isSuccess
        val loadError: String? get() = loading.exceptionOrNull()?.message
    }

    @Volatile private var handle = if (isAvailable) nativeCreate() else 0L

    fun command(request: String): String {
        val current = handle
        return if (current == 0L) {
            """{"ok":false,"error":"Soulseek is unavailable in this build"}"""
        } else nativeCommand(current, request)
    }

    /** Returns a JSON array; receiving a DM does not acknowledge it. */
    fun poll(): String = handle.takeIf { it != 0L }?.let(::nativePoll) ?: "[]"

    /** Called from native upload threads. The caller transfers descriptor ownership. */
    @Keep
    fun openSharedFile(key: String): Int = runCatching { sharedFileOpener(key) }.getOrDefault(-1)

    @Synchronized
    override fun close() {
        val previous = handle
        handle = 0L
        if (previous != 0L) nativeClose(previous)
    }

    private external fun nativeCreate(): Long
    private external fun nativeCommand(handle: Long, request: String): String
    private external fun nativePoll(handle: Long): String
    private external fun nativeClose(handle: Long)
}
