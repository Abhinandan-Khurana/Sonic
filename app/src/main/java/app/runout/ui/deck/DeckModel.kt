package app.runout.ui.deck

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A point or direction in the model's space: x to the right, y up, z towards the front edge. */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(dot(this))
    fun normalized() = this * (1f / length())
}

/**
 * One of the deck's keys: its glTF node, the centre of its cap and the cap's size, and its touch
 * zone on the key tray ([zoneMin]–[zoneMax] in x and z). The caps sit edge to edge, so each key
 * answers up to the middle of the gap around it.
 */
data class DeckKey(val node: String, val centre: V3, val width: Float, val depth: Float, val zoneMin: Pair<Float, Float>, val zoneMax: Pair<Float, Float>)

/**
 * The Runout RG-1 turntable (assets/deck/turntable.glb), in model units, from its spec
 * (docs/design/deck-v3-spec.json). Moving parts are separate nodes whose origins sit where they turn:
 * the platter on its spindle axis, the tonearm on its bearing, each key at the top of its cap. The
 * record (assets/deck/record.glb) is a disc whose top face is at its origin.
 *
 * The tonearm is solved with the law of cosines, so the stylus follows its true arc across the
 * grooves, as on the drawn deck it replaced. It's modelled parked on its rest.
 */
object DeckModel {
    const val PLATTER = "Turntable_Platter"
    const val TONEARM = "Tonearm_Arm"
    const val LED_STATUS = "LED_Status"
    const val LED_STRIP = "LED_Strip"
    const val SCREEN = "Screen_Display"
    const val SCREEN_TIME = "Screen_Time"

    /** Where the platter turns: its axis at the top of the felt mat. */
    val platterOrigin = V3(-1.2575f, 0.57f, 0f)
    val platterCentre = V3(platterOrigin.x, 0f, platterOrigin.z)
    const val PLATTER_R = 1.19f
    /** The record's face, lying on the mat (the disc is 0.018 thick). */
    const val RECORD_Y = 0.588f
    const val RECORD_R = 1.13f

    /** The tonearm's bearing, and the stylus as modelled: parked on the rest, needle up. */
    val pivot = V3(0.0225f, 0.75f, -0.96f)
    private val stylusAtRest = V3(0.2660619f, 0.643f, 0.8346232f)
    /** How far the arm drops to put the needle in the groove. */
    const val NEEDLE_DROP = 0.055f

    // The key tray on the right-hand module: prev | next, then START/STOP, then eject, front to back
    // reversed (z grows towards the front). Zones meet in the middle of the 0.018 gaps.
    private const val TRAY_LEFT = 0.7525f
    private const val TRAY_RIGHT = 2.3925f
    private const val TRAY_MID = 1.5725f
    val prev = DeckKey("Key_Prev", V3(1.168f, 0.522f, -0.67f), 0.791f, 0.62f, TRAY_LEFT to -1.0f, TRAY_MID to -0.351f)
    val next = DeckKey("Key_Next", V3(1.977f, 0.522f, -0.67f), 0.791f, 0.62f, TRAY_MID to -1.0f, TRAY_RIGHT to -0.351f)
    val startStop = DeckKey("Key_StartStop", V3(1.5725f, 0.522f, 0.118f), 1.6f, 0.92f, TRAY_LEFT to -0.351f, TRAY_RIGHT to 0.587f)
    val eject = DeckKey("Key_Eject", V3(1.5725f, 0.522f, 0.846f), 1.6f, 0.5f, TRAY_LEFT to 0.587f, TRAY_RIGHT to 1.116f)
    val keys = listOf(startStop, prev, next, eject)
    /** How far a key goes down when pressed: a couple of millimetres on the real thing. */
    const val KEY_TRAVEL = 0.018f

    val ledStatus = V3(0.83f, 0.479f, -1.14f)

    /**
     * The red LED windows along the front: the title display under the platter and the time display
     * under the keys, corner to corner. Both show one 1024×256 texture: the title fills its top
     * half, the time the left [TIME_U] of its bottom half.
     */
    val titleWindow = Pair(V3(-2.3775f, 0.4735f, 1.315f), V3(0.3725f, 0.4735f, 1.659f))
    val timeWindow = Pair(V3(0.7725f, 0.4735f, 1.315f), V3(2.3725f, 0.4735f, 1.659f))
    const val TIME_U = 0.5813953f

    /** What the camera frames: the deck and its soft shadow, so the shadow never gets cut off. */
    val viewMin = V3(-2.7725f, -0.002f, -1.54f)
    val viewMax = V3(2.7725f, 0.843f, 1.96f)

