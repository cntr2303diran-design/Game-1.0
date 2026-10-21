package id.diran.lisbon1944.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.session.Cue
import id.diran.lisbon1944.session.Names
import id.diran.lisbon1944.session.StampKind
import id.diran.lisbon1944.ui.theme.LisbonColors
import id.diran.lisbon1944.ui.theme.PosterFamily
import id.diran.lisbon1944.ui.theme.TypewriterFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lapisan animasi: memainkan isyarat dari antrean satu per satu (dadu berguling, uang melayang, kartu dibalik,
 * pita teleks Front, cap TERBAKAR/TERPECAHKAN, hujan koin jackpot). Ketuk untuk melewati.
 */
@Composable
fun BoxScope.CueLayer(cue: Cue?, names: Names, playerName: (Int?) -> String, onDone: () -> Unit) {
    if (cue == null) return
    val skip = remember(cue) { mutableStateOf(false) }
    Box(
        Modifier
            .matchParentSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skip.value = true }
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        when (cue) {
            is Cue.Dice -> DiceCue(cue, playerName(cue.player))
            is Cue.Money -> MoneyCue(cue, names, playerName)
            is Cue.Front -> FrontCue(cue, names)
            is Cue.CardDrawn -> CardCue(cue, names, playerName(cue.player))
            is Cue.Stamp -> StampCue(cue, playerName(cue.player))
            is Cue.Jackpot -> JackpotCue(cue, names, playerName(cue.player))
            is Cue.Tactic -> TacticCue(cue, names, playerName(cue.player))
            else -> Unit
        }
    }
    val duration = when (cue) {
        is Cue.Dice -> 900L
        is Cue.Money -> 900L
        is Cue.Front -> 3200L
        is Cue.CardDrawn -> 1900L
        is Cue.Stamp -> 1700L
        is Cue.Jackpot -> 1800L
        is Cue.Tactic -> 1500L
        else -> 10L
    }
    LaunchedEffect(cue) {
        val step = 50L
        var t = 0L
        while (t < duration && !skip.value) {
            delay(step)
            t += step
        }
        onDone()
    }
}

private val pipLayout = mapOf(
    1 to listOf(1 to 1),
    2 to listOf(0 to 0, 2 to 2),
    3 to listOf(0 to 0, 1 to 1, 2 to 2),
    4 to listOf(0 to 0, 0 to 2, 2 to 0, 2 to 2),
    5 to listOf(0 to 0, 0 to 2, 1 to 1, 2 to 0, 2 to 2),
    6 to listOf(0 to 0, 0 to 2, 1 to 0, 1 to 2, 2 to 0, 2 to 2),
)

@Composable
fun DieFace(value: Int, size: Int = 56, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LisbonColors.OldPaper)
            .border(2.dp, LisbonColors.Ink, RoundedCornerShape(8.dp)),
    ) {
        val cell = this.size.width / 3f
        val r = cell * 0.26f
        pipLayout[value.coerceIn(1, 6)].orEmpty().forEach { (row, col) ->
            drawCircle(if (value == 1) LisbonColors.FolderRed else LisbonColors.Ink, r, Offset(cell * (col + 0.5f), cell * (row + 0.5f)))
        }
    }
}

