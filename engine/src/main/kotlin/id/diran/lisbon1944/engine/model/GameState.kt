package id.diran.lisbon1944.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Ownership(
    val ownerId: PlayerId,
    /** 0 = tanpa bangunan, 1..4 = jumlah Rumah Aman, 5 = Markas. Jalur Pelarian selalu 0. */
    val level: Int = 0,
    val mortgaged: Boolean = false,
) {
    init {
        require(level in 0..MAX_LEVEL) { "level harus 0..$MAX_LEVEL, ditemukan $level" }
    }

    companion object {
        const val MAX_LEVEL = 5
        const val HQ_LEVEL = 5
    }
}

@Serializable
data class JailState(val turnsServed: Int = 0)

/** Kartu Sandi/Telegram yang disimpan pemain (Surat Bebas Diplomatik). */
@Serializable
data class HeldCard(val deck: Deck, val cardId: Int)

@Serializable
data class Envelope(val suspect: CardId, val location: CardId, val item: CardId) {
    val cards: List<CardId> get() = listOf(suspect, location, item)
}

@Serializable
data class Guess(val suspect: CardId, val location: CardId, val item: CardId) {
    val cards: List<CardId> get() = listOf(suspect, location, item)
}

@Serializable
data class PlayerState(
    val id: PlayerId,
    val name: String,
    val faction: Faction,
    val isBot: Boolean,
    val botLevel: BotLevel? = null,
    val money: Int,
    val position: Int = 0,
    val hand: List<CardId> = emptyList(),
    val jail: JailState? = null,
    val burned: Boolean = false,
    val bankrupt: Boolean = false,
    val abilityUsed: Boolean = false,
    val infoPaymentsThisStep: Int = 0,
    val heldCards: List<HeldCard> = emptyList(),
    /** Kartu "Kode Dibakar": larangan Dugaan pada giliran berikutnya. */
    val skipNextSuggestion: Boolean = false,
    /** Larangan Dugaan yang berlaku pada giliran ini (dipindahkan dari skipNextSuggestion saat giliran mulai). */
    val suggestionBlockedThisTurn: Boolean = false,
    /** Hak Dugaan gratis dari Front Agu 1944. */
    val freeSuggestions: Int = 0,
    /** >0 = punya 1 Dugaan tambahan (Rekrut Informan); berkurang 1 setiap awal giliran pemain ini. */
    val extraSuggestionTurnsLeft: Int = 0,
    val isDoubleAgent: Boolean = false,
    val doubleAgentLieUsed: Boolean = false,
    val finalGuess: Guess? = null,
    /** Token Laporan Intel — dipakai untuk Dugaan dari properti sendiri. Publik. */
    val intel: Int = 0,
    /** Meteran Kecurigaan polisi rahasia — mencapai batas berarti ditangkap. Publik. */
    val suspicion: Int = 0,
    /** Misi Rahasia (id missions.json) — hanya diketahui pemiliknya. */
    val missionId: Int? = null,
    /** Kartu Taktik di tangan (id tactics.json) — rahasia; lawan hanya melihat jumlahnya. */
    val tactics: List<Int> = emptyList(),
    /** Prestise (poin kemenangan dari Operasi Kota & kartu). Publik. */
    val prestige: Int = 0,
    val operationsClaimed: Int = 0,
    val tacticsPlayed: Int = 0,
) {
    val inJail: Boolean get() = jail != null
    val active: Boolean get() = !bankrupt
}

/** Siapa pemegang sebuah kartu Bukti menurut pengetahuan seorang pemain. */
@Serializable
sealed interface Holder {
    @Serializable @SerialName("player")
    data class Player(val id: PlayerId) : Holder

    @Serializable @SerialName("embassy")
    data object Embassy : Holder

    @Serializable @SerialName("envelope")
    data object Envelope : Holder
}

@Serializable
enum class KnowledgeSource {
    OWN_HAND, EMBASSY, SUGGESTION, RENT_INFO, DECODE, MI6_ABILITY, INFORMANT, WIRETAP,
    SECRET_CONFERENCE, OPEN_TELEGRAM, INTELLIGENCE_REPORT, CITY_MAP, COURIER,
    BLACK_MARKET, TRADE, BANKRUPTCY, ACCUSATION, GAME_END,
}

@Serializable
data class KnownFact(val cardId: CardId, val holder: Holder, val source: KnowledgeSource)

@Serializable
sealed interface Visibility {
    @Serializable @SerialName("public")
    data object Public : Visibility

    @Serializable @SerialName("private")
    data class Private(val playerIds: Set<PlayerId>) : Visibility

    fun visibleTo(player: PlayerId): Boolean = when (this) {
        Public -> true
        is Private -> player in playerIds
    }
}

/**
 * Event log. [type] adalah kunci stabil (lihat GameEvents) yang dipakai UI untuk animasi & teks terlokalisasi;
 * [payload] berisi parameter. Event privat tidak pernah dikirim ke pemain di luar [visibility].
 */
@Serializable
data class GameEvent(
    val seq: Int,
    val type: String,
    val payload: Map<String, String> = emptyMap(),
    val visibility: Visibility = Visibility.Public,
)

@Serializable
data class AuctionState(
    val tile: Int,
    /** Urutan penawar (searah jarum jam mulai setelah pemain yang menolak; penolak terakhir). */
    val order: List<PlayerId>,
    val stillIn: List<PlayerId>,
    /** Indeks di [order] dari penawar yang sedang ditanya. */
    val turnIdx: Int,
    val highBid: Int = 0,
    val highBidder: PlayerId? = null,
)

