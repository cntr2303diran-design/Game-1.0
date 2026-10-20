package id.diran.lisbon1944.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.Screen
import id.diran.lisbon1944.ui.common.Dot
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.PrimaryButton
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.Stamp
import id.diran.lisbon1944.ui.game.seatFactions
import id.diran.lisbon1944.ui.theme.LisbonColors

@Composable
fun EndScreen(vm: AppViewModel) {
    val ui = vm.ui
    val content = vm.content
    val names = vm.names
    if (ui == null || content == null || names == null) {
        MenuScreen(vm)
        return
    }
    val v = ui.view
    val win = v.winner
    val factions = seatFactions(v)
    Backdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stamp(stringResource(R.string.end_stamp), color = LisbonColors.StreetLamp)
            if (win != null) {
                Text(names.winType(win.type), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.StreetLamp, textAlign = TextAlign.Center)
                Text(
                    win.winners.joinToString(" & ") { ui.playerNames[it.value] ?: "?" },
                    style = MaterialTheme.typography.displayLarge,
                    color = LisbonColors.OldPaper,
                    textAlign = TextAlign.Center,
                )
            }
            v.revealedEnvelope?.let { env ->
                PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
                    SectionTitle(stringResource(R.string.notes_envelope))
                    env.cards.forEach { Text(names.card(it), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                }
            }
            ui.scores?.let { scores ->
                PaperCard(Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.end_scores))
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.end_col_player), Modifier.weight(2f), fontSize = 11.sp, color = LisbonColors.InkSoft)
                        listOf(R.string.end_col_cash, R.string.end_col_assets, R.string.end_col_bonus, R.string.end_col_total).forEach {
                            Text(stringResource(it), Modifier.weight(1f), fontSize = 11.sp, color = LisbonColors.InkSoft, textAlign = TextAlign.End)
                        }
                    }
                    scores.sortedByDescending { it.total }.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically) {
                                factions[s.player.value]?.let { Dot(Palette.faction(content, it), 10) }
                                Spacer(Modifier.width(4.dp))
                                Text(ui.playerNames[s.player.value] ?: "?", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }
                            Text("${s.cash}", Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text("${s.property + s.buildings}", Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text("${s.clues + s.mission + s.prestige}" + (if (s.burned) "✗" else ""), Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text("${s.total}", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(stringResource(R.string.menu_new_game), { vm.go(Screen.SETUP) })
            SecondaryButton(stringResource(R.string.end_review), { vm.go(Screen.GAME) })
            SecondaryButton(stringResource(R.string.end_menu), { vm.quitToMenu() })
        }
    }
}
