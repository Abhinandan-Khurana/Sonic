package app.runout.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.runout.R

/**
 * The room's palette and type, after Teenage Engineering's catalogue: black ink on white, hairline
 * rules, square corners, no shadows, and one dot-matrix face for every word. Colour is a signal,
 * never a fill: orange for what's playing, blue for links, green for online, amber for warnings,
 * red for errors. The 3D deck is the only rich object on screen.
 */
object Room {
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF5F5F5)
    val Ink = Color(0xFF000000)
    val NearBlack = Color(0xFF0F0E12)
    val Body = Color(0xFF4D4D4D)
    val Quiet = Color(0xFF767676)
    val Faint = Color(0xFFB2B2B2)
    val Hairline = Color(0xFFCCCCCC)
    val HairlineSoft = Color(0xFFE5E5E5)
    val Chrome = Hairline

    /** Links and information. */
    val Accent = Color(0xFF0071BB)
    /** What's playing or active: Teenage Engineering orange. */
    val Signal = Color(0xFFF05A24)
    val SignalLight = Color(0xFFF7A07F)
    val SignalDark = Color(0xFFB8421A)
    val Online = Color(0xFF006837)
    val Warning = Color(0xFFFAB413)
    val Danger = Color(0xFFB81D13)
    /** Behind every panel and dialog. */
    val Scrim = Color(0x99767676)

    /** The grotesk, kept for the stickers printed on sleeves. */
    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

    /** The interface face; see [TeType]. */
    val Dot: FontFamily get() = TeType.family

    val Label: TextStyle get() = TextStyle(fontFamily = Dot, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp, color = Ink)
    val LabelQuiet: TextStyle get() = Label.copy(color = Quiet)
    val Title: TextStyle get() = TextStyle(fontFamily = Dot, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    val Brand: TextStyle get() = Label.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    /** Poster-size headings: the screen titles. */
    val Display: TextStyle get() = TextStyle(fontFamily = Dot, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = Ink)
}

/**
 * The dot-matrix face. Array (Indian Type Foundry, via Fontshare) when the build carries it in
 * assets/fonts/licensed: its licence lets the app embed it, not a public repository share it, so it
 * stays out of git. Otherwise Silkscreen (SIL Open Font License), which the repository ships.
 */
object TeType {
    private const val LICENSED = "fonts/licensed"
    private const val FALLBACK = "fonts/silkscreen.ttf"

    @Volatile
    var family: FontFamily = FontFamily.Monospace
        private set

    /** Whether Array is in this build (shown in About). */
    var isArray = false
        private set

    fun init(context: Context) {
        if (family != FontFamily.Monospace) return
        val assets = context.applicationContext.assets
        val licensed = runCatching { assets.list(LICENSED)?.toSet() }.getOrNull().orEmpty()
        family = if ("Array-Regular.ttf" in licensed) {
            isArray = true
            FontFamily(
                listOfNotNull(
                    Font("$LICENSED/Array-Regular.ttf", assets, FontWeight.Normal),
                    if ("Array-Semibold.ttf" in licensed) Font("$LICENSED/Array-Semibold.ttf", assets, FontWeight.SemiBold) else null,
                    if ("Array-Bold.ttf" in licensed) Font("$LICENSED/Array-Bold.ttf", assets, FontWeight.Bold) else null,
                ),
            )
        } else {
            runCatching { FontFamily(Font(FALLBACK, assets)) }.getOrDefault(FontFamily.Monospace)
        }
    }
}
