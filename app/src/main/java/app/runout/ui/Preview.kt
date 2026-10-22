package app.runout.ui

/** Where a drop's short listen is: still fetching the start of the track, or playing it. */
enum class PreviewPhase { BUFFERING, PLAYING }

/**
 * The drop listen as the player reports it, so the track row shows what is really happening:
 * null once it stops for any reason (done, error, timeout, focus lost, headphones out).
 */
data class PreviewState(val url: String, val phase: PreviewPhase)
