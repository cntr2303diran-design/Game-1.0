package id.diran.lisbon1944.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.ui.theme.LisbonColors

enum class Hat { NONE, FEDORA, BOWLER, USHANKA, BERET, BOATER, WIDE_BRIM, PEAKED_CAP, CLOCHE }
enum class Hair { NONE, SHORT, LONG, BOB, BUN, SLICKED }
enum class Facial { NONE, MUSTACHE, BEARD, STUBBLE, GOATEE }
enum class Collar { SUIT_TIE, TRENCH, UNIFORM, SCARF, DRESS, GREATCOAT, LAB_COAT }
enum class Extra { NONE, CAMERA, PIPE, EARRINGS, STETHOSCOPE, MEDALS, NOTEPAD, PEARLS, FEATHER, SCAR }

/** Ciri potret karakter — semua digambar dengan Canvas (desain orisinal, tanpa aset). */
data class PortraitSpec(
    val skin: Color,
    val hairColor: Color,
    val coat: Color,
    val hat: Hat = Hat.NONE,
    val hatColor: Color = Color(0xFF2A2420),
    val hair: Hair = Hair.SHORT,
    val facial: Facial = Facial.NONE,
    val collar: Collar = Collar.SUIT_TIE,
    val glasses: Boolean = false,
    val monocle: Boolean = false,
    val extra: Extra = Extra.NONE,
    val accent: Color = Color(0xFFB3261E),
)

object Portraits {
    private val skinLight = Color(0xFFF1CBA8)
    private val skinMid = Color(0xFFD9A27A)
    private val skinTan = Color(0xFFB57A52)
    private val skinDeep = Color(0xFF8A5A3B)

    fun agent(f: Faction): PortraitSpec = when (f) {
        Faction.MI6 -> PortraitSpec(skinLight, Color(0xFF5A3E2B), Color(0xFF1F2A44), Hat.BOWLER, Color(0xFF14110F), Hair.SHORT, Facial.MUSTACHE, Collar.SUIT_TIE, accent = Color(0xFF3A5A8C))
        Faction.OSS -> PortraitSpec(skinMid, Color(0xFF3B2A1E), Color(0xFF8A7458), Hat.FEDORA, Color(0xFF4A3B2C), Hair.SHORT, Facial.NONE, Collar.TRENCH, accent = Color(0xFF7A8B99))
        Faction.NKVD -> PortraitSpec(skinLight, Color(0xFF2B2B2B), Color(0xFF4B4F3A), Hat.USHANKA, Color(0xFF5C4A3A), Hair.SHORT, Facial.MUSTACHE, Collar.GREATCOAT, extra = Extra.MEDALS, accent = Color(0xFF9E2B25))
        Faction.RESISTANCE -> PortraitSpec(skinLight, Color(0xFF2E1F17), Color(0xFF3A3F4A), Hat.BERET, Color(0xFF1C1C24), Hair.BOB, Facial.NONE, Collar.SCARF, accent = Color(0xFF2F5D50))
        Faction.PORTUGAL_TRADER -> PortraitSpec(skinTan, Color(0xFF1E1A16), Color(0xFFE6DCC4), Hat.BOATER, Color(0xFFD9C48A), Hair.SLICKED, Facial.MUSTACHE, Collar.SUIT_TIE, extra = Extra.PIPE, accent = Color(0xFF8C5A2B))
        Faction.SWISS_JOURNALIST -> PortraitSpec(skinLight, Color(0xFFB98B4E), Color(0xFF6E6A62), Hat.NONE, hair = Hair.BOB, collar = Collar.TRENCH, glasses = true, extra = Extra.CAMERA, accent = Color(0xFFC8C3B8))
        Faction.SPANISH_SMUGGLER -> PortraitSpec(skinTan, Color(0xFF15110E), Color(0xFF5A2E22), Hat.WIDE_BRIM, Color(0xFF2A1E16), Hair.SHORT, Facial.BEARD, Collar.SCARF, accent = Color(0xFF8E3B2E))
        Faction.ABWEHR_DEFECTOR -> PortraitSpec(skinLight, Color(0xFFC9B48A), Color(0xFF4D5450), Hat.PEAKED_CAP, Color(0xFF3E4440), Hair.SHORT, Facial.NONE, Collar.UNIFORM, extra = Extra.SCAR, accent = Color(0xFF5A6066))
    }

