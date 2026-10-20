package id.diran.lisbon1944.bot

import id.diran.lisbon1944.bot.deduction.DeductionTracker
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.BotLevel

/** Pabrik bot. Satu [DeductionTracker] dipakai bersama karena tak menyimpan keadaan. */
class Bots(private val content: GameContent, private val config: BotConfig = BotConfig.loadDefault()) {
    private val tracker = DeductionTracker(content)

    fun create(level: BotLevel): Bot = StrategicBot(level, config.params(level), content, tracker)
}
