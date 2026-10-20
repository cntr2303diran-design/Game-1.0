package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.GameState

sealed interface ReduceResult {
    data class Ok(val state: GameState, val events: List<GameEvent>) : ReduceResult
    data class Rejected(val reason: RuleViolation) : ReduceResult
}

enum class ViolationCode {
    GAME_OVER,
    NO_DECISION,
    NOT_YOUR_DECISION,
    WRONG_DECISION,
    INSUFFICIENT_FUNDS,
    INVALID_AMOUNT,
    INVALID_CARD,
    INVALID_TARGET,
    CARD_NOT_ALLOWED,
    IN_JAIL,
    SUGGESTION_BLOCKED,
    LOCATION_NOT_ALLOWED,
    ABILITY_UNAVAILABLE,
    NO_JAIL_CARD,
    INFO_RENT_TOO_HIGH,
    INFO_QUOTA_EXHAUSTED,
    INFO_EMPTY_HAND,
    NOT_OWNER,
    NOT_PROPERTY,
    BUILD_NOT_CELL,
    BUILD_MORTGAGED,
    BUILD_MAX_LEVEL,
    BUILD_UNEVEN,
    BUILD_NO_STOCK,
    SELL_NOTHING,
    SELL_UNEVEN,
    SELL_NO_HOUSE_STOCK,
    ALREADY_MORTGAGED,
    NOT_MORTGAGED,
    DISTRICT_HAS_BUILDINGS,
    ROUTE_NOT_AVAILABLE,
    ACCUSE_NOT_ALLOWED,
    ACCUSE_LOCKED,
    NOT_ENOUGH_INTEL,
    ALREADY_BURNED,
    CAN_STILL_RAISE_FUNDS,
    INVALID_TRADE,
    TACTIC_NOT_ALLOWED,
}

data class RuleViolation(val code: ViolationCode, val message: String) {
    override fun toString(): String = "$code: $message"
}
