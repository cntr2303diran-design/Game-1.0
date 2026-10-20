package id.diran.lisbon1944.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Tile {
    val index: Int
    val name: String
    val nameEn: String
}

/** Petak yang bisa dimiliki: bangunan distrik dan Jalur Pelarian. */
sealed interface OwnableTile : Tile {
    val price: Int
}

@Serializable
@SerialName("property")
data class PropertyTile(
    override val index: Int,
    override val name: String,
    override val nameEn: String,
    val district: District,
    override val price: Int,
    val baseRent: Int,
    val hqRent: Int,
) : OwnableTile

@Serializable
@SerialName("escape")
data class EscapeRouteTile(
    override val index: Int,
    override val name: String,
    override val nameEn: String,
    override val price: Int = 200,
) : OwnableTile

@Serializable
@SerialName("special")
data class SpecialTile(
    override val index: Int,
    override val name: String,
    override val nameEn: String,
    val kind: SpecialKind,
    val amount: Int? = null,
) : Tile
