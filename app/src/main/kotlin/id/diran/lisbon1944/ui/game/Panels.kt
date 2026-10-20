package id.diran.lisbon1944.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.session.Lang
import id.diran.lisbon1944.session.LogKind
import id.diran.lisbon1944.session.LogLine
import id.diran.lisbon1944.session.Names
import id.diran.lisbon1944.ui.GameUi
import id.diran.lisbon1944.ui.common.Dot
import id.diran.lisbon1944.ui.common.KeyValue
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.PipMeter
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.theme.LisbonColors

/** Bilah atas: giliran siapa, uang, Intel, Kecurigaan, Jam Perang. */
@Composable
fun Hud(ui: GameUi, content: GameContent, names: Names, modifier: Modifier = Modifier) {
    val v = ui.view
    val me = v.me
    val b = content.balance
    val turnName = ui.playerNames[v.currentPlayer.value].orEmpty()
    val factions = seatFactions(v)
    Column(modifier.fillMaxWidth().background(LisbonColors.Ink).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(Palette.faction(content, factions[v.currentPlayer.value] ?: me.faction), 10)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.hud_turn, turnName, v.roundNumber),
                color = LisbonColors.OldPaper,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(stringResource(R.string.hud_war_clock, v.warStep), color = LisbonColors.StreetLamp, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(Palette.faction(content, me.faction), 10)
            Spacer(Modifier.width(6.dp))
            Text("${me.name} · ${names.money(me.money)}", color = LisbonColors.OldPaper, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(stringResource(R.string.hud_intel, me.intel), color = LisbonColors.StreetLamp, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.hud_suspicion), color = LisbonColors.PaperShade, fontSize = 11.sp)
            Spacer(Modifier.width(3.dp))
            PipMeter(me.suspicion, b.suspicion.limit, LisbonColors.FolderRed, pip = 6)
        }
        ui.log.lastOrNull()?.let {
            Text(it.text, color = LisbonColors.PaperShade, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun PlayersPanel(ui: GameUi, content: GameContent, names: Names) {
    val v = ui.view
    val b = content.balance
    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val me = v.me
            PaperCard(Modifier.fillMaxWidth(), accent = Palette.faction(content, me.faction)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(Palette.faction(content, me.faction), 12)
                    Spacer(Modifier.width(6.dp))
                    Text("${me.name} (${stringResource(R.string.players_you)}) — ${names.factionShort(me.faction)}", fontWeight = FontWeight.Bold)
                }
                KeyValue(stringResource(R.string.players_money), names.money(me.money))
                KeyValue(stringResource(R.string.players_props), v.ownership.count { it.value.ownerId == me.id }.toString())
                KeyValue(stringResource(R.string.players_hand), me.hand.size.toString())
                KeyValue(stringResource(R.string.players_intel), me.intel.toString())
                KeyValue(stringResource(R.string.players_prestige), me.prestige.toString())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hud_suspicion), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    PipMeter(me.suspicion, b.suspicion.limit, LisbonColors.FolderRed)
                }
                val flags = listOfNotNull(
                    if (me.inJail) stringResource(R.string.flag_jail) else null,
                    if (me.burned) stringResource(R.string.flag_burned) else null,
                    if (me.isDoubleAgent) stringResource(R.string.flag_double_agent) else null,
                    if (me.abilityUsed) stringResource(R.string.flag_ability_used) else null,
                )
                if (flags.isNotEmpty()) Text(flags.joinToString(" · "), color = LisbonColors.FolderRed, fontWeight = FontWeight.Bold)
                Text(names.content.faction(me.faction).let { if (names.lang == Lang.EN) it.abilityTextEn else it.abilityText }, style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft)
            }
        }
        items(v.opponents) { o ->
            val color = Palette.faction(content, o.faction)
            PaperCard(Modifier.fillMaxWidth(), accent = color) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(color, 12)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${o.name} — ${names.factionShort(o.faction)}" + if (o.isBot) " · BOT" else "",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (o.id == v.currentPlayer) Text("◀", color = LisbonColors.FolderRed)
                }
                if (o.bankrupt) {
                    Text(stringResource(R.string.flag_bankrupt), color = LisbonColors.FolderRed, fontWeight = FontWeight.Bold)
                } else {
                    KeyValue(stringResource(R.string.players_money), names.money(o.money))
                    KeyValue(stringResource(R.string.players_props), o.propertyCount.toString())
                    KeyValue(stringResource(R.string.players_hand), o.handCount.toString())
                    KeyValue(stringResource(R.string.players_intel), o.intel.toString())
                    KeyValue(stringResource(R.string.players_tactics), o.tacticCount.toString())
                    KeyValue(stringResource(R.string.players_prestige), o.prestige.toString())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.hud_suspicion), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        PipMeter(o.suspicion, b.suspicion.limit, LisbonColors.FolderRed)
                    }
                    val flags = listOfNotNull(
                        if (o.inJail) stringResource(R.string.flag_jail) else null,
                        if (o.burned) stringResource(R.string.flag_burned) else null,
                        if (o.abilityUsed) stringResource(R.string.flag_ability_used) else null,
                    )
                    if (flags.isNotEmpty()) Text(flags.joinToString(" · "), color = LisbonColors.FolderRed)
                }
            }
        }
    }
}