    // ---- Tonearm ------------------------------------------------------------------------------

    private val toCentre = platterCentre - V3(pivot.x, 0f, pivot.z)
    private val pivotToCentre = hypot(toCentre.x, toCentre.z)
    private val armLength = hypot(stylusAtRest.x - pivot.x, stylusAtRest.z - pivot.z)
    /** Arm angles are signed angles from the pivot→platter-centre line, in the x/z plane. */
    private val restAngle = signedAngle(toCentre, stylusAtRest - pivot)
    private val side = if (restAngle < 0f) -1f else 1f

    /** The arm angle that puts the stylus [r] from the platter centre. */
    fun angleForRadius(r: Float): Float {
        val c = (armLength * armLength + pivotToCentre * pivotToCentre - r * r) / (2f * armLength * pivotToCentre)
        return side * acos(c.coerceIn(-1f, 1f))
    }

    /** Lead-in at the edge of the record, run-out near the label, and the rest as modelled. */
    val angleOut = angleForRadius(RECORD_R * 0.95f)
    val angleIn = angleForRadius(RECORD_R * 0.43f)
    val angleRest = restAngle

    fun angleForFraction(f: Float) = angleOut + (angleIn - angleOut) * f.coerceIn(0f, 1f)
    fun fractionForAngle(a: Float) = ((angleOut - a) / (angleOut - angleIn)).coerceIn(0f, 1f)

    /** Where the stylus is, in the x/z plane, for an arm angle. */
    fun stylus(angle: Float): V3 {
        val u = V3(toCentre.x / pivotToCentre, 0f, toCentre.z / pivotToCentre)
        val d = rotateXZ(u, angle)
        return V3(pivot.x + d.x * armLength, 0f, pivot.z + d.z * armLength)
    }

    /** The arm angle that points the stylus at [p], for dragging the arm with a finger. */
    fun angleTowards(p: V3): Float = signedAngle(toCentre, p - pivot).coerceIn(minOf(angleRest, angleIn), maxOf(angleRest, angleIn))

    /** Whether [p] (on the record's plane) is close to the arm at [angle], within [slop] units. */
    fun nearArm(p: V3, angle: Float, slop: Float): Boolean {
        val b = stylus(angle)
        val abx = b.x - pivot.x
        val abz = b.z - pivot.z
        val t = (((p.x - pivot.x) * abx + (p.z - pivot.z) * abz) / (abx * abx + abz * abz)).coerceIn(0f, 1f)
        val cx = pivot.x + abx * t
        val cz = pivot.z + abz * t
        return hypot(p.x - cx, p.z - cz) < slop || hypot(p.x - b.x, p.z - b.z) < slop * 1.6f
    }

    fun isOnRecord(p: V3) = hypot(p.x - platterCentre.x, p.z - platterCentre.z) <= RECORD_R

    fun stylusOverRecord(angle: Float) = stylus(angle).let { hypot(it.x - platterCentre.x, it.z - platterCentre.z) } <= RECORD_R

    /** The turn about the vertical axis (radians, right-handed, as glTF uses) from rest to [angle]. */
    fun armYaw(angle: Float): Float = -(angle - restAngle)

    private fun signedAngle(a: V3, b: V3): Float = atan2(a.x * b.z - a.z * b.x, a.x * b.x + a.z * b.z)
    private fun rotateXZ(v: V3, a: Float) = V3(v.x * cos(a) - v.z * sin(a), 0f, v.x * sin(a) + v.z * cos(a))

    // ---- Node transforms (column-major 4×4, as Filament takes them) --------------------------

    /** The platter turned by [degrees] clockwise, seen from above. */
    fun platterTransform(degrees: Float): FloatArray = placeTurned(platterOrigin, -degrees * PI.toFloat() / 180f)

    /** The record lying on the mat, turning with the platter. */
    fun recordTransform(degrees: Float): FloatArray = placeTurned(V3(platterOrigin.x, RECORD_Y, platterOrigin.z), -degrees * PI.toFloat() / 180f)

