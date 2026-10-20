package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.Task
import id.diran.lisbon1944.engine.model.TurnPhase

/** Invariant BAGIAN 9 — dicek setelah setiap reduce di test dan simulasi. Kosong = sehat. */
object Invariants {

    fun check(s: GameState, c: GameContent): List<String> = buildList {
        val b = s.config.balance

        // Kartu Bukti: tepat 18 unik di tangan + amplop + Kedutaan.
        val placed = s.players.flatMap { it.hand } + s.envelope.cards + s.embassyCards
        val all = c.evidence.map { it.id }.toSet()
        if (placed.size != all.size || placed.toSet() != all) add("Kartu Bukti tidak lengkap/ganda: ${placed.size} kartu")

        // Uang tidak negatif.
        s.players.filter { it.money < 0 }.forEach { add("Uang ${it.name} negatif: ${it.money}") }

        // Stok bangunan.
        var houses = 0
        var hqs = 0
        s.ownership.values.forEach { if (it.level == Ownership.HQ_LEVEL) hqs++ else houses += it.level }
        if (s.houseStock < 0 || s.hqStock < 0) add("Stok negatif: rumah ${s.houseStock}, markas ${s.hqStock}")
        if (s.houseStock + houses != b.safeHouseStock) add("Rumah Aman tidak seimbang: stok ${s.houseStock} + terpasang $houses ≠ ${b.safeHouseStock}")
        if (s.hqStock + hqs != b.hqStock) add("Markas tidak seimbang: stok ${s.hqStock} + terpasang $hqs ≠ ${b.hqStock}")

        // Bangunan hanya di distrik penuh tanpa gadai, selisih level ≤ 1.
        c.properties.groupBy { it.district }.forEach { (district, tiles) ->
            val os = tiles.map { s.ownership[it.index] }
            val levels = os.map { it?.level ?: 0 }
            if (levels.any { it > 0 }) {
                val owners = os.map { it?.ownerId }.toSet()
                if (owners.size != 1 || owners.first() == null) add("Bangunan di distrik $district yang tidak dimiliki penuh")
                if (os.any { it?.mortgaged == true }) add("Bangunan di distrik $district yang punya properti tergadai")
                if (levels.max() - levels.min() > 1) add("Selisih level distrik $district > 1: $levels")
            }
        }
        s.ownership.forEach { (tile, o) ->
            if (c.board.getOrNull(tile) !is PropertyTile && o.level != 0) add("Petak $tile punya bangunan padahal bukan properti distrik")
            if (s.player(o.ownerId).bankrupt) add("Petak $tile dimiliki pemain bangkrut")
        }

        // Tepat satu keputusan, atau permainan selesai.
        if (s.winner != null) {
            if (s.pending != null || s.phase != TurnPhase.ENDED) add("Permainan selesai tetapi masih ada keputusan/fase aktif")
        } else {
            if (s.pending == null) add("Tidak ada PendingDecision padahal permainan belum selesai")
        }

        if (s.warStep !in 1..12) add("warStep di luar 1..12: ${s.warStep}")
        if (s.activeFront.value != s.warStep) add("Front aktif ${s.activeFront.value} ≠ langkah ${s.warStep}")

        // Kartu Sandi & Telegram tidak hilang (selama permainan berjalan).
        if (s.winner == null) {
            Deck.entries.forEach { deck ->
                val pile = if (deck == Deck.CIPHER) s.cipherDeck else s.telegramDeck
                val held = s.players.flatMap { it.heldCards }.filter { it.deck == deck }.map { it.cardId }
                val inFlight = s.tasks.filterIsInstance<Task.ReturnCard>().filter { it.deck == deck }.map { it.cardId }
                val offered = (s.pending as? PendingDecision.TelegramKeepChoice)?.options?.takeIf { deck == Deck.TELEGRAM } ?: emptyList()
                val total = pile + held + inFlight + offered
                val expected = c.deck(deck).map { it.id }.toSet()
                if (total.size != expected.size || total.toSet() != expected) add("Tumpukan $deck tidak lengkap: ${total.sorted()}")
            }
        }

        s.players.forEach { pl ->
            val cap = b.intel.max + if (pl.faction == id.diran.lisbon1944.engine.model.Faction.ABWEHR_DEFECTOR) b.intel.defectorBonus else 0
            if (pl.intel !in 0..cap) add("Intel ${pl.name} di luar 0..$cap: ${pl.intel}")
            if (pl.suspicion !in 0 until b.suspicion.limit) add("Kecurigaan ${pl.name} di luar 0..${b.suspicion.limit - 1}: ${pl.suspicion}")
        }
        if (s.startPasses < 0) add("startPasses negatif: ${s.startPasses}")
        if (s.config.clockMode == id.diran.lisbon1944.engine.model.WarClockMode.START_PASSES && s.warStep < 12 &&
            s.startPasses >= s.config.passesNeededFor(s.warStep + 1)
        ) {
            add("Jam Perang tertinggal: ${s.startPasses} lintasan tetapi masih langkah ${s.warStep}")
        }

        val knowledgeSize = s.knowledge.size
        if (knowledgeSize != s.players.size) add("Jumlah catatan pengetahuan $knowledgeSize ≠ jumlah pemain ${s.players.size}")
    }
}
