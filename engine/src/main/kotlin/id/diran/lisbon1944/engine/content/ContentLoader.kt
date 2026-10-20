package id.diran.lisbon1944.engine.content

import id.diran.lisbon1944.engine.model.Balance
import id.diran.lisbon1944.engine.model.CardEffect
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.CasinoPayout
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.DeckCard
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.EvidenceCard
import id.diran.lisbon1944.engine.model.FactionDef
import id.diran.lisbon1944.engine.model.FamilyModeRules
import id.diran.lisbon1944.engine.model.FrontCard
import id.diran.lisbon1944.engine.model.FrontEffect
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.IntelRules
import id.diran.lisbon1944.engine.model.MissionCard
import id.diran.lisbon1944.engine.model.MissionCondition
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.QuickModeRules
import id.diran.lisbon1944.engine.model.ScoreRules
import id.diran.lisbon1944.engine.model.SpecialTile
import id.diran.lisbon1944.engine.model.SuspicionRules
import id.diran.lisbon1944.engine.model.WarClockRules
import id.diran.lisbon1944.engine.model.Tile
import kotlinx.serialization.json.Json

/** Teks mentah ketujuh file konten. */
data class ContentSources(
    val board: String,
    val factions: String,
    val evidence: String,
    val front: String,
    val cipher: String,
    val telegram: String,
    val balance: String,
    val missions: String,
)

/**
 * Memuat konten dari JSON lalu memvalidasinya. Gagal keras ([ContentFormatException] untuk format,
 * [ContentValidationException] untuk aturan isi) — game tidak boleh berjalan dengan konten cacat.
 */
object ContentLoader {

    const val BOARD = "board.json"
    const val FACTIONS = "factions.json"
    const val EVIDENCE = "evidence.json"
    const val FRONT = "front.json"
    const val CIPHER = "cipher.json"
    const val TELEGRAM = "telegram.json"
    const val BALANCE = "balance.json"
    const val MISSIONS = "missions.json"

    private val json = Json { isLenient = false }

    /** Konten bawaan yang dibundel sebagai resource Java di modul :engine (juga ikut ke APK). */
    fun loadDefault(): GameContent = load(defaultSources())

    fun defaultSources(): ContentSources = ContentSources(
        board = resource(BOARD),
        factions = resource(FACTIONS),
        evidence = resource(EVIDENCE),
        front = resource(FRONT),
        cipher = resource(CIPHER),
        telegram = resource(TELEGRAM),
        balance = resource(BALANCE),
        missions = resource(MISSIONS),
    )

    /** Muat dan validasi. */
    fun load(sources: ContentSources): GameContent {
        val content = parse(sources)
        ContentValidator.validateOrThrow(content)
        return content
    }

    /** Muat tanpa validasi isi (dipakai test validator). Format tetap diperiksa. */
    fun parse(sources: ContentSources): GameContent = GameContent(
        board = parseBoard(sources.board),
        factions = parseFactions(sources.factions),
        evidence = parseEvidence(sources.evidence),
        fronts = parseFronts(sources.front),
        cipher = parseDeck(sources.cipher, CIPHER, Deck.CIPHER),
        telegram = parseDeck(sources.telegram, TELEGRAM, Deck.TELEGRAM),
        balance = parseBalance(sources.balance),
        missions = parseMissions(sources.missions),
    )

    /** Hanya balance.json — dipakai :sim untuk --balance path. */
    fun parseBalanceOnly(text: String): Balance = parseBalance(text)

    private fun resource(name: String): String {
        val stream = ContentLoader::class.java.getResourceAsStream("/content/$name")
            ?: throw ContentFormatException(name, "$", "resource /content/$name tidak ditemukan di classpath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun root(file: String, text: String): JsonNode {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ContentFormatException(file, "$", "JSON tidak valid: ${e.message}")
        }
        return JsonNode(file, "$", element)
    }

    // ---------------------------------------------------------------------------------

