package id.diran.lisbon1944.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.HeldCard
import id.diran.lisbon1944.engine.model.TradeOffer
import id.diran.lisbon1944.ui.common.LisbonDialog
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.common.WrapGrid
import id.diran.lisbon1944.ui.theme.LisbonColors

/**
 * Penyusun Tawaran Dagang dua kolom (aku / dia): uang, properti tanpa bangunan, kartu simpanan (Surat Bebas),
 * dan kartu Bukti milikku. Sah/tidaknya diperiksa mesin aturan; penerima menjawab di layar privasinya sendiri.
 */
@Composable
fun TradeDialog(ctx: DecisionCtx, onDismiss: () -> Unit) {
    val v = ctx.view
    val partners = v.opponents.filter { !it.bankrupt }
    var partner by remember { mutableStateOf(partners.firstOrNull()?.id) }
    var giveMoney by remember { mutableIntStateOf(0) }
    var takeMoney by remember { mutableIntStateOf(0) }
    var give by remember { mutableStateOf(setOf<Int>()) }
    var take by remember { mutableStateOf(setOf<Int>()) }
    var giveHeld by remember { mutableStateOf(listOf<HeldCard>()) }
    var giveEvidence by remember { mutableStateOf(setOf<CardId>()) }

    val p = partner
    val offer = p?.let {
        TradeOffer(
            from = ctx.me,
            to = it,
            giveMoney = giveMoney,
            takeMoney = takeMoney,
            giveProperties = give.sorted(),
            takeProperties = take.sorted(),
            giveHeldCards = giveHeld,
            giveEvidence = giveEvidence.sortedBy { c -> c.value },
        )
    }
    val action = offer?.let { Action.ProposeTrade(ctx.me, it) }
    val problem = action?.let(ctx.check)
    val touched = giveMoney > 0 || takeMoney > 0 || give.isNotEmpty() || take.isNotEmpty() || giveHeld.isNotEmpty() || giveEvidence.isNotEmpty()

    LisbonDialog(
        title = stringResource(R.string.dec_trade),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.trade_send),
        onConfirm = {
            action?.let(ctx.act)
            onDismiss()
        },
        confirmEnabled = action != null && problem == null,
        dismissText = stringResource(R.string.cancel),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.trade_partner))
            WrapGrid(partners, 3) { o, m ->
                ToggleTag(o.name, partner == o.id, {
                    partner = o.id
                    take = emptySet()
                    takeMoney = 0
                }, m)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.trade_you_give))
                    MoneyStepper(ctx, giveMoney, v.me.money) { giveMoney = it }
                    val mine = v.ownership.filter { it.value.ownerId == ctx.me && it.value.level == 0 }.keys.sorted()
                    mine.forEach { t ->
                        ToggleTag(ctx.names.tile(t), t in give, { give = if (t in give) give - t else give + t }, Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    v.me.heldCards.forEach { h ->
                        val sel = h in giveHeld
                        ToggleTag(ctx.names.deckCard(h.deck.name, h.cardId), sel, { giveHeld = if (sel) giveHeld - h else giveHeld + h }, Modifier.fillMaxWidth().padding(top = 4.dp), accent = LisbonColors.Olive)
                    }
                    v.me.hand.forEach { c ->
                        val sel = c in giveEvidence
                        ToggleTag(ctx.names.card(c), sel, { giveEvidence = if (sel) giveEvidence - c else giveEvidence + c }, Modifier.fillMaxWidth().padding(top = 4.dp), accent = LisbonColors.TagusBlue, sub = stringResource(R.string.trade_evidence))
                    }
                }
                Column(Modifier.weight(1f)) {
                    SectionTitle(p?.let(ctx::player) ?: "—")
                    if (p != null) {
                        val theirMoney = v.opponents.firstOrNull { it.id == p }?.money ?: 0
                        MoneyStepper(ctx, takeMoney, theirMoney) { takeMoney = it }
                        val theirs = v.ownership.filter { it.value.ownerId == p && it.value.level == 0 }.keys.sorted()
                        theirs.forEach { t ->
                            ToggleTag(ctx.names.tile(t), t in take, { take = if (t in take) take - t else take + t }, Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
            }
            if (problem != null && touched) {
                Text(problem, color = LisbonColors.FolderRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun MoneyStepper(ctx: DecisionCtx, value: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(ctx.names.money(value), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleTag("−50", false, { onChange((value - 50).coerceAtLeast(0)) }, Modifier.weight(1f))
            ToggleTag("+50", false, { onChange((value + 50).coerceAtMost(max)) }, Modifier.weight(1f))
        }
    }
}
