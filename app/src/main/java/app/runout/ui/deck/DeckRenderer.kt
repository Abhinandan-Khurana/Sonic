package app.runout.ui.deck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Draws the 3D turntable into a [TextureView] with Filament, over a transparent background so the
 * room's light shows around it. The deck's state comes in through the setters; frames are drawn
 * only when something changed. Everything here runs on the main thread.
 */
class DeckRenderer(private val context: Context, textureView: TextureView) : Choreographer.FrameCallback {
    private val engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(context)
    private var swapChain: SwapChain? = null

    private val materials = UbershaderProvider(engine)
    private val loader = AssetLoader(engine, materials, EntityManager.get())
    private val resources = ResourceLoader(engine)
    private val deck: FilamentAsset = load("deck/turntable.glb")
    private val record: FilamentAsset = load("deck/record.glb")
    private val platter = deck.getFirstEntityByName(DeckModel.PLATTER)
    private val tonearm = deck.getFirstEntityByName(DeckModel.TONEARM)
    private val keyEntities = DeckModel.keys.associateWith { deck.getFirstEntityByName(it.node) }
    private val statusLamp = materialOf(DeckModel.LED_STATUS)
    private val strip = materialOf(DeckModel.LED_STRIP)
    private val screen = materialOf(DeckModel.SCREEN)
    private val sun = EntityManager.get().create()
    private val fill = EntityManager.get().create()
    private val ambient: IndirectLight
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private val flatSampler = TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private var recordTexture: Texture? = null
    private var recordShown = false
    private val screenPixels = Bitmap.createBitmap(256, 64, Bitmap.Config.ARGB_8888)
    private val screenTexture = flatTexture(screenPixels)
    private val stripPixels = Bitmap.createBitmap(256, 8, Bitmap.Config.ARGB_8888)
    private val stripTexture = flatTexture(stripPixels)
    private val pixelFont: Typeface = runCatching { Typeface.createFromAsset(context.assets, "fonts/silkscreen.ttf") }.getOrDefault(Typeface.MONOSPACE)

    private var platterDegrees = 0f
    private var armAngle = DeckModel.angleRest
    private var armDrop = 0f
    private val keyPress = DeckModel.keys.associateWith { 0f }.toMutableMap()
    private var dirty = true
    /** Frames still to draw after a change: a TextureView can drop a lone first frame. */
    private var settle = 0
    private var running = false

    /** Called after each frame is drawn, so the room redraws and the TextureView shows it. */
    var onFrame: (() -> Unit)? = null

