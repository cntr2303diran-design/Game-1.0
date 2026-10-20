package id.diran.lisbon1944.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palet BAGIAN 12.3. */
object LisbonColors {
    val Ink = Color(0xFF14110F)
    val OldPaper = Color(0xFFEFE6D2)
    val FolderRed = Color(0xFFB3261E)
    val Olive = Color(0xFF4B5320)
    val StreetLamp = Color(0xFFE0A526)
    val TagusBlue = Color(0xFF2E4A62)

    val InkSoft = Color(0xFF2A2420)
    val PaperShade = Color(0xFFD9CDB2)
}

private val LisbonColorScheme = darkColorScheme(
    primary = LisbonColors.FolderRed,
    onPrimary = LisbonColors.OldPaper,
    secondary = LisbonColors.StreetLamp,
    onSecondary = LisbonColors.Ink,
    tertiary = LisbonColors.Olive,
    onTertiary = LisbonColors.OldPaper,
    background = LisbonColors.Ink,
    onBackground = LisbonColors.OldPaper,
    surface = LisbonColors.OldPaper,
    onSurface = LisbonColors.Ink,
    surfaceVariant = LisbonColors.PaperShade,
    onSurfaceVariant = LisbonColors.InkSoft,
    error = LisbonColors.FolderRed,
    onError = LisbonColors.OldPaper,
)

/**
 * Tipografi sementara memakai keluarga sistem serif (judul poster) dan monospace (mesin ketik).
 * Font Playfair Display SC / Special Elite dibundel di Tahap 6.
 */
private val PosterFamily = FontFamily.Serif
private val TypewriterFamily = FontFamily.Monospace

private val LisbonTypography = Typography(
    displayLarge = TextStyle(fontFamily = PosterFamily, fontWeight = FontWeight.Black, fontSize = 44.sp, letterSpacing = 4.sp),
    headlineMedium = TextStyle(fontFamily = PosterFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 2.sp),
    titleMedium = TextStyle(fontFamily = PosterFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = TypewriterFamily, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = TypewriterFamily, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = PosterFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 3.sp),
)

@Composable
fun LisbonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LisbonColorScheme, typography = LisbonTypography, content = content)
}
