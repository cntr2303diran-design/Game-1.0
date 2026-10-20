package id.diran.lisbon1944.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.InfoBlockedReason
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.ShowReason
import id.diran.lisbon1944.engine.model.TradeOffer
import id.diran.lisbon1944.session.ActionText
import id.diran.lisbon1944.session.Choices
import id.diran.lisbon1944.session.Names
import id.diran.lisbon1944.ui.GameUi
import id.diran.lisbon1944.ui.common.KeyValue
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.PrimaryButton
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.common.WrapGrid
import id.diran.lisbon1944.ui.theme.LisbonColors
import kotlin.math.roundToInt

/** Konteks yang dibutuhkan semua sub-panel keputusan. */
class DecisionCtx(
    val ui: GameUi,
    val content: GameContent,
    val names: Names,
    val text: ActionText,
    val act: (Action) -> Unit,
    val check: (Action) -> String?,
) {
    val view: PlayerView get() = ui.view
    val legal: List<Action> get() = ui.legal
    val me: PlayerId get() = ui.view.me.id
    fun player(id: PlayerId): String = ui.playerNames[id.value] ?: "P${id.value + 1}"
}

@Composable
fun DecisionPanel(ctx: DecisionCtx, onSkipBots: () -> Unit) {
    val ui = ctx.ui
    val d = ui.view.myDecision
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (d == null || ui.legal.isEmpty()) {
            Waiting(ctx, onSkipBots)
            return@Column
        }
        when (d) {
            is PendingDecision.AwaitRoll -> RollPanel(ctx)
            is PendingDecision.JailChoice -> JailPanel(ctx, d)
            is PendingDecision.BuyOrAuction -> BuyPanel(ctx, d)
            is PendingDecision.AuctionBid -> AuctionPanel(ctx, d)
            is PendingDecision.RentChoice -> RentPanel(ctx, d)
            is PendingDecision.SuggestionOffer -> SuggestionPanel(ctx, d)
            is PendingDecision.RevealForSuggestion -> RevealPanel(ctx, d)
            is PendingDecision.DecodeOperatorPick -> DecodePickPanel(ctx, d)
            is PendingDecision.DecodeContinue -> DecodeContinuePanel(ctx, d)
            is PendingDecision.CasinoChoice -> CasinoPanel(ctx)
            is PendingDecision.BlackMarketChoice -> SimpleChoice(ctx, stringResource(R.string.dec_black_market), stringResource(R.string.dec_black_market_hint, ctx.names.money(ctx.content.balance.blackMarketPrice)))
            is PendingDecision.BlackMarketPickCard -> SimpleChoice(ctx, stringResource(R.string.dec_black_market_sell), stringResource(R.string.dec_black_market_sell_hint, ctx.player(d.buyer)))
            is PendingDecision.AccuseOffer -> AccusePanel(ctx, d.cost)
            is PendingDecision.TradeResponse -> TradeResponsePanel(ctx, d.offer)
            is PendingDecision.TelegramKeepChoice -> TelegramPanel(ctx, d)
            is PendingDecision.ShowCardTo -> SimpleChoice(ctx, stringResource(R.string.dec_show_card), stringResource(R.string.dec_show_card_hint, ctx.player(d.target), showReason(d.reason)))
            is PendingDecision.ChooseWiretapTarget -> SimpleChoice(ctx, stringResource(R.string.dec_wiretap), stringResource(R.string.dec_wiretap_hint))
            is PendingDecision.RaiseFunds -> RaiseFundsPanel(ctx, d)
            is PendingDecision.FinalGuess -> FinalGuessPanel(ctx)
            is PendingDecision.BusinessPhase -> BusinessPanel(ctx)
        }
        AllActions(ctx)
        ui.message?.let { Text(it, color = LisbonColors.FolderRed, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun showReason(r: ShowReason): String = when (r) {
    ShowReason.SECRET_CONFERENCE -> stringResource(R.string.reason_conference)
    ShowReason.COURIER -> stringResource(R.string.reason_courier)
    ShowReason.OPEN_TELEGRAM -> stringResource(R.string.reason_open_telegram)
    ShowReason.INTELLIGENCE_REPORT -> stringResource(R.string.reason_intel_report)
    ShowReason.CITY_MAP -> stringResource(R.string.reason_city_map)
}

@Composable
private fun Waiting(ctx: DecisionCtx, onSkipBots: () -> Unit) {
    val ui = ctx.ui
    PaperCard(Modifier.fillMaxWidth()) {
        val who = ui.waitingOn?.let(ctx::player) ?: "—"
        Text(stringResource(R.string.dec_waiting, who), fontWeight = FontWeight.Bold)
        ui.view.waitingKind?.let { Text(waitingLabel(it), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft) }
        if (ui.isBotTurn) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.dec_skip_bots), onSkipBots)
        }
    }
}

