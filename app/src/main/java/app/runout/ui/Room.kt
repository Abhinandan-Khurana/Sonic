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
        Font(R.font.inter_bold, FontWeight.Bold),
        Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    )

    /** The interface face; see [TeType]. */
    val Dot: FontFamily get() = TeType.family

    // The type scale of runout-te.pen: Array at two weights only, 400 and 700 (the dotted cut).
    /** label / 10 / 400 */
    val Label: TextStyle get() = TextStyle(fontFamily = Dot, fontSize = 10.sp, fontWeight = FontWeight.Normal, color = Ink)
    val LabelQuiet: TextStyle get() = Label.copy(color = Quiet)
    /** body / 12 / 400: row titles, keys, links. */
    val Text: TextStyle get() = Label.copy(fontSize = 12.sp)
    /** 12 / 700: the side on the top bar. */
    val Strong: TextStyle get() = Label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    /** heading / 16 / 400: fields and headings. */
    val Heading: TextStyle get() = Label.copy(fontSize = 16.sp)
    /** 18 / 700: the album under the crate. */
    val Album: TextStyle get() = Label.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    /** title / 22 / 700: panel and dialog titles. */
    val Title: TextStyle get() = Label.copy(fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
    val Brand: TextStyle get() = Label.copy(fontWeight = FontWeight.Bold)
    /** display / 44 / 700: the screen titles. */
    val Display: TextStyle get() = Label.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold)
    /** The stickers on the shrink-wrap are printed in Inter. */
    val Sticker: TextStyle get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, color = Ink)
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
                // The design sets only 400 and 700; anything heavier than 500 takes the dotted Bold.
                listOfNotNull(
                    Font("$LICENSED/Array-Regular.ttf", assets, FontWeight.Normal),
                    if ("Array-Bold.ttf" in licensed) Font("$LICENSED/Array-Bold.ttf", assets, FontWeight.Bold) else null,
                ),
            )
        } else {
            runCatching { FontFamily(Font(FALLBACK, assets)) }.getOrDefault(FontFamily.Monospace)
        }
    }
}
