package id.diran.lisbon1944.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.SpecialTile
import id.diran.lisbon1944.session.Lang
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.Screen
import id.diran.lisbon1944.ui.common.Dot
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.PrimaryButton
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.common.WrapGrid
import id.diran.lisbon1944.ui.common.districtPattern
import id.diran.lisbon1944.ui.game.FactionBadge
import id.diran.lisbon1944.ui.game.specialGlyph
import id.diran.lisbon1944.ui.game.specialHelp
import id.diran.lisbon1944.ui.theme.LisbonColors

private val RULES = listOf(
    R.string.rules_win_t to R.string.rules_win,
    R.string.rules_setup_t to R.string.rules_setup,
    R.string.rules_turn_t to R.string.rules_turn,
    R.string.rules_economy_t to R.string.rules_economy,
    R.string.rules_mystery_t to R.string.rules_mystery,
    R.string.rules_war_t to R.string.rules_war,
    R.string.rules_intel_t to R.string.rules_intel,
    R.string.rules_extras_t to R.string.rules_extras,
    R.string.rules_score_t to R.string.rules_score,
    R.string.rules_modes_t to R.string.rules_modes,
)

/** Cara Bermain (BAGIAN 12.2 no. 17): ringkasan aturan, glosarium, dan tutorial satu giliran terpandu. */
@Composable
fun HowToScreen(vm: AppViewModel) {
    val content = vm.content ?: return
    val names = vm.names ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var gloss by rememberSaveable { mutableIntStateOf(0) }
    Backdrop {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(stringResource(R.string.menu_how_to), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.OldPaper, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            PrimaryButton(stringResource(R.string.how_tutorial), { vm.startTutorial() })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleTag(stringResource(R.string.how_rules), tab == 0, { tab = 0 }, Modifier.weight(1f))
                ToggleTag(stringResource(R.string.how_glossary), tab == 1, { tab = 1 }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f)) {
                if (tab == 0) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RULES) { (t, b) ->
                            PaperCard(Modifier.fillMaxWidth()) {
                                Text(stringResource(t), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(b), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    Column {
                        val cats = listOf(
                            stringResource(R.string.gloss_tiles), stringResource(R.string.gloss_evidence), stringResource(R.string.gloss_factions),
                            stringResource(R.string.gloss_tactics), stringResource(R.string.gloss_operations), stringResource(R.string.gloss_fronts),
                        )
                        WrapGrid(cats.indices.toList(), 3) { i, m -> ToggleTag(cats[i], gloss == i, { gloss = i }, m, accent = LisbonColors.TagusBlue) }
                        Spacer(Modifier.height(8.dp))
                        val en = names.lang == Lang.EN
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (gloss) {
                                0 -> items(content.board) { t ->
                                    PaperCard(Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            when (t) {
                                                is PropertyTile -> Box(Modifier.width(18.dp).height(18.dp).background(Palette.district(t.district)).districtPattern(t.district, LisbonColors.OldPaper))
                                                is EscapeRouteTile -> Text("⛴", color = LisbonColors.TagusBlue)
                                                is SpecialTile -> Text(specialGlyph(t.kind), color = LisbonColors.FolderRed)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text("${t.index}. ${names.tile(t.index)}", fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            when (t) {
                                                is PropertyTile -> "${Palette.districtIcon(t.district)} ${names.district(t.district)} · ${names.money(t.price)}"
                                                is EscapeRouteTile -> stringResource(R.string.tile_escape) + " · " + names.money(t.price)
                                                is SpecialTile -> specialHelp(t.kind)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = LisbonColors.InkMuted,
                                        )
                                    }
                                }
                                1 -> items(content.evidence.sortedBy { it.type.ordinal }) { e ->
                                    PaperCard(Modifier.fillMaxWidth(), accent = Palette.evidence(e.type)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Dot(Palette.evidence(e.type), 10)
                                            Spacer(Modifier.width(6.dp))
                                            Text(names.card(e.id), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text(names.type(e.type), color = LisbonColors.InkMuted, style = MaterialTheme.typography.bodySmall)
                                        }
                                        val role = if (en) e.roleEn else e.role
                                        if (e.type == EvidenceType.SUSPECT && role != null) Text(role, style = MaterialTheme.typography.bodySmall, color = LisbonColors.FolderRed)
                                        Text(if (en) e.blurbEn else e.blurb, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                2 -> items(content.factions) { f ->
                                    PaperCard(Modifier.fillMaxWidth(), accent = Palette.faction(content, f.id)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FactionBadge(content, f.id, 36)
                                            Spacer(Modifier.width(8.dp))
                                            Text("${f.organization} — ${names.faction(f.id)}", fontWeight = FontWeight.Bold)
                                        }
                                        Text(if (en) f.abilityTextEn else f.abilityText, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                3 -> items(content.tactics.distinctBy { it.title }) { t ->
                                    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.StreetLamp) {
                                        Text(names.tactic(t.id), fontWeight = FontWeight.Bold)
                                        Text(names.tacticText(t.id), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                4 -> items(content.operations) { o ->
                                    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.TagusBlue) {
                                        Text(names.operation(o.id), fontWeight = FontWeight.Bold)
                                        Text(names.operationText(o.id), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                else -> items(content.fronts.sortedBy { it.step * 10 + if (it.variant == "A") 0 else 1 }) { f ->
                                    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
                                        Text("${f.month} (${f.variant}) — ${names.front(f.id.value)}", fontWeight = FontWeight.Bold)
                                        Text(names.frontText(f.id.value), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.back), { vm.go(Screen.MENU) })
        }
    }
}

