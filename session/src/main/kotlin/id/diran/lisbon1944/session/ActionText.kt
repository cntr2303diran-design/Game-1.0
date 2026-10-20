package id.diran.lisbon1944.session

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.PlayerId

/** Label tombol untuk sembarang aksi — dipakai panel cadangan dan tombol umum. */
class ActionText(private val names: Names, private val players: (PlayerId) -> String) {
    private fun t(id: String, en: String) = if (names.lang == Lang.EN) en else id

    fun label(a: Action): String = when (a) {
        is Action.RollDice -> t("Lempar dadu", "Roll dice")
        is Action.UseEscapeRoute -> t("Jalur Pelarian → ${names.tile(a.targetTile)}", "Escape route → ${names.tile(a.targetTile)}")
        is Action.PayJailFine -> t("Bayar denda", "Pay fine")
        is Action.UseJailCard -> t("Pakai Surat Bebas", "Use release card")
        is Action.UseResistanceAbility -> t("Kabur (Perlawanan)", "Escape (Resistance)")
        is Action.BuyProperty -> t("Beli", "Buy")
        is Action.DeclineBuy -> t("Lelang", "Auction")
        is Action.Bid -> t("Tawar ${names.money(a.amount)}", "Bid ${names.money(a.amount)}")
        is Action.PassBid -> t("Mundur", "Pass")
        is Action.PayRentMoney -> t("Bayar uang", "Pay money")
        is Action.PayRentInfo -> t("Bayar dengan ${names.card(a.cardId)}", "Pay with ${names.card(a.cardId)}")
        is Action.Suggest -> t("Duga ${a.suspects.joinToString(" & ") { names.card(it) }} / ${names.card(a.item)} / ${names.card(a.location)}",
            "Suggest ${a.suspects.joinToString(" & ") { names.card(it) }} / ${names.card(a.item)} / ${names.card(a.location)}")
        is Action.SkipSuggestion -> t("Lewati Dugaan", "Skip suggestion")
        is Action.RevealCard -> t("Tunjukkan ${names.card(a.cardId)}", "Show ${names.card(a.cardId)}")
        is Action.StaySilent -> t("Bungkam (1 Intel)", "Stay silent (1 Intel)")
        is Action.DecodePickNumber -> t("Angka rahasia ${a.n}", "Secret number ${a.n}")
        is Action.DecodeRollExtra -> t("Lempar lagi", "Roll again")
        is Action.DecodeStop -> t("Berhenti", "Stop")
        is Action.CasinoBet -> t("Taruh ${names.money(a.amount)}", "Bet ${names.money(a.amount)}")
        is Action.CasinoSkip -> t("Tidak berjudi", "Don't gamble")
        is Action.BlackMarketBuy -> t("Beli info dari ${players(a.targetPlayer)}", "Buy info from ${players(a.targetPlayer)}")
        is Action.BlackMarketChooseCard -> t("Jual ${names.card(a.cardId)}", "Sell ${names.card(a.cardId)}")
        is Action.BlackMarketSkip -> t("Lewati Pasar Gelap", "Skip Black Market")
        is Action.Accuse -> t("Tuduh ${names.card(a.suspect)} / ${names.card(a.location)} / ${names.card(a.item)}",
            "Accuse ${names.card(a.suspect)} / ${names.card(a.location)} / ${names.card(a.item)}")
        is Action.DeclineAccuse -> t("Jangan menuduh", "Don't accuse")
        is Action.Build -> t("Bangun di ${names.tile(a.tile)}", "Build on ${names.tile(a.tile)}")
        is Action.SellBuilding -> t("Jual bangunan ${names.tile(a.tile)}", "Sell building ${names.tile(a.tile)}")
        is Action.Mortgage -> t("Gadai ${names.tile(a.tile)}", "Mortgage ${names.tile(a.tile)}")
        is Action.Unmortgage -> t("Tebus ${names.tile(a.tile)}", "Unmortgage ${names.tile(a.tile)}")
        is Action.ProposeTrade -> t("Tawarkan dagang ke ${players(a.offer.to)}", "Offer trade to ${players(a.offer.to)}")
        is Action.RespondTrade -> if (a.accept) t("Terima", "Accept") else t("Tolak", "Reject")
        is Action.UseMi6Ability -> t("MI6: intip ${players(a.targetPlayer)}", "MI6: peek at ${players(a.targetPlayer)}")
        is Action.ChooseTelegram -> t("Simpan ${names.deckCard("TELEGRAM", a.keepCardId)}", "Keep ${names.deckCard("TELEGRAM", a.keepCardId)}")
        is Action.ChooseCardToShow -> t("Tunjukkan ${names.card(a.cardId)}", "Show ${names.card(a.cardId)}")
        is Action.ChooseWiretapTarget -> t("Sadap ${players(a.target)}", "Wiretap ${players(a.target)}")
        is Action.PlayTactic -> names.tactic(a.cardId) + (a.targetPlayer?.let { " → " + players(it) } ?: "") +
            (a.targetTile?.let { " → " + names.tile(it) } ?: "")
        is Action.SubmitFinalGuess -> t("Tebak ${names.card(a.suspect)} / ${names.card(a.location)} / ${names.card(a.item)}",
            "Guess ${names.card(a.suspect)} / ${names.card(a.location)} / ${names.card(a.item)}")
        is Action.DeclareBankruptcy -> t("Nyatakan bangkrut", "Declare bankruptcy")
        is Action.EndTurn -> t("Akhiri giliran", "End turn")
    }
}
