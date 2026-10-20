package id.diran.lisbon1944.engine.content

import id.diran.lisbon1944.engine.model.Balance
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.DeckCard
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.EvidenceCard
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FactionDef
import id.diran.lisbon1944.engine.model.FrontCard
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.SpecialKind
import id.diran.lisbon1944.engine.model.SpecialTile
import id.diran.lisbon1944.engine.model.Tile

/** Seluruh konten statis permainan (papan, kartu, faksi, angka keseimbangan) yang sudah tervalidasi. */
data class GameContent(
    val board: List<Tile>,
    val factions: List<FactionDef>,
    val evidence: List<EvidenceCard>,
    val fronts: List<FrontCard>,
    val cipher: List<DeckCard>,
    val telegram: List<DeckCard>,
    val balance: Balance,
) {
    private val evidenceById: Map<CardId, EvidenceCard> by lazy { evidence.associateBy { it.id } }

    fun tile(index: Int): Tile = board[index]

    val properties: List<PropertyTile> by lazy { board.filterIsInstance<PropertyTile>() }

    val escapeRoutes: List<EscapeRouteTile> by lazy { board.filterIsInstance<EscapeRouteTile>() }

    fun propertiesIn(district: District): List<PropertyTile> = properties.filter { it.district == district }

    fun specialTiles(kind: SpecialKind): List<SpecialTile> =
        board.filterIsInstance<SpecialTile>().filter { it.kind == kind }

    fun faction(id: Faction): FactionDef = factions.first { it.id == id }

    fun evidence(id: CardId): EvidenceCard =
        evidenceById[id] ?: throw NoSuchElementException("Kartu Bukti tidak dikenal: $id")

    fun evidenceOfType(type: EvidenceType): List<EvidenceCard> = evidence.filter { it.type == type }

    /** Kartu Lokasi yang mewakili sebuah distrik. */
    fun locationCardFor(district: District): EvidenceCard =
        evidence.first { it.type == EvidenceType.LOCATION && it.district == district }

    fun front(id: FrontId): FrontCard = fronts.first { it.id == id }

    fun deck(deck: Deck): List<DeckCard> = when (deck) {
        Deck.CIPHER -> cipher
        Deck.TELEGRAM -> telegram
    }

    fun card(deck: Deck, id: Int): DeckCard = deck(deck).first { it.id == id }
}
