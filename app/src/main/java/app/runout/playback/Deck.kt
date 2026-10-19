package app.runout.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.runout.model.Album
import app.runout.library.FlacPolicy
import app.runout.model.Side
import app.runout.model.SideSplitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.File

enum class Speed(val factor: Float, val label: String) {
    RPM33(1f, "33"),
    RPM45(45f / (100f / 3f), "45"),
}

data class DeckState(
    val album: Album? = null,
    val sides: List<Side> = emptyList(),
    val sideIndex: Int = 0,
    val needleDown: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val sideFinished: Boolean = false,
    val speed: Speed = Speed.RPM33,
    val held: Boolean = false,
    /** The pitch fader, in percent (−8 … +8). */
    val pitch: Float = 0f,
) {
    val side: Side? get() = sides.getOrNull(sideIndex)

    /** Which track on this side the needle is in. */
    val trackIndex: Int
        get() {
            val s = side ?: return 0
            var acc = 0L
            s.tracks.forEachIndexed { i, t ->
                if (positionMs < acc + t.durationMs) return i
                acc += t.durationMs
            }
            return s.tracks.lastIndex.coerceAtLeast(0)
        }

    val currentTrack: app.runout.model.Track? get() = side?.tracks?.getOrNull(trackIndex)

    /** Where the needle is inside the current track. */
    val trackPositionMs: Long get() = side?.let { positionMs - it.positionOf(trackIndex, 0) }?.coerceAtLeast(0) ?: 0

    /** The track's place on the record, as printed on vinyl labels: A1, A2 … B1. */
    val trackCode: String get() = side?.let { "${it.letter}${trackIndex + 1}" } ?: ""

    /** How far the needle is through the current side, 0..1. Drives the tonearm. */
    val fraction: Float
        get() {
            val d = side?.durationMs ?: return 0f
            return if (d <= 0) 0f else (positionMs.toFloat() / d).coerceIn(0f, 1f)
        }

    val currentTrackTitle: String? get() = currentTrack?.title
}

/**
 * The turntable's mechanics. Owns the side that's on the platter and translates turntable
 * gestures (needle drop, lift, hold the platter, flip, 33/45) into player commands.
 */
