package id.diran.lisbon1944.session

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.model.WinType
import java.text.NumberFormat
import java.util.Locale

enum class Lang { ID, EN }

/** Nama terlokalisasi untuk konten permainan (dari JSON: name/nameEn). */
class Names(val content: GameContent, val lang: Lang) {
    private fun pick(id: String, en: String) = if (lang == Lang.EN) en else id

    fun tile(index: Int): String = content.board.getOrNull(index)?.let { pick(it.name, it.nameEn) } ?: "#$index"

    fun card(id: CardId): String = runCatching { content.evidence(id) }.getOrNull()?.let { pick(it.name, it.nameEn) } ?: id.value

    fun card(id: String): String = card(CardId(id))

    fun faction(f: Faction): String = content.faction(f).let { pick(it.name, it.nameEn) }

    fun factionShort(f: Faction): String = content.faction(f).organization

    fun front(id: Int): String = runCatching { content.front(FrontId(id)) }.getOrNull()?.let { pick(it.title, it.titleEn) } ?: "Front $id"

    fun frontText(id: Int): String = runCatching { content.front(FrontId(id)) }.getOrNull()?.let { pick(it.text, it.textEn) } ?: ""

    fun tactic(id: Int): String = runCatching { content.tactic(id) }.getOrNull()?.let { pick(it.title, it.titleEn) } ?: "Taktik $id"

    fun tacticText(id: Int): String = runCatching { content.tactic(id) }.getOrNull()?.let { pick(it.text, it.textEn) } ?: ""

    fun operation(id: Int): String = runCatching { content.operation(id) }.getOrNull()?.let { pick(it.title, it.titleEn) } ?: "Operasi $id"

    fun operationText(id: Int): String = runCatching { content.operation(id) }.getOrNull()?.let { pick(it.text, it.textEn) } ?: ""

    fun mission(id: Int): String = runCatching { content.mission(id) }.getOrNull()?.let { pick(it.title, it.titleEn) } ?: "Misi $id"

    fun missionText(id: Int): String = runCatching { content.mission(id) }.getOrNull()?.let { pick(it.text, it.textEn) } ?: ""

    fun deckCard(deck: String, id: Int): String {
        val d = Deck.entries.firstOrNull { it.name == deck } ?: return "#$id"
        return runCatching { content.card(d, id) }.getOrNull()?.let { pick(it.title, it.titleEn) } ?: "#$id"
    }

    fun deckCardText(deck: Deck, cardId: Int): String =
        runCatching { content.card(deck, cardId) }.getOrNull()?.let { pick(it.text, it.textEn) } ?: ""

    fun district(d: District): String = when (d) {
        District.PELABUHAN -> pick("Pelabuhan", "Harbour")
        District.ALFAMA -> "Alfama"
        District.BAIXA -> "Baixa"
        District.CHIADO -> "Chiado"
        District.AVENIDA -> "Avenida"
        District.ESTORIL -> "Estoril"
    }

    fun type(t: EvidenceType): String = when (t) {
        EvidenceType.SUSPECT -> pick("Tersangka", "Suspect")
        EvidenceType.LOCATION -> pick("Lokasi", "Location")
        EvidenceType.ITEM -> pick("Barang", "Item")
    }

    fun winType(t: WinType): String = when (t) {
        WinType.DETECTIVE -> pick("Kemenangan Detektif", "Detective Victory")
        WinType.ECONOMIC -> pick("Kemenangan Ekonomi", "Economic Victory")
        WinType.WAR_END -> pick("Skor Mei 1945", "May 1945 Score")
        WinType.DOUBLE_AGENT -> pick("Kemenangan Agen Ganda", "Double Agent Victory")
    }

    fun money(amount: Int): String {
        val nf = NumberFormat.getIntegerInstance(if (lang == Lang.EN) Locale.US else Locale.forLanguageTag("id-ID"))
        return "${nf.format(amount)} esc"
    }
}

enum class LogKind { INFO, MONEY, MYSTERY, WAR, ALERT, SECRET }

data class LogLine(val seq: Int, val text: String, val kind: LogKind)

/** Mengubah GameEvent menjadi baris log untuk pemain [view]. Event yang terlalu remeh dikembalikan null. */
class LogText(private val names: Names) {

