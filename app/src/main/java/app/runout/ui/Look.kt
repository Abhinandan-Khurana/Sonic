package app.runout.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The room's theme: the phone's own light or dark, or one of them pinned. */
enum class ThemeMode(val label: String) { PHONE("phone"), LIGHT("light"), DARK("dark") }

/**
 * The RG-1's colourways, each its own model from the Blender build (same parts, same names, other
 * materials). [MATCH] follows the theme: white / teal in light, dark in dark.
 */
enum class DeckSkin(val label: String, val asset: String?) {
    MATCH("theme", null),
    WHITE_TEAL("white / teal", "deck/turntable_white_teal.glb"),
    MONO_WHITE("mono white", "deck/turntable_mono_white.glb"),
    DARK("dark", "deck/turntable_dark.glb"),
}

/**
 * Settings › Flags › look: the theme, which deck stands in the room, and whether the needle-drop
 * intro plays each time the app opens (onboarding's first screen, without the steps after it).
 */
data class Look(val theme: ThemeMode = ThemeMode.PHONE, val deck: DeckSkin = DeckSkin.MATCH, val introOnLaunch: Boolean = false) {
    /** Whether the room is dark, given the phone's own setting. */
    fun dark(phoneDark: Boolean): Boolean = when (theme) {
        ThemeMode.PHONE -> phoneDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /** The deck to draw: the pinned one, or the theme's. */
    fun deckFor(dark: Boolean): DeckSkin = when (deck) {
        DeckSkin.MATCH -> if (dark) DeckSkin.DARK else DeckSkin.WHITE_TEAL
        else -> deck
    }
}

/** [Look], kept in the "look" preferences: following the phone and the theme, the intro off, until changed. */
class LookPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("look", Context.MODE_PRIVATE)
    private val _look = MutableStateFlow(read())
    val look: StateFlow<Look> = _look.asStateFlow()

    fun setTheme(theme: ThemeMode) = save(_look.value.copy(theme = theme))
    fun setDeck(deck: DeckSkin) = save(_look.value.copy(deck = deck))
    fun setIntroOnLaunch(on: Boolean) = save(_look.value.copy(introOnLaunch = on))

    private fun save(look: Look) {
        prefs.edit().putString(THEME, look.theme.name).putString(DECK, look.deck.name).putBoolean(INTRO, look.introOnLaunch).apply()
        _look.value = look
    }

    private fun read() = Look(
        prefs.getString(THEME, null)?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } } ?: ThemeMode.PHONE,
        prefs.getString(DECK, null)?.let { n -> DeckSkin.entries.firstOrNull { it.name == n } } ?: DeckSkin.MATCH,
        prefs.getBoolean(INTRO, false),
    )

    private companion object {
        const val THEME = "theme"
        const val DECK = "deck"
        const val INTRO = "intro_on_launch"
    }
}
