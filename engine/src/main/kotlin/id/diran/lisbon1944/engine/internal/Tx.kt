package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.GameEvents
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.Balance
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Creditor
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.FrontEffect
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.GameMode
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.KnownFact
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerState
import id.diran.lisbon1944.engine.model.Task
import id.diran.lisbon1944.engine.model.Visibility
import id.diran.lisbon1944.engine.rng.GameRng

internal const val BOARD_SIZE = 40
internal const val JAIL_TILE = 10
internal const val EMBASSY_TILE = 39
internal const val LAST_FRONT = 12

/**
 * Transaksi satu reduce(): memegang salinan state yang diperbarui secara lokal (GameState tetap immutable
 * — setiap perubahan membuat salinan baru), RNG, dan event yang dihasilkan. Tidak ada I/O, tidak ada waktu sistem.
 */
internal class Tx(var s: GameState, val rng: GameRng, val c: GameContent) {

    private val newEvents = ArrayList<GameEvent>()
    val events: List<GameEvent> get() = newEvents

    val b: Balance get() = s.config.balance
    val n: Int get() = s.players.size
    val familyMode: Boolean get() = s.config.mode == GameMode.FAMILY
    val current: PlayerId get() = PlayerId(s.currentPlayerIdx)

    // --- Event ---------------------------------------------------------------------------

    fun emit(type: String, vararg kv: Pair<String, Any?>, visibleTo: Collection<PlayerId>? = null) {
        val payload = LinkedHashMap<String, String>()
        for ((k, v) in kv) if (v != null) payload[k] = fmt(v)
        val vis: Visibility = if (visibleTo == null) Visibility.Public else Visibility.Private(visibleTo.toSet())
        newEvents += GameEvent(s.eventLog.size + newEvents.size, type, payload, vis)
    }

    private fun fmt(v: Any): String = when (v) {
        is PlayerId -> v.value.toString()
        is CardId -> v.value
        is Creditor -> creditorLabel(v)
        is Collection<*> -> v.joinToString(",") { it?.let(::fmt) ?: "" }
        is Enum<*> -> v.name
        else -> v.toString()
    }

    fun creditorLabel(cr: Creditor): String = when (cr) {
        Creditor.Bank -> "bank"
        Creditor.Vault -> "vault"
        is Creditor.Player -> cr.id.value.toString()
    }

    fun commit(): GameState =
        s.copy(eventLog = if (newEvents.isEmpty()) s.eventLog else s.eventLog + newEvents, rngState = rng.state())

    // --- Pemain --------------------------------------------------------------------------

    fun player(id: PlayerId): PlayerState = s.players[id.value]

    fun upd(id: PlayerId, f: (PlayerState) -> PlayerState) {
        val list = s.players.toMutableList()
        list[id.value] = f(list[id.value])
        s = s.copy(players = list)
    }

    fun isActive(id: PlayerId): Boolean = !player(id).bankrupt

    /** Kursi lain searah jarum jam mulai dari kiri [id] (termasuk yang bangkrut). */
    fun clockwiseOthers(id: PlayerId): List<PlayerId> = (1 until n).map { PlayerId((id.value + it) % n) }

    /** Pemain aktif lain searah jarum jam mulai dari kiri [id]. */
    fun othersFromLeft(id: PlayerId): List<PlayerId> = clockwiseOthers(id).filter(::isActive)

    fun leftOf(id: PlayerId): PlayerId? = othersFromLeft(id).firstOrNull()

    fun rightOf(id: PlayerId): PlayerId? = othersFromLeft(id).lastOrNull()

    fun activeIds(): List<PlayerId> = s.players.filter { !it.bankrupt }.map { it.id }

    // --- Tugas & keputusan ---------------------------------------------------------------

    /** Dorong tugas ke depan tumpukan; argumen pertama dijalankan lebih dulu. */
    fun push(vararg tasks: Task) = pushAll(tasks.asList())

    fun pushAll(tasks: List<Task>) {
        if (tasks.isNotEmpty()) s = s.copy(tasks = tasks + s.tasks)
    }

    fun appendTask(task: Task) {
        s = s.copy(tasks = s.tasks + task)
    }

    fun setPending(d: PendingDecision) {
        check(s.pending == null) { "Dua keputusan sekaligus: ${s.pending} dan $d" }
        s = s.copy(pending = d)
    }

    // --- Uang ----------------------------------------------------------------------------

    fun gainFromBank(id: PlayerId, amount: Int, reason: String) {
        if (amount <= 0) return
        upd(id) { it.copy(money = it.money + amount) }
        emit(GameEvents.MONEY, "from" to "bank", "to" to id, "amount" to amount, "reason" to reason)
    }

    /** Transfer langsung; pemanggil sudah memastikan uang cukup. Kreditur bangkrut diganti bank. */
    fun pay(from: PlayerId, to: Creditor, amount: Int, reason: String) {
        if (amount <= 0) return
        check(player(from).money >= amount) { "Uang ${from} tidak cukup untuk membayar $amount" }
        upd(from) { it.copy(money = it.money - amount) }
        val target = if (to is Creditor.Player && player(to.id).bankrupt) Creditor.Bank else to
        when (target) {
            Creditor.Bank -> Unit
            Creditor.Vault -> s = s.copy(casinoVault = s.casinoVault + amount)
            is Creditor.Player -> upd(target.id) { it.copy(money = it.money + amount) }
        }
        emit(GameEvents.MONEY, "from" to from, "to" to target, "amount" to amount, "reason" to reason)
    }

    // --- Pengetahuan ---------------------------------------------------------------------

    fun learn(viewer: PlayerId, card: CardId, holder: Holder, source: KnowledgeSource) {
        val list = s.knowledge.toMutableList()
        list[viewer.value] = list[viewer.value] + KnownFact(card, holder, source)
        s = s.copy(knowledge = list)
    }

    fun forget(viewer: PlayerId, card: CardId) {
        val list = s.knowledge.toMutableList()
        list[viewer.value] = list[viewer.value].filterNot { it.cardId == card }.toSet()
        s = s.copy(knowledge = list)
    }

    // --- Papan, tumpukan, Front ----------------------------------------------------------

    fun setOwner(tile: Int, ownership: Ownership?) {
        s = s.copy(ownership = if (ownership == null) s.ownership - tile else s.ownership + (tile to ownership))
    }

    fun deck(deck: Deck): List<Int> = when (deck) {
        Deck.CIPHER -> s.cipherDeck
        Deck.TELEGRAM -> s.telegramDeck
    }

    fun setDeck(deck: Deck, cards: List<Int>) {
        s = when (deck) {
            Deck.CIPHER -> s.copy(cipherDeck = cards)
            Deck.TELEGRAM -> s.copy(telegramDeck = cards)
        }
    }

    fun returnCard(deck: Deck, cardId: Int) = setDeck(deck, deck(deck) + cardId)

    fun frontEffects(): List<FrontEffect> = c.front(s.activeFront).effects
}
