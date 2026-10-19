package app.runout

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.runout.discovery.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w412dp-h892dp-xhdpi",application=Application::class)
class RunoutFlowTest {
    @get:Rule val compose=createComposeRule()
    private fun state()=RoomState(emptyList(),false,null,DeckState(),false,"12:00")
    @Test fun `first launch offers offline listening without requesting an account`() {
        val actions=RecordingActions()
        compose.setContent { RunoutScreen(state().copy(onboardingComplete=false),actions) }
        compose.onRoot().captureRoboImage("app/build/outputs/roborazzi/10_retro_welcome.png")
        compose.onNodeWithTag("setup-offline").performClick()
        compose.onNodeWithTag("setup-finish").assertIsDisplayed().performClick()
        compose.onNodeWithTag("screen:home").assertIsDisplayed()
        assertTrue(actions.log.none {it.startsWith("soulseekSignIn")})
    }
    @Test fun `connected setup can go back to the welcome choice without looping into sign in`() {
        compose.setContent { SetupScreen(state().copy(soulseekSession=SoulseekSession(SoulseekConnection.CONNECTED,"listener")),RecordingActions(),2,{}) }
        compose.onNodeWithTag("setup-back").performClick()
        compose.onNodeWithTag("setup-offline").assertIsDisplayed()
    }
    @Test fun `the four destinations preserve the vinyl home and make downloads globally available`() {
        compose.setContent { RunoutScreen(state(),RecordingActions()) }
        compose.onNodeWithTag("screen:home").assertIsDisplayed()
        compose.onNodeWithTag("destination:LIBRARY").performClick()
        compose.onNodeWithTag("screen:library").assertIsDisplayed()
        compose.onNodeWithTag("open-downloads").performClick()
        compose.onNodeWithTag("panel:transfers").assertIsDisplayed()
        compose.onNodeWithTag("close-page").performClick()
        compose.onNodeWithTag("destination:DISCOVER").performClick()
        compose.onNodeWithTag("screen:search").assertIsDisplayed()
        compose.onRoot().captureRoboImage("app/build/outputs/roborazzi/11_retro_discover.png")
        compose.onNodeWithTag("destination:MESSAGES").performClick()
        compose.onNodeWithTag("screen:messages").assertIsDisplayed()
        compose.onNodeWithTag("destination:HOME").performClick()
        compose.onNodeWithTag("screen:home").assertIsDisplayed()
    }
    @Test fun `album preview must finish and have a destination before a folder can be downloaded`() {
        val file=SoulseekSearchResult("peer","Artist/Album/01 Song.flac",100)
        compose.setContent { FolderPreviewScreen(SoulseekFolderPreview("request","peer","Artist/Album",listOf(file),loading=false),state().copy(soulseekSession=SoulseekSession(SoulseekConnection.CONNECTED,"listener")),RecordingActions()) }
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
