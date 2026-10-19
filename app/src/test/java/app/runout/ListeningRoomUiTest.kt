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
import app.runout.ui.PeopleState
import app.runout.account.Account
import app.runout.account.AccountStatus
import app.runout.account.RemoteProfile
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
            Album("a3", "Paper Moons", "Ines Varga", 2021, "", tracks("a3", 3.2, 3.5, 4.1, 2.9, 3.8, 4.4), AudioQuality("MP3", bitrateKbps = 320), paper, AColor.rgb(230, 110, 60), source = Source.RECEIVED, from = "Maya"),
            Album("a4", "Static Bloom", "Halcyon Dial", 2024, "", tracks("a4", 4.0, 4.0, 4.0, 4.0), AudioQuality("FLAC", 24, 192_000), bloom, AColor.rgb(200, 170, 240)),
            Album("a5", "Summer Tape", "Abhi", 2026, "", tracks("a5", 3.0, 4.0, 3.5), AudioQuality("MIXED"), null, null, source = Source.PRESSING, disc = DiscStyle.SPLATTER, labelColor = 0xFFE0569B.toInt()),
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
        account: Account? = me,
        people: PeopleState = PeopleState(),
    ) = RoomState(
        albums = albums, hasFolder = true, scan = null, deck = deck, crackleOn = true, clock = "3:21 PM",
        sound = sound, output = OutputStatus(24, 96_000, floatPath = true, processing = true),
        profile = Profile("me", "Abhi", Profile.ACCENTS[0]), friends = listOf(maya), drops = drops,
        claimed = setOf("DWK123"), message = message, account = account,
        accountStatus = AccountStatus("Profile is up on 3 of 3 relays", true), people = people,
    )

    private val me = Account("7f3a" + "0".repeat(60), "abhi", "Abhi", Profile.ACCENTS[0])

    private val lena = RemoteProfile(
        pubkey = "c21b" + "1".repeat(60), username = "dj.lena", name = "Lena", accent = Profile.ACCENTS[3],
        shelf = listOf(
            ShelfItem("Night Drive", "Vela Park", 2023, "FLAC · 24-bit · 96 kHz", null, AColor.rgb(120, 60, 140)),
            ShelfItem("Blue Hour", "Kite", 2019, "MP3 · 320 kbps", null, AColor.rgb(40, 90, 160)),
            ShelfItem("Concrete Garden", "Studio Kiln", 2018, "AAC · 256 kbps", null, AColor.rgb(110, 112, 118)),
        ),
        shelfCount = 3, updatedAt = 0,
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
    fun `pulling a record offers to send it, with or without audio`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(), actions, initialCrateIndex = 1) }
        compose.onNodeWithTag("sleeve:Night Drive").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pulled-disc").assertIsDisplayed()
        compose.onNodeWithText("Send with audio").assertIsDisplayed() // Creative Commons
        compose.onNodeWithTag("send").performClick()
        assertEquals(listOf("share:Night Drive"), actions.log.filter { it.startsWith("share") })
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/02_landscape_pulled_send.png")
    }

    @Test
    fun `portrait puts the turntable above the crate`() {
        portrait()
        still("03_portrait_playing", state(deck = playing()), crateIndex = 1)
        val deck = compose.onNodeWithTag("turntable").getBoundsInRoot()
        val sleeve = compose.onNodeWithTag("sleeve:Night Drive").getBoundsInRoot()
        assertTrue("turntable ${deck.bottom} above crate ${sleeve.top}", deck.bottom <= sleeve.top)
        assertTrue((deck.right - deck.left) > (deck.bottom - deck.top))
        compose.onNodeWithTag("nav:SOUND").assertIsDisplayed()
    }

    @Test
    fun `the Sound panel controls Pure mode, hi-res and the EQ`() {
        val actions = object : RecordingActions() {
            var sound by mutableStateOf(SoundSettings())
            override fun updateSound(transform: (SoundSettings) -> SoundSettings) { sound = transform(sound) }
        }
        compose.setContent { RoomScreen(state(sound = actions.sound), actions) }
        compose.onNodeWithTag("nav:SOUND").performClick()
        compose.onNodeWithTag("panel-title").assertTextContains("Sound")
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
    fun `Friends shows your handle, your friends, and sends your card`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(), actions, initialPanel = Panel.FRIENDS) }
        compose.onNodeWithTag("my-handle").assertTextContains("@abhi·7F3A")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/06_friends_panel.png")
        compose.onNodeWithText("Send my friend card").performScrollTo().performClick()
        assertTrue(actions.log.contains("shareCard"))
        compose.onNodeWithText("Copy account key").performScrollTo().performClick()
        assertTrue(actions.log.contains("copyKey"))
        compose.onNodeWithTag("friend:Maya").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 records · 1 you both have").assertIsDisplayed()
    }

    @Test
    fun `making an account checks the username first`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(account = null), actions, initialPanel = Panel.FRIENDS) }
        compose.onNodeWithText("Make your account", substring = true, ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag("field:Username").performTextInput("A!")
        compose.onNodeWithText("At least 3 characters").assertIsDisplayed()
        compose.onNodeWithText("Create account").performClick()
        assertTrue("disabled while the name is wrong", actions.log.none { it.startsWith("createAccount") })
        compose.onNodeWithTag("field:Username").performTextClearance()
        compose.onNodeWithTag("field:Username").performTextInput("Abhi_99")
        compose.onNodeWithTag("field:Display name").performTextClearance()
        compose.onNodeWithTag("field:Display name").performTextInput("Abhi")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/10_make_account.png")
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        assertTrue(actions.log.toString(), actions.log.contains("createAccount:abhi_99:Abhi"))
    }

    @Test
    fun `search finds people and opens their profile and shelf`() {
        var people by mutableStateOf(PeopleState(results = listOf(lena), searched = "lena"))
        val actions = object : RecordingActions() {
            override fun openProfile(pubkey: String) { super.openProfile(pubkey); people = people.copy(viewing = lena) }
            override fun closeProfile() { super.closeProfile(); people = people.copy(viewing = null) }
        }
        compose.setContent { RoomScreen(state(people = people), actions, initialPanel = Panel.FRIENDS) }
        compose.onNodeWithTag("field:Search usernames").performScrollTo().performTextInput("lena")
        compose.onNodeWithText("Search").performClick()
        assertTrue(actions.log.contains("search:lena"))
        compose.onNodeWithTag("result:dj.lena").performScrollTo().performClick()
        assertTrue(actions.log.toString(), actions.log.any { it.startsWith("open:c21b11") })

        compose.onNodeWithTag("profile:dj.lena").assertIsDisplayed()
        compose.onNodeWithText("@dj.lena·C21B").assertIsDisplayed()
        compose.onNodeWithText("3 records · 1 you both have").assertIsDisplayed()
        compose.onAllNodes(hasText("Blue Hour"))[0].assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/11_profile.png")
        compose.onNodeWithText("Add friend").performClick()
        assertTrue(actions.log.contains("addFriend:dj.lena"))
        compose.onNodeWithTag("profile-back").performClick()
        assertTrue(actions.log.contains("closeProfile"))
    }

    @Test
    fun `the deck's keys, fader and screen work`() {
        val actions = RecordingActions()
        // Needle up, so the platter is still and the test can wait for the screen to settle.
        compose.setContent { RoomScreen(state(deck = playing().copy(isPlaying = false, needleDown = false)), actions) }
        compose.onNodeWithTag("deck:startstop").performClick()
        compose.onNodeWithTag("deck:next").performClick()
        compose.onNodeWithTag("deck:prev").performClick()
        compose.onNodeWithTag("deck:45").performClick()
        compose.onNodeWithTag("deck:33").performClick() // already at 33: nothing to do
        assertEquals(listOf("startStop", "next", "prev", "speed"), actions.log.filter { it in setOf("startStop", "next", "prev", "speed") })
        compose.onNodeWithTag("deck:pitch").performTouchInput { swipe(center, center + Offset(0f, height * 0.3f), durationMillis = 300) }
        compose.waitForIdle()
        val pitch = actions.log.last { it.startsWith("pitch:") }.removePrefix("pitch:").toFloat()
        assertTrue("dragged towards + : $pitch", pitch > 1f)
        compose.onNodeWithTag("deck:screen").assertContentDescriptionContains("Lowlight", substring = true)
    }

    @Test
    fun `the Pressing Plant presses a record from tracks on your shelf`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(state(), actions, initialPanel = Panel.PRESSING) }
        compose.onNodeWithTag("field:Title").performTextInput("Late Summer")
        compose.onNodeWithText("Splatter").performScrollTo().performClick()
        compose.onNodeWithTag("pick-album:Night Drive").performScrollTo().performClick()
        compose.onNodeWithTag("pick-track:Opening").performScrollTo().performClick()
        compose.onNodeWithTag("pick-track:Lowlight").performScrollTo().performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/07_pressing_plant.png")
        compose.onNodeWithText("Press it").performScrollTo().performClick()
        assertTrue(actions.log.toString(), actions.log.contains("press:Late Summer:2"))
    }

    @Test
    fun `panels slide up as sheets in portrait`() {
        portrait()
        still("08_portrait_sound", state(), panel = Panel.SOUND)
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
