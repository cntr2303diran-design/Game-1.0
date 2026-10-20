package id.diran.lisbon1944.engine.model

/**
 * Petak papan. Bagian dari konten statis (board.json), dibaca manual oleh ContentLoader —
 * tidak pernah diserialisasi ke dalam GameState.
 */
sealed interface Tile {
    val index: Int
    val name: String
    val nameEn: String
}

/** Petak yang bisa dimiliki: bangunan distrik dan Jalur Pelarian. */
sealed interface OwnableTile : Tile {
    val price: Int
}

data class PropertyTile(
    override val index: Int,
    override val name: String,
    override val nameEn: String,
    val district: District,
    override val price: Int,
    val baseRent: Int,
    val hqRent: Int,
) : OwnableTile

data class EscapeRouteTile(
    override val index: Int,
    override val name: String,
    override val nameEn: String,
    override val price: Int = 200,
) : OwnableTile

data class SpecialTile(
    override val index: Int,
    override val name: String,
    override val nameEn: String,
    val kind: SpecialKind,
    val amount: Int? = null,
) : Tile