    init {
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.antiAliasing = View.AntiAliasing.FXAA
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply { enabled = true; sampleCount = 4 }
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = true; radius = 0.25f; intensity = 0.8f }
        view.colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Filmic()).build(engine)
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }

        // A soft studio: a high key light from the front left, a dimmer fill from the right, and an
        // even ambient so the white body never falls into grey.
        LightManager.Builder(LightManager.Type.SUN)
            .color(1f, 0.98f, 0.95f)
            .intensity(95_000f)
            .direction(0.35f, -1f, -0.45f)
            .sunAngularRadius(2.4f)
            .castShadows(true)
            .build(engine, sun)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.92f, 0.95f, 1f)
            .intensity(28_000f)
            .direction(-0.6f, -0.5f, -0.2f)
            .build(engine, fill)
        ambient = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(1f, 1f, 1f))
            .intensity(32_000f)
            .build(engine)
        scene.indirectLight = ambient
        scene.addEntity(sun)
        scene.addEntity(fill)

        scene.addEntities(deck.entities)
        val rm = engine.renderableManager
        // Solid parts cast and catch shadows. Decals, lights, the screen and the baked shadow lie a
        // hair above a surface: casting would print a grey box under each, catching gives acne.
        val flat = setOf("Prints", "Shadow_Plane", "Screen_Glass", "Screen_Display", DeckModel.LED_STRIP, DeckModel.LED_STATUS)
        (deck.entities + record.entities).forEach { e ->
            if (!rm.hasComponent(e)) return@forEach
            val i = rm.getInstance(e)
            val decal = deck.getName(e) in flat
            rm.setCastShadows(i, !decal)
            rm.setReceiveShadows(i, !decal)
            for (p in 0 until rm.getPrimitiveCount(i)) {
                val m = rm.getMaterialInstanceAt(i, p)
                // A key's printed glyph is a second see-through layer on a see-through cap: draw it last.
                if (m.name == "Prints_Mat") rm.setBlendOrderAt(i, p, 1)
                // The baked occlusion counted the label decals as solid, leaving a dark box under
                // each label. Keep only a trace of it until the bake is redone without them.
                if (m.name in BAKED_AO && m.material.hasParameter("aoStrength")) m.setParameter("aoStrength", AO_STRENGTH)
            }
        }
        // The screen and the strip are drawn by the app: start them dark.
        screen?.let { useTexture(it, screenTexture, "baseColorMap", "emissiveMap") }
        strip?.let { useTexture(it, stripTexture, "emissiveMap") }
        setScreen("", "")
        setStrip(0f, false)
        setStatusLamp(false)
        applyTransforms()

        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                displayHelper.attach(renderer, textureView.display)
                warmUp()
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
                view.viewport = Viewport(0, 0, width, height)
                frame(DeckCamera(width.toFloat(), height.toFloat()))
                warmUp()
            }
        }
        uiHelper.attachTo(textureView)
    }

    /** Matches Filament's camera to the one the touch mapping uses. */
    private fun frame(c: DeckCamera) {
        camera.setProjection(c.fovHorizontalDegrees, (c.width / c.height).toDouble(), c.near.toDouble(), c.far.toDouble(), Camera.Fov.HORIZONTAL)
        camera.lookAt(
            c.eye.x.toDouble(), c.eye.y.toDouble(), c.eye.z.toDouble(),
            c.target.x.toDouble(), c.target.y.toDouble(), c.target.z.toDouble(),
            0.0, 1.0, 0.0,
        )
    }

    /** Keeps drawing for a moment after the surface appears or changes size. */
    private fun warmUp() {
        dirty = false
        settle = WARM_UP_FRAMES
    }

    fun setPlatter(degrees: Float) {
        if (degrees != platterDegrees) { platterDegrees = degrees; dirty = true }
    }

    fun setArm(angle: Float, drop: Float) {
        if (angle != armAngle || drop != armDrop) { armAngle = angle; armDrop = drop; dirty = true }
    }

    fun setKey(key: DeckKey, pressed: Float) {
        if (keyPress[key] != pressed) { keyPress[key] = pressed; dirty = true }
    }

    /** The orange lamp by START/STOP: lit while a record plays. */
    fun setStatusLamp(lit: Boolean) {
        val m = statusLamp ?: return
        // Off, the lens is dark amber glass; lit, it glows signal orange without blowing out white.
        if (lit) {
            m.setParameter("baseColorFactor", 1f, 0.254f, 0.072f, 1f)
            m.setParameter("emissiveFactor", 1f, 0.254f, 0.072f)
            glow(m, 2f)
        } else {
            m.setParameter("baseColorFactor", 0.16f, 0.06f, 0.035f, 1f)
            m.setParameter("emissiveFactor", 0f, 0f, 0f)
        }
        dirty = true
    }

    /**
     * The LED strip along the front: lit from the left as far as the side has played, the rest a
     * faint glow; brighter while the record turns.
     */
    fun setStrip(progress: Float, playing: Boolean) {
        val m = strip ?: return
        val c = Canvas(stripPixels)
        val p = Paint().apply { isAntiAlias = false }
        c.drawColor(android.graphics.Color.rgb(40, 34, 30))
        if (progress > 0f) {
            p.color = android.graphics.Color.rgb(255, 214, 180)
            c.drawRect(0f, 0f, stripPixels.width * progress.coerceIn(0f, 1f), stripPixels.height.toFloat(), p)
        }
        TextureHelper.setBitmap(engine, stripTexture, 0, stripPixels)
        glow(m, if (playing) 5f else 2f)
        dirty = true
    }

    /** The deck's little screen: two lines in the pixel font, green on near black. */
    fun setScreen(top: String, bottom: String) {
        val c = Canvas(screenPixels)
        c.drawColor(android.graphics.Color.rgb(10, 14, 12))
        val p = Paint().apply {
            isAntiAlias = false
            typeface = pixelFont
            textSize = 22f
            color = android.graphics.Color.rgb(170, 255, 205)
        }
        c.drawText(fit(top, p, 244f), 6f, 26f, p)
        p.color = android.graphics.Color.rgb(120, 200, 160)
        c.drawText(fit(bottom, p, 244f), 6f, 55f, p)
        TextureHelper.setBitmap(engine, screenTexture, 0, screenPixels)
        screen?.let { glow(it, 2.5f) }
        dirty = true
    }

    private fun fit(text: String, p: Paint, width: Float): String {
        var t = text.uppercase()
        while (t.isNotEmpty() && p.measureText(t) > width) t = t.dropLast(1)
        return t
    }

    /** The record's face (vinyl, label, track gaps), or null for an empty platter. */
    fun setRecord(face: Bitmap?) {
        if (face == null) {
            if (recordShown) { scene.removeEntities(record.entities); recordShown = false; dirty = true }
            return
        }
        val levels = 1 + (31 - Integer.numberOfLeadingZeros(maxOf(face.width, face.height)))
        val texture = Texture.Builder()
            .width(face.width)
            .height(face.height)
            .levels(levels)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8)
            .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
            .build(engine)
        TextureHelper.setBitmap(engine, texture, 0, face)
        texture.generateMipmaps(engine)
        record.instance.materialInstances.forEach { it.setParameter("baseColorMap", texture, sampler) }
        recordTexture?.let { engine.destroyTexture(it) }
        recordTexture = texture
        if (!recordShown) { scene.addEntities(record.entities); recordShown = true }
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
        applyTransforms()
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            settle--
            onFrame?.invoke()
        }
    }

    private fun applyTransforms() {
        val tm = engine.transformManager
        if (platter != 0) tm.setTransform(tm.getInstance(platter), DeckModel.platterTransform(platterDegrees))
        if (tonearm != 0) tm.setTransform(tm.getInstance(tonearm), DeckModel.armTransform(armAngle, armDrop))
        keyEntities.forEach { (key, e) -> if (e != 0) tm.setTransform(tm.getInstance(e), DeckModel.keyTransform(key, keyPress[key] ?: 0f)) }
        tm.setTransform(tm.getInstance(record.root), DeckModel.recordTransform(platterDegrees))
    }

    private fun materialOf(node: String): MaterialInstance? {
        val e = deck.getFirstEntityByName(node)
        val rm = engine.renderableManager
        if (e == 0 || !rm.hasComponent(e)) return null
        return rm.getMaterialInstanceAt(rm.getInstance(e), 0)
    }

    private fun useTexture(m: MaterialInstance, t: Texture, vararg params: String) {
        params.forEach { if (m.material.hasParameter(it)) m.setParameter(it, t, flatSampler) }
    }

    /** Emissive strength (KHR_materials_emissive_strength), where the material has it. */
    private fun glow(m: MaterialInstance, strength: Float) {
        if (m.material.hasParameter("emissiveStrength")) m.setParameter("emissiveStrength", strength)
    }

    private fun flatTexture(b: Bitmap): Texture = Texture.Builder()
        .width(b.width)
        .height(b.height)
        .levels(1)
        .sampler(Texture.Sampler.SAMPLER_2D)
        .format(Texture.InternalFormat.SRGB8_A8)
        .build(engine)

    private fun load(path: String): FilamentAsset {
        val bytes = context.assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
        val asset = loader.createAsset(buffer) ?: error("Couldn't read $path")
        resources.loadResources(asset)
        asset.releaseSourceData()
        return asset
    }

    fun destroy() {
        pause()
        onFrame = null
        uiHelper.detach()
        if (recordShown) scene.removeEntities(record.entities)
        scene.removeEntities(deck.entities)
        recordTexture?.let { engine.destroyTexture(it) }
        engine.destroyTexture(screenTexture)
        engine.destroyTexture(stripTexture)
        loader.destroyAsset(deck)
        loader.destroyAsset(record)
        resources.destroy()
        materials.destroyMaterials()
        materials.destroy()
        loader.destroy()
        engine.destroyEntity(sun)
        engine.destroyEntity(fill)
        engine.destroyIndirectLight(ambient)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    companion object {
        private const val SETTLE_FRAMES = 3
        private const val WARM_UP_FRAMES = 30
        private val BAKED_AO = setOf("Chassis_Mat", "Trim_Mat", "Rubber_Mat")
        private const val AO_STRENGTH = 0.3f

        /**
         * Whether Filament's native code can run here. Off under Robolectric (the JVM tests), where
         * the deck falls back to a flat stand-in drawn in Compose.
         */
        val available: Boolean by lazy {
            if (Build.FINGERPRINT == "robolectric") return@lazy false
            runCatching { Filament.init(); Gltfio.init() }.isSuccess
        }
    }
}
