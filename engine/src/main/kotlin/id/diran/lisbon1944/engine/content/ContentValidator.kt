package id.diran.lisbon1944.engine.content

import id.diran.lisbon1944.engine.model.Balance
import id.diran.lisbon1944.engine.model.CardEffect
import id.diran.lisbon1944.engine.model.DeckCard
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FactionAbility
import id.diran.lisbon1944.engine.model.FrontEffect
import id.diran.lisbon1944.engine.model.MissionCondition
import id.diran.lisbon1944.engine.model.OwnableTile
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.SpecialKind
import id.diran.lisbon1944.engine.model.SpecialTile

class ContentValidationException(val errors: List<String>) :
    RuntimeException("Konten tidak valid (${errors.size} masalah):\n" + errors.joinToString("\n") { " - $it" })

/** Aturan struktural konten. Semua pelanggaran dikumpulkan sekaligus agar mudah diperbaiki. */
object ContentValidator {

    const val BOARD_SIZE = 40
    const val PROPERTIES_PER_DISTRICT = 3
    const val PROPERTY_COUNT = 18
    const val ESCAPE_ROUTE_COUNT = 4
    const val SPECIAL_NON_CORNER_COUNT = 14
    const val EVIDENCE_PER_TYPE = 6
    const val FRONT_COUNT = 12
    const val DECK_SIZE = 24
    const val FACTION_COUNT = 8
    const val MIN_MISSIONS = 6

    /** Sudut papan (indeks → jenis). */
    val CORNERS: Map<Int, SpecialKind> = mapOf(
        0 to SpecialKind.START,
        10 to SpecialKind.JAIL,
        20 to SpecialKind.CASINO,
        30 to SpecialKind.ARREST,
    )

    private val MONTH = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")
    private val HEX_COLOR = Regex("""^#[0-9A-Fa-f]{6}$""")

    fun validateOrThrow(content: GameContent) {
        val errors = validate(content)
        if (errors.isNotEmpty()) throw ContentValidationException(errors)
    }

    fun validate(content: GameContent): List<String> = buildList {
        addAll(validateBoard(content))
        addAll(validateFactions(content))
        addAll(validateEvidence(content))
        addAll(validateFronts(content))
        addAll(validateDeck("cipher.json", content.cipher, content))
        addAll(validateDeck("telegram.json", content.telegram, content))
        addAll(validateBalance(content.balance))
        addAll(validateMissions(content))
    }

    // ---------------------------------------------------------------------------------

