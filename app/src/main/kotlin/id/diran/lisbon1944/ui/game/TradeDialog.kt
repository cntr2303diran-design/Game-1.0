package id.diran.lisbon1944.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import id.diran.lisbon1944.engine.model.TradeOffer
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.common.WrapGrid
import id.diran.lisbon1944.ui.theme.LisbonColors

/** Penyusun Tawaran Dagang: uang + properti tanpa bangunan. Sah/tidaknya diperiksa mesin aturan. */
@Composable
fun TradeDialog(ctx: DecisionCtx, onDismiss: () -> Unit) {
    val v = ctx.view
    val partners = v.opponents.filter { !it.bankrupt }
    var partner by remember { mutableStateOf(partners.firstOrNull()?.id) }
    var giveMoney by remember { mutableIntStateOf(0) }
    var takeMoney by remember { mutableIntStateOf(0) }
    var give by remember { mutableStateOf(setOf<Int>()) }
    var take by remember { mutableStateOf(setOf<Int>()) }

    val p = partner
    val offer = p?.let {
        TradeOffer(
            from = ctx.me,
            to = it,
            giveMoney = giveMoney,
            takeMoney = takeMoney,
            giveProperties = give.sorted(),
            takeProperties = take.sorted(),
        )
    }
    val action = offer?.let { Action.ProposeTrade(ctx.me, it) }
    val problem = action?.let(ctx.check)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dec_trade)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                SectionTitle(stringResource(R.string.trade_partner))
                WrapGrid(partners, 3) { o, m ->
                    ToggleTag(o.name, partner == o.id, {
                        partner = o.id
                        take = emptySet()
                        takeMoney = 0
                    }, m)
                }
                SectionTitle(stringResource(R.string.trade_you_give))
                MoneyStepper(ctx, giveMoney, v.me.money) { giveMoney = it }
                val mine = v.ownership.filter { it.value.ownerId == ctx.me && it.value.level == 0 }.keys.sorted()
                WrapGrid(mine, 2) { t, m -> ToggleTag(ctx.names.tile(t), t in give, { give = if (t in give) give - t else give + t }, m) }
                if (p != null) {
                    val theirMoney = v.opponents.firstOrNull { it.id == p }?.money ?: 0
                    SectionTitle(stringResource(R.string.trade_you_take))
                    MoneyStepper(ctx, takeMoney, theirMoney) { takeMoney = it }
                    val theirs = v.ownership.filter { it.value.ownerId == p && it.value.level == 0 }.keys.sorted()
                    WrapGrid(theirs, 2) { t, m -> ToggleTag(ctx.names.tile(t), t in take, { take = if (t in take) take - t else take + t }, m) }
                }
                Spacer(Modifier.width(4.dp))
                if (problem != null && (giveMoney > 0 || takeMoney > 0 || give.isNotEmpty() || take.isNotEmpty())) {
                    Text(problem, color = LisbonColors.FolderRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = action != null && problem == null,
                onClick = {
                    action?.let(ctx.act)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.trade_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MoneyStepper(ctx: DecisionCtx, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToggleTag("−100", false, { onChange((value - 100).coerceAtLeast(0)) })
        ToggleTag("−10", false, { onChange((value - 10).coerceAtLeast(0)) })
        Text(ctx.names.money(value), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        ToggleTag("+10", false, { onChange((value + 10).coerceAtMost(max)) })
        ToggleTag("+100", false, { onChange((value + 100).coerceAtMost(max)) })
    }
}