    private val en get() = names.lang == Lang.EN
    private fun t(id: String, english: String) = if (en) english else id

    fun lines(view: PlayerView, limit: Int = 200): List<LogLine> {
        val players = playerNames(view)
        return view.log.takeLast(limit * 3).mapNotNull { format(it, players) }.takeLast(limit)
    }

    fun playerNames(view: PlayerView): Map<Int, String> =
        (listOf(view.me.id.value to view.me.name) + view.opponents.map { it.id.value to it.name }).toMap()

    fun format(e: GameEvent, players: Map<Int, String>): LogLine? {
        val p = e.payload
        fun who(key: String = "player") = p[key]?.toIntOrNull()?.let { players[it] } ?: p[key] ?: "?"
        fun party(key: String) = when (p[key]) {
            "bank" -> t("Bank", "the Bank")
            "vault" -> t("Brankas Kasino", "the Casino Vault")
            "escrow" -> t("titipan", "escrow")
            else -> who(key)
        }
        fun tile(key: String = "tile") = p[key]?.toIntOrNull()?.let(names::tile) ?: "?"
        fun amount(key: String = "amount") = p[key]?.toIntOrNull()?.let(names::money) ?: "?"
        fun cards(key: String) = p[key].orEmpty().split(",").filter { it.isNotBlank() }.joinToString(", ") { names.card(it) }
        fun line(text: String, kind: LogKind = LogKind.INFO) = LogLine(e.seq, text, kind)
        return when (e.type) {
            E.GAME_STARTED -> line(t("Permainan dimulai. Map Merah telah dicuri.", "The game begins. The Red Folder has been stolen."), LogKind.WAR)
            E.FIRST_PLAYER -> line(t("${who()} jalan lebih dulu.", "${who()} goes first."))
            E.ROUND_STARTED -> line(t("— Putaran ${p["round"]} —", "— Round ${p["round"]} —"))
            E.TURN_STARTED -> line(t("Giliran ${who()}.", "${who()}'s turn."))
            E.DICE_ROLLED -> if (p["purpose"] == "casino") null else line(t("${who()} melempar ${p["d1"]} + ${p["d2"]}.", "${who()} rolls ${p["d1"]} + ${p["d2"]}."))
            E.LANDED -> line(t("${who()} tiba di ${tile()}.", "${who()} lands on ${tile()}."))
            E.PROPERTY_BOUGHT -> line(t("${who()} membeli ${tile()} seharga ${amount("price")}.", "${who()} buys ${tile()} for ${amount("price")}."), LogKind.MONEY)
            E.AUCTION_STARTED -> line(t("Lelang ${tile()} dibuka.", "Auction for ${tile()} opens."), LogKind.MONEY)
            E.AUCTION_BID -> line(t("${who()} menawar ${amount()}.", "${who()} bids ${amount()}."), LogKind.MONEY)
            E.AUCTION_WON -> line(t("${who()} memenangkan ${tile()} seharga ${amount()}.", "${who()} wins ${tile()} for ${amount()}."), LogKind.MONEY)
            E.AUCTION_UNSOLD -> line(t("${tile()} tidak laku.", "${tile()} goes unsold."))
            E.MONEY -> moneyLine(p, ::party, ::amount)?.let { line(it, LogKind.MONEY) }
            E.RENT_PAID_INFO -> line(t("${who()} membayar sewa ${tile()} dengan informasi kepada ${who("owner")}.", "${who()} pays rent at ${tile()} with information to ${who("owner")}."), LogKind.MYSTERY)
            E.RENT_CANCELLED -> line(t("${who()} menyamar — sewa batal!", "${who()} slips away in disguise — rent cancelled!"), LogKind.ALERT)
            E.CARD_DRAWN -> p["card"]?.toIntOrNull()?.let { id ->
                line(t("${who()} mengambil ${deckName(p["deck"])}: ${names.deckCard(p["deck"].orEmpty(), id)}.", "${who()} draws ${deckName(p["deck"])}: ${names.deckCard(p["deck"].orEmpty(), id)}."))
            }
            E.FRONT_REVEALED -> line(
                t("FRONT ${p["month"]}: ${p["card"]?.toIntOrNull()?.let(names::front) ?: ""}", "FRONT ${p["month"]}: ${p["card"]?.toIntOrNull()?.let(names::front) ?: ""}"),
                LogKind.WAR,
            )
            E.WAR_ENDED -> line(t("Perang di Eropa usai!", "The war in Europe is over!"), LogKind.WAR)
            E.JAIL_ENTERED -> line(t("${who()} dijebloskan ke Penjara PIDE.", "${who()} is thrown into PIDE Prison."), LogKind.ALERT)
            E.JAIL_LEFT -> line(t("${who()} keluar dari Penjara.", "${who()} leaves Prison."))
            E.ESCAPE_TRAVEL -> line(t("${who()} menyelinap lewat jalur ke ${tile("to")}.", "${who()} slips through the route to ${tile("to")}."))
            E.SUGGESTION_MADE -> line(
                t("${who()} menduga: ${cards("suspects")} dengan ${names.card(p["item"].orEmpty())} di ${names.card(p["location"].orEmpty())}.",
                    "${who()} suggests: ${cards("suspects")} with the ${names.card(p["item"].orEmpty())} in ${names.card(p["location"].orEmpty())}."),
                LogKind.MYSTERY,
            )
            E.SUGGESTION_PASS -> line(t("${who()} tidak punya kartu yang disebut.", "${who()} has none of the named cards."), LogKind.MYSTERY)
            E.SUGGESTION_SHOWN -> line(t("${who()} menunjukkan sebuah kartu kepada ${who("asker")}.", "${who()} shows a card to ${who("asker")}."), LogKind.MYSTERY)
            E.SUGGESTION_SILENT -> line(t("${who()} memilih bungkam.", "${who()} stays silent."), LogKind.MYSTERY)
            E.SUGGESTION_STRONG -> line(t("Tak seorang pun bisa membantah ${who()}! (+100)", "No one can refute ${who()}! (+100)"), LogKind.MYSTERY)
            E.CARD_SEEN -> line(t("RAHASIA: ${who("holder")} memegang ${names.card(p["card"].orEmpty())}.", "SECRET: ${who("holder")} holds ${names.card(p["card"].orEmpty())}."), LogKind.SECRET)
            E.DECODE_STARTED -> line(t("${who()} mencoba memecahkan sandi milik ${who("operator")}.", "${who()} tries to crack ${who("operator")}'s cipher."), LogKind.MYSTERY)
            E.DECODE_SUCCESS -> line(t("Sandi terpecahkan oleh ${who()}!", "${who()} cracks the cipher!"), LogKind.MYSTERY)
            E.DECODE_FAILED -> line(t("Dekode ${who()} gagal.", "${who()}'s decoding fails."))
            E.CASINO_RESULT -> when (p["result"]) {
                "lose" -> line(t("${who()} kalah ${amount("lost")} di Kasino.", "${who()} loses ${amount("lost")} at the Casino."), LogKind.MONEY)
                else -> line(t("${who()} menang ${amount("won")} di Kasino!", "${who()} wins ${amount("won")} at the Casino!"), LogKind.MONEY)
            }
            E.BLACK_MARKET_DEAL -> line(t("${who()} membeli informasi dari ${who("seller")} di Pasar Gelap.", "${who()} buys information from ${who("seller")} on the Black Market."), LogKind.MYSTERY)
            E.ACCUSATION_CORRECT -> line(t("TERPECAHKAN! ${who()} menuduh dengan benar.", "SOLVED! ${who()} accuses correctly."), LogKind.ALERT)
            E.BURNED -> line(t("TERBAKAR! Tuduhan ${who()} salah.", "BURNED! ${who()}'s accusation is wrong."), LogKind.ALERT)
            E.ACCUSATION_WRONG_FAMILY -> line(t("Tuduhan ${who()} salah (denda ${amount("penalty")}).", "${who()}'s accusation is wrong (fine ${amount("penalty")})."), LogKind.ALERT)
            E.BUILT -> line(t("${who()} membangun di ${tile()} (level ${p["level"]}).", "${who()} builds on ${tile()} (level ${p["level"]})."), LogKind.MONEY)
            E.MORTGAGED -> line(t("${who()} menggadaikan ${tile()}.", "${who()} mortgages ${tile()}."), LogKind.MONEY)
            E.UNMORTGAGED -> line(t("${who()} menebus ${tile()}.", "${who()} unmortgages ${tile()}."), LogKind.MONEY)
            E.TRADE_PROPOSED -> line(t("${who("from")} menawarkan perdagangan kepada ${who("to")}.", "${who("from")} offers a trade to ${who("to")}."), LogKind.MONEY)
            E.TRADE_ACCEPTED -> line(t("Perdagangan ${who("from")} ↔ ${who("to")} disepakati.", "Trade ${who("from")} ↔ ${who("to")} agreed."), LogKind.MONEY)
            E.TRADE_REJECTED -> line(t("${who("to")} menolak tawaran ${who("from")}.", "${who("to")} rejects ${who("from")}'s offer."))
            E.ABILITY_USED -> line(t("${who()} memakai kemampuan faksi.", "${who()} uses their faction ability."), LogKind.MYSTERY)
            E.BANKRUPT -> line(t("${who()} BANGKRUT!", "${who()} is BANKRUPT!"), LogKind.ALERT)
            E.GAME_OVER -> line(
                t("PERMAINAN SELESAI — ${p["type"]?.let { runCatching { names.winType(WinType.valueOf(it)) }.getOrNull() } ?: ""}",
                    "GAME OVER — ${p["type"]?.let { runCatching { names.winType(WinType.valueOf(it)) }.getOrNull() } ?: ""}"),
                LogKind.ALERT,
            )
            E.SUSPICION_ARREST -> line(t("Polisi rahasia menangkap ${who()}!", "The secret police arrest ${who()}!"), LogKind.ALERT)
            E.PATROL_CAUGHT -> line(t("${who()} berpapasan dengan Patroli PIDE.", "${who()} runs into the PIDE Patrol."), LogKind.ALERT)
            E.CONTACT_MET -> line(t("${who()} bertemu Kontak Netral (+Intel).", "${who()} meets the Neutral Contact (+Intel)."), LogKind.MYSTERY)
            E.TACTIC_PLAYED -> line(t("${who()} memainkan ${p["card"]?.toIntOrNull()?.let(names::tactic) ?: ""}${p["target"]?.let { " → " + who("target") } ?: ""}.",
                "${who()} plays ${p["card"]?.toIntOrNull()?.let(names::tactic) ?: ""}${p["target"]?.let { " → " + who("target") } ?: ""}."), LogKind.ALERT)
            E.TILE_SEALED -> line(t("Properti disegel: ${p["tiles"].orEmpty().split(",").mapNotNull { it.toIntOrNull() }.joinToString { names.tile(it) }}.",
                "Sealed: ${p["tiles"].orEmpty().split(",").mapNotNull { it.toIntOrNull() }.joinToString { names.tile(it) }}."), LogKind.ALERT)
            E.OPERATION_CLAIMED -> line(t("${who()} menuntaskan Operasi ${p["operation"]?.toIntOrNull()?.let(names::operation) ?: ""}!", "${who()} completes Operation ${p["operation"]?.toIntOrNull()?.let(names::operation) ?: ""}!"), LogKind.WAR)
            E.OPERATION_OPENED -> line(t("Operasi baru: ${p["operation"]?.toIntOrNull()?.let(names::operation) ?: ""}.", "New operation: ${p["operation"]?.toIntOrNull()?.let(names::operation) ?: ""}."))
            E.ALLIED_AID -> line(t("Bantuan Sekutu untuk ${who()}.", "Allied aid for ${who()}."), LogKind.MONEY)
            E.EVIDENCE_RESHUFFLED -> line(t("MAP MERAH BERPINDAH — kartu ${p["category"]?.let { runCatching { names.type(EvidenceType.valueOf(it)) }.getOrNull() } ?: ""} dibagi ulang!",
                "THE RED FOLDER MOVES — ${p["category"]?.let { runCatching { names.type(EvidenceType.valueOf(it)) }.getOrNull() } ?: ""} cards redealt!"), LogKind.WAR)
            E.ECONOMIC_DOMINANCE -> line(t("${who()} menguasai perekonomian kota!", "${who()} dominates the city's economy!"), LogKind.ALERT)
            E.MISSION_ASSIGNED -> line(t("Misi Rahasiamu: ${p["mission"]?.toIntOrNull()?.let(names::mission) ?: ""}.", "Your Secret Mission: ${p["mission"]?.toIntOrNull()?.let(names::mission) ?: ""}."), LogKind.SECRET)
            E.MISSION_COMPLETED -> line(t("${who()} menuntaskan misi ${p["mission"]?.toIntOrNull()?.let(names::mission) ?: ""} (+${p["points"]}).", "${who()} completed mission ${p["mission"]?.toIntOrNull()?.let(names::mission) ?: ""} (+${p["points"]})."), LogKind.WAR)
            E.DOUBLE_AGENT_ROLE -> line(t("Kamu adalah AGEN GANDA.", "You are the DOUBLE AGENT."), LogKind.SECRET)
            E.DOUBLE_AGENT_REVEALED -> line(t("${who()} ternyata Agen Ganda!", "${who()} was the Double Agent!"), LogKind.ALERT)
            else -> null
        }
    }

