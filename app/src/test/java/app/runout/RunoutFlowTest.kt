package app.runout

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.runout.discovery.*
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Track
import app.runout.playback.DeckState
import app.runout.soulseek.*
import app.runout.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w412dp-h892dp-xhdpi",application=Application::class)
class RunoutFlowTest {
    @get:Rule val compose=createComposeRule()
    private fun state()=RoomState(emptyList(),false,null,DeckState(),false,"12:00")
    @Test fun `first launch plays the intro, then offers offline listening without requesting an account`() {
        val actions=RecordingActions()
        compose.setContent { RoomScreen(state().copy(onboardingComplete=false),actions) }
        compose.onNodeWithTag("turntable").assertExists()
        // The intro plays, then hands over to the sign-in by itself.
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/10_setup_login.png")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/readme/login.png")
        compose.onNodeWithTag("setup-offline").performClick()
        compose.onNodeWithTag("setup-finish").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("screen:setup").assertDoesNotExist()
        compose.onNodeWithTag("turntable").assertIsDisplayed()
        assertTrue(actions.log.none {it.startsWith("soulseekSignIn")})
    }
    @Test fun `the intro plays first and a tap skips it`() {
        compose.mainClock.autoAdvance=false
        compose.setContent { RoomScreen(state().copy(onboardingComplete=false),RecordingActions()) }
        compose.mainClock.advanceTimeBy(2_200)
        compose.onNodeWithTag("onboarding-intro").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/13_intro.png")
        // Later on: the grooves lit and RUNOUT typing out (for the README).
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/readme/intro.png")
        compose.onNodeWithTag("onboarding-intro").performClick()
        compose.mainClock.autoAdvance=true
        compose.waitForIdle()
        compose.onNodeWithTag("onboarding-intro").assertDoesNotExist()
        compose.onNodeWithTag("submit-soulseek").assertExists()
    }
    @Test fun `connected setup can go back to the welcome choice without looping into sign in`() {
        compose.setContent { SetupScreen(state().copy(soulseekSession=SoulseekSession(SoulseekConnection.CONNECTED,"listener")),RecordingActions(),2) {} }
        compose.onNodeWithTag("setup-back").performClick()
        compose.onNodeWithTag("setup-offline").assertIsDisplayed()
    }
    @Test fun `the room is home and its pills open every sheet, with downloads one tap away`() {
        compose.setContent { RoomScreen(state(),RecordingActions()) }
        compose.onNodeWithTag("turntable").assertIsDisplayed()
        // The original room: no tab bar, no mini player, no app name over the turntable.
        compose.onNodeWithTag("destination:HOME").assertDoesNotExist()
        compose.onNodeWithTag("mini-play").assertDoesNotExist()
        compose.onAllNodesWithText("RUNOUT").assertCountEquals(0)
        val sheets=listOf(Panel.DIG to "screen:search",Panel.DROPS to "panel:drops",Panel.FRIENDS to "panel:messages",Panel.SETTINGS to "panel:settings")
        for((panel,content) in sheets) {
            compose.onNodeWithTag("nav:${panel.name}").performClick()
            compose.onNodeWithTag(content).assertExists()
            if(panel==Panel.DIG) compose.onRoot().captureRoboImage("build/outputs/roborazzi/11_dig_sheet.png")
            compose.onNodeWithTag("action:Close").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(content).assertDoesNotExist()
        }
        compose.onNodeWithTag("nav:DOWNLOADS").assertDoesNotExist()
        compose.onNodeWithTag("open-downloads").performClick()
        compose.onNodeWithTag("panel:transfers").assertIsDisplayed()
        compose.onNodeWithTag("action:Close").performClick()
    }
    @Test fun `an album found on this device goes on the platter cued, and only the deck plays it`() {
        val track=Track("t2","content://test/02.flac","Lowlight",2,1,60_000)
        val album=Album("night","Night Drive","Vela Park",2023,"",listOf(Track("t1","content://test/01.flac","Opening",1,1,60_000),track),AudioQuality("FLAC"))
        // A song match still surfaces its album: Dig shows albums only.
        val discovery=DiscoveryState("Lowlight",DiscoveryOptions(source=SearchSource.LOCAL),local=listOf(LocalMatch(album,track,1)))
        val actions=RecordingActions()
        compose.setContent { DiscoveryScreen(state().copy(discovery=discovery),actions,{}, {}) }
        // Only the search field says "Lowlight": no song row.
        compose.onAllNodesWithText("Lowlight").assertCountEquals(1)
        compose.onNodeWithTag("cue-local:album:night").performScrollTo().performClick()
        assertEquals(listOf("cue:Night Drive:null"),actions.log)
        assertTrue(actions.log.none {it=="startStop"||it=="needleDrop"})
    }
    @Test fun `album preview must finish and have a destination before a folder can be downloaded`() {
        val file=SoulseekSearchResult("peer","Artist/Album/01 Song.flac",100)
        compose.setContent { FolderPreview(SoulseekFolderPreview("request","peer","Artist/Album",listOf(file),loading=false),state().copy(soulseekSession=SoulseekSession(SoulseekConnection.CONNECTED,"listener")),RecordingActions()) }
        compose.onNodeWithTag("confirm-folder").assertIsNotEnabled()
        compose.onNodeWithText("Choose download folder").assertIsDisplayed()
    }
    @Test fun `discovery shows whole album folders and a tap on one opens its preview`() {
        val files=(1..201).map {SoulseekSearchResult("peer","Artist/Album/${it.toString().padStart(3,'0')} Song.flac",100)}
        val options=DiscoveryOptions(source=SearchSource.SOULSEEK)
        val status=SoulseekSearchStatus("request","Album",SearchPhase.COMPLETE)
        val discovery=DiscoveryState("Album",options,remote=remoteAlbums("Album",files,options),online=status)
        val actions=RecordingActions()
        compose.setContent { DiscoveryScreen(state().copy(discovery=discovery,soulseekSession=SoulseekSession(SoulseekConnection.CONNECTED,"listener")),actions,{}, {}) }
        // Albums only: no song/album switch and no "all sources".
        compose.onNodeWithText("Songs").assertDoesNotExist()
        compose.onNodeWithText("All sources").assertDoesNotExist()
        compose.onNodeWithText("FLAC · 201 TRACKS", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Preview album folder").assertDoesNotExist()
        compose.onNodeWithTag("preview-folder:peer\u0000Artist/Album").performClick()
        assertTrue(actions.log.any {it.startsWith("soulseekFolder:")})
        // Filters & sort live behind a floating key.
        compose.onNodeWithTag("search-filters").performClick()
        compose.onNodeWithTag("search-filter-card").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/17_dig_results.png")
    }
}
