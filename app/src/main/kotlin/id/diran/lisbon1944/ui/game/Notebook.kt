package id.diran.lisbon1944.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.session.Lang
import id.diran.lisbon1944.session.LogKind
import id.diran.lisbon1944.session.Names
import id.diran.lisbon1944.ui.GameUi
import id.diran.lisbon1944.ui.common.EvidenceThumb
import id.diran.lisbon1944.ui.common.LisbonDialog
import id.diran.lisbon1944.ui.common.Portrait
import id.diran.lisbon1944.ui.common.Portraits
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.theme.LisbonColors
import id.diran.lisbon1944.ui.theme.LocalUiPrefs
import kotlin.math.roundToInt

/** Akses catatan manual (disimpan per permainan & per pemain di ViewModel). */
class NotesAccess(
    val marks: Map<String, String>,
    val setMark: (CardId, String, String?) -> Unit,
    val setNote: (CardId, String) -> Unit,
)

private val CYCLE = listOf("✓", "✗", "?")

/**
 * Catatan Detektif (BAGIAN 12.2 no. 6): grid 18 kartu × (tiap pemain + Amplop). Status otomatis dari pengetahuan,
 * tanda manual ✓ ✗ ? per sel, catatan teks per kartu, tab Log Dugaan, dan panel Kemungkinan Amplop.
 */
@Composable
fun NotebookPanel(ui: GameUi, content: GameContent, names: Names, notes: NotesAccess) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleTag(stringResource(R.string.notes_grid), tab == 0, { tab = 0 }, Modifier.weight(1f), accent = LisbonColors.TagusBlue)
            ToggleTag(stringResource(R.string.notes_suggestion_log), tab == 1, { tab = 1 }, Modifier.weight(1f), accent = LisbonColors.TagusBlue)
        }
        if (tab == 0) NotebookGrid(ui, content, names, notes) else SuggestionLog(ui)
    }
}

