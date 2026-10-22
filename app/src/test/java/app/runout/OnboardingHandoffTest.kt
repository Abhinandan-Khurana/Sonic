package app.runout

import android.app.Application
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import app.runout.playback.DeckState
import app.runout.ui.RoomScreen
import app.runout.ui.RoomState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The end of onboarding: the welcome's deck is the room's own, in its own place, so finishing
 * drops you into the room without the deck moving.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi", application = Application::class)
class OnboardingHandoffTest {
    @get:Rule val compose = createComposeRule()

    private val atWelcome = RoomState(emptyList(), false, null, DeckState(), false, "12:00")
        .copy(onboardingComplete = false, onboardingStep = 5)

    private fun assertSameBounds(a: DpRect, b: DpRect) {
        fun near(x: Dp, y: Dp) = abs(x.value - y.value) <= 1f
        assertTrue("welcome deck $a vs room deck $b", near(a.left, b.left) && near(a.top, b.top) && near(a.right, b.right) && near(a.bottom, b.bottom))
    }

    private fun welcomeOverRoom(shots: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent { RoomScreen(atWelcome, RecordingActions()) }
        compose.mainClock.advanceTimeBy(700)
        val room = compose.onNodeWithTag("turntable").getBoundsInRoot()
        val welcome = compose.onNodeWithTag("welcome-deck", useUnmergedTree = true).getBoundsInRoot()
        assertSameBounds(welcome, room)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${shots}_0_welcome.png")

        // Skip the hold: the greeting fades off the deck and the room rises in around it.
        compose.onNodeWithTag("setup-welcome").performClick()
        var at = 0L
        listOf(100L, 160L, 160L, 220L).forEachIndexed { i, ms ->
            compose.mainClock.advanceTimeBy(ms)
            at += ms
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${shots}_${i + 1}_at_${at}ms.png")
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithTag("screen:setup").assertDoesNotExist()
        // The deck never moved.
        assertSameBounds(compose.onNodeWithTag("turntable").getBoundsInRoot(), room)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${shots}_5_room.png")
    }

    @Test fun `the welcome's deck is the room's deck, in portrait`() = welcomeOverRoom("40_handoff_portrait")

    @Config(qualifiers = "w892dp-h412dp-xhdpi")
    @Test fun `the welcome's deck is the room's deck, in landscape`() = welcomeOverRoom("41_handoff_landscape")

    @Test fun `the intro draws its deck where the room's will be`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { RoomScreen(atWelcome.copy(onboardingStep = 0), RecordingActions()) }
        compose.onNodeWithTag("onboarding-intro").assertExists()
        // The record is down and the arm in the groove (about 1.9 s into the storyboard, paced 1.8×).
        compose.mainClock.advanceTimeBy(3600)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/42_intro_over_room.png")
    }
}
