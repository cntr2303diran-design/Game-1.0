package id.diran.lisbon1944.session

import id.diran.lisbon1944.bot.Bots
import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerSetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChoicesTest {
    private val content = ContentLoader.loadDefault()
    private val engine = GameEngine(content)
    private val bots = Bots(content)

    @Test
    fun boardCellsAreUniqueAndOnTheRim() {
        val cells = (0 until 40).map(BoardGeometry::cell)
        assertEquals(40, cells.map { it.row to it.col }.toSet().size)
        assertTrue(cells.all { it.row == 0 || it.row == 10 || it.col == 0 || it.col == 10 })
        assertEquals(Cell(10, 10, Side.BOTTOM, true), BoardGeometry.cell(0))
        assertEquals(Cell(0, 0, Side.TOP, true), BoardGeometry.cell(20))
        assertEquals(listOf(38, 39, 0, 1), BoardGeometry.path(37, 4))
        assertEquals(listOf(1, 0, 39), BoardGeometry.path(2, -3))
    }

    @Test
    fun menusCoverEveryLegalActionDuringABotGame() {
        val config = GameConfig(
            players = (0 until 4).map { PlayerSetup("Bot $it", Faction.entries[it + 1], isBot = true, botLevel = BotLevel.NORMAL) } ,
            balance = content.balance,
        )
        val s = GameSession.newGame(engine, bots, config, seed = 77)
        var sawSuggest = false
        var sawBusiness = false
        var guard = 0
        while (!s.isOver && guard++ < 3_000) {
            val d = s.decider ?: break
            val legal = engine.legalActions(s.state, d)
            when (s.state.pending) {
                is PendingDecision.SuggestionOffer -> {
                    val menu = assertNotNull(Choices.suggestionMenu(legal))
                    for (a in legal.filterIsInstance<Action.Suggest>()) assertEquals(a, menu.find(a.suspects.reversed(), a.item, a.location))
                    sawSuggest = true
                }
                is PendingDecision.BusinessPhase -> {
                    val ops = Choices.tileOps(legal)
                    val count = ops.values.sumOf { listOfNotNull(it.build, it.sell, it.mortgage, it.unmortgage).size }
                    assertEquals(legal.count { it is Action.Build || it is Action.SellBuilding || it is Action.Mortgage || it is Action.Unmortgage }, count)
                    sawBusiness = true
                }
                is PendingDecision.AuctionBid -> assertEquals(legal.count { it is Action.Bid }, Choices.bidAmounts(legal).size)
                else -> Unit
            }
            s.stepBot()
        }
        assertTrue(sawSuggest && sawBusiness)
    }
}

class CampaignPresetTest {
    private val content = ContentLoader.loadDefault()

    @Test
    fun everySeatGetsTwoPropertiesFromDifferentDistrictsAndGameRuns() {
        for (n in 3..6) {
            val preset = CampaignPreset.deal(content, n, 99)
            assertEquals(n * 2, preset.size)
            for (seat in 0 until n) {
                val ds = preset.filterValues { it == seat }.keys.map { t -> content.properties.first { it.index == t }.district }
                assertEquals(2, ds.toSet().size)
            }
            val config = GameConfig(
                players = (0 until n).map { PlayerSetup("B$it", Faction.entries[it], isBot = true, botLevel = BotLevel.EASY) },
                balance = content.balance,
                mode = id.diran.lisbon1944.engine.model.GameMode.CAMPAIGN,
                presetOwnership = preset,
            )
            assertTrue(config.validate().isEmpty())
            val s = GameEngine(content).newGame(config, 5)
            assertEquals(n * 2, s.ownership.size)
        }
    }
}