    /** Potret 6 tersangka (S1–S6). */
    fun suspect(id: CardId): PortraitSpec? = when (id.value) {
        "S1" -> PortraitSpec(skinLight, Color(0xFFBDBDBD), Color(0xFF2B2B33), Hat.NONE, hair = Hair.NONE, facial = Facial.MUSTACHE, collar = Collar.SUIT_TIE, monocle = true, accent = Color(0xFF6B2D2A))
        "S2" -> PortraitSpec(skinLight, Color(0xFF1A1414), Color(0xFF7A1F3D), Hat.CLOCHE, Color(0xFF2A1A22), Hair.LONG, collar = Collar.DRESS, extra = Extra.FEATHER, accent = Color(0xFF7A1F3D))
        "S3" -> PortraitSpec(skinMid, Color(0xFF7A5A3A), Color(0xFF5B5A3A), Hat.PEAKED_CAP, Color(0xFF4B4A2E), Hair.SHORT, Facial.MUSTACHE, Collar.UNIFORM, extra = Extra.MEDALS, accent = Color(0xFF4B5320))
        "S4" -> PortraitSpec(skinTan, Color(0xFF2B1E16), Color(0xFF1F3A4A), Hat.NONE, hair = Hair.BUN, collar = Collar.DRESS, glasses = true, extra = Extra.PEARLS, accent = Color(0xFF2E4A62))
        "S5" -> PortraitSpec(skinDeep, Color(0xFF221A14), Color(0xFFE9E4D8), Hat.NONE, hair = Hair.SHORT, facial = Facial.GOATEE, collar = Collar.LAB_COAT, glasses = true, extra = Extra.STETHOSCOPE, accent = Color(0xFF3B6B5A))
        "S6" -> PortraitSpec(skinLight, Color(0xFF6A4A2A), Color(0xFF3E3A36), Hat.FEDORA, Color(0xFF3A3230), Hair.SHORT, Facial.STUBBLE, Collar.TRENCH, extra = Extra.NOTEPAD, accent = Color(0xFF6B4E2E))
        else -> null
    }
}

/** Potret karakter dalam medali bulat. [ring] = warna bingkai (mis. warna faksi atau sorotan giliran). */
@Composable
fun Portrait(
    spec: PortraitSpec,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color = LisbonColors.StreetLamp,
    ringWidth: Dp = 2.dp,
    description: String? = null,
) {
    Canvas(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(ringWidth, ring, CircleShape)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) { drawPortrait(spec) }
}

