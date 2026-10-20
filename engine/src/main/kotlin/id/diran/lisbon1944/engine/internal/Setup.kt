package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Envelope
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameMode
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.KnownFact
import id.diran.lisbon1944.engine.model.OwnableTile
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerState
import id.diran.lisbon1944.engine.model.Task
import id.diran.lisbon1944.engine.rng.SplitMix64Rng

/** Persiapan permainan (BAGIAN 6.1). Semua keacakan dari seed. */
internal object Setup {

    fun newGame(c: GameContent, config: GameConfig, seed: Long): GameState {
        val problems = config.validate()
        require(problems.isEmpty()) { "Konfigurasi tidak valid: ${problems.joinToString("; ")}" }
        config.presetOwnership.keys.forEach {
            require(c.board.getOrNull(it) is OwnableTile) { "presetOwnership: petak $it tidak bisa dimiliki" }
        }
        val rng = SplitMix64Rng.fromSeed(seed)
        val b = config.balance
        val n = config.players.size

        // 1. Amplop Map Merah: satu kartu acak dari tiap tumpukan.
        val suspects = rng.shuffled(c.evidenceOfType(EvidenceType.SUSPECT).map { it.id })
        val locations = rng.shuffled(c.evidenceOfType(EvidenceType.LOCATION).map { it.id })
        val items = rng.shuffled(c.evidenceOfType(EvidenceType.ITEM).map { it.id })
        val envelope = Envelope(suspects.first(), locations.first(), items.first())

        // 2. Sisa 15 kartu dikocok dan dibagi rata; sisa pembagian terbuka di Kedutaan.
        val rest = rng.shuffled(suspects.drop(1) + locations.drop(1) + items.drop(1))
        val perPlayer = rest.size / n
        val hands: List<List<CardId>> = (0 until n).map { rest.subList(it * perPlayer, (it + 1) * perPlayer).toList() }
        val embassy = rest.drop(perPlayer * n)

        // 7. Urutan jalan: lempar 2 dadu, tertinggi mulai; seri di puncak lempar ulang.
        val rolls = ArrayList<Triple<Int, Int, Int>>()
        var contenders = (0 until n).toList()
        var first: Int
        while (true) {
            val results = contenders.map { seat ->
                val d1 = rng.rollDie()
                val d2 = rng.rollDie()
                rolls += Triple(seat, d1, d2)
                seat to d1 + d2
            }
            val best = results.maxOf { it.second }
            val top = results.filter { it.second == best }.map { it.first }
            if (top.size == 1) {
                first = top.first()
                break
            }
            contenders = top
        }

        // 8. Varian Agen Ganda.
        val doubleAgent = if (config.doubleAgentVariant) rng.nextInt(n) else -1

        // 5. Kocok Sandi & Telegram.
        val cipherDeck = rng.shuffled(c.cipher.map { it.id })
        val telegramDeck = rng.shuffled(c.telegram.map { it.id })

        // Kepemilikan awal: Kampanye (preset) dan Mode Cepat (2 properti acak tiap pemain).
        val ownership = LinkedHashMap<Int, Ownership>()
        config.presetOwnership.toSortedMap().forEach { (tile, seat) -> ownership[tile] = Ownership(PlayerId(seat)) }
        if (config.mode == GameMode.QUICK) {
            val pool = rng.shuffled(c.properties.map { it.index }.filter { it !in ownership })
            var k = 0
            for (seat in 0 until n) repeat(b.quickMode.startProperties) {
                if (k < pool.size) ownership[pool[k++]] = Ownership(PlayerId(seat))
            }
        }

        // Misi Rahasia: satu per pemain, rahasia.
        val missionIds = if (b.score.missionsEnabled && c.missions.size >= n) rng.shuffled(c.missions.map { it.id }).take(n) else emptyList()

        val players = config.players.mapIndexed { i, setup ->
            val intel = b.intel.start + if (setup.faction == Faction.ABWEHR_DEFECTOR) b.intel.defectorBonus else 0
            PlayerState(
                id = PlayerId(i), name = setup.name, faction = setup.faction, isBot = setup.isBot, botLevel = setup.botLevel,
                money = b.startingMoney, hand = hands[i], isDoubleAgent = i == doubleAgent,
                intel = intel, missionId = missionIds.getOrNull(i),
            )
        }
        val knowledge = (0 until n).map { i ->
            (hands[i].map { KnownFact(it, Holder.Player(PlayerId(i)), KnowledgeSource.OWN_HAND) } +
                embassy.map { KnownFact(it, Holder.Embassy, KnowledgeSource.EMBASSY) }).toSet()
        }
        val startStep = if (config.mode == GameMode.QUICK) b.quickMode.startWarStep else 1

        val state = GameState(
            config = config,
            seed = seed,
            rngState = rng.state(),
            players = players,
            ownership = ownership,
            envelope = envelope,
            embassyCards = embassy,
            warStep = startStep,
            activeFront = FrontId(startStep),
            cipherDeck = cipherDeck,
            telegramDeck = telegramDeck,
            houseStock = b.safeHouseStock,
            hqStock = b.hqStock,
            firstPlayerIdx = first,
            currentPlayerIdx = first,
            knowledge = knowledge,
            tasks = listOf(Task.StartTurn(PlayerId(first))),
        )
        val tx = Tx(state, rng, c)
        tx.emit(E.GAME_STARTED, "players" to n, "seed" to seed, "mode" to config.mode, "embassy" to embassy)
        rolls.forEach { (seat, d1, d2) -> tx.emit(E.INITIATIVE_ROLL, "player" to PlayerId(seat), "d1" to d1, "d2" to d2) }
        tx.emit(E.FIRST_PLAYER, "player" to PlayerId(first))
        if (doubleAgent >= 0) tx.emit(E.DOUBLE_AGENT_ROLE, "player" to PlayerId(doubleAgent), visibleTo = listOf(PlayerId(doubleAgent)))
        missionIds.forEachIndexed { i, m -> tx.emit(E.MISSION_ASSIGNED, "player" to PlayerId(i), "mission" to m, visibleTo = listOf(PlayerId(i))) }
        tx.revealFront(startStep)
        tx.advance()
        return tx.commit()
    }
}