/** Buku catatan detektif: status tiap kartu Bukti menurut pengetahuan pemain. */
@Composable
fun NotebookPanel(ui: GameUi, content: GameContent, names: Names) {
    val v = ui.view
    val facts = v.knowledge.groupBy { it.cardId }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(stringResource(R.string.notes_hint), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.PaperShade)
        }
        v.revealedEnvelope?.let { env ->
            item {
                PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
                    SectionTitle(stringResource(R.string.notes_envelope))
                    Text(env.cards.joinToString(" · ") { names.card(it) }, fontWeight = FontWeight.Bold)
                }
            }
        }
        for (type in EvidenceType.entries) {
            val cards = content.evidenceOfType(type)
            val unknown = cards.count { facts[it.id].isNullOrEmpty() }
            item(key = type.name) {
                PaperCard(Modifier.fillMaxWidth(), accent = Palette.evidence(type)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(names.type(type).uppercase(), color = Palette.evidence(type), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.notes_unknown, unknown), fontSize = 12.sp, color = LisbonColors.InkSoft)
                    }
                    cards.forEach { card ->
                        val known = facts[card.id].orEmpty()
                        val holder = known.firstOrNull()?.holder
                        val (label, color) = when {
                            card.id in v.me.hand -> stringResource(R.string.notes_mine) to LisbonColors.Olive
                            holder is Holder.Player -> (ui.playerNames[holder.id.value] ?: "?") to LisbonColors.TagusBlue
                            holder is Holder.Embassy -> stringResource(R.string.notes_embassy) to LisbonColors.TagusBlue
                            holder is Holder.Envelope -> stringResource(R.string.notes_in_envelope) to LisbonColors.FolderRed
                            else -> "—" to LisbonColors.FolderRed
                        }
                        val struck = holder != null && holder !is Holder.Envelope || card.id in v.me.hand
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (struck) "✓ " else "◻ ") + names.card(card.id),
                                modifier = Modifier.weight(1f),
                                color = if (struck) LisbonColors.InkSoft.copy(alpha = 0.7f) else LisbonColors.Ink,
                                fontWeight = if (struck) FontWeight.Normal else FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Kota: Front aktif, Misi, Operasi, Taktik di tangan, kartu simpanan. */
@Composable
fun CityPanel(ui: GameUi, content: GameContent, names: Names) {
    val v = ui.view
    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
                SectionTitle(stringResource(R.string.city_front))
                val front = runCatching { content.front(v.activeFront) }.getOrNull()
                if (front != null) {
                    Text("${front.month} — ${names.front(front.id.value)}", fontWeight = FontWeight.Bold)
                    Text(names.frontText(front.id.value), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                PipMeter(v.warStep, 12, LisbonColors.FolderRed)
                v.passesToNextStep?.let { Text(stringResource(R.string.hud_passes, it), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft) }
            }
        }
        v.me.missionId?.let { m ->
            item {
                PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.Olive) {
                    SectionTitle(stringResource(R.string.city_mission), color = LisbonColors.Olive)
                    Text(names.mission(m), fontWeight = FontWeight.Bold)
                    Text(names.missionText(m), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.TagusBlue) {
                SectionTitle(stringResource(R.string.city_operations), color = LisbonColors.TagusBlue)
                if (v.openOperations.isEmpty()) Text("—")
                v.openOperations.forEach { op ->
                    Text(names.operation(op), fontWeight = FontWeight.Bold)
                    Text(names.operationText(op), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                }
                if (v.claimedOperations.isNotEmpty()) {
                    Text(stringResource(R.string.city_claimed, v.claimedOperations.size), color = LisbonColors.InkSoft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.StreetLamp) {
                SectionTitle(stringResource(R.string.city_tactics), color = LisbonColors.InkSoft)
                if (v.me.tactics.isEmpty()) Text(stringResource(R.string.city_no_tactics), style = MaterialTheme.typography.bodyMedium)
                v.me.tactics.forEach { t ->
                    Text(names.tactic(t), fontWeight = FontWeight.Bold)
                    Text(names.tacticText(t), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                }
                if (v.me.heldCards.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.city_held))
                    v.me.heldCards.forEach { h ->
                        Text(names.deckCard(h.deck.name, h.cardId), fontWeight = FontWeight.Bold)
                        Text(names.deckCardText(h.deck, h.cardId), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            PaperCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.city_bank))
                KeyValue(stringResource(R.string.city_vault), names.money(v.casinoVault))
                KeyValue(stringResource(R.string.city_houses), v.houseStock.toString())
                KeyValue(stringResource(R.string.city_hqs), v.hqStock.toString())
                KeyValue(stringResource(R.string.city_decks), "${v.cipherDeckSize} / ${v.telegramDeckSize} / ${v.tacticDeckSize}")
            }
        }
    }
}

@Composable
fun LogPanel(lines: List<LogLine>) {
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.scrollToItem(lines.lastIndex) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), state = state) {
        items(lines, key = { it.seq }) { l ->
            Text(
                l.text,
                color = logColor(l.kind),
                fontSize = 12.sp,
                fontWeight = if (l.kind == LogKind.WAR || l.kind == LogKind.ALERT) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

fun logColor(k: LogKind): Color = when (k) {
    LogKind.INFO -> LisbonColors.PaperShade
    LogKind.MONEY -> Color(0xFFB9C98A)
    LogKind.MYSTERY -> Color(0xFF9DB8D6)
    LogKind.WAR -> LisbonColors.StreetLamp
    LogKind.ALERT -> Color(0xFFE46A5E)
    LogKind.SECRET -> Color(0xFFD9A6E8)
}