    fun validateBoard(content: GameContent): List<String> = buildList {
        val board = content.board
        if (board.size != BOARD_SIZE) add("board.json: papan harus $BOARD_SIZE petak, ditemukan ${board.size}")
        board.forEachIndexed { pos, t ->
            if (t.index != pos) add("board.json: petak ke-$pos punya index ${t.index} (harus berurutan 0..${BOARD_SIZE - 1})")
            if (t.name.isBlank() || t.nameEn.isBlank()) add("board.json: petak ${t.index} tanpa nama")
        }
        CORNERS.forEach { (idx, kind) ->
            val t = board.getOrNull(idx)
            if (t !is SpecialTile || t.kind != kind) add("board.json: petak $idx harus sudut $kind")
        }
        val props = board.filterIsInstance<PropertyTile>()
        val routes = board.filterIsInstance<EscapeRouteTile>()
        val specials = board.filterIsInstance<SpecialTile>().filter { it.index !in CORNERS.keys }
        if (props.size != PROPERTY_COUNT) add("board.json: harus $PROPERTY_COUNT bangunan, ditemukan ${props.size}")
        if (routes.size != ESCAPE_ROUTE_COUNT) add("board.json: harus $ESCAPE_ROUTE_COUNT Jalur Pelarian, ditemukan ${routes.size}")
        if (specials.size != SPECIAL_NON_CORNER_COUNT) {
            add("board.json: harus $SPECIAL_NON_CORNER_COUNT petak khusus non-sudut, ditemukan ${specials.size}")
        }
        District.entries.forEach { d ->
            val n = props.count { it.district == d }
            if (n != PROPERTIES_PER_DISTRICT) add("board.json: distrik $d harus punya $PROPERTIES_PER_DISTRICT properti, ditemukan $n")
        }
        props.forEach { p ->
            if (p.price <= 0 || p.baseRent <= 0) add("board.json: properti ${p.index} harga/sewa harus positif")
            if (p.hqRent <= p.baseRent) add("board.json: properti ${p.index} sewa Markas harus > sewa dasar")
        }
        board.filterIsInstance<OwnableTile>().forEach { o ->
            if (o.price <= 0) add("board.json: petak ${o.index} harga harus positif")
        }
        board.filterIsInstance<SpecialTile>().forEach { s ->
            val needsAmount = s.kind == SpecialKind.WAR_TAX || s.kind == SpecialKind.BRIBE || s.kind == SpecialKind.BLACK_MARKET
            if (needsAmount && (s.amount == null || s.amount <= 0)) add("board.json: petak ${s.index} (${s.kind}) wajib punya amount positif")
            if (s.kind in CORNERS.values && CORNERS[s.index] != s.kind) add("board.json: ${s.kind} hanya boleh di sudut")
        }
        val required = listOf(
            SpecialKind.EMBASSY to 1, SpecialKind.INTERROGATION to null, SpecialKind.RADIO to null,
            SpecialKind.TELEGRAPH to null, SpecialKind.CIPHER to null, SpecialKind.TELEGRAM to null,
            SpecialKind.BLACK_MARKET to null, SpecialKind.WAR_TAX to null, SpecialKind.BRIBE to null,
        )
        required.forEach { (kind, exact) ->
            val n = board.count { it is SpecialTile && it.kind == kind }
            if (n == 0) add("board.json: tidak ada petak $kind")
            if (exact != null && n != exact) add("board.json: harus tepat $exact petak $kind, ditemukan $n")
        }
    }

    fun validateFactions(content: GameContent): List<String> = buildList {
        val f = content.factions
        if (f.size != FACTION_COUNT) add("factions.json: harus $FACTION_COUNT faksi, ditemukan ${f.size}")
        val ids = f.map { it.id }
        if (ids.toSet().size != ids.size) add("factions.json: id faksi duplikat")
        val missing = Faction.entries - ids.toSet()
        if (missing.isNotEmpty()) add("factions.json: faksi hilang: ${missing.joinToString()}")
        val abilities = f.map { it.ability }
        if (abilities.toSet().size != abilities.size) add("factions.json: kemampuan faksi duplikat")
        val missingAbilities = FactionAbility.entries - abilities.toSet()
        if (missingAbilities.isNotEmpty()) add("factions.json: kemampuan hilang: ${missingAbilities.joinToString()}")
        f.forEach {
            if (!HEX_COLOR.matches(it.accentColor)) add("factions.json: ${it.id} accentColor harus #RRGGBB")
            if (it.abilityText.isBlank() || it.abilityTextEn.isBlank()) add("factions.json: ${it.id} deskripsi kemampuan kosong")
        }
    }

