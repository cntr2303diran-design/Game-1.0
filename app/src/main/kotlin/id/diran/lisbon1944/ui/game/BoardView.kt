package id.diran.lisbon1944.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.SpecialKind
import id.diran.lisbon1944.engine.model.SpecialTile
import id.diran.lisbon1944.engine.model.Tile
import id.diran.lisbon1944.session.BoardGeometry
import id.diran.lisbon1944.session.Names
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.PipMeter
import id.diran.lisbon1944.ui.theme.LisbonColors

/** Warna faksi tiap kursi menurut PlayerView. */
fun seatFactions(view: PlayerView): Map<Int, Faction> =
    (listOf(view.me.id.value to view.me.faction) + view.opponents.map { it.id.value to it.faction }).toMap()

fun specialGlyph(kind: SpecialKind): String = when (kind) {
    SpecialKind.START -> "⚓"
    SpecialKind.TELEGRAM -> "✉"
    SpecialKind.CIPHER -> "⌗"
    SpecialKind.WAR_TAX -> "⚖"
    SpecialKind.JAIL -> "▦"
    SpecialKind.RADIO -> "📻"
    SpecialKind.TELEGRAPH -> "⌁"
    SpecialKind.INTERROGATION -> "?"
    SpecialKind.CASINO -> "⚅"
    SpecialKind.BLACK_MARKET -> "☾"
    SpecialKind.ARREST -> "⚠"
    SpecialKind.BRIBE -> "¤"
    SpecialKind.EMBASSY -> "⚑"
}

