package id.diran.lisbon1944.bot

import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Envelope
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.KnownFact
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerSetup

object BotTestSupport {
    val content: GameContent by lazy { ContentLoader.loadDefault() }
    val engine: GameEngine by lazy { GameEngine(content) }
    val bots: Bots by lazy { Bots(content) }

    fun c(id: String) = CardId(id)
    fun p(i: Int) = PlayerId(i)

    fun config(n: Int, level: BotLevel = BotLevel.HARD, factions: List<Faction> = Faction.entries.take(n)) = GameConfig(
        players = factions.mapIndexed { i, f -> PlayerSetup("Bot $i", f, isBot = true, botLevel = level) },
        balance = content.balance,
    )

    /** Permainan baru dengan amplop dan tangan yang ditentukan; sisa kartu ke Kedutaan; pengetahuan disusun ulang. */
    fun deal(n: Int, envelope: List<String>, vararg hands: List<String>, factions: List<Faction> = Faction.entries.take(n)): GameState {
        val s = engine.newGame(config(n, factions = factions), 3)
        val env = Envelope(c(envelope[0]), c(envelope[1]), c(envelope[2]))
        val handIds = (0 until n).map { i -> hands.getOrNull(i)?.map(::c) ?: emptyList() }
        val used = env.cards + handIds.flatten()
        val embassy = content.evidence.map { it.id }.filter { it !in used }
        return s.copy(
            envelope = env,
            embassyCards = embassy,
            players = s.players.mapIndexed { i, pl -> pl.copy(hand = handIds[i]) },
            knowledge = (0 until n).map { i ->
                (handIds[i].map { KnownFact(it, Holder.Player(PlayerId(i)), KnowledgeSource.OWN_HAND) } +
                    embassy.map { KnownFact(it, Holder.Embassy, KnowledgeSource.EMBASSY) }).toSet()
            },
            eventLog = emptyList(),
        )
    }

    fun learn(s: GameState, viewer: Int, card: String, holder: Holder): GameState {
        val list = s.knowledge.toMutableList()
        list[viewer] = list[viewer] + KnownFact(c(card), holder, KnowledgeSource.SUGGESTION)
        return s.copy(knowledge = list)
    }
}
