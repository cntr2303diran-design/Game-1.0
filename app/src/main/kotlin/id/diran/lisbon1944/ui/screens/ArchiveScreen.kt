package id.diran.lisbon1944.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.WinType
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.ArchiveStats
import id.diran.lisbon1944.ui.Screen
import id.diran.lisbon1944.ui.common.KeyValue
import id.diran.lisbon1944.ui.common.LisbonDialog
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.theme.LisbonColors
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Arsip (BAGIAN 12.2 no. 19): riwayat permainan, win rate per faksi, statistik pribadi. */
@Composable
fun ArchiveScreen(vm: AppViewModel) {
    val names = vm.names
    val content = vm.content
    val entries = vm.archive
    val stats = remember(entries) { ArchiveStats.of(entries) }
    var confirm by remember { mutableStateOf(false) }
    fun factionName(f: String) = runCatching { Faction.valueOf(f) }.getOrNull()?.let { names?.factionShort(it) } ?: f
    fun winName(w: String) = runCatching { WinType.valueOf(w) }.getOrNull()?.let { names?.winType(it) } ?: w
    Backdrop {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.menu_archive), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.OldPaper) }
            if (entries.isEmpty()) {
                item { Text(stringResource(R.string.archive_empty), color = LisbonColors.PaperMuted) }
            } else {
                item {
                    PaperCard(Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.archive_personal))
                        KeyValue(stringResource(R.string.archive_games), "${stats.games}")
                        KeyValue(stringResource(R.string.archive_human_games), "${stats.humanGames}")
                        val rate = if (stats.humanGames == 0) 0 else (stats.humanWins * 100.0 / stats.humanGames).roundToInt()
                        KeyValue(stringResource(R.string.archive_human_wins), "${stats.humanWins} ($rate%)")
                        KeyValue(stringResource(R.string.archive_avg_rounds), "%.1f".format(stats.averageRounds))
                        stats.winTypes.forEach { (w, n) -> KeyValue(winName(w), "$n") }
                    }
                }
                item {
                    PaperCard(Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.archive_factions))
                        stats.factions.forEach { r ->
                            val f = runCatching { Faction.valueOf(r.faction) }.getOrNull()
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(factionName(r.faction), modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold)
                                Box(Modifier.weight(1f).height(14.dp).background(LisbonColors.PaperShade)) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(r.rate.toFloat().coerceIn(0.02f, 1f))
                                            .fillMaxHeight()
                                            .background(f?.let { Palette.faction(content, it) } ?: LisbonColors.Olive),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("${(r.rate * 100).roundToInt()}% · ${r.wins}/${r.games}", fontSize = 12.sp)
                            }
                        }
                    }
                }
                item { SectionTitle(stringResource(R.string.archive_history), color = LisbonColors.StreetLamp) }
                items(entries.reversed()) { e ->
                    PaperCard(Modifier.fillMaxWidth()) {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(e.endedAt)), style = MaterialTheme.typography.bodySmall, color = LisbonColors.InkMuted)
                        Text(winName(e.winType) + " — " + e.players.filter { it.winner }.joinToString(" & ") { it.name }, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            e.players.sortedByDescending { it.score }.forEach { p ->
                                Row {
                                    Text(
                                        (if (p.winner) "★ " else "  ") + p.name + " (" + factionName(p.faction) + (if (p.bot) " · bot" else "") + ")",
                                        modifier = Modifier.weight(1f),
                                        fontSize = 12.sp,
                                    )
                                    Text("${p.score}", fontSize = 12.sp)
                                }
                            }
                        }
                        Text(stringResource(R.string.archive_entry_meta, e.rounds, e.warStep, e.seed), style = MaterialTheme.typography.bodySmall, color = LisbonColors.InkMuted)
                    }
                }
                item { SecondaryButton(stringResource(R.string.archive_clear), { confirm = true }) }
            }
            item { SecondaryButton(stringResource(R.string.back), { vm.go(Screen.MENU) }) }
        }
        if (confirm) {
            LisbonDialog(
                title = stringResource(R.string.archive_clear),
                onDismiss = { confirm = false },
                confirmText = stringResource(R.string.archive_clear),
                onConfirm = {
                    vm.clearArchive()
                    confirm = false
                },
                dismissText = stringResource(R.string.cancel),
                danger = true,
            )
        }
    }
}