    fun validateEvidence(content: GameContent): List<String> = buildList {
        val cards = content.evidence
        val ids = cards.map { it.id }
        if (ids.toSet().size != ids.size) add("evidence.json: id kartu duplikat")
        EvidenceType.entries.forEach { type ->
            val n = cards.count { it.type == type }
            if (n != EVIDENCE_PER_TYPE) add("evidence.json: harus $EVIDENCE_PER_TYPE kartu $type, ditemukan $n")
        }
        val locations = cards.filter { it.type == EvidenceType.LOCATION }
        locations.filter { it.district == null }.forEach { add("evidence.json: kartu Lokasi ${it.id} tanpa distrik") }
        val covered = locations.mapNotNull { it.district }
        if (covered.toSet().size != covered.size) add("evidence.json: dua kartu Lokasi menunjuk distrik yang sama")
        val uncovered = District.entries - covered.toSet()
        if (uncovered.isNotEmpty()) add("evidence.json: distrik tanpa kartu Lokasi: ${uncovered.joinToString()}")
        cards.filter { it.type != EvidenceType.LOCATION && it.district != null }
            .forEach { add("evidence.json: kartu ${it.id} bukan Lokasi tetapi punya distrik") }
        cards.filter { it.type == EvidenceType.SUSPECT && it.role.isNullOrBlank() }
            .forEach { add("evidence.json: tersangka ${it.id} tanpa peran") }
    }

    fun validateFronts(content: GameContent): List<String> = buildList {
        val fronts = content.fronts
        if (fronts.size != FRONT_COUNT) add("front.json: harus $FRONT_COUNT kartu Front, ditemukan ${fronts.size}")
        fronts.forEachIndexed { i, f ->
            if (f.id.value != i + 1) add("front.json: Front ke-${i + 1} ber-id ${f.id.value} (harus urut 1..$FRONT_COUNT, tidak dikocok)")
            if (!MONTH.matches(f.month)) add("front.json: Front ${f.id.value} bulan '${f.month}' harus YYYY-MM")
            if (f.effects.isEmpty()) add("front.json: Front ${f.id.value} tanpa efek")
            f.effects.forEach { e ->
                when (e) {
                    is FrontEffect.DistrictRentMultiplier -> {
                        if (e.districts.isEmpty()) add("front.json: Front ${f.id.value} pengali sewa tanpa distrik")
                        if (e.numerator <= 0 || e.denominator <= 0) add("front.json: Front ${f.id.value} pengali harus positif")
                    }
                    is FrontEffect.EscapeRouteRentMultiplier ->
                        if (e.numerator <= 0 || e.denominator <= 0) add("front.json: Front ${f.id.value} pengali harus positif")
                    is FrontEffect.BuildCostMultiplier ->
                        if (e.numerator <= 0 || e.denominator <= 0) add("front.json: Front ${f.id.value} pengali harus positif")
                    is FrontEffect.CloseTile ->
                        if (content.board.getOrNull(e.tile) !is OwnableTile) add("front.json: Front ${f.id.value} menutup petak ${e.tile} yang bukan properti/jalur")
                    is FrontEffect.OnRevealPayIfOwnsInDistrict ->
                        if (e.amount <= 0) add("front.json: Front ${f.id.value} jumlah harus positif")
                    is FrontEffect.OnRevealFactionBonus ->
                        if (e.amount <= 0) add("front.json: Front ${f.id.value} jumlah harus positif")
                    is FrontEffect.FreeSuggestionEachPlayer ->
                        if (e.count <= 0) add("front.json: Front ${f.id.value} jumlah Dugaan gratis harus positif")
                    is FrontEffect.OnRevealJailIfMoneyAbove ->
                        if (e.threshold < 0) add("front.json: Front ${f.id.value} ambang tidak boleh negatif")
                    FrontEffect.NoStartSalary, FrontEffect.OnRevealSecretConference,
                    FrontEffect.AccuseFromAnywhere, FrontEffect.EndGame, is FrontEffect.ReshuffleEvidence -> Unit
                }
            }
        }
        val months = fronts.map { it.month }
        if (months != months.sorted() || months.toSet().size != months.size) add("front.json: bulan Front harus naik kronologis tanpa duplikat")
        val endGameFronts = fronts.filter { f -> f.effects.any { it is FrontEffect.EndGame } }.map { it.id.value }
        if (endGameFronts != listOf(FRONT_COUNT)) add("front.json: hanya Front #$FRONT_COUNT yang boleh mengakhiri permainan, ditemukan $endGameFronts")
    }