private fun DrawScope.drawPortrait(p: PortraitSpec) {
    val u = size.minDimension
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    fun s(w: Float, h: Float) = Size(w * u, h * u)
    val ink = Color(0xFF14110F)

    // Latar: lampu sorot hangat di atas warna aksen.
    drawRect(Brush.radialGradient(listOf(p.accent.copy(alpha = 1f).lighten(0.35f), p.accent, p.accent.darken(0.45f)), center = o(0.5f, 0.35f), radius = u * 0.75f))

    // Rambut panjang / bob di belakang kepala.
    when (p.hair) {
        Hair.LONG -> drawRoundRect(p.hairColor, o(0.27f, 0.26f), s(0.46f, 0.52f), CornerRadius(u * 0.2f))
        Hair.BOB -> drawRoundRect(p.hairColor, o(0.29f, 0.25f), s(0.42f, 0.36f), CornerRadius(u * 0.17f))
        else -> Unit
    }

    // Bahu & mantel.
    val shoulders = Path().apply {
        moveTo(0.06f * u, u)
        lineTo(0.08f * u, 0.9f * u)
        quadraticBezierTo(0.13f * u, 0.73f * u, 0.36f * u, 0.69f * u)
        lineTo(0.64f * u, 0.69f * u)
        quadraticBezierTo(0.87f * u, 0.73f * u, 0.92f * u, 0.9f * u)
        lineTo(0.94f * u, u)
        close()
    }
    drawPath(shoulders, p.coat)

    // Leher.
    drawRect(p.skin.darken(0.12f), o(0.44f, 0.56f), s(0.12f, 0.16f))

    // Kerah / pakaian.
    when (p.collar) {
        Collar.SUIT_TIE, Collar.LAB_COAT -> {
            drawPath(Path().apply { moveTo(0.4f * u, 0.69f * u); lineTo(0.5f * u, 0.86f * u); lineTo(0.6f * u, 0.69f * u); close() }, Color(0xFFF4F0E6))
            if (p.collar == Collar.SUIT_TIE) {
                drawPath(Path().apply { moveTo(0.48f * u, 0.71f * u); lineTo(0.52f * u, 0.71f * u); lineTo(0.535f * u, 0.85f * u); lineTo(0.5f * u, 0.9f * u); lineTo(0.465f * u, 0.85f * u); close() }, p.accent.darken(0.3f))
            }
            lapels(u, p.coat.darken(0.25f))
        }
        Collar.TRENCH -> {
            drawPath(Path().apply { moveTo(0.42f * u, 0.69f * u); lineTo(0.5f * u, 0.82f * u); lineTo(0.58f * u, 0.69f * u); close() }, Color(0xFFE9E1CF))
            lapels(u, p.coat.lighten(0.15f))
            drawCircle(p.coat.darken(0.4f), u * 0.015f, o(0.44f, 0.9f))
            drawCircle(p.coat.darken(0.4f), u * 0.015f, o(0.56f, 0.9f))
        }
        Collar.UNIFORM, Collar.GREATCOAT -> {
            drawRect(p.coat.darken(0.25f), o(0.4f, 0.68f), s(0.2f, 0.05f))
            drawLine(p.coat.darken(0.35f), o(0.5f, 0.73f), o(0.5f, u), strokeWidth = u * 0.012f)
            for (k in 0 until 3) drawCircle(Color(0xFFC9A227), u * 0.014f, o(0.5f, 0.78f + k * 0.07f))
            drawRect(Color(0xFFC9A227), o(0.18f, 0.74f), s(0.12f, 0.025f))
            drawRect(Color(0xFFC9A227), o(0.70f, 0.74f), s(0.12f, 0.025f))
        }
        Collar.SCARF -> {
            drawRoundRect(p.accent.lighten(0.2f), o(0.37f, 0.64f), s(0.26f, 0.09f), CornerRadius(u * 0.04f))
            drawRoundRect(p.accent.lighten(0.2f), o(0.53f, 0.68f), s(0.08f, 0.2f), CornerRadius(u * 0.03f))
        }
        Collar.DRESS -> {
            drawPath(Path().apply { moveTo(0.36f * u, 0.69f * u); quadraticBezierTo(0.5f * u, 0.86f * u, 0.64f * u, 0.69f * u); close() }, p.skin.darken(0.06f))
        }
    }
    if (p.collar == Collar.LAB_COAT) lapels(u, Color(0xFFD6D0C2))

    // Kepala & telinga.
    drawCircle(p.skin.darken(0.08f), u * 0.04f, o(0.335f, 0.46f))
    drawCircle(p.skin.darken(0.08f), u * 0.04f, o(0.665f, 0.46f))
    drawOval(p.skin, o(0.345f, 0.25f), s(0.31f, 0.38f))

    // Rambut depan.
    when (p.hair) {
        Hair.SHORT -> drawArc(p.hairColor, 180f, 180f, true, o(0.34f, 0.24f), s(0.32f, 0.2f))
        Hair.SLICKED -> drawArc(p.hairColor, 170f, 200f, true, o(0.34f, 0.23f), s(0.32f, 0.17f))
        Hair.BOB, Hair.LONG -> {
            drawArc(p.hairColor, 180f, 180f, true, o(0.33f, 0.23f), s(0.34f, 0.22f))
            drawRect(p.hairColor, o(0.33f, 0.33f), s(0.05f, 0.16f))
            drawRect(p.hairColor, o(0.62f, 0.33f), s(0.05f, 0.16f))
        }
        Hair.BUN -> {
            drawCircle(p.hairColor, u * 0.08f, o(0.5f, 0.2f))
            drawArc(p.hairColor, 180f, 180f, true, o(0.34f, 0.24f), s(0.32f, 0.18f))
        }
        Hair.NONE -> drawArc(p.skin.darken(0.1f), 200f, 140f, false, o(0.36f, 0.27f), s(0.28f, 0.1f), style = Stroke(u * 0.01f))
    }

    // Wajah: alis, mata, hidung, mulut.
    val brow = p.hairColor.darken(0.2f)
    drawLine(brow, o(0.41f, 0.405f), o(0.47f, 0.4f), strokeWidth = u * 0.016f, cap = StrokeCap.Round)
    drawLine(brow, o(0.53f, 0.4f), o(0.59f, 0.405f), strokeWidth = u * 0.016f, cap = StrokeCap.Round)
    drawCircle(ink, u * 0.017f, o(0.44f, 0.44f))
    drawCircle(ink, u * 0.017f, o(0.56f, 0.44f))
    drawLine(p.skin.darken(0.25f), o(0.5f, 0.45f), o(0.49f, 0.51f), strokeWidth = u * 0.012f, cap = StrokeCap.Round)
    when (p.facial) {
        Facial.MUSTACHE -> drawRoundRect(p.hairColor.darken(0.1f), o(0.44f, 0.525f), s(0.12f, 0.03f), CornerRadius(u * 0.015f))
        Facial.BEARD -> drawPath(Path().apply {
            moveTo(0.36f * u, 0.47f * u)
            quadraticBezierTo(0.38f * u, 0.66f * u, 0.5f * u, 0.65f * u)
            quadraticBezierTo(0.62f * u, 0.66f * u, 0.64f * u, 0.47f * u)
            lineTo(0.6f * u, 0.53f * u)
            lineTo(0.4f * u, 0.53f * u)
            close()
        }, p.hairColor)
        Facial.GOATEE -> {
            drawRoundRect(p.hairColor, o(0.45f, 0.525f), s(0.1f, 0.025f), CornerRadius(u * 0.01f))
            drawOval(p.hairColor, o(0.465f, 0.575f), s(0.07f, 0.06f))
        }
        Facial.STUBBLE -> drawOval(p.hairColor.copy(alpha = 0.28f), o(0.37f, 0.5f), s(0.26f, 0.13f))
        Facial.NONE -> Unit
    }
    val mouthY = if (p.facial == Facial.BEARD) 0.575f else 0.575f
    drawLine(if (p.collar == Collar.DRESS) Color(0xFF9E2B3A) else p.skin.darken(0.35f), o(0.47f, mouthY), o(0.53f, mouthY), strokeWidth = u * 0.014f, cap = StrokeCap.Round)

    // Kacamata / monokel.
    if (p.glasses) {
        drawCircle(ink, u * 0.042f, o(0.44f, 0.44f), style = Stroke(u * 0.011f))
        drawCircle(ink, u * 0.042f, o(0.56f, 0.44f), style = Stroke(u * 0.011f))
        drawLine(ink, o(0.482f, 0.44f), o(0.518f, 0.44f), strokeWidth = u * 0.01f)
    }
    if (p.monocle) {
        drawCircle(Color(0xFFC9A227), u * 0.045f, o(0.56f, 0.44f), style = Stroke(u * 0.012f))
        drawLine(Color(0xFFC9A227), o(0.6f, 0.47f), o(0.62f, 0.62f), strokeWidth = u * 0.007f)
    }

    // Aksesori.
    when (p.extra) {
        Extra.SCAR -> drawLine(Color(0xFF9E4A3A), o(0.39f, 0.45f), o(0.42f, 0.53f), strokeWidth = u * 0.01f)
        Extra.EARRINGS -> {
            drawCircle(Color(0xFFC9A227), u * 0.015f, o(0.335f, 0.5f)); drawCircle(Color(0xFFC9A227), u * 0.015f, o(0.665f, 0.5f))
        }
        Extra.PEARLS -> for (k in 0 until 7) drawCircle(Color(0xFFF4F0E6), u * 0.014f, o(0.41f + k * 0.03f, 0.715f + (if (k in 2..4) 0.02f else 0f)))
        Extra.MEDALS -> {
            drawRect(Color(0xFFB3261E), o(0.27f, 0.8f), s(0.04f, 0.04f)); drawCircle(Color(0xFFC9A227), u * 0.02f, o(0.29f, 0.86f))
            drawRect(Color(0xFF3A5A8C), o(0.33f, 0.8f), s(0.04f, 0.04f)); drawCircle(Color(0xFFC9A227), u * 0.02f, o(0.35f, 0.86f))
        }
        Extra.CAMERA -> {
            drawRoundRect(ink, o(0.6f, 0.78f), s(0.2f, 0.13f), CornerRadius(u * 0.02f))
            drawCircle(Color(0xFF6E7A86), u * 0.035f, o(0.7f, 0.845f))
            drawLine(ink, o(0.38f, 0.7f), o(0.62f, 0.8f), strokeWidth = u * 0.01f)
        }
        Extra.PIPE -> {
            drawLine(Color(0xFF3B2414), o(0.53f, 0.585f), o(0.64f, 0.62f), strokeWidth = u * 0.016f, cap = StrokeCap.Round)
            drawRoundRect(Color(0xFF3B2414), o(0.62f, 0.58f), s(0.05f, 0.06f), CornerRadius(u * 0.01f))
        }
        Extra.STETHOSCOPE -> {
            drawArc(Color(0xFF2B2B33), 20f, 140f, false, o(0.36f, 0.6f), s(0.28f, 0.2f), style = Stroke(u * 0.014f))
            drawCircle(Color(0xFF9AA3AA), u * 0.025f, o(0.62f, 0.8f))
        }
        Extra.NOTEPAD -> {
            drawRect(Color(0xFFF4F0E6), o(0.62f, 0.8f), s(0.14f, 0.16f))
            for (k in 0 until 3) drawLine(Color(0xFF6E6A62), o(0.64f, 0.84f + k * 0.035f), o(0.74f, 0.84f + k * 0.035f), strokeWidth = u * 0.006f)
        }
        Extra.FEATHER, Extra.NONE -> Unit
    }

    // Topi (digambar terakhir).
    val hc = p.hatColor
    when (p.hat) {
        Hat.FEDORA -> {
            drawOval(hc.darken(0.15f), o(0.22f, 0.27f), s(0.56f, 0.08f))
            drawRoundRect(hc, o(0.33f, 0.13f), s(0.34f, 0.18f), CornerRadius(u * 0.06f))
            drawRect(p.accent.darken(0.4f), o(0.33f, 0.25f), s(0.34f, 0.035f))
            drawLine(hc.darken(0.3f), o(0.5f, 0.14f), o(0.5f, 0.2f), strokeWidth = u * 0.012f)
        }
        Hat.BOWLER -> {
            drawRoundRect(hc, o(0.28f, 0.26f), s(0.44f, 0.05f), CornerRadius(u * 0.025f))
            drawArc(hc, 180f, 180f, true, o(0.33f, 0.1f), s(0.34f, 0.34f))
            drawRect(Color(0xFF3A3A3A), o(0.33f, 0.24f), s(0.34f, 0.025f))
        }
        Hat.USHANKA -> {
            drawRoundRect(hc, o(0.3f, 0.11f), s(0.4f, 0.2f), CornerRadius(u * 0.08f))
            drawRoundRect(hc.darken(0.1f), o(0.29f, 0.26f), s(0.09f, 0.22f), CornerRadius(u * 0.04f))
            drawRoundRect(hc.darken(0.1f), o(0.62f, 0.26f), s(0.09f, 0.22f), CornerRadius(u * 0.04f))
            drawCircle(Color(0xFFB3261E), u * 0.03f, o(0.5f, 0.2f))
        }
        Hat.BERET -> rotate(-12f, o(0.5f, 0.24f)) {
            drawOval(hc, o(0.3f, 0.16f), s(0.42f, 0.13f))
            drawCircle(hc, u * 0.015f, o(0.52f, 0.15f))
        }
        Hat.BOATER -> {
            drawOval(hc.darken(0.1f), o(0.24f, 0.26f), s(0.52f, 0.07f))
            drawRect(hc, o(0.34f, 0.17f), s(0.32f, 0.11f))
            drawRect(p.accent.darken(0.3f), o(0.34f, 0.23f), s(0.32f, 0.03f))
        }
        Hat.WIDE_BRIM -> {
            drawOval(hc.darken(0.1f), o(0.12f, 0.25f), s(0.76f, 0.1f))
            drawRoundRect(hc, o(0.35f, 0.16f), s(0.3f, 0.13f), CornerRadius(u * 0.04f))
            drawRect(Color(0xFFC9A227), o(0.35f, 0.25f), s(0.3f, 0.02f))
        }
        Hat.PEAKED_CAP -> {
            drawOval(hc, o(0.28f, 0.14f), s(0.44f, 0.13f))
            drawRect(hc.darken(0.1f), o(0.34f, 0.2f), s(0.32f, 0.08f))
            drawOval(ink, o(0.33f, 0.26f), s(0.34f, 0.05f))
            drawCircle(Color(0xFFC9A227), u * 0.022f, o(0.5f, 0.22f))
        }
        Hat.CLOCHE -> {
            drawArc(hc, 180f, 180f, true, o(0.31f, 0.15f), s(0.38f, 0.28f))
            drawRect(hc, o(0.31f, 0.28f), s(0.38f, 0.03f))
            if (p.extra == Extra.FEATHER) {
                drawLine(Color(0xFFE9E1CF), o(0.62f, 0.25f), o(0.78f, 0.08f), strokeWidth = u * 0.03f, cap = StrokeCap.Round)
            }
        }
        Hat.NONE -> Unit
    }
}