@Composable
fun BoardView(
    view: PlayerView,
    content: GameContent,
    names: Names,
    modifier: Modifier = Modifier,
    highlight: Set<Int> = emptySet(),
    onTileTap: ((Int) -> Unit)? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val factions = seatFactions(view)
    val positions = buildMap<Int, MutableList<Int>> {
        getOrPut(view.me.position) { mutableListOf() }.add(view.me.id.value)
        view.opponents.filter { !it.bankrupt }.forEach { getOrPut(it.position) { mutableListOf() }.add(it.id.value) }
    }

    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .clipToBounds()
            .background(LisbonColors.TagusBlue)
            .pointerInput(Unit) {
                detectTransformGestures { _, p, zoom, _ ->
                    val s = (scale * zoom).coerceIn(1f, 3.2f)
                    scale = s
                    val limit = size.width * (s - 1f) / 2f
                    pan = Offset((pan.x + p.x).coerceIn(-limit, limit), (pan.y + p.y).coerceIn(-limit, limit))
                }
            },
    ) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        val cell: Dp = side / BoardGeometry.GRID
        Box(
            Modifier
                .size(side)
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y),
        ) {
            for (i in 0 until BoardGeometry.TILES) {
                val c = BoardGeometry.cell(i)
                TileBox(
                    tile = content.board[i],
                    view = view,
                    content = content,
                    names = names,
                    factions = factions,
                    pawns = positions[i].orEmpty(),
                    highlighted = i in highlight || i == selected,
                    modifier = Modifier
                        .offset(x = cell * c.col, y = cell * c.row)
                        .size(cell),
                    onClick = {
                        selected = if (selected == i) null else i
                        onTileTap?.invoke(i)
                    },
                )
            }
            Box(
                Modifier
                    .offset(x = cell, y = cell)
                    .size(cell * 9)
                    .padding(4.dp),
            ) {
                BoardCenter(view, content, names, selected, factions, onClear = { selected = null })
            }
        }
        if (scale > 1.01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(LisbonColors.Ink.copy(alpha = 0.75f))
                    .clickable { scale = 1f; pan = Offset.Zero }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("⤢ 1×", color = LisbonColors.OldPaper, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun TileBox(
    tile: Tile,
    view: PlayerView,
    content: GameContent,
    names: Names,
    factions: Map<Int, Faction>,
    pawns: List<Int>,
    highlighted: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val own = view.ownership[tile.index]
    val sealed = view.sealedTiles.containsKey(tile.index)
    val ownerColor = own?.let { Palette.faction(content, factions[it.ownerId.value] ?: Faction.MI6) }
    Box(
        modifier
            .padding(0.5.dp)
            .background(if (own?.mortgaged == true) LisbonColors.PaperShade else LisbonColors.OldPaper)
            .border(if (highlighted) 2.dp else 0.5.dp, if (highlighted) LisbonColors.StreetLamp else LisbonColors.InkSoft.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize()) {
            when (tile) {
                is PropertyTile -> Box(Modifier.fillMaxWidth().height(5.dp).background(Palette.district(tile.district)))
                is EscapeRouteTile -> Box(Modifier.fillMaxWidth().height(5.dp).background(LisbonColors.Ink))
                is SpecialTile -> Unit
            }
            if (tile is SpecialTile) {
                Text(
                    specialGlyph(tile.kind),
                    fontSize = 11.sp,
                    color = LisbonColors.FolderRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                names.tile(tile.index),
                fontSize = 6.sp,
                lineHeight = 7.sp,
                color = LisbonColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp),
            )
            Spacer(Modifier.weight(1f))
            if (own != null) {
                val marks = when {
                    own.level >= 5 -> "★"
                    own.level > 0 -> "▪".repeat(own.level)
                    else -> ""
                }
                Box(Modifier.fillMaxWidth().height(7.dp).background(ownerColor ?: LisbonColors.Ink)) {
                    Text(marks + if (own.mortgaged) "G" else "", fontSize = 5.sp, lineHeight = 6.sp, color = LisbonColors.OldPaper, modifier = Modifier.align(Alignment.Center))
                }
            } else if (tile is PropertyTile || tile is EscapeRouteTile) {
                Text(
                    names.money((tile as? PropertyTile)?.price ?: (tile as EscapeRouteTile).price).removeSuffix(" esc"),
                    fontSize = 5.sp,
                    lineHeight = 6.sp,
                    color = LisbonColors.InkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (sealed) Text("⛔", fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd))
        if (view.patrolPos == tile.index) PawnToken("P", LisbonColors.Ink, Modifier.align(Alignment.CenterStart))
        if (view.contactPos == tile.index) PawnToken("K", LisbonColors.Olive, Modifier.align(Alignment.CenterEnd))
        if (pawns.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.Center).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                pawns.forEach { seat ->
                    val color = Palette.faction(content, factions[seat] ?: Faction.MI6)
                    Box(
                        Modifier
                            .size(if (seat == view.currentPlayer.value) 11.dp else 9.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, if (seat == view.me.id.value) LisbonColors.StreetLamp else LisbonColors.OldPaper, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun PawnToken(label: String, color: Color, modifier: Modifier) {
    Box(
        modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 6.sp, lineHeight = 6.sp, color = LisbonColors.OldPaper, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BoardCenter(
    view: PlayerView,
    content: GameContent,
    names: Names,
    selected: Int?,
    factions: Map<Int, Faction>,
    onClear: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(LisbonColors.Ink.copy(alpha = 0.35f))
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val front = runCatching { content.front(view.activeFront) }.getOrNull()
        Text(stringResource(R.string.title_main), color = LisbonColors.OldPaper, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 3.sp)
        Text(stringResource(R.string.hud_war_clock, view.warStep), color = LisbonColors.StreetLamp, fontSize = 11.sp)
        PipMeter(view.warStep, 12, LisbonColors.FolderRed, pip = 6)
        if (front != null) {
            Spacer(Modifier.height(4.dp))
            Text("${front.month} — ${names.front(front.id.value)}", color = LisbonColors.OldPaper, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        view.passesToNextStep?.let {
            Text(stringResource(R.string.hud_passes, it), color = LisbonColors.PaperShade, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(6.dp))
        if (selected != null) {
            TileDetail(selected, view, content, names, factions, onClear)
        } else {
            Text(stringResource(R.string.board_tap_hint), color = LisbonColors.PaperShade.copy(alpha = 0.8f), fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TileDetail(index: Int, view: PlayerView, content: GameContent, names: Names, factions: Map<Int, Faction>, onClear: () -> Unit) {
    val tile = content.board[index]
    val own = view.ownership[index]
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(LisbonColors.OldPaper)
            .clickable(onClick = onClear)
            .padding(8.dp),
    ) {
        if (tile is PropertyTile) Box(Modifier.fillMaxWidth().height(6.dp).background(Palette.district(tile.district)))
        Text(names.tile(index), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LisbonColors.Ink)
        val small = 11.sp
        when (tile) {
            is PropertyTile -> {
                Text(names.district(tile.district), fontSize = small, color = LisbonColors.InkSoft)
                Text(stringResource(R.string.tile_price, names.money(tile.price)), fontSize = small, color = LisbonColors.Ink)
                Text(stringResource(R.string.tile_rent, names.money(tile.baseRent), names.money(tile.hqRent)), fontSize = small, color = LisbonColors.Ink)
            }
            is EscapeRouteTile -> {
                Text(stringResource(R.string.tile_escape), fontSize = small, color = LisbonColors.InkSoft)
                Text(stringResource(R.string.tile_price, names.money(tile.price)), fontSize = small, color = LisbonColors.Ink)
            }
            is SpecialTile -> Text(specialHelp(tile.kind), fontSize = small, color = LisbonColors.InkSoft)
        }
        if (own != null) {
            val ownerName = if (own.ownerId == view.me.id) view.me.name else view.opponents.firstOrNull { it.id == own.ownerId }?.name.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.faction(content, factions[own.ownerId.value] ?: Faction.MI6)))
                Text(
                    " " + stringResource(R.string.tile_owner, ownerName) +
                        (if (own.level in 1..4) " · " + stringResource(R.string.tile_houses, own.level) else "") +
                        (if (own.level >= 5) " · " + stringResource(R.string.tile_hq) else "") +
                        (if (own.mortgaged) " · " + stringResource(R.string.tile_mortgaged) else ""),
                    fontSize = small,
                    color = LisbonColors.Ink,
                )
            }
        }
        view.sealedTiles[index]?.let { Text(stringResource(R.string.tile_sealed, it), fontSize = small, color = LisbonColors.FolderRed) }
        if (view.patrolPos == index) Text(stringResource(R.string.tile_patrol), fontSize = small, color = LisbonColors.FolderRed)
        if (view.contactPos == index) Text(stringResource(R.string.tile_contact), fontSize = small, color = LisbonColors.Olive)
    }
}

@Composable
fun specialHelp(kind: SpecialKind): String = when (kind) {
    SpecialKind.START -> stringResource(R.string.help_start)
    SpecialKind.TELEGRAM -> stringResource(R.string.help_telegram)
    SpecialKind.CIPHER -> stringResource(R.string.help_cipher)
    SpecialKind.WAR_TAX -> stringResource(R.string.help_war_tax)
    SpecialKind.JAIL -> stringResource(R.string.help_jail)
    SpecialKind.RADIO -> stringResource(R.string.help_radio)
    SpecialKind.TELEGRAPH -> stringResource(R.string.help_telegraph)
    SpecialKind.INTERROGATION -> stringResource(R.string.help_interrogation)
    SpecialKind.CASINO -> stringResource(R.string.help_casino)
    SpecialKind.BLACK_MARKET -> stringResource(R.string.help_black_market)
    SpecialKind.ARREST -> stringResource(R.string.help_arrest)
    SpecialKind.BRIBE -> stringResource(R.string.help_bribe)
    SpecialKind.EMBASSY -> stringResource(R.string.help_embassy)
}
