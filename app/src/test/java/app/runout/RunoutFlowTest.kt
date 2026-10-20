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
    @Test fun `first launch offers offline listening in a sheet over the room without requesting an account`() {
        val actions=RecordingActions()
        compose.setContent { RoomScreen(state().copy(onboardingComplete=false),actions) }
        compose.onNodeWithTag("turntable").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/10_setup_sheet.png")
        compose.onNodeWithTag("setup-offline").performClick()
        compose.onNodeWithTag("setup-finish").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("screen:setup").assertDoesNotExist()
        compose.onNodeWithTag("turntable").assertIsDisplayed()
        assertTrue(actions.log.none {it.startsWith("soulseekSignIn")})
    }
    @Test fun `connected setup can go back to the welcome choice without looping into sign in`() {
        compose.setContent { SetupScreen(state().copy(soulseekSession=SoulseekSession(SoulseekConnection.CONNECTED,"listener")),RecordingActions(),2,{}) }
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
        val sheets=listOf(Panel.DIG to "screen:search",Panel.DROPS to "panel:drops",Panel.FRIENDS to "panel:messages",Panel.PRESSING to "panel:pressing",Panel.SETTINGS to "panel:sound")
        for((panel,content) in sheets) {
            compose.onNodeWithTag("nav:${panel.name}").performScrollTo().performClick()
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
    @Test fun `a song found on this device goes on the platter cued, and only the deck plays it`() {
        val track=Track("t2","content://test/02.flac","Lowlight",2,1,60_000)
        val album=Album("night","Night Drive","Vela Park",2023,"",listOf(Track("t1","content://test/01.flac","Opening",1,1,60_000),track),AudioQuality("FLAC"))
        val hit=LocalMatch(album,track,1)
        val discovery=DiscoveryState("Lowlight",DiscoveryOptions(source=SearchSource.LOCAL),local=listOf(hit))
        val actions=RecordingActions()
        compose.setContent { DiscoveryScreen(state().copy(discovery=discovery),actions,{}, {}) }
        compose.onNodeWithText("Play song").assertDoesNotExist()
        compose.onNodeWithTag("cue-local:${hit.id}").performScrollTo().performClick()
        assertEquals(listOf("cue:Night Drive:t2"),actions.log)
        assertTrue(actions.log.none {it=="startStop"||it=="needleDrop"})
    }
    @Test fun `album preview must finish and have a destination before a folder can be downloaded`() {
        val file=SoulseekSearchResult("peer","Artist/Album/01 Song.flac",100)
        compose.setContent { FolderPreview(SoulseekFolderPreview("request","peer","Artist/Album",listOf(file),loading=false),state().copy(soulseekSession=SoulseekSession(SoulseekConnection.CONNECTED,"listener")),RecordingActions()) }
        compose.onNodeWithTag("confirm-folder").assertIsNotEnabled()
        compose.onNodeWithText("Choose download folder").assertIsDisplayed()
    }
    @Test fun `discovery uses the complete album group rather than page fragments`() {
        val files=(1..201).map {SoulseekSearchResult("peer","Artist/Album/${it.toString().padStart(3,'0')} Song.flac",100)}
        val options=DiscoveryOptions(source=SearchSource.SOULSEEK)
        val status=SoulseekSearchStatus("request","Album",SearchPhase.COMPLETE)
        val discovery=DiscoveryState("Album",options,remote=remoteAlbums("Album",files,options),online=status)
        val actions=RecordingActions()
        compose.setContent { DiscoveryScreen(state().copy(discovery=discovery,soulseekSession=SoulseekSession(SoulseekConnection.CONNECTED,"listener")),actions,{}, {}) }
        compose.onNodeWithTag("screen:search").performScrollToNode(hasText("peer · 201 matching tracks"))
        compose.onNodeWithText("peer · 201 matching tracks").assertIsDisplayed()
        compose.onNodeWithText("Preview album folder").performClick()
        assertTrue(actions.log.any {it.startsWith("soulseekFolder:")})
    }
}
