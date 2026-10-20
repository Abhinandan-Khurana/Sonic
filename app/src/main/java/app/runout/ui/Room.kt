package app.runout.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.runout.R

/** The room's palette and type: Y2K white, tiny grotesk labels, one blue accent. */
object Room {
    val Background = Color(0xFFF3EFE7)
    val Ink = Color(0xFF1F1E1B)
    val Quiet = Color(0xFF6E685D)
    val Faint = Color(0xFF646B60)
    val Accent = Color(0xFF276347)
    val Chrome = Color(0xFFD7D8DC)

    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

    val Label = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.sp, color = Ink)
    val LabelQuiet = Label.copy(color = Quiet)
    val Title = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
    val Brand = Label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
}