class Deck(
    private val context: Context,
    private val sfx: VinylSfx,
    private val scope: CoroutineScope,
    private val sound: app.runout.sound.SoundEngine? = null,
    /** How the deck reaches a player: the playback service by default; tests hand it one directly. */
    private val connectPlayer: (onReady: (Player?) -> Unit) -> Unit = { ready -> connectToService(context, ready) },
) {
    /** Pure mode turns off everything that alters the sound: crackle, 45 rpm and the platter wind-down. */
    private val pure: Boolean get() = sound?.settings?.value?.pure == true
    private val _state = MutableStateFlow(DeckState())
    val state: StateFlow<DeckState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("deck", Context.MODE_PRIVATE)
    private val _crackleOn = MutableStateFlow(prefs.getBoolean("crackle", true))
    val crackleOn: StateFlow<Boolean> = _crackleOn.asStateFlow()

    private var controller: Player? = null
    private val pending = mutableListOf<(Player) -> Unit>()
    private var rampJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying && _state.value.needleDown && !pure) sfx.startCrackle() else if (!isPlaying) sfx.stopCrackle()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && _state.value.needleDown) {
                sfx.stopCrackle()
                sfx.runout()
                _state.update { it.copy(needleDown = false, isPlaying = false, sideFinished = true, positionMs = it.side?.durationMs ?: 0) }
            }
        }
    }

    private var connecting = false

    /**
     * Starts the playback service the first time a record goes on the platter, not at app launch:
     * an app that's only being browsed doesn't hold a media session or a service.
     */
    private fun connect() {
        if (connecting || controller != null) return
        connecting = true
        connectPlayer { c ->
            connecting = false
            if (c == null) return@connectPlayer
            controller = c
            c.addListener(listener)
            pending.toList().forEach { it(c) }
            pending.clear()
        }
    }

    init {
        sfx.crackleEnabled = _crackleOn.value

        scope.launch {
            while (isActive) {
                val c = controller
                val s = _state.value
                if (c != null && s.needleDown && c.isPlaying) {
                    val side = s.side
                    if (side != null) {
                        val pos = side.positionOf(c.currentMediaItemIndex, c.currentPosition)
                        _state.update { it.copy(positionMs = pos) }
                    }
                }
                delay(200)
            }
        }
    }

    private fun withController(block: (Player) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pending += block
            connect()
        }
    }

    /** A record has been dropped on the platter. Nothing plays until the needle goes down. */
    fun load(album: Album) {
        val verified = FlacPolicy.filterAlbum(context, album, refreshMetadata = false) ?: return
        val sides = SideSplitter.split(verified.tracks)
        sfx.stopCrackle()
        _state.value = DeckState(album = verified, sides = sides, speed = _state.value.speed)
        cueSide(verified, sides.firstOrNull())
    }

    private fun cueSide(album: Album, side: Side?) {
        withController { c ->
            c.pause()
            if (side == null) {
                c.clearMediaItems()
                return@withController
            }
            c.setMediaItems(mediaItemsFor(album, side))
            val f = effectiveSpeed()
            c.playbackParameters = PlaybackParameters(f, f)
            c.prepare()
        }
    }

    fun needleDrop(fraction: Float) {
        val side = _state.value.side ?: return
        val (index, offset) = side.locate(fraction)
        dropAt(index, offset)
    }

    /** Lowers the needle at an exact place: a track and a time inside it. */
    private fun dropAt(index: Int, offset: Long) {
        val side = _state.value.side ?: return
        if (!pure) sfx.needleDrop()
        _state.update { it.copy(needleDown = true, sideFinished = false, positionMs = side.positionOf(index, offset), held = false) }
        rampJob?.cancel()
        rampJob = scope.launch {
            withController { c ->
                c.volume = 0f
                c.seekTo(index, offset)
                val f = effectiveSpeed()
                c.playbackParameters = PlaybackParameters(f, f)
            }
            delay(420) // the needle settles into the groove before the music starts
            withController { c -> c.play() }
            for (step in 1..8) {
                delay(25)
                withController { c -> c.volume = step / 8f }
            }
        }
    }

    fun liftNeedle() {
        if (!_state.value.needleDown) return
        rampJob?.cancel()
        sfx.lift()
        sfx.stopCrackle()
        withController { c -> c.pause() }
        _state.update { it.copy(needleDown = false, held = false) }
    }

    /** A finger on the platter: the music winds down like a real turntable. */
    fun holdPlatter() {
        val s = _state.value
        if (!s.needleDown || s.held) return
        _state.update { it.copy(held = true) }
        rampJob?.cancel()
        rampJob = scope.launch {
            if (!pure) {
                val start = effectiveSpeed()
                for (step in 1..10) {
                    val f = (start * (1f - step / 10f)).coerceAtLeast(MIN_SPEED)
                    withController { c -> c.playbackParameters = PlaybackParameters(f, f) }
                    delay(45)
                }
            }
            withController { c -> c.pause() }
        }
    }

    fun releasePlatter() {
        val s = _state.value
        if (!s.held) return
        _state.update { it.copy(held = false) }
        if (!s.needleDown) return
        rampJob?.cancel()
        rampJob = scope.launch {
            val end = effectiveSpeed()
            if (pure) {
                withController { c -> c.play() }
                return@launch
            }
            val startF = (end * 0.25f).coerceAtLeast(MIN_SPEED)
            withController { c ->
                c.playbackParameters = PlaybackParameters(startF, startF)
                c.play()
            }
            for (step in 1..12) {
                val f = (startF + (end - startF) * step / 12f)
                withController { c -> c.playbackParameters = PlaybackParameters(f, f) }
                delay(50)
            }
        }
    }

    /**
     * The big start/stop button. With the needle up it lowers it where the side left off; with the
     * needle down it stops the platter (the music winds down) or starts it again.
     */
    fun startStop() {
        val s = _state.value
        if (s.album == null) return
        when {
            !s.needleDown -> needleDrop(if (s.sideFinished) 0f else s.fraction)
            s.held -> releasePlatter()
            else -> holdPlatter()
        }
    }

    /** Next track. At the end of a side the record is turned over to the next one. */
    fun nextTrack() {
        val s = _state.value
        val side = s.side ?: return
        if (s.trackIndex < side.tracks.lastIndex) jumpTo(s.sideIndex, s.trackIndex + 1)
        else jumpTo((s.sideIndex + 1) % s.sides.size, 0)
    }

    /** Back to the start of this track, or to the one before if it has only just begun. */
    fun previousTrack() {
        val s = _state.value
        if (s.side == null) return
        when {
            s.needleDown && s.trackPositionMs > RESTART_WINDOW_MS -> jumpTo(s.sideIndex, s.trackIndex)
            s.trackIndex > 0 -> jumpTo(s.sideIndex, s.trackIndex - 1)
            s.sideIndex > 0 -> jumpTo(s.sideIndex - 1, s.sides[s.sideIndex - 1].tracks.lastIndex)
            else -> jumpTo(0, 0)
        }
    }

    private fun jumpTo(sideIndex: Int, trackIndex: Int) {
        val s = _state.value
        val album = s.album ?: return
        if (sideIndex != s.sideIndex) {
            // Another side: lift, turn the record over, then drop at the track.
            rampJob?.cancel()
            if (s.needleDown) sfx.lift()
            sfx.stopCrackle()
            _state.update { it.copy(sideIndex = sideIndex, sideFinished = false, positionMs = 0, needleDown = false, held = false) }
            cueSide(album, s.sides[sideIndex])
        }
        dropAt(trackIndex, 0)
    }

    /** The pitch fader: ±8 %, like a DJ deck. It clicks into place at zero. Off in Pure mode. */
    fun setPitch(percent: Float) {
        if (pure) return
        val p = percent.coerceIn(-MAX_PITCH, MAX_PITCH).let { if (kotlin.math.abs(it) < 0.25f) 0f else (it * 10).roundToInt() / 10f }
        if (p == _state.value.pitch) return
        _state.update { it.copy(pitch = p) }
        val s = _state.value
        if (s.album != null && !s.held) {
            val f = effectiveSpeed()
            withController { c -> c.playbackParameters = PlaybackParameters(f, f) }
        }
    }

    fun toggleSpeed() {
        if (pure) return
        val next = if (_state.value.speed == Speed.RPM33) Speed.RPM45 else Speed.RPM33
        _state.update { it.copy(speed = next) }
        if (_state.value.album != null) {
            val f = effectiveSpeed()
            withController { c -> c.playbackParameters = PlaybackParameters(f, f) }
        }
    }

    /** Pure mode always plays at the speed the music was recorded at. */
    fun effectiveSpeed(): Float = if (pure) 1f else _state.value.speed.factor * (1f + _state.value.pitch / 100f)

    /** Called when Pure mode is switched, so the deck drops crackle and speed changes straight away. */
    fun onPureChanged(on: Boolean) {
        if (on) {
            sfx.stopCrackle()
            _state.update { it.copy(speed = Speed.RPM33, pitch = 0f) }
            withController { c -> c.playbackParameters = PlaybackParameters(1f, 1f) }
        } else if (_state.value.isPlaying && _crackleOn.value) {
            sfx.startCrackle()
        }
    }

    /** Turn the record over. Sides run A, B, then C, D for double albums. */
    fun flip() {
        val s = _state.value
        val album = s.album ?: return
        if (s.sides.size < 2) {
            _state.update { it.copy(sideFinished = false, positionMs = 0) }
            cueSide(album, s.side)
            return
        }
        liftNeedle()
        val next = (s.sideIndex + 1) % s.sides.size
        _state.update { it.copy(sideIndex = next, sideFinished = false, positionMs = 0, needleDown = false) }
        cueSide(album, s.sides[next])
    }

    fun eject() {
        liftNeedle()
        withController { c -> c.stop(); c.clearMediaItems() }
        _state.value = DeckState(speed = _state.value.speed)
    }

    fun toggleCrackle() {
        val on = !_crackleOn.value
        _crackleOn.value = on
        prefs.edit().putBoolean("crackle", on).apply()
        sfx.crackleEnabled = on
        if (on && _state.value.isPlaying && !pure) sfx.startCrackle()
    }

    companion object {
        /** Binds to [PlaybackService] through a MediaController. */
        fun connectToService(context: Context, ready: (Player?) -> Unit) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({ ready(runCatching { future.get() }.getOrNull()) }, ContextCompat.getMainExecutor(context))
        }

        /** Android's playback-speed control doesn't go much lower without distorting. */
        const val MIN_SPEED = 0.25f
        const val MAX_PITCH = 8f
        /** Pressing "previous" this far into a track restarts it instead. */
        const val RESTART_WINDOW_MS = 3_000L

        fun mediaItemsFor(album: Album, side: Side): List<MediaItem> = side.tracks.map { t ->
            require(FlacPolicy.isFlacName(t.fileName.ifBlank { Uri.parse(t.uri).lastPathSegment.orEmpty() })) { "Only FLAC tracks can be played" }
            val extras = android.os.Bundle().apply {
                album.albumGainDb?.let { putFloat(PlaybackService.EXTRA_ALBUM_GAIN, it) }
                t.trackGainDb?.let { putFloat(PlaybackService.EXTRA_TRACK_GAIN, it) }
            }
            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(Uri.parse(t.uri))
                .setMimeType("audio/flac")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(album.artist)
                        .setAlbumTitle("${album.title} · Side ${side.letter}")
                        .setTrackNumber(t.trackNumber)
                        .setArtworkUri(album.coverPath?.let { Uri.fromFile(File(it)) })
                        .setExtras(extras)
                        .build(),
                )
                .build()
        }
    }
}