    private fun parseBoard(text: String): List<Tile> =
        root(BOARD, text).child("tiles").array().map { n ->
            val index = n.int("index")
            val name = n.string("name")
            val nameEn = n.string("nameEn")
            when (val type = n.string("type")) {
                "property" -> PropertyTile(
                    index = index, name = name, nameEn = nameEn,
                    district = n.enum("district"),
                    price = n.int("price"),
                    baseRent = n.int("baseRent"),
                    hqRent = n.int("hqRent"),
                )
                "escape" -> EscapeRouteTile(index = index, name = name, nameEn = nameEn, price = n.int("price"))
                "special" -> SpecialTile(
                    index = index, name = name, nameEn = nameEn,
                    kind = n.enum("kind"),
                    amount = n.intOrNull("amount"),
                )
                else -> n.child("type").fail("jenis petak '$type' tidak dikenal (property|escape|special)")
            }
        }

    private fun parseFactions(text: String): List<FactionDef> =
        root(FACTIONS, text).child("factions").array().map { n ->
            FactionDef(
                id = n.enum("id"),
                name = n.string("name"),
                nameEn = n.string("nameEn"),
                organization = n.string("organization"),
                accentColor = n.string("accentColor"),
                badgeIcon = n.string("badgeIcon"),
                ability = n.enum("ability"),
                oneShot = n.boolean("oneShot"),
                abilityText = n.string("abilityText"),
                abilityTextEn = n.string("abilityTextEn"),
            )
        }

    private fun parseEvidence(text: String): List<EvidenceCard> =
        root(EVIDENCE, text).child("cards").array().map { n ->
            EvidenceCard(
                id = CardId(n.string("id")),
                type = n.enum("type"),
                name = n.string("name"),
                nameEn = n.string("nameEn"),
                district = n.childOrNull("district")?.asEnum<District>(),
                role = n.stringOrNull("role"),
                roleEn = n.stringOrNull("roleEn"),
                art = n.stringOrNull("portrait") ?: n.stringOrNull("icon"),
                blurb = n.string("blurb"),
                blurbEn = n.string("blurbEn"),
            )
        }

    private fun parseFronts(text: String): List<FrontCard> =
        root(FRONT, text).child("fronts").array().map { n ->
            FrontCard(
                id = FrontId(n.int("id")),
                month = n.string("month"),
                title = n.string("title"),
                titleEn = n.string("titleEn"),
                text = n.string("text"),
                textEn = n.string("textEn"),
                effects = n.child("effects").array().map(::parseFrontEffect),
            )
        }

    private fun parseFrontEffect(n: JsonNode): FrontEffect = when (val type = n.string("type")) {
        "districtRentMultiplier" -> FrontEffect.DistrictRentMultiplier(
            districts = n.child("districts").array().map { it.asEnum<District>() },
            numerator = n.int("numerator"),
            denominator = n.int("denominator"),
        )
        "escapeRouteRentMultiplier" -> FrontEffect.EscapeRouteRentMultiplier(n.int("numerator"), n.int("denominator"))
        "onRevealPayIfOwnsInDistrict" -> FrontEffect.OnRevealPayIfOwnsInDistrict(n.enum("district"), n.int("amount"))
        "onRevealFactionBonus" -> FrontEffect.OnRevealFactionBonus(n.enum("faction"), n.int("amount"))
        "freeSuggestionEachPlayer" -> FrontEffect.FreeSuggestionEachPlayer(n.int("count"))
        "closeTile" -> FrontEffect.CloseTile(n.int("tile"))
        "onRevealJailIfMoneyAbove" -> FrontEffect.OnRevealJailIfMoneyAbove(n.int("threshold"))
        "buildCostMultiplier" -> FrontEffect.BuildCostMultiplier(n.int("numerator"), n.int("denominator"))
        "noStartSalary" -> FrontEffect.NoStartSalary
        "onRevealSecretConference" -> FrontEffect.OnRevealSecretConference
        "accuseFromAnywhere" -> FrontEffect.AccuseFromAnywhere
        "endGame" -> FrontEffect.EndGame
        "reshuffleEvidence" -> FrontEffect.ReshuffleEvidence(n.enum("category"))
        else -> n.child("type").fail("efek Front '$type' tidak dikenal")
    }

