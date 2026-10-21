package id.diran.lisbon1944.ui.screens

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.session.GameStats
import id.diran.lisbon1944.session.Names
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.Screen
import id.diran.lisbon1944.ui.common.KeyValue
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.PrimaryButton
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.Stamp
import id.diran.lisbon1944.ui.game.FactionBadge
import id.diran.lisbon1944.ui.game.seatFactions
import id.diran.lisbon1944.ui.theme.LisbonColors
import id.diran.lisbon1944.ui.theme.LocalUiPrefs
import id.diran.lisbon1944.ui.theme.PosterFamily
import id.diran.lisbon1944.ui.theme.TypewriterFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layar Akhir (BAGIAN 12.2 no. 16): pemenang & cara menang, pengungkapan amplop, tabel skor rinci,
 * pengungkapan Agen Ganda & kebohongannya, statistik permainan, Main Lagi, Bagikan Seed.
 */
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
    val stats = remember(v.log.size) { GameStats.from(v.log) }
    val context = LocalContext.current
    val shareText = stringResource(R.string.end_share_text, ui.seed)
    val shareTitle = stringResource(R.string.end_share)
    Backdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stamp(stringResource(R.string.end_stamp), color = LisbonColors.StreetLamp)
            if (win != null) {
                Text(names.winType(win.type), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.StreetLamp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    win.winners.forEach { w -> factions[w.value]?.let { FactionBadge(content, it, 44) } }
                }
                Text(
                    win.winners.joinToString(" & ") { ui.playerNames[it.value] ?: "?" },
                    style = MaterialTheme.typography.displayLarge,
                    color = LisbonColors.OldPaper,
                    textAlign = TextAlign.Center,
                )
            }
            v.revealedEnvelope?.let { env -> EnvelopeReveal(env.cards, names) }

            stats.doubleAgent?.let { da ->
                PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
                    SectionTitle(stringResource(R.string.flag_double_agent))
                    Text(stringResource(R.string.end_double_agent, ui.playerNames[da].orEmpty()), fontWeight = FontWeight.Bold)
                    if (stats.doubleAgentLies.isEmpty()) {
                        Text(stringResource(R.string.end_no_lies), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        stats.doubleAgentLies.forEach { (asker, card) ->
                            Text(stringResource(R.string.end_lie, ui.playerNames[asker].orEmpty(), names.card(card)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            ui.scores?.let { scores ->
                PaperCard(Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.end_scores))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            val cols = listOf(
                                R.string.end_col_cash, R.string.end_col_assets, R.string.end_col_clues,
                                R.string.end_col_mission, R.string.end_col_prestige, R.string.end_col_total,
                            )
                            Row {
                                Text(stringResource(R.string.end_col_player), Modifier.width(96.dp), fontSize = 10.sp, color = LisbonColors.InkMuted)
                                cols.forEach { Text(stringResource(it), Modifier.width(COL.dp), fontSize = 10.sp, color = LisbonColors.InkMuted, textAlign = TextAlign.End, maxLines = 1) }
                            }
                            scores.sortedByDescending { it.total }.forEach { s ->
                                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Row(Modifier.width(96.dp), verticalAlignment = Alignment.CenterVertically) {
                                        factions[s.player.value]?.let { FactionBadge(content, it, 20) }
                                        Spacer(Modifier.width(4.dp))
                                        Text((ui.playerNames[s.player.value] ?: "?") + if (s.burned) " ✗" else "", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                    }
                                    listOf(s.cash, s.property + s.buildings, s.clues, s.mission, s.prestige).forEach {
                                        Text("$it", Modifier.width(COL.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                                    }
                                    Text("${s.total}", Modifier.width(COL.dp), fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = LisbonColors.FolderRed)
                                }
                            }
                        }
                    }
                    if (scores.any { it.burned }) Text(stringResource(R.string.end_burned_note), style = MaterialTheme.typography.bodySmall, color = LisbonColors.InkMuted)
                }
            }

            PaperCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.end_stats))
                if (stats.biggestRent > 0) {
                    KeyValue(
                        stringResource(R.string.end_stat_biggest_rent),
                        names.money(stats.biggestRent) + (stats.biggestRentTile?.let { " · " + names.tile(it) } ?: "") +
                            (stats.biggestRentPayer?.let { " · " + (ui.playerNames[it] ?: "") } ?: ""),
                    )
                }
                KeyValue(stringResource(R.string.end_stat_leaks), "${stats.cardsShown}")
                KeyValue(stringResource(R.string.end_stat_info_rent), "${stats.infoPayments}")
                KeyValue(stringResource(R.string.end_stat_suggestions), "${stats.suggestions}")
                KeyValue(stringResource(R.string.end_stat_auctions), "${stats.auctions}")
                KeyValue(stringResource(R.string.end_stat_trades), "${stats.trades}")
                KeyValue(stringResource(R.string.end_stat_arrests), "${stats.arrests}")
                KeyValue(stringResource(R.string.end_stat_rounds), "${v.roundNumber} · ${stringResource(R.string.hud_war_clock, v.warStep)}")
                KeyValue("Seed", "${ui.seed}")
            }

            Spacer(Modifier.height(8.dp))
            PrimaryButton(stringResource(R.string.end_play_again), { vm.playAgain() })
            SecondaryButton(stringResource(R.string.end_share), {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                runCatching { context.startActivity(Intent.createChooser(send, shareTitle)) }
            })
            SecondaryButton(stringResource(R.string.end_review), { vm.go(Screen.GAME) })
            SecondaryButton(stringResource(R.string.end_menu), { vm.quitToMenu() })
            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val COL = 44

/** Amplop merah dibuka; tiga kartu dibalik satu per satu. */
@Composable
private fun EnvelopeReveal(cards: List<CardId>, names: Names) {
    val reduce = LocalUiPrefs.current.reduceMotion
    val flips = remember { List(3) { Animatable(if (reduce) 180f else 0f) } }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(400)
        flips.forEach { a ->
            launch { a.animateTo(180f, tween(600, easing = FastOutSlowInEasing)) }
            delay(450)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.notes_envelope).uppercase(), fontFamily = PosterFamily, fontWeight = FontWeight.Black, color = LisbonColors.FolderRed, letterSpacing = 3.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            cards.forEachIndexed { i, c ->
                val angle = flips.getOrNull(i)?.value ?: 180f
                val front = angle > 90f
                Box(
                    Modifier
                        .size(100.dp, 132.dp)
                        .graphicsLayer {
                            rotationY = angle
                            cameraDistance = 12f * density
                        }
                        .background(if (front) LisbonColors.OldPaper else LisbonColors.FolderRed, RoundedCornerShape(6.dp))
                        .border(2.dp, LisbonColors.Ink, RoundedCornerShape(6.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (front) {
                        Text(
                            names.card(c),
                            fontFamily = TypewriterFamily,
                            fontSize = 14.sp,
                            color = LisbonColors.Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.graphicsLayer { rotationY = 180f },
                        )
                    } else {
                        Text("?", fontFamily = PosterFamily, fontSize = 40.sp, color = LisbonColors.OldPaper, modifier = Modifier.rotate(-8f))
                    }
                }
            }
        }
    }
}

