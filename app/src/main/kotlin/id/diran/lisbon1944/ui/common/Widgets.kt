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
import androidx.compose.ui.graphics.Color
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
    fun district(d: District): Color = when (d) {
        District.PELABUHAN -> Color(0xFF8A5A3B)
        District.ALFAMA -> Color(0xFF7FA7CF)
        District.BAIXA -> Color(0xFFB4457A)
        District.CHIADO -> Color(0xFFD9822B)
        District.AVENIDA -> Color(0xFFB3261E)
        District.ESTORIL -> Color(0xFF2F7A4D)
    }

    fun faction(content: GameContent?, f: Faction): Color {
        val hex = content?.faction(f)?.accentColor ?: return LisbonColors.StreetLamp
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(LisbonColors.StreetLamp)
    }

    fun evidence(t: EvidenceType): Color = when (t) {
        EvidenceType.SUSPECT -> Color(0xFF6B2D2A)
        EvidenceType.LOCATION -> LisbonColors.TagusBlue
        EvidenceType.ITEM -> LisbonColors.Olive
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
        modifier = modifier.fillMaxWidth().height(52.dp),
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
        modifier = modifier.fillMaxWidth().height(46.dp),
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
) {
    val bg = when {
        selected -> accent
        !enabled -> LisbonColors.PaperShade.copy(alpha = 0.5f)
        else -> LisbonColors.OldPaper
    }
    val fg = if (selected) LisbonColors.OldPaper else LisbonColors.Ink
    Column(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, if (selected) accent else LisbonColors.InkSoft.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2, textAlign = TextAlign.Center)
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