    fun validateDeck(file: String, cards: List<DeckCard>, content: GameContent): List<String> = buildList {
        if (cards.size != DECK_SIZE) add("$file: harus $DECK_SIZE kartu, ditemukan ${cards.size}")
        val ids = cards.map { it.id }
        if (ids.toSet().size != ids.size) add("$file: id kartu duplikat")
        if (ids.sorted() != (1..cards.size).toList()) add("$file: id kartu harus 1..${cards.size}")
        val kinds = content.board.filterIsInstance<SpecialTile>().map { it.kind }.toSet()
        cards.forEach { c ->
            if (c.effects.isEmpty()) add("$file: kartu ${c.id} tanpa efek")
            val hasPass = c.effects.any { it is CardEffect.JailPass }
            if (c.keep != hasPass) add("$file: kartu ${c.id} keep=${c.keep} harus sama dengan memiliki efek jailPass")
            c.effects.forEach { e ->
                when (e) {
                    is CardEffect.MoveToTile -> {
                        if (e.tile !in 0 until BOARD_SIZE) add("$file: kartu ${c.id} tujuan ${e.tile} di luar papan")
                        if (e.rentMultiplier < 1) add("$file: kartu ${c.id} rentMultiplier minimal 1")
                        if (e.rentMultiplier > 1 && content.board.getOrNull(e.tile) !is OwnableTile) {
                            add("$file: kartu ${c.id} rentMultiplier hanya untuk tujuan properti/jalur")
                        }
                    }
                    is CardEffect.MoveToNearest -> if (e.kind !in kinds) add("$file: kartu ${c.id} menuju ${e.kind} yang tidak ada di papan")
                    is CardEffect.MoveBack -> if (e.steps !in 1 until BOARD_SIZE) add("$file: kartu ${c.id} langkah mundur tidak valid")
                    is CardEffect.GainFromBank -> if (e.amount <= 0) add("$file: kartu ${c.id} jumlah harus positif")
                    is CardEffect.PayToVault -> if (e.amount <= 0) add("$file: kartu ${c.id} jumlah harus positif")
                    is CardEffect.PayToBank -> if (e.amount <= 0) add("$file: kartu ${c.id} jumlah harus positif")
                    is CardEffect.PayPerBuilding -> if (e.perSafeHouse < 0 || e.perHq < 0) add("$file: kartu ${c.id} biaya negatif")
                    is CardEffect.PayPercentOfCashToVault -> if (e.percent !in 1..100) add("$file: kartu ${c.id} persen harus 1..100")
                    is CardEffect.PayEachOtherPlayer -> if (e.amount <= 0) add("$file: kartu ${c.id} jumlah harus positif")
                    is CardEffect.CollectFromEachOtherPlayer -> if (e.amount <= 0) add("$file: kartu ${c.id} jumlah harus positif")
                    is CardEffect.ExtraSuggestion -> if (e.turns <= 0) add("$file: kartu ${c.id} jumlah giliran harus positif")
                    is CardEffect.GainIntel -> if (e.amount <= 0) add("$file: kartu ${c.id} jumlah harus positif")
                    is CardEffect.LoseIntel -> if (e.amount <= 0) add("$file: kartu ${c.id} jumlah harus positif")
                    is CardEffect.AddSuspicion -> if (e.amount <= 0) add("$file: kartu ${c.id} jumlah harus positif")
                    is CardEffect.ReduceSuspicion -> if (e.amount <= 0) add("$file: kartu ${c.id} jumlah harus positif")
                    is CardEffect.PeekRandomFromNeighbor, CardEffect.PeekRandomFromRichest,
                    is CardEffect.ShowOwnCardToNeighbor, is CardEffect.ForceShowByType,
                    CardEffect.GoToJail, CardEffect.JailPass, CardEffect.SkipNextSuggestion -> Unit
                }
            }
        }
    }