private fun DrawScope.lapels(u: Float, c: Color) {
    drawPath(Path().apply { moveTo(0.36f * u, 0.69f * u); lineTo(0.47f * u, 0.86f * u); lineTo(0.4f * u, 0.95f * u); lineTo(0.3f * u, 0.72f * u); close() }, c)
    drawPath(Path().apply { moveTo(0.64f * u, 0.69f * u); lineTo(0.53f * u, 0.86f * u); lineTo(0.6f * u, 0.95f * u); lineTo(0.7f * u, 0.72f * u); close() }, c)
}

fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

/** Gambar kecil kartu Bukti: potret untuk Tersangka, ikon distrik untuk Lokasi, ikon barang untuk Barang. */
@Composable
fun EvidenceThumb(content: id.diran.lisbon1944.engine.content.GameContent, card: CardId, size: Dp, modifier: Modifier = Modifier) {
    val ev = runCatching { content.evidence(card) }.getOrNull() ?: return
    val spec = Portraits.suspect(card)
    if (spec != null) {
        Portrait(spec, size, modifier, ring = Palette.evidence(ev.type), ringWidth = 2.dp)
        return
    }
    val glyph = ev.district?.let { Palette.districtIcon(it) } ?: itemGlyph(card)
    val bg = ev.district?.let { Palette.district(it) } ?: Palette.evidence(ev.type)
    androidx.compose.foundation.layout.Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, Palette.evidence(ev.type), CircleShape)
            .then(Modifier.background(bg)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(glyph, color = Palette.onColor(bg), fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.45f, androidx.compose.ui.unit.TextUnitType.Sp))
    }
}

private fun itemGlyph(card: CardId): String = when (card.value) {
    "I1" -> "▤" // mikrofilm
    "I2" -> "⚙" // rotor mesin sandi
    "I3" -> "⌖" // peta pantai pendaratan
    "I4" -> "❏" // buku kode
    "I5" -> "☰" // daftar nama agen
    "I6" -> "◩" // foto udara
    else -> "✦"
}