    /**
     * The record turning over, [t] from 0 to 1: it lifts off the mat, rolls about the left-right
     * axis towards you and settles back, still turned [degrees] with the platter. Past halfway the
     * roll restarts from the other edge, so the face drawn on top is always the one showing: swap the
     * face to the new side at t = 0.5, when the record is edge-on.
     */
    fun flipTransform(degrees: Float, t: Float): FloatArray {
        val f = t.coerceIn(0f, 1f)
        if (f <= 0f || f >= 1f) return recordTransform(degrees)
        val roll = (if (f < 0.5f) f else f - 1f) * PI.toFloat()
        // High enough at every angle that the lower half never dips into the platter or the
        // plinth: the half-width it hangs below its axis, plus a little air.
        val lift = RECORD_R * kotlin.math.abs(sin(roll)) + FLIP_CLEAR * sin(f * PI.toFloat())
        val yaw = -degrees * PI.toFloat() / 180f
        val c = cos(yaw); val s = sin(yaw)
        val ca = cos(roll); val sa = sin(roll)
        // Rx(roll) · Ry(yaw), column-major.
        return floatArrayOf(
            c, sa * s, -ca * s, 0f,
            0f, ca, sa, 0f,
            s, -sa * c, ca * c, 0f,
            platterOrigin.x, RECORD_Y + lift, platterOrigin.z, 1f,
        )
    }

    /** The air under the record's lowest edge at the top of the flip, above the spindle. */
    const val FLIP_CLEAR = 0.12f

    /** How high the record's centre rises as it turns over, edge-on: its radius plus the air. */
    const val FLIP_LIFT = RECORD_R + FLIP_CLEAR

    /** The tonearm at [angle], lowered by [drop] (0 = cued up, 1 = needle in the groove). */
    fun armTransform(angle: Float, drop: Float): FloatArray =
        placeTurned(V3(pivot.x, pivot.y - NEEDLE_DROP * drop.coerceIn(0f, 1f), pivot.z), armYaw(angle))

    /**
     * The record in your hand, for the screen pixel [px] (in the deck view's pixels, also outside
     * it) where its centre is, drawn [radiusPx] across. Held, it hangs along the camera ray at the
     * depth that gives it that size, tilted [HELD_TILT] of the way towards you. As [landing] goes
     * from 0 to 1 it slides onto the record's plane and lies flat, so at 1, over the platter centre,
     * it is exactly the record on the platter, turned [spinDegrees] as [recordTransform] turns it.
     */
    fun heldDiscTransform(cam: DeckCamera, px: Offset, radiusPx: Float, landing: Float, spinDegrees: Float): FloatArray {
        val l = landing.coerceIn(0f, 1f)
        val w = l * l * (3f - 2f * l)
        val ray = cam.ray(px)
        val depth = RECORD_R * cam.focalPixels / radiusPx.coerceAtLeast(1f)
        val held = cam.eye + ray * (depth / ray.dot(cam.viewForward))
        val flat = cam.onPlane(px, RECORD_Y) ?: held
        val at = held + (flat - held) * w
        val up = V3(0f, 1f, 0f)
        val tilted = (up + (ray * -1f - up) * HELD_TILT).normalized()
        val normal = (tilted + (up - tilted) * w).normalized()
        return placeTilted(at, normal, -spinDegrees * PI.toFloat() / 180f)
    }

    /** How far a held record leans from lying flat towards facing you (0 flat, 1 face-on). */
    const val HELD_TILT = 0.7f

    /** Translate to [at], tilted so its up axis is [normal], after turning [yaw] about that axis. */
    private fun placeTilted(at: V3, normal: V3, yaw: Float): FloatArray {
        // Rodrigues: the rotation taking +y onto the normal (axis y×n), then the turn about +y.
        val vx = normal.z
        val vz = -normal.x
        val c = normal.y
        val k = if (c > -0.9999f) 1f / (1f + c) else 0f
        val a00 = 1f - vz * vz * k; val a01 = -vz; val a02 = vx * vz * k
        val a10 = vz; val a11 = c; val a12 = -vx
        val a20 = vx * vz * k; val a21 = vx; val a22 = 1f - vx * vx * k
        val cy = cos(yaw)
        val sy = sin(yaw)
        // M = A · Ry, with Ry = [[c, 0, s], [0, 1, 0], [-s, 0, c]] as in placeTurned.
        val m00 = a00 * cy - a02 * sy; val m01 = a01; val m02 = a00 * sy + a02 * cy
        val m10 = a10 * cy - a12 * sy; val m11 = a11; val m12 = a10 * sy + a12 * cy
        val m20 = a20 * cy - a22 * sy; val m21 = a21; val m22 = a20 * sy + a22 * cy
        return floatArrayOf(
            m00, m10, m20, 0f,
            m01, m11, m21, 0f,
            m02, m12, m22, 0f,
            at.x, at.y, at.z, 1f,
        )
    }

    /** A key, pushed down by [pressed] (0 to 1) of its travel. */
    fun keyTransform(key: DeckKey, pressed: Float): FloatArray =
        placeTurned(V3(key.centre.x, key.centre.y - KEY_TRAVEL * pressed.coerceIn(0f, 1f), key.centre.z), 0f)

