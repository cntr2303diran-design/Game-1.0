package id.diran.lisbon1944.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.session.Cue
import id.diran.lisbon1944.session.Sfx
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.GameUi
import id.diran.lisbon1944.ui.common.LisbonDialog
import id.diran.lisbon1944.ui.common.PrimaryButton
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.Stamp
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.screens.Backdrop
import id.diran.lisbon1944.ui.screens.ErrorDialog
import id.diran.lisbon1944.ui.theme.LisbonColors

private enum class Tab { ACTION, PLAYERS, NOTES, PROPS, CARDS, LOG }

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
    Box(Modifier.fillMaxSize()) {
        if (ui.needsPrivacy) {
            PrivacyScreen(ui, onReady = vm::reveal)
        } else {
            GameLayout(vm, ui)
        }
        if (vm.introPending) {
            IntroOverlay(ui, content, names, onDone = vm::introDone, sound = vm::uiSound)
        } else if (!ui.needsPrivacy) {
            CueLayer(vm.cues.firstOrNull(), names, { seat -> seat?.let { ui.playerNames[it] } ?: "" }, vm::consumeCue)
        }
    }
    ErrorDialog(vm)
    if (quit) {
        LisbonDialog(
            title = stringResource(R.string.quit_title),
            onDismiss = { quit = false },
            confirmText = stringResource(R.string.quit_yes),
            onConfirm = {
                quit = false
                vm.quitToMenu()
            },
            dismissText = stringResource(R.string.cancel),
        ) { Text(stringResource(R.string.quit_text)) }
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
    // Getaran papan saat Razia/penangkapan.
    val head = vm.cues.firstOrNull()
    val shakeKey = if (head is Cue.Stamp && head.shake) head.hashCode() else 0
    @Suppress("UNUSED_VARIABLE") val notesVersion = vm.notesVersion
    val notes = NotesAccess(vm.marks(), vm::setMark, vm::setNote)

    Column(Modifier.fillMaxSize().background(LisbonColors.Ink).safeDrawingPadding()) {
        Hud(ui, content, names)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Orientasi layar sebenarnya (bukan sisa ruang — HUD bisa tinggi saat teks 200%).
            val cfg = LocalConfiguration.current
            val landscape = cfg.screenWidthDp > cfg.screenHeightDp
            val bigText = LocalDensity.current.fontScale > 1.15f
            val boxHeight = maxHeight
            val boxWidth = maxWidth
            val panel: @Composable (Modifier) -> Unit = { m ->
                Column(m.background(LisbonColors.InkSoft)) {
                    vm.tutorial?.let { t -> if (!t.dismissed) TutorialCoach(vm, ui) }
                    Row(
                        Modifier.fillMaxWidth().then(if (bigText) Modifier.horizontalScroll(rememberScrollState()) else Modifier).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        val tabs = listOf(
                            Tab.ACTION to stringResource(R.string.tab_action) + if (myTurnBadge) " •" else "",
                            Tab.PLAYERS to stringResource(R.string.tab_players),
                            Tab.NOTES to stringResource(R.string.tab_notes),
                            Tab.PROPS to stringResource(R.string.tab_props),
                            Tab.CARDS to stringResource(R.string.tab_city),
                            Tab.LOG to stringResource(R.string.tab_log),
                        )
                        tabs.forEach { (t, label) ->
                            ToggleTag(label, tab == t, {
                                tab = t
                                vm.uiSound(Sfx.CARD)
                            }, if (bigText) Modifier else Modifier.weight(1f), dense = true)
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        when (tab) {
                            Tab.ACTION -> DecisionPanel(ctx, onSkipBots = vm::skipBots)
                            Tab.PLAYERS -> PlayersPanel(ui, content, names)
                            Tab.NOTES -> NotebookPanel(ui, content, names, notes)
                            Tab.PROPS -> PropertiesPanel(ctx)
                            Tab.CARDS -> CityPanel(ui, content, names)
                            Tab.LOG -> LogPanel(ui.log)
                        }
                    }
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    BoardView(ui.view, content, names, Modifier.fillMaxHeight(), highlight, shakeKey, onPawnStep = { vm.uiSound(Sfx.STEP) })
                    panel(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Saat teks besar, papan dibatasi agar panel aksi tetap punya ruang.
                    BoardView(ui.view, content, names, Modifier.size(minOf(boxWidth, boxHeight * 0.6f)), highlight, shakeKey, onPawnStep = { vm.uiSound(Sfx.STEP) })
                    panel(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
    if (ui.isOver && !vm.endSeen && vm.cues.isEmpty()) {
        LisbonDialog(
            title = stringResource(R.string.end_title),
            onDismiss = vm::showEnd,
            confirmText = stringResource(R.string.end_show),
            onConfirm = vm::showEnd,
        )
    }
    val t = vm.tutorial
    if (t != null && t.finished && !t.dismissed) {
        LisbonDialog(
            title = stringResource(R.string.tut_done_title),
            onDismiss = vm::dismissTutorial,
            confirmText = stringResource(R.string.tut_keep_playing),
            onConfirm = vm::dismissTutorial,
            dismissText = stringResource(R.string.tut_to_menu),
            onDismissClick = {
                vm.dismissTutorial()
                vm.quitToMenu()
            },
        ) { Text(stringResource(R.string.tut_done_text)) }
    }
}

/** Gelembung pelatih tutorial — menjelaskan keputusan yang sedang dihadapi pemain. */
@Composable
private fun TutorialCoach(vm: AppViewModel, ui: GameUi) {
    val kind = ui.view.myDecision?.let { it::class.simpleName } ?: "Waiting"
    val textRes = when (kind) {
        "AwaitRoll" -> R.string.tut_roll
        "BuyOrAuction" -> R.string.tut_buy
        "AuctionBid" -> R.string.tut_auction
        "RentChoice" -> R.string.tut_rent
        "SuggestionOffer" -> R.string.tut_suggest
        "RevealForSuggestion" -> R.string.tut_reveal
        "BusinessPhase" -> R.string.tut_business
        "Waiting" -> R.string.tut_waiting
        else -> R.string.tut_other
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .border(2.dp, LisbonColors.StreetLamp, RoundedCornerShape(6.dp))
            .background(LisbonColors.OldPaper, RoundedCornerShape(6.dp))
            .padding(10.dp),
    ) {
        Text("☞ " + stringResource(R.string.tut_title), fontWeight = FontWeight.Bold, color = LisbonColors.FolderRed)
        Text(stringResource(textRes), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.Ink)
        SecondaryButton(stringResource(R.string.tut_hide), vm::dismissTutorial, Modifier.padding(top = 6.dp))
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
            Text(stringResource(R.string.privacy_pass_to), color = LisbonColors.PaperMuted, style = MaterialTheme.typography.bodyLarge)
            Text(name, color = LisbonColors.OldPaper, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.privacy_hint), color = LisbonColors.PaperMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))
            PrimaryButton(stringResource(R.string.privacy_ready), onReady, Modifier.width(260.dp))
        }
    }
}
