package app.runout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.longClick
import app.runout.drops.DropItem
import app.runout.drops.DropState
import app.runout.drops.DropWeek
import app.runout.library.ScanReport
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.DiscStyle
import app.runout.model.SideSplitter
import app.runout.model.Source
import app.runout.model.Track
import app.runout.playback.DeckState
import app.runout.social.Friend
import app.runout.social.Profile
import app.runout.social.ShelfItem
import app.runout.sound.OutputStatus
import app.runout.sound.SoundSettings
import app.runout.ui.CoverCache
import app.runout.ui.Panel
import app.runout.ui.RoomScreen
import app.runout.ui.RoomState
import app.runout.ui.SettingsSection
import app.runout.soulseek.*
import app.runout.sharing.VinylPackage
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import android.graphics.Color as AColor

/**
 * Suite 10 — the listening room on screen: landscape and portrait layouts, the four panels, pulling and
 * sending a record, and the empty state that explains a scan. Renders real frames (Robolectric native
 * graphics) and saves them as screenshots in app/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w892dp-h412dp-land-xhdpi", application = android.app.Application::class)
class ListeningRoomUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun portrait() = RuntimeEnvironment.setQualifiers("w412dp-h892dp-port-xhdpi")

    private fun cover(name: String, draw: (Canvas, Int) -> Unit): String {
        val dir = File(Fixtures.context.cacheDir, "ui-covers").apply { mkdirs() }
        val size = 600
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), size)
        val file = File(dir, "$name.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        runBlocking { CoverCache.load(file.absolutePath) }
        return file.absolutePath
    }

    private fun tracks(id: String, vararg minutes: Double) = minutes.mapIndexed { i, m ->
        Track("$id-$i", "content://test/$id/$i", listOf("Opening", "Lowlight", "Glass Hours", "Signal", "Tidewater", "Afterimage", "Northbound", "Late Bloom", "Coda")[i % 9], i + 1, 1, (m * 60_000).toLong())
    }

    private val albums: List<Album> by lazy {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val night = cover("night") { c, s ->
            p.shader = LinearGradient(0f, 0f, 0f, s.toFloat(), AColor.rgb(28, 34, 92), AColor.rgb(206, 92, 160), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
            p.shader = RadialGradient(s * 0.5f, s * 0.62f, s * 0.22f, AColor.rgb(255, 214, 120), AColor.rgb(255, 120, 80), Shader.TileMode.CLAMP)
            c.drawCircle(s * 0.5f, s * 0.62f, s * 0.22f, p)
            p.shader = null; p.color = AColor.rgb(20, 20, 40)
            c.drawRect(0f, s * 0.72f, s.toFloat(), s.toFloat(), p)
        }
        val monsoon = cover("monsoon") { c, s ->
            c.drawColor(AColor.rgb(18, 92, 96)); p.shader = null
            for (i in 0 until 14) { p.color = if (i % 2 == 0) AColor.rgb(40, 140, 140) else AColor.rgb(230, 240, 232); c.drawRect(0f, i * s / 14f, s.toFloat(), i * s / 14f + s / 40f, p) }
        }
        val paper = cover("paper") { c, s ->
            c.drawColor(AColor.rgb(242, 234, 218)); p.shader = null; p.color = AColor.rgb(238, 98, 52)
            c.drawCircle(s * 0.35f, s * 0.4f, s * 0.2f, p); p.color = AColor.rgb(30, 30, 30); c.drawCircle(s * 0.62f, s * 0.58f, s * 0.12f, p)
        }
        val bloom = cover("bloom") { c, s ->
            p.shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), AColor.rgb(255, 200, 220), AColor.rgb(170, 190, 255), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p); p.shader = null; p.color = AColor.WHITE
            for (i in 0 until 6) c.drawCircle(s * (0.2f + i * 0.12f), s * (0.3f + (i % 3) * 0.18f), s * 0.05f, p)
        }
        listOf(
            Album("a1", "Monsoon Sessions", "Tidal Room", 2019, "", tracks("a1", 5.0, 6.2, 4.8, 7.1, 5.5, 6.0), AudioQuality("FLAC", 16, 44_100), monsoon, AColor.rgb(30, 120, 120)),
            Album("a2", "Night Drive", "Vela Park", 2023, "", tracks("a2", 4.2, 3.9, 5.1, 4.4, 3.6, 4.8, 4.0, 3.7, 5.3), AudioQuality("FLAC", 24, 96_000), night, AColor.rgb(180, 80, 160), license = "https://creativecommons.org/licenses/by/4.0/"),
            Album("a3", "Paper Moons", "Ines Varga", 2021, "", tracks("a3", 3.2, 3.5, 4.1, 2.9, 3.8, 4.4), AudioQuality("FLAC", 16, 44_100), paper, AColor.rgb(230, 110, 60), source = Source.RECEIVED, from = "Maya"),
            Album("a4", "Static Bloom", "Halcyon Dial", 2024, "", tracks("a4", 4.0, 4.0, 4.0, 4.0), AudioQuality("FLAC", 24, 192_000), bloom, AColor.rgb(200, 170, 240)),
            Album("a5", "Summer Tape", "Abhi", 2026, "", tracks("a5", 3.0, 4.0, 3.5), AudioQuality("FLAC", 16, 44_100), null, null, source = Source.PRESSING, disc = DiscStyle.SPLATTER, labelColor = 0xFFE0569B.toInt()),
        )
    }

    private val maya = Friend(
        Profile("m", "Maya", Profile.ACCENTS[1]),
        listOf(ShelfItem("Night Drive", "Vela Park", 2023, "FLAC", null), ShelfItem("Blue Hour", "Kite", 2019, "MP3", null)),
        0L,
    )

    private val week = DropWeek(
        2911, LocalDate.parse("2026-10-16"), LocalDate.parse("2026-10-23"),
        listOf(
            DropItem("NS050", "Another Day, Another Way", "No-Source Netlabel", 2012, "http://creativecommons.org/licenses/by-nc-sa/4.0/"),
            DropItem("DWK123", "Curses From Past Times (LP)", "ProleteR", 2012, "http://creativecommons.org/licenses/by-nc-nd/3.0/"),
            DropItem("badpanda018", "Brick City Love Song", "Riding Alone For Thousands Of Miles", null, "http://creativecommons.org/licenses/by-nc-sa/3.0/"),
        ),
    )

    private fun state(
        albums: List<Album> = this.albums,
        deck: DeckState = DeckState(),
        drops: DropState = DropState.Ready(week),
        sound: SoundSettings = SoundSettings(),
        message: String? = null,
        session: SoulseekSession = SoulseekSession(SoulseekConnection.CONNECTED, "abhi"),
        results: List<SoulseekSearchResult> = emptyList(),
        contacts: List<SoulseekContact> = emptyList(),
    ) = RoomState(
        albums = albums, hasFolder = true, scan = null, deck = deck, crackleOn = true, clock = "3:21 PM",
        sound = sound, output = OutputStatus(24, 96_000, floatPath = true, processing = true),
        profile = Profile("me", "Abhi", Profile.ACCENTS[0]), friends = listOf(maya), drops = drops,
        claimed = setOf("DWK123"), message = message, soulseekSession = session,
        soulseekResults = results, soulseekContacts = contacts, downloadFolder = "Music",
    )

    private fun playing(): DeckState {
        val album = albums[1]
        val sides = SideSplitter.split(album.tracks)
        return DeckState(album = album, sides = sides, needleDown = true, isPlaying = true, positionMs = (sides[0].durationMs * 0.38).toLong())
    }

    /** Renders a still frame: the platter keeps spinning, so time is stepped by hand. */
    private fun still(name: String, s: RoomState, pulledId: String? = null, crateIndex: Int = 0, panel: Panel? = null) {
        compose.mainClock.autoAdvance = false
        compose.setContent { RoomScreen(s, RecordingActions(), initialPulledId = pulledId, initialCrateIndex = crateIndex, initialPanel = panel) }
        repeat(40) { compose.mainClock.advanceTimeBy(100) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `landscape room, playing and pulled, renders`() {
        still("01_landscape_playing", state(deck = playing()), crateIndex = 3)
        compose.onNodeWithText("Now spinning — Side A", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("nav:DROPS").assertIsDisplayed()
    }

    @Test
    fun `pulling a record, then a long press offers to send it with audio`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(), actions, initialCrateIndex = 1) }
        compose.onNodeWithTag("sleeve:Night Drive").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pulled-disc").assertIsDisplayed()
        // No buttons beside the disc any more: what you can do lives behind a long press.
        compose.onNodeWithText("Send with audio").assertDoesNotExist()
        compose.onNodeWithTag("pulled-disc").performTouchInput { longClick() }
        compose.onNodeWithText("Send with audio").assertIsDisplayed() // Creative Commons
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/02_landscape_record_menu.png")
        compose.onNodeWithTag("menu:Send with audio").performClick()
        assertEquals(listOf("share:Night Drive"), actions.log.filter { it.startsWith("share") })
    }

    @Test
    fun `a record's details hold the licence the short caption leaves out`() {
        compose.setContent { RoomScreen(state(), RecordingActions(), initialCrateIndex = 1) }
        compose.onNodeWithText("Vela Park").assertIsDisplayed()
        compose.onNodeWithText("CC BY 4.0").assertDoesNotExist()
        compose.onNodeWithTag("sleeve:Night Drive").performTouchInput { longClick() }
        compose.onNodeWithTag("menu:Details").performClick()
        compose.onNodeWithTag("record-details").assertIsDisplayed()
        compose.onNodeWithText("CC BY 4.0", substring = true).assertExists()
    }

    @Test
    fun `portrait puts the turntable above the crate`() {
        portrait()
        still("03_portrait_playing", state(deck = playing()), crateIndex = 1)
        val deck = compose.onNodeWithTag("turntable").getBoundsInRoot()
        val sleeve = compose.onNodeWithTag("sleeve:Night Drive").getBoundsInRoot()
        assertTrue("turntable ${deck.bottom} above crate ${sleeve.top}", deck.bottom <= sleeve.top)
        assertTrue((deck.right - deck.left) > (deck.bottom - deck.top))
        // The platter never idles here, so check the pill strip without scrolling it.
        compose.onNodeWithTag("nav:DIG").assertIsDisplayed()
        compose.onNodeWithTag("nav:SETTINGS").assertExists()
    }

    @Test
    fun `Settings opens on Sound, which controls Pure mode, hi-res and the EQ`() {
        val actions = object : RecordingActions() {
            var sound by mutableStateOf(SoundSettings())
            override fun updateSound(transform: (SoundSettings) -> SoundSettings) { sound = transform(sound) }
        }
        compose.setContent { RoomScreen(state(sound = actions.sound), actions) }
        compose.onNodeWithTag("nav:SETTINGS").performClick()
        compose.onNodeWithTag("panel-title").assertTextContains("Settings")
        compose.onNodeWithTag("panel:sound").assertIsDisplayed()
        compose.onNodeWithText("24-bit · 96 kHz → 32-bit float").assertIsDisplayed()

        compose.onNodeWithTag("fader:1k").performScrollTo().performTouchInput {
            swipe(center, center - Offset(0f, 140f), durationMillis = 300)
        }
        compose.waitForIdle()
        assertTrue("1 kHz boosted to ${actions.sound.graphic[5]}", actions.sound.graphic[5] > 1f)
        assertEquals("Custom", actions.sound.presetName)
        assertTrue(actions.sound.eqEnabled)

        compose.onNodeWithText("Warm vinyl").performScrollTo().performClick()
        assertTrue(actions.log.contains("preset:Warm vinyl"))

        compose.onNodeWithTag("toggle:Hi-res output").performScrollTo().performClick()
        assertEquals(false, actions.sound.hiResOutput)
        compose.onNodeWithTag("toggle:Pure").performScrollTo().performClick()
        assertEquals(true, actions.sound.pure)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/04_sound_panel.png")

        compose.onNodeWithTag("action:Close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("panel:sound").assertDoesNotExist()
    }

    @Test
    fun `Friday Drops shows the week and claims a record`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(), actions, initialPanel = Panel.DROPS) }
        compose.onNodeWithText("Week of 16 October").assertIsDisplayed()
        compose.onNodeWithText("2 of 3 claims left this week").assertIsDisplayed()
        compose.onNodeWithText("In your crate").assertIsDisplayed() // already claimed
        compose.onNodeWithText("CC BY-NC-SA 4.0").assertIsDisplayed()
        compose.onNodeWithTag("drop:NS050").assertIsDisplayed()
        // Two records are still claimable; the first is NS050's.
        val claims = compose.onAllNodes(hasText("Claim"))
        claims.assertCountEquals(2)
        claims[0].performClick()
        assertTrue(actions.log.toString(), actions.log.contains("claim:NS050"))
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/05_drops_panel.png")
    }

    @Test
    fun `an unloaded drop asks for this week's records`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(drops = DropState.Idle), actions, initialPanel = Panel.DROPS) }
        compose.waitForIdle()
        assertTrue(actions.log.contains("refreshDrops"))
        compose.onNodeWithText("Digging through the archive…").assertIsDisplayed()
    }

    @Test
    fun `private messages open by Soulseek username and send through the action`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(contacts = listOf(SoulseekContact("maya", true))), actions, initialPanel = Panel.FRIENDS) }
        compose.onNodeWithTag("panel:messages").assertIsDisplayed()
        compose.onNodeWithText("maya").assertIsDisplayed()
        compose.onNodeWithText("Message").performScrollTo().performClick()
        assertTrue(actions.log.contains("soulseekConversation:maya"))
        compose.onNodeWithTag("field:Private message").performScrollTo().performTextInput("Hi Maya")
        compose.onNodeWithText("Send message").performScrollTo().performClick()
        assertTrue(actions.log.contains("soulseekMessage:maya:Hi Maya"))
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/06_messages_panel.png")
    }

    @Test
    fun `Soulseek account checks credentials and offers offline playback`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(session = SoulseekSession()), actions, initialPanel = Panel.SETTINGS, initialSettings = SettingsSection.ACCOUNT) }
        compose.onNodeWithTag("field:Soulseek username").performTextInput("अ")
        compose.onNodeWithTag("field:Soulseek password").performTextInput("test password")
        compose.onNodeWithText("Sign in or create Soulseek account").performScrollTo().performClick()
        assertTrue(actions.log.none { it.startsWith("soulseekSignIn") })
        compose.onNodeWithTag("field:Soulseek username").performScrollTo().performTextClearance()
        compose.onNodeWithTag("field:Soulseek username").performTextInput("Abhi_99")
        compose.onNodeWithText("Sign in or create Soulseek account").performScrollTo().performClick()
        assertTrue(actions.log.toString(), actions.log.contains("soulseekSignIn:Abhi_99:false"))
        compose.onNodeWithText("Continue offline").performScrollTo().performClick()
        compose.onNodeWithTag("panel:account").assertDoesNotExist()
    }

    @Test
    fun `Internet Archive claim actions are disabled without Soulseek sign in`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(session = SoulseekSession()), actions, initialPanel = Panel.DROPS) }
        compose.onNodeWithText("Sign in with Soulseek").assertIsDisplayed()
        compose.onAllNodes(hasText("Claim"))[0].performScrollTo().performClick()
        assertTrue(actions.log.none { it.startsWith("claim:") })
    }

    @Test
    fun `the deck's keys and screen work, and it has no speed controls`() {
        val actions = RecordingActions()
        // Needle up, so the platter is still and the test can wait for the screen to settle.
        compose.setContent { RoomScreen(state(deck = playing().copy(isPlaying = false, needleDown = false)), actions) }
        compose.onNodeWithTag("deck:startstop").performClick()
        compose.onNodeWithTag("deck:next").performClick()
        compose.onNodeWithTag("deck:prev").performClick()
        assertEquals(listOf("startStop", "next", "prev"), actions.log.filter { it in setOf("startStop", "next", "prev") })
        compose.onNodeWithTag("deck:33").assertDoesNotExist()
        compose.onNodeWithTag("deck:45").assertDoesNotExist()
        compose.onNodeWithTag("deck:pitch").assertDoesNotExist()
        compose.onNodeWithTag("deck:screen").assertContentDescriptionContains("Lowlight", substring = true)
    }

    @Test
    fun `the footer is four icon tabs and the Pressing Plant is gone`() {
        compose.setContent { RoomScreen(state(), RecordingActions()) }
        listOf("DIG", "DROPS", "FRIENDS", "SETTINGS").forEach { compose.onNodeWithTag("nav:$it").assertIsDisplayed() }
        compose.onNodeWithTag("nav:PRESSING").assertDoesNotExist()
        compose.onNodeWithText("Pressing Plant").assertDoesNotExist()
        // The top bar: no clock, no Change folder; the record count stays.
        compose.onNodeWithText("3:21 PM", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Change folder").assertDoesNotExist()
        compose.onNodeWithTag("action:Rescan").assertIsDisplayed()
        compose.onNodeWithText("5 records").assertIsDisplayed()
    }

    @Test
    fun `panels slide up as sheets in portrait`() {
        portrait()
        still("08_portrait_settings", state(), panel = Panel.SETTINGS)
        val sheet = compose.onNodeWithTag("panel-title").getBoundsInRoot()
        assertTrue("sheet starts in the lower part of the screen", sheet.top.value > 892 * 0.2f)
    }

    @Test
    fun `an empty folder explains what the scan found`() {
        val report = ScanReport(folders = 3, audioFiles = 0, unreadable = 0, albums = 0, otherFiles = mapOf("m4p" to 12))
        compose.setContent {
            RoomScreen(state(albums = emptyList()).copy(report = report), RecordingActions())
        }
        compose.onNodeWithTag("empty-title", useUnmergedTree = true).assertTextContains("No albums found")
        compose.onNodeWithTag("empty-detail", useUnmergedTree = true).assertTextContains("Looked in 3 folders and found no music files (found .m4p ×12)", substring = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/09_empty_with_report.png")
    }

    @Test
    fun `messages appear and can be dismissed`() {
        var message by mutableStateOf<String?>("“Night Drive” from Maya is in your crate")
        val actions = object : RecordingActions() {
            override fun dismissMessage() { message = null }
        }
        compose.setContent { RoomScreen(state(message = message), actions) }
        compose.onNodeWithTag("message").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("message").assertDoesNotExist()
    }

    @Test
    fun `a Soulseek problem shows as the room's glass note and stays until tapped away`() {
        var problem by mutableStateOf<String?>("This Soulseek ID connected in another client. Reconnect when you are ready.")
        val actions = object : RecordingActions() {
            override fun clearSoulseekError() { problem = null }
        }
        compose.setContent { RoomScreen(state().copy(soulseekError = problem), actions) }
        compose.onNodeWithTag("message").assertIsDisplayed()
        compose.onNodeWithText("another client", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("message").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("message").assertDoesNotExist()
    }

    @Test
    @Config(application = RunoutApp::class)
    fun `the real app opens and takes a friend card shared from another app`() {
        val runout = androidx.test.core.app.ApplicationProvider.getApplicationContext<RunoutApp>()
        // A friend card made on someone else's phone, opened from a chat app.
        val card = File(runout.cacheDir, "Maya on Runout.vinyl")
        FileOutputStream(card).use {
            VinylPackage.write(
                it, VinylPackage.Kind.FRIEND, Profile("maya-id", "Maya", Profile.ACCENTS[1]), null,
                listOf(ShelfItem("Night Drive", "Vela Park", 2023, "FLAC", null)),
            )
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.fromFile(card))
            .setClass(runout, MainActivity::class.java)
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val deadline = System.currentTimeMillis() + 15_000
            while (runout.graph.social.friends.value.isEmpty() && System.currentTimeMillis() < deadline) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(25)
            }
            assertEquals(listOf("Maya"), runout.graph.social.friends.value.map { it.profile.name })
            scenario.onActivity { assertTrue(it.window.decorView.isAttachedToWindow) }
            assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
