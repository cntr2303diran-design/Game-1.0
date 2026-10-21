package id.diran.lisbon1944.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R

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
    /** Teks sekunder di atas kertas — kontras ≥ 4.5:1 terhadap OldPaper. */
    val InkMuted = Color(0xFF4A4038)
    /** Teks sekunder di atas tinta — kontras ≥ 4.5:1 terhadap Ink. */
    val PaperMuted = Color(0xFFC9BDA2)
}

/** Judul poster: Playfair Display SC (OFL). */
val PosterFamily = FontFamily(
    Font(R.font.playfair_sc_regular, FontWeight.Normal),
    Font(R.font.playfair_sc_bold, FontWeight.Bold),
    Font(R.font.playfair_sc_black, FontWeight.Black),
)

/** Teks kartu bergaya mesin ketik: Special Elite (Apache 2.0). */
val TypewriterFamily = FontFamily(Font(R.font.special_elite, FontWeight.Normal))

/** Isi & angka: Courier Prime (OFL) — monospace, angka otomatis tabular. */
val BodyFamily = FontFamily(
    Font(R.font.courier_prime_regular, FontWeight.Normal),
    Font(R.font.courier_prime_bold, FontWeight.Bold),
)

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

private val LisbonTypography = Typography(
    displayLarge = TextStyle(fontFamily = PosterFamily, fontWeight = FontWeight.Black, fontSize = 40.sp, letterSpacing = 3.sp),
    headlineMedium = TextStyle(fontFamily = PosterFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 2.sp),
    titleMedium = TextStyle(fontFamily = PosterFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = TypewriterFamily, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = PosterFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp),
)

/** Preferensi tampilan yang dibaca komponen di mana saja. */
data class UiPrefs(
    val colorBlind: Boolean = false,
    val showPossibilities: Boolean = true,
    val reduceMotion: Boolean = false,
)

val LocalUiPrefs = staticCompositionLocalOf { UiPrefs() }

/**
 * [textScale] 1.0–2.0 dikalikan dengan skala font sistem (BAGIAN 12.5: teks bisa diperbesar sampai 200%).
 */
@Composable
fun LisbonTheme(textScale: Float = 1f, prefs: UiPrefs = UiPrefs(), content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val density = Density(base.density, (base.fontScale * textScale).coerceIn(0.85f, 3f))
    CompositionLocalProvider(LocalDensity provides density, LocalUiPrefs provides prefs) {
        MaterialTheme(colorScheme = LisbonColorScheme, typography = LisbonTypography, content = content)
    }
}
