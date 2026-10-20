package id.diran.lisbon1944.engine.model

import kotlinx.serialization.Serializable

@Serializable
enum class District { PELABUHAN, ALFAMA, BAIXA, CHIADO, AVENIDA, ESTORIL }

@Serializable
enum class Faction { MI6, OSS, NKVD, RESISTANCE, PORTUGAL_TRADER, SWISS_JOURNALIST }

@Serializable
enum class EvidenceType { SUSPECT, LOCATION, ITEM }

@Serializable
enum class SpecialKind {
    START, TELEGRAM, CIPHER, WAR_TAX, JAIL, RADIO, TELEGRAPH,
    INTERROGATION, CASINO, BLACK_MARKET, ARREST, BRIBE, EMBASSY,
}

@Serializable
enum class TurnPhase { MOVE, TILE_ACTION, INVESTIGATE, BUSINESS, ENDED }

@Serializable
enum class Deck { CIPHER, TELEGRAM }

/** Arah relatif ke pemain aktif. LEFT = kursi berikutnya (searah jarum jam), RIGHT = kursi sebelumnya. */
@Serializable
enum class Direction { LEFT, RIGHT }

@Serializable
enum class TargetScope { LEFT, RIGHT, ALL_OTHERS }

@Serializable
enum class FactionAbility {
    PEEK_RANDOM_CARD,
    START_SALARY_BONUS,
    DOUBLE_SUSPECT,
    FREE_JAIL_EXIT,
    PURCHASE_DISCOUNT,
    TELEGRAM_DRAW_TWO,
}

@Serializable
enum class GameMode { STANDARD, QUICK, FAMILY, CAMPAIGN }

@Serializable
enum class WarClockMode { FIRST_PASS_PER_ROUND, EVERY_ROUND }

@Serializable
enum class BotLevel { EASY, NORMAL, HARD }

@Serializable
enum class WinType { DETECTIVE, ECONOMIC, WAR_END, DOUBLE_AGENT }
