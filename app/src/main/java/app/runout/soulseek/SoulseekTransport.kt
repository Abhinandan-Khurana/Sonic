package app.runout.soulseek

/** Injectable wire boundary; repository tests never need a public Soulseek account. */
interface SoulseekTransport : AutoCloseable {
    fun command(request: String): String
    fun poll(): String
}

internal class NativeTransport(open: (String) -> Int) : SoulseekTransport {
    private val native = NativeSoulseek(open)
    override fun command(request: String) = native.command(request)
    override fun poll() = native.poll()
    override fun close() = native.close()
}