@Composable
private fun SuggestionLog(ui: GameUi) {
    val lines = ui.log.filter { it.kind == LogKind.MYSTERY || it.kind == LogKind.SECRET }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        if (lines.isEmpty()) Text(stringResource(R.string.notes_no_suggestions), color = LisbonColors.PaperMuted)
        lines.forEach { l ->
            Text(
                l.text,
                color = logColor(l.kind),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun NotebookGrid(ui: GameUi, content: GameContent, names: Names, notes: NotesAccess) {
    val v = ui.view
    val prefs = LocalUiPrefs.current
    val facts = v.knowledge.groupBy { it.cardId }
    val seats = listOf(v.me.id.value) + v.opponents.map { it.id.value }
    val poss = if (prefs.showPossibilities) ui.possibilities else null
    var editing by remember { mutableStateOf<CardId?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        if (poss != null) {
            PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.StreetLamp) {
                SectionTitle(stringResource(R.string.poss_title), color = LisbonColors.InkSoft)
                Text(stringResource(R.string.poss_combos, poss.combos), fontWeight = FontWeight.Bold)
                poss.best?.let { best ->
                    Text(
                        stringResource(R.string.poss_best, best.joinToString(" · ") { names.card(it) }, (poss.bestProb * 100).roundToInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(stringResource(R.string.poss_hint), style = MaterialTheme.typography.bodySmall, color = LisbonColors.InkMuted)
            }
            Spacer(Modifier.height(8.dp))
        }
        v.revealedEnvelope?.let { env ->
            PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
                SectionTitle(stringResource(R.string.notes_envelope))
                Text(env.cards.joinToString(" · ") { names.card(it) }, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(stringResource(R.string.notes_grid_hint), style = MaterialTheme.typography.bodySmall, color = LisbonColors.PaperMuted)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                // Kepala kolom.
                Row {
                    Box(Modifier.width((LABEL_W + CELL).dp).height(36.dp))
                    seats.forEach { s ->
                        HeaderCell(ui.playerNames[s] ?: "P${s + 1}", Palette.faction(content, seatFactions(v)[s] ?: v.me.faction), seatFactions(v)[s])
                    }
                    HeaderCell("✉", LisbonColors.FolderRed)
                    if (poss != null) HeaderCell("%", LisbonColors.StreetLamp)
                }
                for (type in EvidenceType.entries) {
                    Row(Modifier.padding(top = 6.dp)) {
                        Text(
                            names.type(type).uppercase(),
                            color = Palette.evidence(type).copy(alpha = 1f),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.background(LisbonColors.OldPaper).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    content.evidenceOfType(type).forEach { card ->
                        val known = facts[card.id].orEmpty()
                        val holder = known.firstOrNull()?.holder
                        val embassy = holder is Holder.Embassy || card.id in v.embassyCards
                        val note = notes.marks["${card.id.value}|note"]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(CELL.dp).background(LisbonColors.OldPaper), contentAlignment = Alignment.Center) {
                                EvidenceThumb(content, card.id, 36.dp)
                            }
                            Column(
                                Modifier
                                    .width(LABEL_W.dp)
                                    .height(CELL.dp)
                                    .background(LisbonColors.OldPaper)
                                    .border(0.5.dp, LisbonColors.InkSoft.copy(alpha = 0.3f))
                                    .clickable { editing = card.id }
                                    .padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                val struck = card.id in v.me.hand || (holder != null && holder !is Holder.Envelope)
                                Text(
                                    names.card(card.id),
                                    fontSize = 11.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (struck) LisbonColors.InkMuted else LisbonColors.Ink,
                                    fontWeight = if (struck) FontWeight.Normal else FontWeight.Bold,
                                )
                                val sub = when {
                                    embassy -> stringResource(R.string.notes_embassy)
                                    note != null -> "✎ $note"
                                    else -> null
                                }
                                if (sub != null) Text(sub, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LisbonColors.TagusBlue)
                            }
                            seats.forEach { s ->
                                val auto = when {
                                    s == v.me.id.value && card.id in v.me.hand -> "●"
                                    holder is Holder.Player && holder.id.value == s -> sourceGlyph(known.first().source)
                                    holder != null || embassy || card.id in v.me.hand -> "–"
                                    else -> null
                                }
                                val key = "${card.id.value}|$s"
                                MarkCell(auto, notes.marks[key], names.card(card.id)) { m -> notes.setMark(card.id, "$s", m) }
                            }
                            val envAuto = when {
                                v.revealedEnvelope?.cards?.contains(card.id) == true -> "★"
                                holder is Holder.Envelope -> "★"
                                holder != null || embassy || card.id in v.me.hand -> "✗"
                                else -> null
                            }
                            MarkCell(envAuto, notes.marks["${card.id.value}|env"], names.card(card.id)) { m -> notes.setMark(card.id, "env", m) }
                            if (poss != null) {
                                val pct = ((poss.envelopeProb[card.id] ?: 0.0) * 100).roundToInt()
                                Box(Modifier.size(CELL.dp).background(LisbonColors.Ink), contentAlignment = Alignment.Center) {
                                    Text("$pct", fontSize = 11.sp, color = if (pct >= 50) LisbonColors.StreetLamp else LisbonColors.PaperMuted, fontWeight = if (pct >= 50) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.notes_legend), style = MaterialTheme.typography.bodySmall, color = LisbonColors.PaperMuted)
    }

    editing?.let { card ->
        var text by remember(card) { mutableStateOf(notes.marks["${card.value}|note"].orEmpty()) }
        LisbonDialog(
            title = names.card(card),
            onDismiss = { editing = null },
            confirmText = stringResource(R.string.notes_save),
            onConfirm = {
                notes.setNote(card, text)
                editing = null
            },
            dismissText = stringResource(R.string.cancel),
        ) {
            Column {
                runCatching { content.evidence(card) }.getOrNull()?.let {
                    Text(if (names.lang == Lang.EN) it.blurbEn else it.blurb, style = MaterialTheme.typography.bodySmall, color = LisbonColors.InkMuted)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    label = { Text(stringResource(R.string.notes_note)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

private const val CELL = 44
private const val LABEL_W = 132

private fun sourceGlyph(s: KnowledgeSource): String = when (s) {
    KnowledgeSource.SUGGESTION -> "◉"
    KnowledgeSource.RENT_INFO -> "$"
    KnowledgeSource.BANKRUPTCY, KnowledgeSource.GAME_END -> "◍"
    else -> "●"
}

@Composable
private fun HeaderCell(label: String, color: androidx.compose.ui.graphics.Color, faction: id.diran.lisbon1944.engine.model.Faction? = null) {
    Box(
        Modifier.size(width = CELL.dp, height = 36.dp).background(color).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (faction != null) {
            Portrait(Portraits.agent(faction), 34.dp, ring = color, ringWidth = 1.dp, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        Text(label, fontSize = 9.sp, lineHeight = 10.sp, color = Palette.onColor(color), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

/** Sel grid: status otomatis (tidak bisa diubah) atau tanda manual yang berputar ✓ → ✗ → ? → kosong. */
@Composable
private fun MarkCell(auto: String?, manual: String?, cardName: String, onMark: (String?) -> Unit) {
    val shown = auto ?: manual.orEmpty()
    val desc = "$cardName: ${if (shown.isEmpty()) "—" else shown}"
    Box(
        Modifier
            .size(CELL.dp)
            .background(if (auto != null) LisbonColors.PaperShade else LisbonColors.OldPaper)
            .border(0.5.dp, LisbonColors.InkSoft.copy(alpha = 0.3f))
            .then(
                if (auto == null) {
                    Modifier.clickable {
                        val i = CYCLE.indexOf(manual)
                        onMark(if (i == CYCLE.lastIndex) null else CYCLE[i + 1])
                    }
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            shown,
            fontSize = 16.sp,
            color = when (shown) {
                "✓", "●", "◉", "$", "◍" -> LisbonColors.Olive
                "✗", "–" -> LisbonColors.InkMuted
                "★" -> LisbonColors.FolderRed
                else -> LisbonColors.TagusBlue
            },
            fontWeight = FontWeight.Bold,
        )
    }
}