    private fun parseDeck(text: String, file: String, expected: Deck): List<DeckCard> {
        val r = root(file, text)
        val deck = r.enum<Deck>("deck")
        if (deck != expected) r.child("deck").fail("harus $expected, ditemukan $deck")
        return r.child("cards").array().map { n ->
            DeckCard(
                deck = deck,
                id = n.int("id"),
                title = n.string("title"),
                titleEn = n.string("titleEn"),
                text = n.string("text"),
                textEn = n.string("textEn"),
                keep = n.booleanOr("keep", false),
                effects = n.child("effects").array().map(::parseCardEffect),
            )
        }
    }

    private fun parseCardEffect(n: JsonNode): CardEffect = when (val type = n.string("type")) {
        "moveToTile" -> CardEffect.MoveToTile(
            tile = n.int("tile"),
            collectSalary = n.boolean("collectSalary"),
            rentMultiplier = n.intOr("rentMultiplier", 1),
            freeAccuse = n.booleanOr("freeAccuse", false),
        )
        "moveToNearest" -> CardEffect.MoveToNearest(n.enum("kind"), n.boolean("collectSalary"))
        "moveBack" -> CardEffect.MoveBack(n.int("steps"))
        "goToJail" -> CardEffect.GoToJail
        "gainFromBank" -> CardEffect.GainFromBank(n.int("amount"))
        "payToVault" -> CardEffect.PayToVault(n.int("amount"))
        "payToBank" -> CardEffect.PayToBank(n.int("amount"))
        "payPerBuilding" -> CardEffect.PayPerBuilding(n.int("perSafeHouse"), n.int("perHq"))
        "payPercentOfCashToVault" -> CardEffect.PayPercentOfCashToVault(n.int("percent"))
        "payEachOtherPlayer" -> CardEffect.PayEachOtherPlayer(n.int("amount"))
        "collectFromEachOtherPlayer" -> CardEffect.CollectFromEachOtherPlayer(n.int("amount"))
        "peekRandomFromNeighbor" -> CardEffect.PeekRandomFromNeighbor(n.enum("direction"))
        "peekRandomFromRichest" -> CardEffect.PeekRandomFromRichest
        "showOwnCardToNeighbor" -> CardEffect.ShowOwnCardToNeighbor(n.enum("direction"))
        "forceShowByType" -> CardEffect.ForceShowByType(n.enum("targets"), n.enum("cardType"))
        "jailPass" -> CardEffect.JailPass
        "skipNextSuggestion" -> CardEffect.SkipNextSuggestion
        "extraSuggestion" -> CardEffect.ExtraSuggestion(n.int("turns"))
        "gainIntel" -> CardEffect.GainIntel(n.int("amount"))
        "loseIntel" -> CardEffect.LoseIntel(n.int("amount"))
        "addSuspicion" -> CardEffect.AddSuspicion(n.int("amount"))
        "reduceSuspicion" -> CardEffect.ReduceSuspicion(n.int("amount"))
        else -> n.child("type").fail("efek kartu '$type' tidak dikenal")
    }

    private fun parseMissions(text: String): List<MissionCard> =
        root(MISSIONS, text).child("missions").array().map { n ->
            val c = n.child("condition")
            val condition = when (val type = c.string("type")) {
                "ownDistrict" -> MissionCondition.OwnDistrict(c.enum("district"))
                "escapeRoutes" -> MissionCondition.EscapeRoutes(c.int("count"))
                "cashAtLeast" -> MissionCondition.CashAtLeast(c.int("amount"))
                "buildingPoints" -> MissionCondition.BuildingPoints(c.int("count"))
                "knownCards" -> MissionCondition.KnownCards(c.int("count"))
                "suspicionAtMost" -> MissionCondition.SuspicionAtMost(c.int("max"))
                "propertyCount" -> MissionCondition.PropertyCount(c.int("count"))
                "intelAtLeast" -> MissionCondition.IntelAtLeast(c.int("count"))
                "correctGuesses" -> MissionCondition.CorrectGuesses(c.int("count"))
                else -> c.child("type").fail("syarat misi '$type' tidak dikenal")
            }
            MissionCard(
                id = n.int("id"),
                title = n.string("title"),
                titleEn = n.string("titleEn"),
                text = n.string("text"),
                textEn = n.string("textEn"),
                points = n.int("points"),
                condition = condition,
            )
        }