    fun validateMissions(content: GameContent): List<String> = buildList {
        val m = content.missions
        if (m.size < MIN_MISSIONS) add("missions.json: minimal $MIN_MISSIONS misi (satu per pemain), ditemukan ${m.size}")
        if (m.map { it.id }.toSet().size != m.size) add("missions.json: id misi duplikat")
        m.forEach { mission ->
            if (mission.points <= 0) add("missions.json: misi ${mission.id} poin harus positif")
            val bad = when (val c = mission.condition) {
                is MissionCondition.OwnDistrict -> false
                is MissionCondition.EscapeRoutes -> c.count !in 1..ESCAPE_ROUTE_COUNT
                is MissionCondition.CashAtLeast -> c.amount <= 0
                is MissionCondition.BuildingPoints -> c.count <= 0
                is MissionCondition.KnownCards -> c.count !in 1..content.evidence.size
                is MissionCondition.SuspicionAtMost -> c.max < 0
                is MissionCondition.PropertyCount -> c.count !in 1..(PROPERTY_COUNT + ESCAPE_ROUTE_COUNT)
                is MissionCondition.IntelAtLeast -> c.count <= 0
                is MissionCondition.CorrectGuesses -> c.count !in 1..3
            }
            if (bad) add("missions.json: misi ${mission.id} syarat tidak masuk akal")
        }
    }

