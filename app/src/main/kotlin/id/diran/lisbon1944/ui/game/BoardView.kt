package id.diran.lisbon1944.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import id.diran.lisbon1944.session.RentTable
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.districtPattern
import id.diran.lisbon1944.ui.theme.LisbonColors
import id.diran.lisbon1944.ui.theme.LocalUiPrefs
import id.diran.lisbon1944.ui.theme.PosterFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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

/** Posisi pion yang ditampilkan: berjalan petak demi petak menuju posisi sebenarnya. */
@Composable
private fun rememberWalkingPawns(view: PlayerView, reduceMotion: Boolean, onStep: () -> Unit): Map<Int, Int> {
    val target = buildMap {
        put(view.me.id.value, view.me.position)
        view.opponents.filter { !it.bankrupt }.forEach { put(it.id.value, it.position) }
    }
    val shown = remember { mutableStateMapOf<Int, Int>() }
    if (shown.isEmpty()) shown.putAll(target)
    LaunchedEffect(target) {
        shown.keys.filter { it !in target }.forEach { shown.remove(it) }
        for ((seat, to) in target) {
            val from = shown[seat] ?: to
            if (from == to) continue
            val fwd = ((to - from) % BoardGeometry.TILES + BoardGeometry.TILES) % BoardGeometry.TILES
            val path = when {
                reduceMotion -> listOf(to)
                fwd in 1..12 -> BoardGeometry.path(from, fwd)
                BoardGeometry.TILES - fwd in 1..3 -> BoardGeometry.path(from, -(BoardGeometry.TILES - fwd))
                else -> listOf(to) // dipindahkan (penjara, jalur pelarian, kartu)
            }
            for (t in path) {
                shown[seat] = t
                if (path.size > 1) {
                    onStep()
                    delay(110)
                }
            }
        }
    }
    return target.keys.associateWith { shown[it] ?: target.getValue(it) }
}

@Composable
fun BoardView(
    view: PlayerView,
    content: GameContent,
    names: Names,
    modifier: Modifier = Modifier,
    highlight: Set<Int> = emptySet(),
    shakeKey: Int = 0,
    onPawnStep: () -> Unit = {},
    onTileTap: ((Int) -> Unit)? = null,
) {
    val prefs = LocalUiPrefs.current
    var scale by remember { mutableFloatStateOf(1f) }
    var manualPan by remember { mutableStateOf(Offset.Zero) }
    var follow by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val factions = seatFactions(view)
    val shownPos = rememberWalkingPawns(view, prefs.reduceMotion, onPawnStep)
    val positions = shownPos.entries.groupBy({ it.value }, { it.key })

    // Papan bergetar saat Razia/penangkapan.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey != 0 && !prefs.reduceMotion) {
            for (k in 0 until 8) shake.animateTo(if (k % 2 == 0) 6f else -6f, tween(40))
            shake.animateTo(0f, tween(60))
        }
    }

    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .clipToBounds()
            .background(Brush.radialGradient(listOf(LisbonColors.TagusBlue, Color(0xFF1B2E3F))))
            .pointerInput(Unit) {
                detectTransformGestures { _, p, zoom, _ ->
                    val s = (scale * zoom).coerceIn(1f, 3.2f)
                    scale = s
                    if (p != Offset.Zero) follow = false
                    val limit = size.width * (s - 1f) / 2f
                    manualPan = Offset((manualPan.x + p.x).coerceIn(-limit, limit), (manualPan.y + p.y).coerceIn(-limit, limit))
                }
            },
    ) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        val cell: Dp = side / BoardGeometry.GRID
        val sidePx = with(LocalDensity.current) { side.toPx() }
        // Kamera mengikuti pion aktif saat diperbesar.
        val active = shownPos[view.currentPlayer.value] ?: view.me.position
        val ac = BoardGeometry.cell(active)
        val cellPx = sidePx / BoardGeometry.GRID
        val limit = sidePx * (scale - 1f) / 2f
        val followPan = Offset(
            (-((ac.col + 0.5f) * cellPx - sidePx / 2f) * scale).coerceIn(-limit, limit),
            (-((ac.row + 0.5f) * cellPx - sidePx / 2f) * scale).coerceIn(-limit, limit),
        )
        val animatedFollow by animateOffsetAsState(followPan, tween(400), label = "camera")
        val pan = if (follow && scale > 1.01f) animatedFollow else manualPan

        Box(
            Modifier
                .size(side)
                .offset { IntOffset(shake.value.roundToInt(), 0) }
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
                    colorBlind = prefs.colorBlind,
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
        Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (scale > 1.01f) {
                MiniChip(if (follow) "◎" else "○", stringResource(R.string.board_follow)) { follow = !follow }
                MiniChip("⤢", stringResource(R.string.board_reset)) {
                    scale = 1f
                    manualPan = Offset.Zero
                    follow = true
                }
            } else {
                MiniChip("＋", stringResource(R.string.board_zoom)) { scale = 2f; follow = true }
            }
        }
    }
}

