package id.diran.lisbon1944.engine.model

import kotlinx.serialization.Serializable

/**
 * Satu-satunya pandangan atas permainan yang boleh dibaca UI dan bot.
 * Dibangun oleh viewFor(state, playerId) (Tahap 2). Tidak memuat isi amplop, tangan pemain lain,
 * atau kartu yang ditunjukkan kepada orang lain — kecuali sudah menjadi pengetahuan viewer.
 */
@Serializable
data class PlayerView(
    val viewerId: PlayerId,
    val me: PlayerState,
    val opponents: List<OpponentView>,
    val ownership: Map<Int, Ownership>,
    val embassyCards: List<CardId>,
    val warStep: Int,
    val activeFront: FrontId,
    /** Lintasan START yang masih dibutuhkan sebelum Jam Perang maju (mode START_PASSES), null untuk mode lain. */
    val passesToNextStep: Int? = null,
    val openOperations: List<Int> = emptyList(),
    val claimedOperations: List<Int> = emptyList(),
    val sealedTiles: Map<Int, Int> = emptyMap(),
    val patrolPos: Int = -1,
    val contactPos: Int = -1,
    val tacticDeckSize: Int = 0,
    val cipherDeckSize: Int,
    val telegramDeckSize: Int,
    val casinoVault: Int,
    val houseStock: Int,
    val hqStock: Int,
    val currentPlayer: PlayerId,
    val roundNumber: Int,
    val phase: TurnPhase,
    val doublesCount: Int,
    /** Keputusan lengkap — hanya terisi bila viewer adalah decider. */
    val myDecision: PendingDecision?,
    /** Siapa yang sedang ditunggu (publik) dan jenis keputusannya, tanpa detail privat. */
    val waitingOn: PlayerId?,
    val waitingKind: String?,
    val auction: AuctionState?,
    /** Tawaran dagang terlihat hanya oleh kedua pihak; kartu Bukti di dalamnya disamarkan untuk pihak lain. */
    val trade: TradeState?,
    val winner: WinResult?,
    /** Terisi hanya jika game berakhir atau viewer sudah Terbakar (sudah melihat amplop). */
    val revealedEnvelope: Envelope?,
    val knowledge: Set<KnownFact>,
    val log: List<GameEvent>,
    val config: GameConfig,
)

@Serializable
data class OpponentView(
    val id: PlayerId,
    val name: String,
    val faction: Faction,
    val isBot: Boolean,
    val money: Int,
    val position: Int,
    val handCount: Int,
    /** Hanya terisi untuk pemain bangkrut (tangannya menjadi publik). */
    val publicHand: List<CardId>?,
    val inJail: Boolean,
    val burned: Boolean,
    val bankrupt: Boolean,
    val abilityUsed: Boolean,
    val heldCardCount: Int,
    val propertyCount: Int,
    val intel: Int = 0,
    val suspicion: Int = 0,
    val tacticCount: Int = 0,
    val prestige: Int = 0,
    val operationsClaimed: Int = 0,
)
