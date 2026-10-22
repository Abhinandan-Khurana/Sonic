package app.runout.ui.deck

import android.content.Context
import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.io.File

/**
 * Debug builds only: renders the intro's four deck pictures from the room's live deck, so the
 * intro and the 3D deck it hands over to are the same model, camera and size. Start it with
 *
 *     adb shell am start -n app.sonic.dev/app.runout.MainActivity --ez capture_intro_deck true
 *
 * on an empty room in the three-quarter view, with the deck to capture chosen in Settings › Flags.
 * The PNGs land in the app's external files, under deck-capture/: deck-empty, deck-loaded (the
 * record down, the arm left out), deck-playing (the needle in the lead-in) and deck-arm (the arm
 * alone at rest, the whole view; the intro crops it).
 */
object DeckCapture {
    /** Set by MainActivity from the intent; the room's turntable runs it once its 3D deck is up. */
    var requested by mutableStateOf(false)

    suspend fun run(context: Context, renderer: DeckRenderer, view: TextureView, face: Bitmap, camera: DeckCamera?) = try {
        camera?.let(::logGeometry)
        capture(context, renderer, view, face)
    } finally {
        // Last: the turntable runs this while [requested] holds, and clearing it ends the run.
        requested = false
    }

    private suspend fun capture(context: Context, renderer: DeckRenderer, view: TextureView, face: Bitmap) {
        val out = File(context.getExternalFilesDir(null), "deck-capture").apply { mkdirs() }
        suspend fun shoot(name: String) {
            // A few frames for the renderer to settle and the view to show its newest picture.
            delay(900)
            val shot = view.getBitmap() ?: return
            File(out, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            android.util.Log.i("DeckCapture", "${out.path}/$name.png ${shot.width}x${shot.height}")
        }
        renderer.setGroundShadow(false)
        renderer.setRecord(null)
        renderer.setArm(DeckModel.angleRest, 0f)
        renderer.setStatusLamp(false)
        renderer.setScreen("DROP A RECORD", null, null)
        shoot("deck-empty")

        renderer.setRecord(face)
        renderer.setStatusLamp(true)
        renderer.setScreen("OPENING", "A1", "00:28")
        renderer.showPart(DeckModel.TONEARM, false)
        shoot("deck-loaded")

        renderer.showPart(DeckModel.TONEARM, true)
        renderer.setArm(DeckModel.angleForFraction(0f), 1f)
        renderer.setScreen("LOWLIGHT", "A2", "01:12")
        shoot("deck-playing")

        renderer.setRecord(null)
        renderer.setArm(DeckModel.angleRest, 0f)
        renderer.showPart(DeckModel.TONEARM, true, only = true)
        shoot("deck-arm")

        renderer.showPart(null, true)
        renderer.setStatusLamp(false)
        android.util.Log.i("DeckCapture", "done")
    }

    /**
     * Where the intro's moving parts sit in these pictures, in its 2064-wide frame: the arm's pivot,
     * the stylus at rest and in the lead-in, and the record's centre and size on the platter.
     */
    private fun logGeometry(cam: DeckCamera) {
        val k = 2064f / cam.width
        fun p(v: V3) = cam.project(v).let { "%.1f, %.1f".format(it.x * k, it.y * k) }
        val c = DeckModel.platterCentre
        val r = DeckModel.RECORD_R
        android.util.Log.i("DeckCapture", "pivot " + p(V3(DeckModel.pivot.x, 0.84f, DeckModel.pivot.z)))
        android.util.Log.i("DeckCapture", "rest " + p(DeckModel.stylus(DeckModel.angleRest).let { V3(it.x, 0.70f, it.z) }))
        android.util.Log.i("DeckCapture", "play " + p(DeckModel.stylus(DeckModel.angleForFraction(0f)).let { V3(it.x, DeckModel.RECORD_Y, it.z) }))
        android.util.Log.i("DeckCapture", "record " + p(V3(c.x, DeckModel.RECORD_Y, c.z)) + " | " +
            p(V3(c.x - r, DeckModel.RECORD_Y, c.z)) + " | " + p(V3(c.x + r, DeckModel.RECORD_Y, c.z)) + " | " +
            p(V3(c.x, DeckModel.RECORD_Y, c.z - r)) + " | " + p(V3(c.x, DeckModel.RECORD_Y, c.z + r)))
    }
}
