package app.runout.ui.deck

import android.content.Context
import android.graphics.Bitmap
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.ui.geometry.Offset
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.FilamentAsset

/**
 * The record in your hand, in 3D: the same model, material and light as the record on the deck,
 * drawn into a full-screen transparent [TextureView] above the room while a record is out of its
 * sleeve. Its camera is the deck's camera stretched to the whole screen (an off-centre frustum), so
 * a disc posed over the platter covers exactly the deck's pixels, and the hand-over on landing
 * shows nothing. Main thread only.
 */
class DiscRenderer(context: Context, textureView: TextureView) : Choreographer.FrameCallback {
    private val engine = FilamentShared.engine
    private val renderer: Renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(context)
    private var swapChain: SwapChain? = null
    private val record: FilamentAsset = FilamentShared.load(context, "deck/record.glb")
    private val lights = StudioLights(engine, scene, shadows = false)
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private var textures: List<Texture> = emptyList()

    private var deckCamera: DeckCamera? = null
    private var deckOffset = Offset.Zero
    private var width = 0
    private var height = 0
    private var transform: FloatArray? = null
    private var dirty = true
    private var settle = 0
    private var running = false
    private var drawn = 0

    /** Called once the disc is on screen, so the 2D stand-in can step aside. */
    var onReady: (() -> Unit)? = null

    init {
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.antiAliasing = View.AntiAliasing.FXAA
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply { enabled = true; sampleCount = 4 }
        view.colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
        camera.setExposure(1f)
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
        scene.addEntities(record.entities)

        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                displayHelper.attach(renderer, textureView.display)
                settle = WARM_UP_FRAMES
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                this@DiscRenderer.width = width
                this@DiscRenderer.height = height
                view.viewport = Viewport(0, 0, width, height)
                updateProjection()
                settle = WARM_UP_FRAMES
            }
        }
        uiHelper.attachTo(textureView)
    }

    /** The deck's camera, and where the deck's view sits within this full-screen one (pixels). */
    fun setDeckCamera(cam: DeckCamera, offset: Offset) {
        if (cam === deckCamera && offset == deckOffset) return
        deckCamera = cam
        deckOffset = offset
        updateProjection()
    }

    private fun updateProjection() {
        val cam = deckCamera ?: return
        if (width == 0 || height == 0) return
        val near = cam.near.toDouble()
        val (hw, hh) = cam.halfExtentsAt(cam.near)
        // Stretch the deck's frustum so its own view's pixels stay where they are on screen.
        val left = -hw - deckOffset.x / cam.width * 2f * hw
        val right = left + width / cam.width * 2f * hw
        val top = hh + deckOffset.y / cam.height * 2f * hh
        val bottom = top - height / cam.height * 2f * hh
        camera.setProjection(Camera.Projection.PERSPECTIVE, left.toDouble(), right.toDouble(), bottom.toDouble(), top.toDouble(), near, cam.far.toDouble())
        camera.lookAt(
            cam.eye.x.toDouble(), cam.eye.y.toDouble(), cam.eye.z.toDouble(),
            cam.target.x.toDouble(), cam.target.y.toDouble(), cam.target.z.toDouble(),
            cam.viewUp.x.toDouble(), cam.viewUp.y.toDouble(), cam.viewUp.z.toDouble(),
        )
        dirty = true
    }

    /** The record's face and surface maps, the same bitmaps the deck uses. */
    fun setRecord(face: Bitmap, normal: Bitmap?, roughness: Bitmap?) {
        val f = FilamentShared.mipmapped(face, srgb = true)
        val n = normal?.let { FilamentShared.mipmapped(it, srgb = false) }
        val r = roughness?.let { FilamentShared.mipmapped(it, srgb = false) }
        record.instance.materialInstances.forEach {
            it.setParameter("baseColorMap", f, sampler)
            if (n != null && it.material.hasParameter("normalMap")) it.setParameter("normalMap", n, sampler)
            if (n != null && it.material.hasParameter("normalScale")) it.setParameter("normalScale", 1f)
            if (r != null && it.material.hasParameter("metallicRoughnessMap")) it.setParameter("metallicRoughnessMap", r, sampler)
        }
        textures.forEach { engine.destroyTexture(it) }
        textures = listOfNotNull(f, n, r)
        dirty = true
    }

    /** Where the record is: a column-major model transform (see DeckModel.heldDiscTransform). */
    fun setTransform(m: FloatArray) {
        if (transform?.contentEquals(m) == true) return
        transform = m
        dirty = true
    }

    fun resume() {
        if (!running) { running = true; dirty = true; Choreographer.getInstance().postFrameCallback(this) }
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        val chain = swapChain ?: return
        if (dirty) { settle = maxOf(settle, SETTLE_FRAMES); dirty = false }
        if (settle <= 0 || !uiHelper.isReadyToRender) return
        transform?.let { engine.transformManager.setTransform(engine.transformManager.getInstance(record.root), it) }
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            settle--
            if (++drawn == READY_FRAMES) onReady?.invoke()
        }
    }

    fun destroy() {
        pause()
        onReady = null
        uiHelper.detach()
        scene.removeEntities(record.entities)
        textures.forEach { engine.destroyTexture(it) }
        FilamentShared.loader.destroyAsset(record)
        lights.destroy()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
    }

    private companion object {
        const val SETTLE_FRAMES = 3
        const val WARM_UP_FRAMES = 20
        const val READY_FRAMES = 2
    }
}