@Composable
private fun MiniChip(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(LisbonColors.Ink.copy(alpha = 0.78f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = LisbonColors.OldPaper, fontSize = 16.sp) }
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
    colorBlind: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val own = view.ownership[tile.index]
    val sealed = view.sealedTiles.containsKey(tile.index)
    val ownerName = own?.let { o -> if (o.ownerId == view.me.id) view.me.name else view.opponents.firstOrNull { it.id == o.ownerId }?.name }
    val ownerColor = own?.let { Palette.faction(content, factions[it.ownerId.value] ?: Faction.MI6) }
    val pawnNames = pawns.mapNotNull { s -> if (s == view.me.id.value) view.me.name else view.opponents.firstOrNull { it.id.value == s }?.name }
    val desc = buildString {
        append(names.tile(tile.index))
        if (tile is PropertyTile) append(", ").append(names.district(tile.district))
        if (ownerName != null) append(", ").append(ownerName)
        if (own != null && own.level in 1..4) append(", ").append(own.level).append(" ⌂")
        if (own != null && own.level >= 5) append(", HQ")
        if (own?.mortgaged == true) append(", ").append("M")
        if (pawnNames.isNotEmpty()) append(". ").append(pawnNames.joinToString())
    }
    Box(
        modifier
            .padding(0.5.dp)
            .background(if (own?.mortgaged == true) LisbonColors.PaperShade else LisbonColors.OldPaper)
            .border(if (highlighted) 2.dp else 0.5.dp, if (highlighted) LisbonColors.StreetLamp else LisbonColors.InkSoft.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc },
    ) {
        Column(Modifier.fillMaxSize()) {
            when (tile) {
                is PropertyTile -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (colorBlind) 8.dp else 6.dp)
                        .background(Palette.district(tile.district))
                        .then(if (colorBlind) Modifier.districtPattern(tile.district, LisbonColors.OldPaper) else Modifier),
                ) {
                    if (colorBlind) Text(Palette.districtIcon(tile.district), fontSize = 6.sp, lineHeight = 6.sp, color = LisbonColors.OldPaper, modifier = Modifier.align(Alignment.Center))
                }
                is EscapeRouteTile -> Box(Modifier.fillMaxWidth().height(6.dp).background(LisbonColors.TagusBlue))
                is SpecialTile -> Unit
            }
            if (tile is SpecialTile) {
                Text(specialGlyph(tile.kind), fontSize = 11.sp, color = LisbonColors.FolderRed, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else if (tile is EscapeRouteTile) {
                Text("⛴", fontSize = 9.sp, color = LisbonColors.TagusBlue, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                    own.level > 0 -> "⌂".repeat(own.level)
                    else -> "⚑"
                }
                Box(Modifier.fillMaxWidth().height(8.dp).background(ownerColor ?: LisbonColors.Ink)) {
                    Text(marks, fontSize = 6.sp, lineHeight = 7.sp, color = LisbonColors.OldPaper, modifier = Modifier.align(Alignment.Center))
                }
            } else if (tile is PropertyTile || tile is EscapeRouteTile) {
                Text(
                    names.money((tile as? PropertyTile)?.price ?: (tile as EscapeRouteTile).price).removeSuffix(" esc"),
                    fontSize = 5.sp,
                    lineHeight = 6.sp,
                    color = LisbonColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (own?.mortgaged == true) {
            Text(
                stringResource(R.string.stamp_mortgaged),
                fontSize = 5.sp,
                lineHeight = 6.sp,
                color = LisbonColors.FolderRed,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center).rotate(-28f).border(0.5.dp, LisbonColors.FolderRed).padding(horizontal = 1.dp),
            )
        }
        if (sealed) Text("⛔", fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd))
        if (view.patrolPos == tile.index) PawnToken("P", LisbonColors.Ink, Modifier.align(Alignment.CenterStart))
        if (view.contactPos == tile.index) PawnToken("K", LisbonColors.Olive, Modifier.align(Alignment.CenterEnd))
        if (pawns.isNotEmpty()) {
            Row(Modifier.align(Alignment.Center).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                pawns.forEach { seat ->
                    val color = Palette.faction(content, factions[seat] ?: Faction.MI6)
                    val isTurn = seat == view.currentPlayer.value
                    Box(
                        Modifier
                            .size(if (isTurn) 12.dp else 9.dp)
                            .then(if (isTurn) Modifier.background(Brush.radialGradient(listOf(LisbonColors.StreetLamp.copy(alpha = 0.7f), Color.Transparent)), CircleShape) else Modifier)
                            .padding(if (isTurn) 1.5.dp else 0.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, if (seat == view.me.id.value) LisbonColors.StreetLamp else LisbonColors.OldPaper, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (LocalUiPrefs.current.colorBlind) {
                            Text(factions[seat]?.name?.take(1).orEmpty(), fontSize = 5.sp, lineHeight = 5.sp, color = LisbonColors.OldPaper)
                        }
                    }
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
        if (selected != null) {
            TileDetail(selected, view, content, names, factions, onClear)
            return@Column
        }
        val front = runCatching { content.front(view.activeFront) }.getOrNull()
        Text(stringResource(R.string.title_main), color = LisbonColors.OldPaper, fontFamily = PosterFamily, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 3.sp)
        Text(stringResource(R.string.hud_war_clock, view.warStep), color = LisbonColors.StreetLamp, fontSize = 11.sp)
        WarTrack(view.warStep)
        if (front != null) {
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(LisbonColors.OldPaper).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(front.month.uppercase(), color = LisbonColors.FolderRed, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(names.front(front.id.value), color = LisbonColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                Text(names.frontText(front.id.value), color = LisbonColors.InkMuted, fontSize = 8.sp, lineHeight = 10.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        view.passesToNextStep?.let {
            Text(stringResource(R.string.hud_passes, it), color = LisbonColors.PaperMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DeckPile("⌗", view.cipherDeckSize, LisbonColors.Olive)
            DeckPile("✉", view.telegramDeckSize, LisbonColors.TagusBlue)
            VaultBadge(view.casinoVault, names)
        }
    }
}

@Composable
private fun WarTrack(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..12) {
            Box(
                Modifier
                    .size(width = 9.dp, height = 12.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        when {
                            i < step -> LisbonColors.Olive
                            i == step -> LisbonColors.FolderRed
                            else -> LisbonColors.OldPaper.copy(alpha = 0.18f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun DeckPile(glyph: String, count: Int, color: Color) {
    Column(
        Modifier.clip(RoundedCornerShape(3.dp)).background(color).padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, color = LisbonColors.OldPaper, fontSize = 10.sp)
        Text("$count", color = LisbonColors.OldPaper, fontSize = 9.sp)
    }
}

/** Brankas Kasino — angka berdenyut saat bertambah. */
@Composable
private fun VaultBadge(amount: Int, names: Names) {
    var last by remember { mutableIntStateOf(amount) }
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(amount) {
        if (amount > last) {
            pulse = true
            delay(450)
            pulse = false
        }
        last = amount
    }
    val s by animateFloatAsState(if (pulse) 1.25f else 1f, tween(220), label = "vault")
    Column(
        Modifier.scale(s).clip(RoundedCornerShape(3.dp)).background(LisbonColors.StreetLamp).padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⚅", color = LisbonColors.Ink, fontSize = 10.sp)
        Text(names.money(amount).removeSuffix(" esc"), color = LisbonColors.Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
        if (tile is PropertyTile) Box(Modifier.fillMaxWidth().height(8.dp).background(Palette.district(tile.district)).districtPattern(tile.district, LisbonColors.OldPaper))
        Text(names.tile(index), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LisbonColors.Ink)
        val small = 11.sp
        when (tile) {
            is PropertyTile -> {
                Text("${Palette.districtIcon(tile.district)} ${names.district(tile.district)}", fontSize = small, color = LisbonColors.InkMuted)
                Text(stringResource(R.string.tile_price, names.money(tile.price)), fontSize = small, color = LisbonColors.Ink)
                val table = RentTable.property(tile, content.balance)
                val labels = listOf(
                    stringResource(R.string.rent_base), stringResource(R.string.rent_district),
                    "⌂", "⌂⌂", "⌂⌂⌂", "⌂⌂⌂⌂", stringResource(R.string.tile_hq),
                )
                val level = own?.level
                table.forEachIndexed { i, r ->
                    val current = when (level) {
                        null -> false
                        0 -> i == 0 || i == 1
                        else -> i == level + 1
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(labels[i], fontSize = 10.sp, color = LisbonColors.InkMuted, modifier = Modifier.weight(1f))
                        Text(names.money(r), fontSize = 10.sp, color = if (current) LisbonColors.FolderRed else LisbonColors.Ink, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                frontNote(view, content, names, tile)?.let { Text(it, fontSize = 10.sp, color = LisbonColors.FolderRed) }
            }
            is EscapeRouteTile -> {
                Text(stringResource(R.string.tile_escape), fontSize = small, color = LisbonColors.InkMuted)
                Text(stringResource(R.string.tile_price, names.money(tile.price)), fontSize = small, color = LisbonColors.Ink)
                RentTable.escape(tile, content.balance).forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${i + 1} ⛴", fontSize = 10.sp, color = LisbonColors.InkMuted, modifier = Modifier.weight(1f))
                        Text(names.money(r), fontSize = 10.sp, color = LisbonColors.Ink)
                    }
                }
            }
            is SpecialTile -> Text(specialHelp(tile.kind), fontSize = small, color = LisbonColors.InkMuted)
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

/** Catatan bila Front aktif menyebut distrik petak ini. */
private fun frontNote(view: PlayerView, content: GameContent, names: Names, tile: PropertyTile): String? {
    val front = runCatching { content.front(view.activeFront) }.getOrNull() ?: return null
    val text = names.frontText(front.id.value)
    val d = names.district(tile.district)
    return if (text.contains(d, ignoreCase = true)) "Front: ${names.front(front.id.value)}" else null
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