    /** Translate to [at], turned [yaw] radians about the vertical axis. */
    private fun placeTurned(at: V3, yaw: Float): FloatArray {
        val c = cos(yaw)
        val s = sin(yaw)
        return floatArrayOf(
            c, 0f, -s, 0f,
            0f, 1f, 0f, 0f,
            s, 0f, c, 0f,
            at.x, at.y, at.z, 1f,
        )
    }
}

/**
 * The camera on the deck, for a viewport [width]×[height] pixels: tilted [TILT_DEG] from straight
 * down towards the front edge, framed so the deck and its shadow fit. The same numbers drive
 * Filament's camera and the touch mapping, so a finger lands where the eye sees the part.
 */
class DeckCamera(val width: Float, val height: Float) {
    private val aspect = width / height
    private val tanH = tan(Math.toRadians(FOV_H_DEG / 2.0)).toFloat()
    private val tanV = tanH / aspect
    private val back = V3(0f, cos(TILT), sin(TILT))
    var target = V3(0f, 0.32f, 0.05f)
        private set
    var eye = target + back * 10f
        private set
    private lateinit var forward: V3
    private lateinit var right: V3
    private lateinit var up: V3

    init {
        // Fit the model, then centre it vertically; a couple of passes settle both.
        repeat(3) {
            var lo = 1f
            var hi = 60f
            repeat(40) {
                val mid = (lo + hi) / 2f
                place(mid)
                if (fits()) hi = mid else lo = mid
            }
            place(hi)
            val ys = corners().map { ndc(it).second }
            val shift = (ys.max() + ys.min()) / 2f
            target += up * (shift * tanV * (target - eye).length())
        }
        place((target - eye).length())
    }

    val near = 0.1f
    val far = 100f
    val fovHorizontalDegrees = FOV_H_DEG

    /** Screen pixels for a point in the model. */
    fun project(p: V3): Offset {
        val (nx, ny) = ndc(p)
        return Offset((nx + 1f) / 2f * width, (1f - ny) / 2f * height)
    }

    /** The point on the horizontal plane at height [y] under the screen pixel [px]. */
    fun onPlane(px: Offset, y: Float): V3? {
        val nx = px.x / width * 2f - 1f
        val ny = 1f - px.y / height * 2f
        val dir = forward + right * (nx * tanH) + up * (ny * tanV)
        if (abs(dir.y) < 1e-6f) return null
        val t = (y - eye.y) / dir.y
        if (t <= 0f) return null
        return eye + dir * t
    }

    /** The unit view direction through the screen pixel [px], which may lie outside the view. */
    fun ray(px: Offset): V3 {
        val nx = px.x / width * 2f - 1f
        val ny = 1f - px.y / height * 2f
        return (forward + right * (nx * tanH) + up * (ny * tanV)).normalized()
    }

    /** Pixels per model unit at a depth of one unit along the view axis. */
    val focalPixels: Float get() = width / 2f / tanH

    val viewForward: V3 get() = forward

    /** Half the width and half the height of the view's frustum at a distance [d] from the eye. */
    fun halfExtentsAt(d: Float): Pair<Float, Float> = d * tanH to d * tanV

    /** How many pixels one model unit covers across the screen at [p]. */
    fun pixelsPerUnit(p: V3): Float = project(p + V3(0.5f, 0f, 0f)).x - project(p - V3(0.5f, 0f, 0f)).x

    private fun place(distance: Float) {
        eye = target + back * distance
        forward = (target - eye).normalized()
        right = forward.cross(V3(0f, 1f, 0f)).normalized()
        up = right.cross(forward)
    }

    private fun ndc(p: V3): Pair<Float, Float> {
        val d = p - eye
        val z = d.dot(forward)
        return (d.dot(right) / (z * tanH)) to (d.dot(up) / (z * tanV))
    }

    private fun corners(): List<V3> {
        val a = DeckModel.viewMin
        val b = DeckModel.viewMax
        return listOf(a.x, b.x).flatMap { x -> listOf(a.y, b.y).flatMap { y -> listOf(a.z, b.z).map { z -> V3(x, y, z) } } }
    }

    private fun fits() = corners().all { val (x, y) = ndc(it); abs(x) <= MARGIN && abs(y) <= MARGIN }

    companion object {
        const val FOV_H_DEG = 30.0
        private val TILT = Math.toRadians(25.0).toFloat()
        private const val MARGIN = 1.0f
    }
}