    fun validateBalance(b: Balance): List<String> = buildList {
        fun positive(name: String, v: Int) { if (v <= 0) add("balance.json: $name harus positif, ditemukan $v") }
        fun nonNegative(name: String, v: Int) { if (v < 0) add("balance.json: $name tidak boleh negatif, ditemukan $v") }
        fun fraction(name: String, v: Double) { if (v < 0.0 || v > 1.0) add("balance.json: $name harus 0..1, ditemukan $v") }

        positive("startingMoney", b.startingMoney)
        nonNegative("startSalary", b.startSalary)
        nonNegative("ossSalary", b.ossSalary)
        val missingCost = District.entries - b.buildCost.keys
        if (missingCost.isNotEmpty()) add("balance.json: buildCost tidak lengkap: ${missingCost.joinToString()}")
        b.buildCost.forEach { (d, c) -> positive("buildCost.$d", c) }
        if (b.levelMultipliers.size != 4) add("balance.json: levelMultipliers harus 4 nilai (level 1–4)")
        if (b.levelMultipliers.any { it <= 0 } || b.levelMultipliers != b.levelMultipliers.sorted()) {
            add("balance.json: levelMultipliers harus positif dan naik")
        }
        positive("monopolyBaseMultiplier", b.monopolyBaseMultiplier)
        if (b.escapeRouteRent.size != ESCAPE_ROUTE_COUNT) add("balance.json: escapeRouteRent harus $ESCAPE_ROUTE_COUNT nilai")
        if (b.escapeRouteRent.any { it <= 0 } || b.escapeRouteRent != b.escapeRouteRent.sorted()) {
            add("balance.json: escapeRouteRent harus positif dan naik")
        }
        nonNegative("escapeRouteTravelCost", b.escapeRouteTravelCost)
        nonNegative("infoPaymentMaxRent", b.infoPaymentMaxRent)
        nonNegative("infoPaymentQuota", b.infoPaymentQuota)
        nonNegative("suggestionNoCardReward", b.suggestionNoCardReward)
        positive("blackMarketPrice", b.blackMarketPrice)
        nonNegative("decodeExtraRollCost", b.decodeExtraRollCost)
        nonNegative("decodeMaxExtraRolls", b.decodeMaxExtraRolls)
        nonNegative("decodeEmptyHandReward", b.decodeEmptyHandReward)
        positive("casinoMinBet", b.casinoMinBet)
        positive("casinoBetStep", b.casinoBetStep)
        if (b.casinoMaxBet < b.casinoMinBet) add("balance.json: casinoMaxBet harus ≥ casinoMinBet")
        if (b.casinoBetStep > 0 && (b.casinoMaxBet - b.casinoMinBet) % b.casinoBetStep != 0) {
            add("balance.json: rentang taruhan kasino harus habis dibagi casinoBetStep")
        }
        positive("casinoPayout.seven", b.casinoPayout.seven)
        positive("casinoPayout.eleven", b.casinoPayout.eleven)
        positive("casinoPayout.doubles", b.casinoPayout.doubles)
        nonNegative("jailFine", b.jailFine)
        positive("maxJailTurns", b.maxJailTurns)
        nonNegative("embassyPassCost", b.embassyPassCost)
        nonNegative("aprilAccuseCost", b.aprilAccuseCost)
        fraction("mortgageInterest", b.mortgageInterest)
        fraction("portugalDiscount", b.portugalDiscount)
        positive("auctionStart", b.auctionStart)
        positive("auctionStep", b.auctionStep)
        nonNegative("warTax", b.warTax)
        nonNegative("bribe", b.bribe)
        fraction("burnPenaltyFraction", b.burnPenaltyFraction)
        positive("safeHouseStock", b.safeHouseStock)
        positive("hqStock", b.hqStock)
        positive("score.cashPer", b.score.cashPer)
        positive("score.propertyPer", b.score.propertyPer)
        nonNegative("score.safeHouse", b.score.safeHouse)
        nonNegative("score.hq", b.score.hq)
        nonNegative("score.correctGuess", b.score.correctGuess)
        fraction("score.burnedFactor", b.score.burnedFactor)
        nonNegative("familyMode.wrongAccusePenalty", b.familyMode.wrongAccusePenalty)
        positive("quickMode.startProperties", b.quickMode.startProperties)
        if (b.quickMode.startWarStep !in 1..FRONT_COUNT) add("balance.json: quickMode.startWarStep harus 1..$FRONT_COUNT")
        if (b.warClock.passesPerStep < 1.0) add("balance.json: warClock.passesPerStep minimal 1")
        if (b.accuseUnlockStep !in 1..FRONT_COUNT) add("balance.json: accuseUnlockStep harus 1..$FRONT_COUNT")
        nonNegative("intel.start", b.intel.start)
        positive("intel.max", b.intel.max)
        if (b.intel.start > b.intel.max) add("balance.json: intel.start tidak boleh melebihi intel.max")
        nonNegative("intel.perStartPass", b.intel.perStartPass)
        nonNegative("intel.suggestionCost", b.intel.suggestionCost)
        nonNegative("intel.decodeSuccess", b.intel.decodeSuccess)
        nonNegative("intel.defectorBonus", b.intel.defectorBonus)
        nonNegative("intel.accuseCost", b.intel.accuseCost)
        positive("intel.silenceCost", b.intel.silenceCost)
        if (b.intel.accuseCost > b.intel.max) add("balance.json: intel.accuseCost tidak boleh melebihi intel.max")
        positive("suspicion.limit", b.suspicion.limit)
        nonNegative("suspicion.blackMarket", b.suspicion.blackMarket)
        nonNegative("suspicion.casinoWin", b.suspicion.casinoWin)
        nonNegative("suspicion.decayPerStartPass", b.suspicion.decayPerStartPass)
        nonNegative("suspicion.bribeRelief", b.suspicion.bribeRelief)
        positive("smugglerBlackMarketPrice", b.smugglerBlackMarketPrice)
        positive("buildingRentPercent", b.buildingRentPercent)
        if (b.dominancePercent !in 0..100) add("balance.json: dominancePercent harus 0..100")
        if (b.dominanceFromStep !in 1..FRONT_COUNT) add("balance.json: dominanceFromStep harus 1..$FRONT_COUNT")
        if (b.dominanceMinPlayers !in 3..6) add("balance.json: dominanceMinPlayers harus 3..6")
    }
}
