package id.diran.lisbon1944.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.GameUi
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.PrimaryButton
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.Stamp
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.screens.Backdrop
import id.diran.lisbon1944.ui.theme.LisbonColors

private enum class Tab { ACTION, PLAYERS, NOTES, CITY, LOG }

@Composable
fun GameScreen(vm: AppViewModel) {
    val ui = vm.ui
    val content = vm.content
    val names = vm.names
    var quit by remember { mutableStateOf(false) }
    BackHandler { quit = true }
    if (ui == null || content == null || names == null) {
        Backdrop { Text(stringResource(R.string.dossier_loading), color = LisbonColors.OldPaper, modifier = Modifier.padding(24.dp)) }
        return
    }
    if (ui.needsPrivacy) {
        PrivacyScreen(ui, onReady = vm::reveal)
    } else {
        GameLayout(vm, ui)
    }
    if (quit) {
        AlertDialog(
            onDismissRequest = { quit = false },
            title = { Text(stringResource(R.string.quit_title)) },
            text = { Text(stringResource(R.string.quit_text)) },
            confirmButton = { TextButton(onClick = { quit = false; vm.quitToMenu() }) { Text(stringResource(R.string.quit_yes)) } },
            dismissButton = { TextButton(onClick = { quit = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun GameLayout(vm: AppViewModel, ui: GameUi) {
    val content = vm.content ?: return
    val names = vm.names ?: return
    val text = vm.actionText() ?: return
    var tab by rememberSaveable { mutableStateOf(Tab.ACTION) }
    val ctx = DecisionCtx(ui, content, names, text, vm::act, vm::check)
    val highlight = when (val d = ui.view.myDecision) {
        is PendingDecision.BuyOrAuction -> setOf(d.tile)
        is PendingDecision.AuctionBid -> setOf(d.tile)
        is PendingDecision.RentChoice -> setOf(d.quote.tile)
        else -> setOf(ui.view.me.position)
    }
    val myTurnBadge = ui.view.myDecision != null && ui.legal.isNotEmpty()

    Column(Modifier.fillMaxSize().background(LisbonColors.Ink).safeDrawingPadding()) {
        Hud(ui, content, names)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val panel: @Composable (Modifier) -> Unit = { m ->
                Column(m.background(LisbonColors.InkSoft)) {
                    Row(
                        Modifier.fillMaxWidth().padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val tabs = listOf(
                            Tab.ACTION to stringResource(R.string.tab_action) + if (myTurnBadge) " •" else "",
                            Tab.PLAYERS to stringResource(R.string.tab_players),
                            Tab.NOTES to stringResource(R.string.tab_notes),
                            Tab.CITY to stringResource(R.string.tab_city),
                            Tab.LOG to stringResource(R.string.tab_log),
                        )
                        tabs.forEach { (t, label) -> ToggleTag(label, tab == t, { tab = t }, Modifier.weight(1f)) }
                    }
                    Box(Modifier.fillMaxSize()) {
                        when (tab) {
                            Tab.ACTION -> DecisionPanel(ctx, onSkipBots = vm::skipBots)
                            Tab.PLAYERS -> PlayersPanel(ui, content, names)
                            Tab.NOTES -> NotebookPanel(ui, content, names)
                            Tab.CITY -> CityPanel(ui, content, names)
                            Tab.LOG -> LogPanel(ui.log)
                        }
                    }
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    BoardView(ui.view, content, names, Modifier.fillMaxHeight(), highlight)
                    panel(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    BoardView(ui.view, content, names, Modifier.fillMaxWidth(), highlight)
                    panel(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
    if (ui.isOver && !vm.endSeen) {
        AlertDialog(
            onDismissRequest = vm::showEnd,
            title = { Text(stringResource(R.string.end_title)) },
            confirmButton = { TextButton(onClick = vm::showEnd) { Text(stringResource(R.string.end_show)) } },
        )
    }
}

/** Layar privasi pass-and-play: tidak ada informasi rahasia yang dirender di sini. */
@Composable
fun PrivacyScreen(ui: GameUi, onReady: () -> Unit) {
    val name = ui.waitingOn?.let { ui.playerNames[it.value] } ?: "?"
    Backdrop {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Stamp(stringResource(R.string.stamp_secret))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.privacy_pass_to), color = LisbonColors.PaperShade, style = MaterialTheme.typography.bodyLarge)
            Text(name, color = LisbonColors.OldPaper, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.privacy_hint), color = LisbonColors.PaperShade, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))
            PrimaryButton(stringResource(R.string.privacy_ready), onReady, Modifier.width(260.dp))
        }
    }
}

