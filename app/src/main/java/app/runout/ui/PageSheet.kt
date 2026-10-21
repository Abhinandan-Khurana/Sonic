package app.runout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Where a full page (Dig, Friends) draws its bottom sheets: over the whole page, header and system
 * bars included, so the shade is one even layer instead of a box inside the content's padding.
 */
internal class PageSheetHost {
    var sheet by mutableStateOf<(@Composable () -> Unit)?>(null)
}

internal val LocalPageSheetHost = compositionLocalOf<PageSheetHost?> { null }

/** Draws the sheet a page's content asked for, if any. Call last inside the page's root Box. */
@Composable
internal fun PageSheetLayer(host: PageSheetHost) {
    host.sheet?.invoke()
}

/**
 * A white sheet that rises from the bottom of the page over a shade. Tapping the shade or Back
 * closes it. Inside a full page it's drawn by the page itself (see [PageSheetHost]); anywhere else
 * it's drawn in place.
 */
@Composable
internal fun PageSheet(tag: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val host = LocalPageSheetHost.current
    val sheet: @Composable () -> Unit = { SheetBody(tag, onDismiss, content) }
    if (host == null) {
        sheet()
        return
    }
    // Hand the latest content to the page on every recomposition, and take it back when closed.
    SideEffect { host.sheet = sheet }
    DisposableEffect(host) { onDispose { if (host.sheet != null) host.sheet = null } }
}

@Composable
private fun SheetBody(tag: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // A sibling of the card, so the card's own controls stay separate for TalkBack.
        AnimatedVisibility(shown, enter = fadeIn()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .testTag("sheet-shade")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(shown, enter = slideInVertically { it } + fadeIn()) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color.Black.copy(alpha = 0.2f))
                    .background(Color.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 18.dp)
                    .testTag(tag),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The grab handle, so it reads as a sheet that belongs to the page.
                Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp).size(36.dp, 4.dp).background(Room.Ink.copy(alpha = 0.15f), RoundedCornerShape(50)))
                content()
            }
        }
    }
}