@Composable
private fun waitingLabel(kind: String): String = when (kind) {
    "AwaitRoll" -> stringResource(R.string.wait_roll)
    "AuctionBid" -> stringResource(R.string.wait_auction)
    "RentChoice" -> stringResource(R.string.wait_rent)
    "SuggestionOffer" -> stringResource(R.string.wait_suggest)
    "RevealForSuggestion" -> stringResource(R.string.wait_reveal)
    "BusinessPhase" -> stringResource(R.string.wait_business)
    "TradeResponse" -> stringResource(R.string.wait_trade)
    else -> stringResource(R.string.wait_other)
}

// ---------------------------------------------------------------------------------------------
// Gerak, penjara, beli, lelang, sewa
// ---------------------------------------------------------------------------------------------

@Composable
private fun RollPanel(ctx: DecisionCtx) {
    PaperCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.dec_move))
        Choices.first<Action.RollDice>(ctx.legal)?.let { PrimaryButton(stringResource(R.string.dec_roll), { ctx.act(it) }) }
        val routes = Choices.all<Action.UseEscapeRoute>(ctx.legal)
        if (routes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.dec_escape_hint, ctx.names.money(ctx.content.balance.escapeRouteTravelCost)), style = MaterialTheme.typography.bodyMedium)
            routes.forEach { a -> SecondaryButton(ctx.text.label(a), { ctx.act(a) }, Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun JailPanel(ctx: DecisionCtx, d: PendingDecision.JailChoice) {
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
        SectionTitle(stringResource(R.string.dec_jail))
        Text(stringResource(R.string.dec_jail_hint, d.turnsServed), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        ctx.legal.forEach { a -> SecondaryButton(ctx.text.label(a), { ctx.act(a) }, Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun TileHeader(ctx: DecisionCtx, tile: Int) {
    val t = ctx.content.board[tile]
    Text(ctx.names.tile(tile), style = MaterialTheme.typography.titleMedium)
    when (t) {
        is PropertyTile -> {
            Text(ctx.names.district(t.district), color = Palette.district(t.district), fontWeight = FontWeight.Bold)
            KeyValue(stringResource(R.string.tile_price_label), ctx.names.money(t.price))
            KeyValue(stringResource(R.string.tile_rent_label), "${ctx.names.money(t.baseRent)} → ${ctx.names.money(t.hqRent)}")
            val sameDistrict = ctx.content.propertiesIn(t.district)
            val mine = sameDistrict.count { ctx.view.ownership[it.index]?.ownerId == ctx.me }
            KeyValue(stringResource(R.string.tile_district_owned), "$mine / ${sameDistrict.size}")
        }
        is EscapeRouteTile -> {
            Text(stringResource(R.string.tile_escape), color = LisbonColors.InkSoft)
            KeyValue(stringResource(R.string.tile_price_label), ctx.names.money(t.price))
        }
        else -> Unit
    }
}

@Composable
private fun BuyPanel(ctx: DecisionCtx, d: PendingDecision.BuyOrAuction) {
    PaperCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.dec_buy))
        TileHeader(ctx, d.tile)
        KeyValue(stringResource(R.string.dec_your_money), ctx.names.money(ctx.view.me.money))
        Spacer(Modifier.height(8.dp))
        Choices.first<Action.BuyProperty>(ctx.legal)?.let { PrimaryButton(stringResource(R.string.dec_buy_for, ctx.names.money(d.price)), { ctx.act(it) }) }
        Choices.first<Action.DeclineBuy>(ctx.legal)?.let { SecondaryButton(stringResource(R.string.dec_to_auction), { ctx.act(it) }, Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun AmountPicker(amounts: List<Int>, ctx: DecisionCtx, label: @Composable (Int) -> String, onPick: (Int) -> Unit) {
    if (amounts.isEmpty()) return
    var idx by remember(amounts) { mutableIntStateOf(0) }
    val i = idx.coerceIn(0, amounts.lastIndex)
    if (amounts.size > 1) {
        Slider(
            value = i.toFloat(),
            onValueChange = { idx = it.roundToInt() },
            valueRange = 0f..amounts.lastIndex.toFloat(),
            steps = (amounts.size - 2).coerceAtLeast(0),
        )
        val quick = listOf(0, amounts.lastIndex / 4, amounts.lastIndex / 2, amounts.lastIndex).distinct()
        WrapGrid(quick, 4) { q, m -> ToggleTag(ctx.names.money(amounts[q]), q == i, { idx = q }, m) }
        Spacer(Modifier.height(6.dp))
    }
    PrimaryButton(label(amounts[i]), { onPick(amounts[i]) })
}

@Composable
private fun AuctionPanel(ctx: DecisionCtx, d: PendingDecision.AuctionBid) {
    val auction = ctx.view.auction
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.StreetLamp) {
        SectionTitle(stringResource(R.string.dec_auction))
        TileHeader(ctx, d.tile)
        KeyValue(
            stringResource(R.string.dec_high_bid),
            if (auction?.highBidder != null) "${ctx.names.money(auction.highBid)} (${ctx.player(auction.highBidder!!)})" else "—",
        )
        auction?.let { a -> KeyValue(stringResource(R.string.dec_still_in), a.stillIn.joinToString { ctx.player(it) }) }
        KeyValue(stringResource(R.string.dec_your_money), ctx.names.money(ctx.view.me.money))
        Spacer(Modifier.height(6.dp))
        val amounts = Choices.bidAmounts(ctx.legal)
        AmountPicker(amounts, ctx, { stringResource(R.string.dec_bid, ctx.names.money(it)) }) { amt ->
            ctx.legal.filterIsInstance<Action.Bid>().firstOrNull { it.amount == amt }?.let(ctx.act)
        }
        Choices.first<Action.PassBid>(ctx.legal)?.let { SecondaryButton(stringResource(R.string.dec_pass), { ctx.act(it) }, Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun RentPanel(ctx: DecisionCtx, d: PendingDecision.RentChoice) {
    val q = d.quote
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
        SectionTitle(stringResource(R.string.dec_rent))
        Text(stringResource(R.string.dec_rent_due, ctx.names.tile(q.tile), ctx.player(q.ownerId)), style = MaterialTheme.typography.bodyMedium)
        Text(ctx.names.money(q.amount), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.FolderRed)
        KeyValue(stringResource(R.string.dec_your_money), ctx.names.money(ctx.view.me.money))
        Spacer(Modifier.height(6.dp))
        Choices.first<Action.PayRentMoney>(ctx.legal)?.let { PrimaryButton(stringResource(R.string.dec_pay_money), { ctx.act(it) }) }
        val info = Choices.all<Action.PayRentInfo>(ctx.legal)
        SectionTitle(stringResource(R.string.dec_pay_info))
        if (q.forcedLocation) Text(stringResource(R.string.dec_rent_forced), color = LisbonColors.FolderRed, style = MaterialTheme.typography.bodyMedium)
        if (info.isEmpty()) {
            Text(
                when (q.infoBlockedReason) {
                    InfoBlockedReason.RENT_TOO_HIGH -> stringResource(R.string.info_blocked_high, ctx.names.money(ctx.content.balance.infoPaymentMaxRent))
                    InfoBlockedReason.QUOTA_EXHAUSTED -> stringResource(R.string.info_blocked_quota)
                    InfoBlockedReason.EMPTY_HAND -> stringResource(R.string.info_blocked_empty)
                    else -> stringResource(R.string.info_blocked_na)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LisbonColors.InkSoft,
            )
        } else {
            Text(stringResource(R.string.dec_pay_info_hint, ctx.player(q.ownerId)), style = MaterialTheme.typography.bodyMedium)
            WrapGrid(info, 2) { a, m -> EvidenceButton(ctx, a.cardId, m) { ctx.act(a) } }
        }
        TacticButtons(ctx)
    }
}

@Composable
private fun EvidenceButton(ctx: DecisionCtx, card: CardId, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val type = runCatching { ctx.content.evidence(card).type }.getOrDefault(EvidenceType.ITEM)
    ToggleTag(ctx.names.card(card), selected, onClick, modifier, accent = Palette.evidence(type), sub = ctx.names.type(type))
}

@Composable
private fun TacticButtons(ctx: DecisionCtx) {
    val tactics = Choices.tactics(ctx.legal)
    if (tactics.isEmpty()) return
    SectionTitle(stringResource(R.string.dec_tactics))
    tactics.forEach { (tid, variants) ->
        Text(ctx.names.tactic(tid), fontWeight = FontWeight.Bold)
        Text(ctx.names.tacticText(tid), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft)
        if (variants.size == 1 && variants[0].targetPlayer == null && variants[0].targetTile == null) {
            SecondaryButton(stringResource(R.string.dec_play), { ctx.act(variants[0]) }, Modifier.padding(top = 4.dp))
        } else {
            WrapGrid(variants, 2) { a, m ->
                val label = a.targetPlayer?.let(ctx::player) ?: a.targetTile?.let(ctx.names::tile) ?: "▶"
                ToggleTag(label, false, { ctx.act(a) }, m)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Misteri
// ---------------------------------------------------------------------------------------------

/** Tanda pengetahuan kecil di samping kartu: ✓ sudah diketahui tidak di amplop. */
private fun knownElsewhere(view: PlayerView, card: CardId): Boolean =
    card in view.me.hand || view.knowledge.any { it.cardId == card && it.holder !is Holder.Envelope }

@Composable
private fun PickGroup(ctx: DecisionCtx, title: String, cards: List<CardId>, isSel: (CardId) -> Boolean, toggle: (CardId) -> Unit) {
    SectionTitle(title)
    WrapGrid(cards, 2) { c, m ->
        val type = runCatching { ctx.content.evidence(c).type }.getOrDefault(EvidenceType.ITEM)
        ToggleTag(
            (if (knownElsewhere(ctx.view, c)) "✓ " else "") + ctx.names.card(c),
            isSel(c),
            { toggle(c) },
            m,
            accent = Palette.evidence(type),
        )
    }
}

@Composable
private fun <A : Action> TriplePicker(
    ctx: DecisionCtx,
    menu: Choices.TripleMenu<A>,
    confirmLabel: String,
    fixedHint: String? = null,
    onSubmit: (A) -> Unit,
) {
    var suspects by remember(menu.suspectSets) { mutableStateOf(listOf<CardId>()) }
    var item by remember(menu.items) { mutableStateOf<CardId?>(menu.items.singleOrNull()) }
    var location by remember(menu.locations) { mutableStateOf<CardId?>(menu.locations.singleOrNull()) }
    val maxSuspects = if (menu.allowsPair) 2 else 1

    Text(stringResource(R.string.dec_known_mark), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft)
    PickGroup(
        ctx,
        if (maxSuspects == 2) stringResource(R.string.dec_pick_suspects_nkvd) else stringResource(R.string.dec_pick_suspect),
        menu.suspects,
        { it in suspects },
    ) { c ->
        suspects = when {
            c in suspects -> suspects - c
            maxSuspects == 1 -> listOf(c)
            suspects.size < maxSuspects -> suspects + c
            else -> listOf(suspects.last(), c)
        }
    }
    PickGroup(ctx, stringResource(R.string.dec_pick_item), menu.items, { it == item }) { item = it }
    if (menu.locations.size == 1) {
        SectionTitle(stringResource(R.string.dec_pick_location))
        Text(ctx.names.card(menu.locations.first()) + (fixedHint?.let { " — $it" } ?: ""), fontWeight = FontWeight.Bold)
    } else {
        PickGroup(ctx, stringResource(R.string.dec_pick_location), menu.locations, { it == location }) { location = it }
    }
    Spacer(Modifier.height(8.dp))
    val action = menu.find(suspects, item, location)
    PrimaryButton(confirmLabel, { action?.let(onSubmit) }, enabled = action != null)
}

@Composable
private fun SuggestionPanel(ctx: DecisionCtx, d: PendingDecision.SuggestionOffer) {
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.TagusBlue) {
        SectionTitle(stringResource(R.string.dec_suggest), color = LisbonColors.TagusBlue)
        Text(stringResource(R.string.dec_suggest_hint, ctx.view.me.intel), style = MaterialTheme.typography.bodyMedium)
        val menu = Choices.suggestionMenu(ctx.legal)
        if (menu != null) {
            TriplePicker(ctx, menu, stringResource(R.string.dec_suggest_go), fixedHint = if (d.fixedLocation != null) stringResource(R.string.dec_fixed_location) else null) { ctx.act(it) }
        } else {
            Text(stringResource(R.string.dec_suggest_none), color = LisbonColors.InkSoft)
        }
        Choices.first<Action.SkipSuggestion>(ctx.legal)?.let { SecondaryButton(stringResource(R.string.dec_skip), { ctx.act(it) }, Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun RevealPanel(ctx: DecisionCtx, d: PendingDecision.RevealForSuggestion) {
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.TagusBlue) {
        SectionTitle(stringResource(R.string.dec_reveal), color = LisbonColors.TagusBlue)
        Text(stringResource(R.string.dec_reveal_hint, ctx.player(d.asker)), style = MaterialTheme.typography.bodyMedium)
        Text(d.named.joinToString(" · ") { ctx.names.card(it) }, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val reveals = Choices.all<Action.RevealCard>(ctx.legal)
        WrapGrid(reveals, 2) { a, m -> EvidenceButton(ctx, a.cardId, m) { ctx.act(a) } }
        Choices.first<Action.StaySilent>(ctx.legal)?.let {
            Spacer(Modifier.height(6.dp))
            SecondaryButton(stringResource(R.string.dec_silent, ctx.view.me.intel), { ctx.act(it) })
        }
    }
}

@Composable
private fun DecodePickPanel(ctx: DecisionCtx, d: PendingDecision.DecodeOperatorPick) {
    PaperCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.dec_decode_pick))
        Text(stringResource(R.string.dec_decode_pick_hint, ctx.player(d.asker)), style = MaterialTheme.typography.bodyMedium)
        WrapGrid(Choices.all<Action.DecodePickNumber>(ctx.legal), 6) { a, m -> ToggleTag("${a.n}", false, { ctx.act(a) }, m) }
    }
}

@Composable
private fun DecodeContinuePanel(ctx: DecisionCtx, d: PendingDecision.DecodeContinue) {
    PaperCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.dec_decode))
        Text(stringResource(R.string.dec_decode_hint, d.extraRollsUsed, ctx.names.money(d.paid), ctx.names.money(ctx.content.balance.decodeExtraRollCost)), style = MaterialTheme.typography.bodyMedium)
        Choices.first<Action.DecodeRollExtra>(ctx.legal)?.let { PrimaryButton(stringResource(R.string.dec_decode_roll), { ctx.act(it) }) }
        Choices.first<Action.DecodeStop>(ctx.legal)?.let { SecondaryButton(stringResource(R.string.dec_decode_stop), { ctx.act(it) }, Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun AccusePanel(ctx: DecisionCtx, cost: Int) {
    val menu = Choices.accuseMenu(ctx.legal)
    var pending by remember { mutableStateOf<Action.Accuse?>(null) }
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
        SectionTitle(stringResource(R.string.dec_accuse))
        Text(stringResource(R.string.dec_accuse_hint, ctx.names.money(cost)), style = MaterialTheme.typography.bodyMedium)
        if (menu != null) TriplePicker(ctx, menu, stringResource(R.string.dec_accuse_go)) { pending = it }
        Choices.first<Action.DeclineAccuse>(ctx.legal)?.let { SecondaryButton(stringResource(R.string.dec_accuse_decline), { ctx.act(it) }, Modifier.padding(top = 6.dp)) }
    }
    AccuseConfirm(ctx, pending, onDismiss = { pending = null })
}

@Composable
private fun AccuseConfirm(ctx: DecisionCtx, action: Action.Accuse?, onDismiss: () -> Unit) {
    if (action == null) return
    var second by remember(action) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (second) stringResource(R.string.accuse_confirm_2) else stringResource(R.string.accuse_confirm_1)) },
        text = {
            Column {
                Text(listOf(action.suspect, action.location, action.item).joinToString(" · ") { ctx.names.card(it) }, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.accuse_warning), color = LisbonColors.FolderRed)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (second) {
                    onDismiss()
                    ctx.act(action)
                } else {
                    second = true
                }
            }) { Text(if (second) stringResource(R.string.accuse_final) else stringResource(R.string.accuse_sure)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FinalGuessPanel(ctx: DecisionCtx) {
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.StreetLamp) {
        SectionTitle(stringResource(R.string.dec_final_guess))
        Text(stringResource(R.string.dec_final_guess_hint), style = MaterialTheme.typography.bodyMedium)
        Choices.finalGuessMenu(ctx.legal)?.let { menu -> TriplePicker(ctx, menu, stringResource(R.string.dec_final_guess_go)) { ctx.act(it) } }
    }
}

// ---------------------------------------------------------------------------------------------
// Kasino, Telegram, dagang, dana darurat, pilihan sederhana
// ---------------------------------------------------------------------------------------------

@Composable
private fun CasinoPanel(ctx: DecisionCtx) {
    val p = ctx.content.balance.casinoPayout
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.StreetLamp) {
        SectionTitle(stringResource(R.string.dec_casino))
        Text(stringResource(R.string.dec_casino_hint, p.seven, p.eleven, p.doubles), style = MaterialTheme.typography.bodyMedium)
        KeyValue(stringResource(R.string.city_vault), ctx.names.money(ctx.view.casinoVault))
        AmountPicker(Choices.casinoAmounts(ctx.legal), ctx, { stringResource(R.string.dec_casino_bet, ctx.names.money(it)) }) { amt ->
            ctx.legal.filterIsInstance<Action.CasinoBet>().firstOrNull { it.amount == amt }?.let(ctx.act)
        }
        Choices.first<Action.CasinoSkip>(ctx.legal)?.let { SecondaryButton(stringResource(R.string.dec_casino_skip), { ctx.act(it) }, Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun TelegramPanel(ctx: DecisionCtx, d: PendingDecision.TelegramKeepChoice) {
    PaperCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.dec_telegram))
        Choices.all<Action.ChooseTelegram>(ctx.legal).forEach { a ->
            PaperCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(ctx.names.deckCard("TELEGRAM", a.keepCardId), fontWeight = FontWeight.Bold)
                Text(ctx.names.deckCardText(Deck.TELEGRAM, a.keepCardId), style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(stringResource(R.string.dec_keep), { ctx.act(a) }, Modifier.padding(top = 4.dp))
            }
        }
        if (d.options.isEmpty()) Text("—")
    }
}

@Composable
private fun TradeResponsePanel(ctx: DecisionCtx, o: TradeOffer) {
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.Olive) {
        SectionTitle(stringResource(R.string.dec_trade_offer), color = LisbonColors.Olive)
        Text(stringResource(R.string.dec_trade_from, ctx.player(o.from)), fontWeight = FontWeight.Bold)
        TradeSummary(ctx, o)
        Spacer(Modifier.height(6.dp))
        ctx.legal.filterIsInstance<Action.RespondTrade>().sortedBy { !it.accept }.forEach { a ->
            if (a.accept) PrimaryButton(stringResource(R.string.dec_accept), { ctx.act(a) })
            else SecondaryButton(stringResource(R.string.dec_reject), { ctx.act(a) }, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun TradeSummary(ctx: DecisionCtx, o: TradeOffer) {
    val give = buildList {
        if (o.giveMoney > 0) add(ctx.names.money(o.giveMoney))
        o.giveProperties.forEach { add(ctx.names.tile(it)) }
        o.giveHeldCards.forEach { add(ctx.names.deckCard(it.deck.name, it.cardId)) }
        o.giveEvidence.forEach { add(ctx.names.card(it)) }
    }
    val take = buildList {
        if (o.takeMoney > 0) add(ctx.names.money(o.takeMoney))
        o.takeProperties.forEach { add(ctx.names.tile(it)) }
        o.takeHeldCards.forEach { add(ctx.names.deckCard(it.deck.name, it.cardId)) }
        o.takeEvidence.forEach { add(ctx.names.card(it)) }
    }
    KeyValue(stringResource(R.string.trade_gives, ctx.player(o.from)), give.joinToString().ifEmpty { "—" })
    KeyValue(stringResource(R.string.trade_gives, ctx.player(o.to)), take.joinToString().ifEmpty { "—" })
}

@Composable
private fun RaiseFundsPanel(ctx: DecisionCtx, d: PendingDecision.RaiseFunds) {
    var confirm by remember { mutableStateOf(false) }
    PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.FolderRed) {
        SectionTitle(stringResource(R.string.dec_raise))
        Text(stringResource(R.string.dec_raise_hint, ctx.names.money(d.amountOwed), ctx.names.money(ctx.view.me.money)), style = MaterialTheme.typography.bodyMedium)
        PropertyOps(ctx)
        Choices.first<Action.DeclareBankruptcy>(ctx.legal)?.let { SecondaryButton(stringResource(R.string.dec_bankrupt), { confirm = true }, Modifier.padding(top = 8.dp)) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.dec_bankrupt_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    Choices.first<Action.DeclareBankruptcy>(ctx.legal)?.let(ctx.act)
                }) { Text(stringResource(R.string.dec_bankrupt)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SimpleChoice(ctx: DecisionCtx, title: String, hint: String) {
    PaperCard(Modifier.fillMaxWidth()) {
        SectionTitle(title)
        Text(hint, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        ctx.legal.forEach { a -> SecondaryButton(ctx.text.label(a), { ctx.act(a) }, Modifier.padding(top = 4.dp)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Fase Bisnis
// ---------------------------------------------------------------------------------------------

@Composable
private fun PropertyOps(ctx: DecisionCtx) {
    val ops = Choices.tileOps(ctx.legal).values.filter { it.any }
    if (ops.isEmpty()) return
    SectionTitle(stringResource(R.string.dec_properties))
    ops.forEach { op ->
        val tile = ctx.content.board[op.tile]
        val own = ctx.view.ownership[op.tile]
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ctx.names.tile(op.tile), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = (tile as? PropertyTile)?.let { Palette.district(it.district) } ?: LisbonColors.Ink)
                val lvl = own?.level ?: 0
                Text(
                    (if (lvl >= 5) stringResource(R.string.tile_hq) else stringResource(R.string.tile_houses, lvl)) +
                        if (own?.mortgaged == true) " · " + stringResource(R.string.tile_mortgaged) else "",
                    fontSize = 11.sp,
                    color = LisbonColors.InkSoft,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                op.build?.let { ToggleTag("+🏠", false, { ctx.act(it) }) }
                op.sell?.let { ToggleTag("−🏠", false, { ctx.act(it) }) }
                op.mortgage?.let { ToggleTag(stringResource(R.string.op_mortgage), false, { ctx.act(it) }) }
                op.unmortgage?.let { ToggleTag(stringResource(R.string.op_unmortgage), false, { ctx.act(it) }) }
            }
        }
    }
}

@Composable
private fun BusinessPanel(ctx: DecisionCtx) {
    var trading by remember { mutableStateOf(false) }
    var accusing by remember { mutableStateOf(false) }
    var pendingAccuse by remember { mutableStateOf<Action.Accuse?>(null) }
    PaperCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.dec_business))
        Text(stringResource(R.string.dec_business_hint), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft)
        Spacer(Modifier.height(6.dp))
        Choices.first<Action.EndTurn>(ctx.legal)?.let { PrimaryButton(stringResource(R.string.dec_end_turn), { ctx.act(it) }) }
        PropertyOps(ctx)
        TacticButtons(ctx)
        val mi6 = Choices.all<Action.UseMi6Ability>(ctx.legal)
        if (mi6.isNotEmpty()) {
            SectionTitle(stringResource(R.string.dec_mi6))
            WrapGrid(mi6, 3) { a, m -> ToggleTag(ctx.player(a.targetPlayer), false, { ctx.act(a) }, m) }
        }
        val accuseMenu = Choices.accuseMenu(ctx.legal)
        if (accuseMenu != null) {
            SectionTitle(stringResource(R.string.dec_april_accuse))
            if (!accusing) {
                SecondaryButton(stringResource(R.string.dec_open_accuse), { accusing = true })
            } else {
                TriplePicker(ctx, accuseMenu, stringResource(R.string.dec_accuse_go)) { pendingAccuse = it }
            }
        }
        if (ctx.view.opponents.any { !it.bankrupt }) {
            SectionTitle(stringResource(R.string.dec_trade))
            SecondaryButton(stringResource(R.string.dec_trade_open), { trading = true })
        }
    }
    AccuseConfirm(ctx, pendingAccuse, onDismiss = { pendingAccuse = null })
    if (trading) TradeDialog(ctx, onDismiss = { trading = false })
}

@Composable
private fun AllActions(ctx: DecisionCtx) {
    val big = setOf(Action.Suggest::class, Action.Accuse::class, Action.SubmitFinalGuess::class, Action.Bid::class, Action.CasinoBet::class)
    val rest = ctx.legal.filter { it::class !in big }
    if (rest.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    SecondaryButton(stringResource(R.string.dec_all_actions, rest.size) + if (open) " ▲" else " ▼", { open = !open })
    if (open) rest.forEach { a -> SecondaryButton(ctx.text.label(a), { ctx.act(a) }, Modifier.padding(top = 4.dp)) }
}
