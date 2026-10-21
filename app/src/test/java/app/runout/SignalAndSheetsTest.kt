package app.runout

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.runout.discovery.DiscoveryOptions
import app.runout.discovery.DiscoveryState
import app.runout.discovery.SearchSource
import app.runout.library.ScanReport
import app.runout.library.UnreadableFile
import app.runout.playback.DeckState
import app.runout.soulseek.SoulseekConnection
import app.runout.soulseek.SoulseekPolicy
import app.runout.soulseek.SoulseekSession
import app.runout.sound.BitPerfect
import app.runout.sound.OutputDevice
import app.runout.sound.OutputKind
import app.runout.sound.OutputStatus
import app.runout.sound.SoundSettings
import app.runout.ui.Panel
import app.runout.ui.RoomScreen
import app.runout.ui.RoomState
import app.runout.ui.SettingsSection
import app.runout.ui.signalPath
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The audio-quality chain, Crackle as a switch, Repair local albums, and page-wide sheets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi", application = Application::class)
class SignalAndSheetsTest {
    @get:Rule val compose = createComposeRule()

    private val speaker48 = OutputDevice(OutputKind.SPEAKER, mixerRate = 48_000)
    private val playing = OutputStatus(24, 96_000, floatPath = true, processing = true, device = speaker48)
    private val room = RoomState(listOf(Fixtures.nightDrive()), true, null, DeckState(), true, "12:00")
        .copy(output = playing, soulseekSession = SoulseekSession(SoulseekConnection.CONNECTED, "listener"))

    @Test fun `a hi-res file on the phone speaker reads as lossless, resampled by Android's mixer`() {
        val path = signalPath(playing, SoundSettings())!!
        assertEquals("24-bit · 96 kHz", path.track.spec)
        assertEquals("32-bit float · 96 kHz", path.runout.spec)
        assertEquals("Mixer · 48 kHz", path.output.spec)
        assertEquals("Lossless, resampled to 48 kHz", path.verdict)
        assertEquals("24/96 → Phone speaker", path.short)
        assertTrue(path.note!!.contains("USB DAC"))
    }

    @Test fun `Pure to a bit-perfect USB DAC keeps the file's own format all the way`() {
        val dac = playing.copy(processing = false, bitPerfect = BitPerfect.ON, device = OutputDevice(OutputKind.USB, "FiiO KA3", 48_000))
        val path = signalPath(dac, SoundSettings(pure = true))!!
        assertEquals("Bit-perfect", path.verdict)
        assertEquals("Pure", path.runout.title)
        assertEquals("24-bit · 96 kHz", path.output.spec)
        assertEquals("USB DAC · FiiO KA3", path.output.title)
        assertNull(path.note)
    }

    @Test fun `Bluetooth is lossy, and Hi-res off cuts 24-bit to 16`() {
        val bt = signalPath(playing.copy(device = OutputDevice(OutputKind.BLUETOOTH, "Buds")), SoundSettings())!!
        assertEquals("Lossy over Bluetooth", bt.verdict)
        assertFalse(bt.output.ok)
        val folded = signalPath(playing.copy(floatPath = false), SoundSettings(hiResOutput = false))!!
        assertEquals("Cut to 16-bit", folded.verdict)
        assertFalse(folded.runout.ok)
        assertNull(signalPath(OutputStatus(), SoundSettings()))
    }

    @Test fun `the home screen shows the chain as a chip that opens the Audio quality sheet`() {
        compose.setContent { RoomScreen(room, RecordingActions()) }
        compose.onNodeWithTag("signal-chip").assertIsDisplayed().performClick()
        compose.onNodeWithTag("panel:signal").assertIsDisplayed()
        compose.onNodeWithTag("signal-verdict").assertTextEquals("Lossless, resampled to 48 kHz")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/30_audio_quality.png")
    }

    @Test fun `Crackle on the home screen is a switch`() {
        val actions = RecordingActions()
        compose.setContent { RoomScreen(room, actions) }
        compose.onNodeWithTag("toggle-crackle").assertIsDisplayed().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Switch))
        compose.onNodeWithTag("toggle-crackle").performClick()
        assertTrue(actions.log.contains("crackle"))
        compose.onNodeWithText("Crackle on").assertDoesNotExist()
    }

    @Test fun `Settings › Library repairs local albums and lists the files it couldn't read`() {
        val actions = RecordingActions()
        val report = ScanReport(4, 12, 2, 3, emptyMap(), unreadableFiles = listOf(
            UnreadableFile("Music/Air/Moon Safari/03 All I Need.flac", "An MP3 (or other format) renamed to .flac. Only real FLAC plays in Runout."),
            UnreadableFile("Music/Air/Moon Safari/04 Kelly.flac", "Damaged FLAC: the header or the first audio frame is broken, or the download was cut off."),
        ))
        compose.setContent { RoomScreen(room.copy(report = report), actions, initialPanel = Panel.SETTINGS, initialSettings = SettingsSection.LIBRARY) }
        compose.onNodeWithTag("settings-repair").performScrollTo().performClick()
        assertTrue(actions.log.contains("repair"))
        compose.onNodeWithTag("repair-report").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 files couldn't be read").assertExists()
        compose.onNodeWithText("Music/Air/Moon Safari/03 All I Need.flac").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/31_repair_library.png")
    }

    @Test fun `Dig searches with a round key, and its filters sheet shades the whole page`() {
        val actions = RecordingActions()
        val dig = room.copy(discovery = DiscoveryState("moon safari", DiscoveryOptions(source = SearchSource.SOULSEEK)))
        compose.setContent { RoomScreen(dig, actions, initialPanel = Panel.DIG) }
        compose.onNodeWithText("Search Soulseek").assertDoesNotExist()
        compose.onNodeWithTag("submit-search").assertIsDisplayed().performClick()
        assertTrue(actions.log.contains("soulseekSearch:moon safari"))

        compose.onNodeWithTag("search-filters").performClick()
        compose.onNodeWithTag("search-filter-card").assertIsDisplayed()
        val root = compose.onRoot().getBoundsInRoot()
        val shade = compose.onNodeWithTag("sheet-shade").getBoundsInRoot()
        assertEquals(root, shade)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/32_dig_filters_sheet.png")
        compose.onNodeWithTag("sheet-shade").performTouchInput { click(topCenter + androidx.compose.ui.geometry.Offset(0f, 40f)) }
        compose.onNodeWithTag("search-filter-card").assertDoesNotExist()
    }

    @Test fun `the new chat sheet shades the whole Friends page`() {
        compose.setContent { RoomScreen(room, RecordingActions(), initialPanel = Panel.FRIENDS) }
        compose.onNodeWithTag("new-chat").performClick()
        compose.onNodeWithTag("new-chat-card").assertIsDisplayed()
        assertEquals(compose.onRoot().getBoundsInRoot(), compose.onNodeWithTag("sheet-shade").getBoundsInRoot())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/33_new_chat_sheet.png")
    }

    @Test fun `an interrupted read is recognised as Android pausing the app`() {
        assertTrue(SoulseekPolicy.isInterruption("Stream read error: Interrupted system call (os error 4)"))
        assertFalse(SoulseekPolicy.isInterruption("Stream read error: Connection reset by peer (os error 104)"))
        assertFalse(SoulseekPolicy.isInterruption(null))
    }
}