    private fun parseBalance(text: String): Balance {
        val n = root(BALANCE, text)
        val casino = n.child("casinoPayout")
        val score = n.child("score")
        val family = n.child("familyMode")
        val quick = n.child("quickMode")
        val clock = n.child("warClock")
        val intel = n.child("intel")
        val sus = n.child("suspicion")
        return Balance(
            startingMoney = n.int("startingMoney"),
            startSalary = n.int("startSalary"),
            ossSalary = n.int("ossSalary"),
            buildCost = n.enumIntMap<District>("buildCost"),
            levelMultipliers = n.ints("levelMultipliers"),
            monopolyBaseMultiplier = n.int("monopolyBaseMultiplier"),
            escapeRouteRent = n.ints("escapeRouteRent"),
            escapeRouteTravelCost = n.int("escapeRouteTravelCost"),
            infoPaymentMaxRent = n.int("infoPaymentMaxRent"),
            infoPaymentQuota = n.int("infoPaymentQuota"),
            suggestionNoCardReward = n.int("suggestionNoCardReward"),
            blackMarketPrice = n.int("blackMarketPrice"),
            decodeExtraRollCost = n.int("decodeExtraRollCost"),
            decodeMaxExtraRolls = n.int("decodeMaxExtraRolls"),
            decodeEmptyHandReward = n.int("decodeEmptyHandReward"),
            casinoMinBet = n.int("casinoMinBet"),
            casinoMaxBet = n.int("casinoMaxBet"),
            casinoBetStep = n.int("casinoBetStep"),
            casinoPayout = CasinoPayout(casino.int("seven"), casino.int("eleven"), casino.int("doubles")),
            sevenWinsVault = n.boolean("sevenWinsVault"),
            jailFine = n.int("jailFine"),
            maxJailTurns = n.int("maxJailTurns"),
            embassyPassCost = n.int("embassyPassCost"),
            aprilAccuseCost = n.int("aprilAccuseCost"),
            mortgageInterest = n.double("mortgageInterest"),
            portugalDiscount = n.double("portugalDiscount"),
            auctionStart = n.int("auctionStart"),
            auctionStep = n.int("auctionStep"),
            warTax = n.int("warTax"),
            bribe = n.int("bribe"),
            burnPenaltyFraction = n.double("burnPenaltyFraction"),
            safeHouseStock = n.int("safeHouseStock"),
            hqStock = n.int("hqStock"),
            score = ScoreRules(
                cashPer = score.int("cashPer"),
                propertyPer = score.int("propertyPer"),
                safeHouse = score.int("safeHouse"),
                hq = score.int("hq"),
                correctGuess = score.int("correctGuess"),
                burnedFactor = score.double("burnedFactor"),
                missionsEnabled = score.boolean("missionsEnabled"),
            ),
            familyMode = FamilyModeRules(family.int("wrongAccusePenalty")),
            quickMode = QuickModeRules(quick.int("startProperties"), quick.int("startWarStep")),
            warClock = WarClockRules(clock.enum("mode"), clock.double("passesPerStep")),
            accuseUnlockStep = n.int("accuseUnlockStep"),
            publicPasses = n.boolean("publicPasses"),
            intel = IntelRules(
                start = intel.int("start"),
                max = intel.int("max"),
                perStartPass = intel.int("perStartPass"),
                suggestionCost = intel.int("suggestionCost"),
                interrogationFree = intel.boolean("interrogationFree"),
                decodeSuccess = intel.int("decodeSuccess"),
                defectorBonus = intel.int("defectorBonus"),
                accuseCost = intel.int("accuseCost"),
                silenceCost = intel.int("silenceCost"),
            ),
            suspicion = SuspicionRules(
                limit = sus.int("limit"),
                blackMarket = sus.int("blackMarket"),
                casinoWin = sus.int("casinoWin"),
                decayPerStartPass = sus.int("decayPerStartPass"),
                bribeRelief = sus.int("bribeRelief"),
            ),
            smugglerBlackMarketPrice = n.int("smugglerBlackMarketPrice"),
            buildingRentPercent = n.int("buildingRentPercent"),
        )
    }
}