@Composable
private fun DiceCue(cue: Cue.Dice, who: String) {
    var a by remember { mutableIntStateOf(1) }
    var b by remember { mutableIntStateOf(1) }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(cue) {
        launch { spin.animateTo(720f, tween(500, easing = FastOutSlowInEasing)) }
        val rnd = Random(cue.hashCode())
        repeat(8) {
            a = rnd.nextInt(1, 7)
            b = rnd.nextInt(1, 7)
            delay(55)
        }
        a = cue.d1
        b = cue.d2
    }
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).background(LisbonColors.Ink.copy(alpha = 0.82f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(who, color = LisbonColors.OldPaper, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            DieFace(a, modifier = Modifier.rotate(spin.value))
            DieFace(b, modifier = Modifier.rotate(-spin.value))
        }
        Text("${cue.d1 + cue.d2}" + if (cue.d1 == cue.d2) " ×2" else "", color = LisbonColors.StreetLamp, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MoneyCue(cue: Cue.Money, names: Names, playerName: (Int?) -> String) {
    // Uang "melayang" dari pembayar (kiri) ke penerima (kanan).
    val p = remember { Animatable(0f) }
    LaunchedEffect(cue) { p.animateTo(1f, tween(800, easing = LinearEasing)) }
    val from = cue.from?.let { playerName(it) } ?: stringResource(R.string.cue_bank)
    val to = cue.to?.let { playerName(it) } ?: stringResource(R.string.cue_bank)
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NameTag(from)
            NameTag(to)
        }
        Text(
            "◉ ${names.money(cue.amount)}",
            color = LisbonColors.StreetLamp,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            modifier = Modifier
                .graphicsLayer { translationX = (p.value - 0.5f) * 360f; translationY = -40f * sin(p.value * Math.PI.toFloat()) }
                .alpha(1f - (p.value - 0.85f).coerceAtLeast(0f) * 6f)
                .clip(RoundedCornerShape(12.dp))
                .background(LisbonColors.Ink.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NameTag(name: String) {
    Text(
        name,
        color = LisbonColors.OldPaper,
        fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(LisbonColors.InkSoft.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Pita teleks mengetik judul Front, lalu teks efeknya. */
@Composable
private fun FrontCue(cue: Cue.Front, names: Names) {
    val title = names.front(cue.cardId)
    var typed by remember { mutableIntStateOf(0) }
    val drop = remember { Animatable(-60f) }
    LaunchedEffect(cue) {
        launch { drop.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }
        while (typed < title.length) {
            typed++
            delay(45)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = drop.value }
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LisbonColors.OldPaper)
            .border(3.dp, LisbonColors.FolderRed, RoundedCornerShape(4.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.cue_front_label, cue.month).uppercase(), fontFamily = PosterFamily, fontWeight = FontWeight.Black, color = LisbonColors.FolderRed, fontSize = 13.sp, letterSpacing = 3.sp)
        Text(
            title.take(typed) + if (typed < title.length) "▌" else "",
            fontFamily = TypewriterFamily,
            fontSize = 22.sp,
            color = LisbonColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (typed >= title.length) {
            Text(names.frontText(cue.cardId), fontSize = 13.sp, color = LisbonColors.InkMuted, textAlign = TextAlign.Center)
        }
    }
}

/** Kartu Sandi/Telegram dibalik dengan bayangan. */
@Composable
private fun CardCue(cue: Cue.CardDrawn, names: Names, who: String) {
    val flip = remember { Animatable(0f) }
    LaunchedEffect(cue) { flip.animateTo(180f, tween(600, easing = FastOutSlowInEasing)) }
    val deck = Deck.entries.firstOrNull { it.name == cue.deck } ?: Deck.TELEGRAM
    val showFront = flip.value > 90f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(who, color = LisbonColors.OldPaper, fontSize = 13.sp, modifier = Modifier.background(LisbonColors.Ink.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 2.dp))
        Box(
            Modifier
                .padding(top = 8.dp)
                .widthIn(max = 280.dp)
                .graphicsLayer {
                    rotationY = flip.value
                    cameraDistance = 12f * density
                    shadowElevation = 16f
                }
                .clip(RoundedCornerShape(6.dp))
                .background(if (showFront) LisbonColors.OldPaper else if (deck == Deck.TELEGRAM) LisbonColors.TagusBlue else LisbonColors.Olive)
                .border(2.dp, LisbonColors.Ink, RoundedCornerShape(6.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showFront) {
                Column(Modifier.graphicsLayer { rotationY = 180f }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (deck == Deck.TELEGRAM) "✉ TELEGRAM" else "⌗ SANDI", fontFamily = PosterFamily, color = LisbonColors.FolderRed, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text(names.deckCard(deck.name, cue.cardId), fontFamily = TypewriterFamily, fontSize = 18.sp, color = LisbonColors.Ink, textAlign = TextAlign.Center)
                    Text(names.deckCardText(deck, cue.cardId), fontSize = 12.sp, color = LisbonColors.InkMuted, textAlign = TextAlign.Center)
                }
            } else {
                Text(if (deck == Deck.TELEGRAM) "✉" else "⌗", fontSize = 64.sp, color = LisbonColors.OldPaper)
            }
        }
    }
}

@Composable
private fun StampCue(cue: Cue.Stamp, who: String) {
    val s = remember { Animatable(3f) }
    LaunchedEffect(cue) { s.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    val (label, color) = when (cue.kind) {
        StampKind.SOLVED -> stringResource(R.string.stamp_solved) to LisbonColors.Olive
        StampKind.BURNED -> stringResource(R.string.stamp_burned) to LisbonColors.FolderRed
        StampKind.BANKRUPT -> stringResource(R.string.flag_bankrupt) to LisbonColors.FolderRed
        StampKind.ARRESTED -> stringResource(R.string.stamp_arrested) to LisbonColors.FolderRed
        StampKind.JAIL -> stringResource(R.string.stamp_jail) to LisbonColors.InkSoft
        StampKind.DOMINANCE -> stringResource(R.string.stamp_dominance) to LisbonColors.StreetLamp
        StampKind.DOUBLE_AGENT -> stringResource(R.string.flag_double_agent) to LisbonColors.FolderRed
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .scale(s.value)
                .rotate(-12f)
                .border(5.dp, color, RoundedCornerShape(6.dp))
                .background(LisbonColors.OldPaper.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(label.uppercase(), fontFamily = PosterFamily, fontWeight = FontWeight.Black, color = color, fontSize = 34.sp, letterSpacing = 4.sp)
        }
        Text(who, color = LisbonColors.OldPaper, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp).background(LisbonColors.Ink.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Ledakan koin saat jackpot Kasino. */
@Composable
private fun JackpotCue(cue: Cue.Jackpot, names: Names, who: String) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(cue) { t.animateTo(1f, tween(1500, easing = LinearEasing)) }
    val coins = remember(cue) {
        val r = Random(cue.hashCode())
        List(36) { Triple(r.nextFloat() * 6.283f, 0.4f + r.nextFloat() * 0.6f, r.nextFloat()) }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val maxR = size.minDimension * 0.55f
            for ((ang, speed, wobble) in coins) {
                val d = maxR * speed * t.value
                val pos = c + Offset(cos(ang) * d, sin(ang) * d + 120f * t.value * t.value)
                drawCircle(Color(0xFFE0A526).copy(alpha = 1f - t.value * 0.8f), 7f + 4f * wobble, pos)
                drawCircle(Color(0xFF8A6512).copy(alpha = 1f - t.value * 0.8f), 3f + 2f * wobble, pos)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(LisbonColors.Ink.copy(alpha = 0.75f), RoundedCornerShape(8.dp)).padding(12.dp)) {
            Text("JACKPOT!", fontFamily = PosterFamily, fontWeight = FontWeight.Black, color = LisbonColors.StreetLamp, fontSize = 30.sp)
            Text("$who +${names.money(cue.amount)}", color = LisbonColors.OldPaper, fontSize = 15.sp)
        }
    }
}

@Composable
private fun TacticCue(cue: Cue.Tactic, names: Names, who: String) {
    val s = remember { Animatable(0.6f) }
    LaunchedEffect(cue) { s.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
    Column(
        Modifier.scale(s.value).widthIn(max = 300.dp).clip(RoundedCornerShape(6.dp)).background(LisbonColors.OldPaper).border(2.dp, LisbonColors.StreetLamp, RoundedCornerShape(6.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.cue_tactic, who).uppercase(), fontFamily = PosterFamily, fontSize = 12.sp, color = LisbonColors.FolderRed, fontWeight = FontWeight.Black)
        Text(names.tactic(cue.cardId), fontFamily = TypewriterFamily, fontSize = 18.sp, color = LisbonColors.Ink, textAlign = TextAlign.Center)
        Text(names.tacticText(cue.cardId), fontSize = 12.sp, color = LisbonColors.InkMuted, textAlign = TextAlign.Center)
    }
}
