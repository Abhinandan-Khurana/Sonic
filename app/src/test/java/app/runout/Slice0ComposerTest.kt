package app.runout

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.runout.playback.DeckState
import app.runout.soulseek.SoulseekConnection
import app.runout.soulseek.SoulseekSession
import app.runout.ui.RoomState
import app.runout.ui.SoulseekMessagesPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi", application = Application::class)
class Slice0ComposerTest {
    @get:Rule val compose = createComposeRule()
    private fun open(actions: RecordingActions = RecordingActions()) {
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SoulseekMessagesPanel(RoomState(emptyList(), false, null, DeckState(), false, "12:00").copy(
                    soulseekSession = SoulseekSession(SoulseekConnection.CONNECTED, "fixture-owner"),
                    soulseekConversation = "fixture-peer",
                ), actions, {})
            }
        }
    }

    @Test fun `send request without durable insert preserves composer`() {
        val actions = RecordingActions()
        open(actions)
        compose.onNodeWithTag("field:Private message").performTextInput("Keep draft")
        compose.onNodeWithText("Send message").performScrollTo().performClick()
        assertEquals(1, actions.log.count { it.startsWith("soulseekMessage:") })
        compose.onNodeWithTag("field:Private message").assertTextContains("Keep draft")
    }

    @Test fun `durable insert acknowledgment clears composer before transport result`() {
        var persisted: (() -> Unit)? = null
        open(object : RecordingActions() {
            override fun sendSoulseekMessage(username: String, text: String, onPersisted: () -> Unit) { persisted = onPersisted }
        })
        compose.onNodeWithTag("field:Private message").performTextInput("Keep draft")
        compose.onNodeWithText("Send message").performScrollTo().performClick()
        compose.onNodeWithTag("field:Private message").assertTextContains("Keep draft")
        compose.runOnIdle { persisted!!() }
        compose.onNodeWithTag("field:Private message").assertTextEquals("", "Private message")
    }

    @Test fun `late durable insert cannot erase text typed after sending`() {
        var persisted: (() -> Unit)? = null
        open(object : RecordingActions() {
            override fun sendSoulseekMessage(username: String, text: String, onPersisted: () -> Unit) { persisted = onPersisted }
        })
        compose.onNodeWithTag("field:Private message").performTextInput("Keep draft")
        compose.onNodeWithText("Send message").performScrollTo().performClick()
        compose.onNodeWithTag("field:Private message").performTextInput(" edited")
        compose.runOnIdle { persisted!!() }
        compose.onNodeWithTag("field:Private message").assertTextContains("Keep draft edited")
    }

    @Test fun `multibyte draft above 4096 UTF8 bytes is not sent or truncated`() {
        val actions = RecordingActions()
        open(actions)
        val text = "界".repeat(1366)
        compose.onNodeWithTag("field:Private message").performTextInput(text)
        compose.onNodeWithText("Send message").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("field:Private message").assertTextContains(text)
        assertEquals(0, actions.log.count { it.startsWith("soulseekMessage:") })
    }

    @Test fun `4096 ASCII bytes remain valid without silent 4000 character truncation`() {
        open()
        val text = "a".repeat(4096)
        compose.onNodeWithTag("field:Private message").performTextInput(text)
        compose.onNodeWithTag("field:Private message").assertTextContains(text)
        compose.onNodeWithText("Send message").performScrollTo().assertIsEnabled()
    }
}
