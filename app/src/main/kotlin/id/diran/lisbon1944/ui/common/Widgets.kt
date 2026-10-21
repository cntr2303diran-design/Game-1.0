package id.diran.lisbon1944.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.ui.theme.LisbonColors

object Palette {
    /** BAGIAN 12.3: Pelabuhan biru baja, Alfama terakota, Baixa oker, Chiado hijau botol, Avenida ungu anggur, Estoril emas. */
    fun district(d: District): Color = when (d) {
        District.PELABUHAN -> Color(0xFF4F6D8A)
        District.ALFAMA -> Color(0xFFB5562F)
        District.BAIXA -> Color(0xFFC9A227)
        District.CHIADO -> Color(0xFF1F5E3B)
        District.AVENIDA -> Color(0xFF6B2346)
        District.ESTORIL -> Color(0xFFD4AF37)
    }

    /** Ikon distrik — selalu tampil di mode buta warna. */
    fun districtIcon(d: District): String = when (d) {
        District.PELABUHAN -> "⚓"
        District.ALFAMA -> "✝"
        District.BAIXA -> "⚖"
        District.CHIADO -> "♪"
        District.AVENIDA -> "★"
        District.ESTORIL -> "☀"
    }

    fun faction(content: GameContent?, f: Faction): Color {
        val hex = content?.faction(f)?.accentColor ?: return LisbonColors.StreetLamp
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(LisbonColors.StreetLamp)
    }

    /** Warna teks yang terbaca di atas [bg] (kontras ≥ 4.5:1 untuk palet ini). */
    fun onColor(bg: Color): Color = if (bg.luminance() > 0.42f) LisbonColors.Ink else LisbonColors.OldPaper

    fun evidence(t: EvidenceType): Color = when (t) {
        EvidenceType.SUSPECT -> Color(0xFF6B2D2A)
        EvidenceType.LOCATION -> LisbonColors.TagusBlue
        EvidenceType.ITEM -> LisbonColors.Olive
    }
}

/** Pola distrik (garis, titik, kotak, …) agar distrik bisa dibedakan tanpa warna. */
fun Modifier.districtPattern(d: District, color: Color): Modifier = drawBehind {
    val c = color.copy(alpha = 0.55f)
    val step = 4.dp.toPx()
    val w = size.width
    val h = size.height
    val sw = 1.dp.toPx()
    when (d) {
        District.PELABUHAN -> { var y = step / 2; while (y < h) { drawLine(c, Offset(0f, y), Offset(w, y), sw); y += step } }
        District.ALFAMA -> { var x = -h; while (x < w) { drawLine(c, Offset(x, h), Offset(x + h, 0f), sw); x += step } }
        District.BAIXA -> { var y = step / 2; while (y < h) { var x = step / 2; while (x < w) { drawCircle(c, sw, Offset(x, y)); x += step }; y += step } }
        District.CHIADO -> {
            var y = 0f; while (y < h) { drawLine(c, Offset(0f, y), Offset(w, y), sw / 2); y += step }
            var x = 0f; while (x < w) { drawLine(c, Offset(x, 0f), Offset(x, h), sw / 2); x += step }
        }
        District.AVENIDA -> { var x = step / 2; while (x < w) { drawLine(c, Offset(x, 0f), Offset(x, h), sw); x += step } }
        District.ESTORIL -> {
            var x = -h; while (x < w) { drawLine(c, Offset(x, h), Offset(x + h, 0f), sw / 2); drawLine(c, Offset(x, 0f), Offset(x + h, h), sw / 2); x += step }
        }
    }
}

/** Dialog bertema berkas rahasia (pengganti AlertDialog Material). */
@Composable
fun LisbonDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    confirmEnabled: Boolean = true,
    danger: Boolean = false,
    onDismissClick: (() -> Unit)? = null,
    body: @Composable (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(20.dp).widthIn(max = 520.dp).fillMaxWidth(),
            color = LisbonColors.OldPaper,
            contentColor = LisbonColors.Ink,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(2.dp, if (danger) LisbonColors.FolderRed else LisbonColors.InkSoft),
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(title.uppercase(), style = MaterialTheme.typography.titleMedium, color = if (danger) LisbonColors.FolderRed else LisbonColors.Ink)
                Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(LisbonColors.InkSoft.copy(alpha = 0.4f)))
                if (body != null) Box(Modifier.heightIn(max = 520.dp)) { body() }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dismissText != null) SecondaryButton(dismissText, onDismissClick ?: onDismiss, Modifier.weight(1f))
                    if (confirmText != null && onConfirm != null) PrimaryButton(confirmText, onConfirm, Modifier.weight(1f), enabled = confirmEnabled)
                }
            }
        }
    }
}

/** Kartu kertas tua berbingkai tipis. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    accent: Color = LisbonColors.InkSoft,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = LisbonColors.OldPaper,
        contentColor = LisbonColors.Ink,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, accent),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = LisbonColors.FolderRed) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LisbonColors.FolderRed,
            contentColor = LisbonColors.OldPaper,
            disabledContainerColor = LisbonColors.PaperShade,
            disabledContentColor = LisbonColors.InkSoft,
        ),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, LisbonColors.InkSoft),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LisbonColors.OldPaper,
            contentColor = LisbonColors.Ink,
            disabledContainerColor = LisbonColors.PaperShade.copy(alpha = 0.6f),
            disabledContentColor = LisbonColors.InkSoft,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Tombol pilihan kecil yang bisa aktif/nonaktif (pengganti chip — tanpa API eksperimental). */
@Composable
fun ToggleTag(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LisbonColors.FolderRed,
    enabled: Boolean = true,
    sub: String? = null,
    dense: Boolean = false,
) {
    val bg = when {
        selected -> accent
        !enabled -> LisbonColors.PaperShade.copy(alpha = 0.5f)
        else -> LisbonColors.OldPaper
    }
    val fg = if (selected) LisbonColors.OldPaper else LisbonColors.Ink
    val isSelected = selected
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, if (selected) accent else LisbonColors.InkSoft.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.selected = isSelected; role = Role.Button }
            .padding(horizontal = if (dense) 4.dp else 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = if (dense) 1 else 3, softWrap = !dense, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (sub != null) Text(sub, color = fg.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1)
    }
}

/** Baris tombol pilihan yang membungkus ke baris berikutnya (tanpa FlowRow eksperimental). */
@Composable
fun <T> WrapGrid(items: List<T>, columns: Int, modifier: Modifier = Modifier, cell: @Composable (T, Modifier) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Meteran berpetak (Kecurigaan, Jam Perang). */
@Composable
fun PipMeter(value: Int, max: Int, color: Color, modifier: Modifier = Modifier, pip: Int = 8) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(max) { i ->
            Box(
                Modifier
                    .size(width = pip.dp, height = (pip + 2).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < value) color else color.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
fun Dot(color: Color, size: Int = 10, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

/** Cap stempel miring merah. */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, color: Color = LisbonColors.FolderRed) {
    Box(
        modifier
            .border(2.dp, color, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text.uppercase(), color = color, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp)
    }
}