    private fun moneyLine(p: Map<String, String>, party: (String) -> String, amount: (String) -> String): String? {
        val reason = p["reason"] ?: return null
        val from = p["from"]
        val a = amount("amount")
        if (reason == "decode" && (from == "escrow" || p["to"] == "escrow")) return null
        if (from == "bank") {
            val why = when (reason) {
                "salary", "start" -> t("gaji", "salary")
                "casino" -> return null
                "sell_building" -> t("jual bangunan", "building sale")
                "mortgage" -> return null
                else -> reasonLabel(reason)
            }
            return t("${party("to")} menerima $a ($why).", "${party("to")} receives $a ($why).")
        }
        return when (reason) {
            "rent" -> t("${party("from")} membayar sewa $a kepada ${party("to")}.", "${party("from")} pays $a rent to ${party("to")}.")
            "purchase", "auction", "build", "unmortgage", "casino" -> null
            else -> t("${party("from")} membayar $a kepada ${party("to")} (${reasonLabel(reason)}).",
                "${party("from")} pays $a to ${party("to")} (${reasonLabel(reason)}).")
        }
    }

    private fun reasonLabel(r: String): String = when (r) {
        "card" -> t("kartu", "card")
        "tactic" -> t("taktik", "tactic")
        "front" -> "Front"
        "operation" -> t("operasi", "operation")
        "trade" -> t("dagang", "trade")
        "suggestion" -> t("dugaan", "suggestion")
        "strong_suggestion" -> t("dugaan kuat", "strong suggestion")
        "jail_fine" -> t("denda penjara", "jail fine")
        "decode" -> t("dekode", "decoding")
        "contact" -> t("kontak", "contact")
        "bribe" -> t("suap", "bribe")
        "black_market" -> t("pasar gelap", "black market")
        "allied_aid" -> t("bantuan Sekutu", "Allied aid")
        "accusation", "wrong_accusation" -> t("tuduhan", "accusation")
        "war_tax" -> t("pajak perang", "war tax")
        "silence" -> t("bungkam", "silence")
        "resistance" -> t("Perlawanan", "Resistance")
        "repairs" -> t("perbaikan", "repairs")
        "patrol" -> t("patroli", "patrol")
        "fine" -> t("denda", "fine")
        "escape_route" -> t("jalur pelarian", "escape route")
        "doubles" -> t("dadu kembar", "doubles")
        "debt" -> t("utang", "debt")
        "burn_penalty" -> t("denda terbakar", "burn penalty")
        "blackmail" -> t("pemerasan", "blackmail")
        "bankruptcy" -> t("kebangkrutan", "bankruptcy")
        "audit" -> t("audit", "audit")
        "forced" -> t("paksa", "forced")
        else -> r.replace('_', ' ')
    }

    private fun deckName(deck: String?) = when (deck) {
        "CIPHER" -> t("Kartu Sandi", "a Cipher card")
        "TELEGRAM" -> t("Telegram", "a Telegram")
        else -> t("kartu", "a card")
    }
}
