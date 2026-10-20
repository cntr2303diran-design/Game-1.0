package id.diran.lisbon1944.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameMode
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.session.Lang
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.Screen
import id.diran.lisbon1944.ui.common.Dot
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.PrimaryButton
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.common.WrapGrid
import id.diran.lisbon1944.ui.theme.LisbonColors
import kotlin.random.Random

private data class SeatDraft(val name: String, val human: Boolean, val level: BotLevel, val faction: Faction)

@Composable
fun SetupScreen(vm: AppViewModel) {
    val content = vm.content ?: return
    val names = vm.names ?: return
    val defaultHuman = stringResource(R.string.setup_default_human)
    val defaultBot = stringResource(R.string.setup_default_bot)
    val seats = remember {
        mutableStateListOf(
            SeatDraft("$defaultHuman 1", true, BotLevel.NORMAL, Faction.MI6),
            SeatDraft("$defaultBot 2", false, BotLevel.NORMAL, Faction.OSS),
            SeatDraft("$defaultBot 3", false, BotLevel.HARD, Faction.NKVD),
            SeatDraft("$defaultBot 4", false, BotLevel.NORMAL, Faction.RESISTANCE),
        )
    }
    var mode by remember { mutableStateOf(GameMode.STANDARD) }
    var doubleAgent by remember { mutableStateOf(false) }
    var seedText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Int?>(null) }

    fun setCount(n: Int) {
        while (seats.size < n) {
            val used = seats.map { it.faction }.toSet()
            val f = Faction.entries.first { it !in used }
            seats.add(SeatDraft("$defaultBot ${seats.size + 1}", false, BotLevel.NORMAL, f))
        }
        while (seats.size > n) seats.removeAt(seats.lastIndex)
        if (n < 5) doubleAgent = false
    }

    Backdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.menu_new_game), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.OldPaper)

            PaperCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.setup_players))
                WrapGrid((3..6).toList(), 4) { n, m -> ToggleTag("$n", seats.size == n, { setCount(n) }, m) }
                SectionTitle(stringResource(R.string.setup_mode))
                val modes = listOf(
                    GameMode.STANDARD to stringResource(R.string.mode_standard),
                    GameMode.QUICK to stringResource(R.string.mode_quick),
                    GameMode.FAMILY to stringResource(R.string.mode_family),
                )
                WrapGrid(modes, 3) { (gm, label), m -> ToggleTag(label, mode == gm, { mode = gm }, m) }
                Text(
                    when (mode) {
                        GameMode.QUICK -> stringResource(R.string.mode_quick_hint)
                        GameMode.FAMILY -> stringResource(R.string.mode_family_hint)
                        else -> stringResource(R.string.mode_standard_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = LisbonColors.InkSoft,
                )
                if (seats.size >= 5) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.setup_double_agent), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.setup_double_agent_hint), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft)
                        }
                        Switch(checked = doubleAgent, onCheckedChange = { doubleAgent = it })
                    }
                }
            }

            seats.forEachIndexed { i, seat ->
                val accent = Palette.faction(content, seat.faction)
                PaperCard(Modifier.fillMaxWidth(), accent = accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(accent, 14)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.setup_seat, i + 1), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(names.factionShort(seat.faction), color = accent, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = seat.name,
                        onValueChange = { v -> seats[i] = seat.copy(name = v.take(16)) },
                        label = { Text(stringResource(R.string.setup_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    val kinds = listOf(
                        Triple(true, null, stringResource(R.string.setup_human)),
                        Triple(false, BotLevel.EASY, stringResource(R.string.bot_easy)),
                        Triple(false, BotLevel.NORMAL, stringResource(R.string.bot_normal)),
                        Triple(false, BotLevel.HARD, stringResource(R.string.bot_hard)),
                    )
                    WrapGrid(kinds, 4) { (human, level, label), m ->
                        val sel = if (human) seat.human else !seat.human && seat.level == level
                        ToggleTag(label, sel, { seats[i] = seat.copy(human = human, level = level ?: seat.level) }, m)
                    }
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton(
                        names.faction(seat.faction) + if (editing == i) " ▲" else " ▼",
                        { editing = if (editing == i) null else i },
                    )
                    if (editing == i) {
                        Spacer(Modifier.height(6.dp))
                        val taken = seats.filterIndexed { j, _ -> j != i }.map { it.faction }.toSet()
                        content.factions.forEach { def ->
                            val free = def.id !in taken
                            PaperCard(Modifier.fillMaxWidth().padding(vertical = 3.dp), accent = Palette.faction(content, def.id)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${def.organization} — ${names.faction(def.id)}", fontWeight = FontWeight.Bold)
                                        Text(if (vm.lang == Lang.EN) def.abilityTextEn else def.abilityText, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                ToggleTag(
                                    if (free) stringResource(R.string.setup_pick) else stringResource(R.string.setup_taken),
                                    seat.faction == def.id,
                                    { seats[i] = seat.copy(faction = def.id); editing = null },
                                    Modifier.fillMaxWidth(),
                                    enabled = free,
                                )
                            }
                        }
                    }
                }
            }

            PaperCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = seedText,
                    onValueChange = { v -> seedText = v.filter { it.isDigit() }.take(12) },
                    label = { Text(stringResource(R.string.setup_seed)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.setup_seed_hint), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft)
            }

            val config = GameConfig(
                players = seats.mapIndexed { i, s ->
                    PlayerSetup(
                        name = s.name.ifBlank { "P${i + 1}" },
                        faction = s.faction,
                        isBot = !s.human,
                        botLevel = if (s.human) null else s.level,
                    )
                },
                balance = content.balance,
                mode = mode,
                doubleAgentVariant = doubleAgent && seats.size >= 5,
            )
            val problems = config.validate()
            problems.firstOrNull()?.let { Text(it, color = LisbonColors.FolderRed) }
            if (seats.none { it.human }) Text(stringResource(R.string.setup_spectate), color = LisbonColors.StreetLamp)
            PrimaryButton(
                stringResource(R.string.setup_start),
                { vm.newGame(config, seedText.toLongOrNull() ?: Random.nextLong(1, 1_000_000_000)) },
                enabled = problems.isEmpty() && !vm.busy,
            )
            SecondaryButton(stringResource(R.string.back), { vm.go(Screen.MENU) })
            Spacer(Modifier.height(24.dp))
        }
    }
}
