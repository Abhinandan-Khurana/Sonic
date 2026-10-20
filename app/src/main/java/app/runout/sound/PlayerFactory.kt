package app.runout.sound

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** Builds the player with the sound engine wired into its audio output. */
object PlayerFactory {

    @OptIn(UnstableApi::class)
    fun create(
        context: Context,
        engine: SoundEngine,
        handleAudioFocus: Boolean = true,
        clock: Clock? = null,
        /** Replaces Android's audio output, for tests. Receives whether hi-res output is on. */
        outputSink: ((Context, Boolean) -> AudioSink)? = null,
    ): ExoPlayer {
        val hiRes = engine.settings.value.hiResOutput
        val pure = engine.settings.value.pure
        val floatOut = hiRes && !pure
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink {
                val base = outputSink?.invoke(context, floatOut) ?: DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(floatOut)
                    // Speed (platter hold, 45 rpm) via Android's AudioTrack, which also works in the float path.
                    .setEnableAudioOutputPlaybackParameters(true)
                    .build()
                return DspAudioSink(base, engine, floatOutput = floatOut)
            }
        }
        return ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .apply { if (clock != null) setClock(clock) }
            .build()
    }
}