@Serializable
data class TradeOffer(
    val from: PlayerId,
    val to: PlayerId,
    val giveMoney: Int = 0,
    val takeMoney: Int = 0,
    val giveProperties: List<Int> = emptyList(),
    val takeProperties: List<Int> = emptyList(),
    val giveHeldCards: List<HeldCard> = emptyList(),
    val takeHeldCards: List<HeldCard> = emptyList(),
    val giveEvidence: List<CardId> = emptyList(),
    val takeEvidence: List<CardId> = emptyList(),
)

@Serializable
data class TradeState(val offer: TradeOffer)

/** Mini-game Dekode yang sedang berjalan. [number] rahasia milik operator — tidak pernah masuk PlayerView. */
@Serializable
data class DecodeState(
    val asker: PlayerId,
    val operator: PlayerId,
    val number: Int? = null,
    val extraRolls: Int = 0,
    /** Uang lemparan tambahan yang ditahan sampai hasil diketahui. */
    val paid: Int = 0,
)

/** Kebohongan Agen Ganda — event privat sampai layar akhir. */
@Serializable
data class LieRecord(
    val liar: PlayerId,
    val asker: PlayerId,
    val shown: CardId,
    val named: List<CardId>,
    val actionIndex: Int,
)

@Serializable
data class WinResult(
    val type: WinType,
    val winners: List<PlayerId>,
)

@Serializable
data class ScoreBreakdown(
    val player: PlayerId,
    val cash: Int,
    val property: Int,
    val buildings: Int,
    val clues: Int,
    val mission: Int = 0,
    val prestige: Int = 0,
    val burned: Boolean,
    val total: Int,
)

@Serializable
data class GameState(
    val config: GameConfig,
    val seed: Long,
    /** Status internal GameRng setelah aksi terakhir — save/load & replay melanjutkan dari sini. */
    val rngState: Long,
    val players: List<PlayerState>,
    val ownership: Map<Int, Ownership> = emptyMap(),
    val envelope: Envelope,
    val embassyCards: List<CardId> = emptyList(),
    val warStep: Int = 1,
    val warClockAdvancedThisRound: Boolean = false,
    val activeFront: FrontId = FrontId(1),
    /** Front #12 sudah dibalik; permainan berakhir setelah aksi yang sedang berjalan selesai. */
    val warEnded: Boolean = false,
    /** Total lintasan START sejak awal permainan (mode START_PASSES). */
    val startPasses: Int = 0,
    /** Kartu Front untuk tiap langkah 1..12 (id front.json) — rahasia sampai dibalik. */
    val frontPlan: List<Int> = (1..12).toList(),
    /** Tumpukan Kartu Taktik; indeks 0 = teratas, kartu terpakai kembali ke bawah. */
    val tacticDeck: List<Int> = emptyList(),
    val tacticsPlayedThisTurn: Int = 0,
    /** Operasi Kota: tumpukan tertutup, yang terbuka, dan yang sudah diklaim. */
    val operationDeck: List<Int> = emptyList(),
    val openOperations: List<Int> = emptyList(),
    val claimedOperations: List<Int> = emptyList(),
    /** Petak yang disegel (tanpa sewa) → putaran terakhir segel berlaku. */
    val sealedTiles: Map<Int, Int> = emptyMap(),
    /** Posisi pion Patroli PIDE dan Kontak Netral; -1 = tidak dipakai. */
    val patrolPos: Int = -1,
    val contactPos: Int = -1,
    /** Urutan tumpukan Sandi/Telegram; indeks 0 = kartu teratas. Kartu terpakai kembali ke bawah. */
    val cipherDeck: List<Int>,
    val telegramDeck: List<Int>,
    val casinoVault: Int = 0,
    /** Stok Rumah Aman tersisa di bank. */
    val houseStock: Int,
    /** Stok Markas tersisa di bank. */
    val hqStock: Int,
    val firstPlayerIdx: Int = 0,
    val currentPlayerIdx: Int = 0,
    val roundNumber: Int = 1,
    val phase: TurnPhase = TurnPhase.MOVE,
    val doublesCount: Int = 0,
    /** Hak Dugaan dari properti sendiri sudah dipakai pada giliran ini. */
    val propertySuggestionUsed: Boolean = false,
    val pending: PendingDecision? = null,
    /** Tumpukan kelanjutan engine (indeks 0 dijalankan lebih dulu). Lihat DECISIONS.md D15. */
    val tasks: List<Task> = emptyList(),
    val auction: AuctionState? = null,
    val trade: TradeState? = null,
    val decode: DecodeState? = null,
    val winner: WinResult? = null,
    /** Pengetahuan per kursi pemain (indeks = PlayerId.value). */
    val knowledge: List<Set<KnownFact>> = emptyList(),
    /** Kartu yang "diarahkan" Agen Ganda (rahasia). */
    val doubleAgentSteered: Set<CardId> = emptySet(),
    val doubleAgentLies: List<LieRecord> = emptyList(),
    val eventLog: List<GameEvent> = emptyList(),
    val actionCount: Int = 0,
) {
    val currentPlayer: PlayerState get() = players[currentPlayerIdx]
    fun player(id: PlayerId): PlayerState = players[id.value]
}
