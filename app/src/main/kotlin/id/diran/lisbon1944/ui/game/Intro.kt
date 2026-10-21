package id.diran.lisbon1944.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.GameEvents
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.session.Names
import id.diran.lisbon1944.session.Sfx
import id.diran.lisbon1944.ui.GameUi
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.theme.LisbonColors
import id.diran.lisbon1944.ui.theme.PosterFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animasi Persiapan (BAGIAN 12.2 no. 4): 3 kartu tertutup meluncur ke amplop, kartu dibagi ke tiap pemain,
 * sisa kartu terbuka di Kedutaan, lempar dadu urutan. Ketuk untuk melewati.
 */
@Composable
fun IntroOverlay(ui: GameUi, content: GameContent, names: Names, onDone: () -> Unit, sound: (Sfx) -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        sound(Sfx.CARD)
        launch { slide.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        delay(1500)
        phase = 1
        sound(Sfx.CARD)
        delay(1500)
        phase = 2
        delay(1600)
        phase = 3
        sound(Sfx.DICE)
        delay(2200)
        onDone()
    }
    val v = ui.view
    val factions = seatFactions(v)
    Box(
        Modifier
            .fillMaxSize()
            .background(LisbonColors.Ink.copy(alpha = 0.93f))
            .clickable(onClick = onDone)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(
                    when (phase) {
                        0 -> R.string.intro_envelope
                        1 -> R.string.intro_deal
                        2 -> R.string.intro_embassy
                        else -> R.string.intro_initiative
                    },
                ).uppercase(),
                fontFamily = PosterFamily,
                fontWeight = FontWeight.Black,
                color = LisbonColors.StreetLamp,
                fontSize = 18.sp,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            when (phase) {
                0 -> Box(Modifier.size(260.dp, 170.dp), contentAlignment = Alignment.Center) {
                    // Amplop merah.
                    Box(
                        Modifier.size(200.dp, 120.dp).background(LisbonColors.FolderRed, RoundedCornerShape(4.dp)).border(2.dp, LisbonColors.Ink, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.stamp_secret),
                            color = LisbonColors.OldPaper,
                            fontFamily = PosterFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = 4.sp,
                            modifier = Modifier.rotate(-10f).border(2.dp, LisbonColors.OldPaper).padding(horizontal = 6.dp),
                        )
                    }
                    for (i in 0 until 3) {
                        val start = -260f + i * 40f
                        Box(
                            Modifier
                                .graphicsLayer {
                                    translationY = start * (1f - slide.value)
                                    translationX = (i - 1) * 46f * (1f - slide.value)
                                    alpha = 1f - slide.value * 0.85f
                                    rotationZ = (i - 1) * 12f * (1f - slide.value)
                                }
                                .size(54.dp, 78.dp)
                                .background(LisbonColors.Olive, RoundedCornerShape(4.dp))
                                .border(2.dp, LisbonColors.OldPaper, RoundedCornerShape(4.dp)),
                        )
                    }
                }
                1 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val seats = listOf(Triple(v.me.id.value, v.me.name, v.me.hand.size)) + v.opponents.map { Triple(it.id.value, it.name, it.handCount) }
                    seats.forEach { (seat, name, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            factions[seat]?.let { FactionBadge(content, it, 28) }
                            Spacer(Modifier.width(8.dp))
                            Text(name, color = LisbonColors.OldPaper, modifier = Modifier.width(140.dp))
                            repeat(count) {
                                Box(Modifier.padding(end = 3.dp).size(14.dp, 20.dp).background(LisbonColors.Olive, RoundedCornerShape(2.dp)).border(1.dp, LisbonColors.OldPaper, RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }
                2 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (v.embassyCards.isEmpty()) Text("—", color = LisbonColors.OldPaper)
                    v.embassyCards.forEach { c ->
                        val type = runCatching { content.evidence(c).type }.getOrNull()
                        Text(
                            names.card(c),
                            color = LisbonColors.Ink,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(LisbonColors.OldPaper, RoundedCornerShape(4.dp))
                                .border(2.dp, type?.let(Palette::evidence) ?: LisbonColors.Ink, RoundedCornerShape(4.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                else -> {
                    val rolls = v.log.filter { it.type == GameEvents.INITIATIVE_ROLL }
                    val first = v.log.firstOrNull { it.type == GameEvents.FIRST_PLAYER }?.payload?.get("player")?.toIntOrNull()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rolls.forEach { e ->
                            val seat = e.payload["player"]?.toIntOrNull() ?: return@forEach
                            val d1 = e.payload["d1"]?.toIntOrNull() ?: 1
                            val d2 = e.payload["d2"]?.toIntOrNull() ?: 1
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(ui.playerNames[seat].orEmpty(), color = if (seat == first) LisbonColors.StreetLamp else LisbonColors.OldPaper, modifier = Modifier.width(140.dp), fontWeight = if (seat == first) FontWeight.Bold else FontWeight.Normal)
                                DieFace(d1, 30)
                                Spacer(Modifier.width(4.dp))
                                DieFace(d2, 30)
                                Spacer(Modifier.width(8.dp))
                                Text("${d1 + d2}", color = LisbonColors.OldPaper, fontWeight = FontWeight.Bold)
                            }
                        }
                        first?.let {
                            Text(stringResource(R.string.intro_first, ui.playerNames[it].orEmpty()), color = LisbonColors.StreetLamp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.intro_skip), color = LisbonColors.PaperMuted, fontSize = 12.sp)
        }
    }
}
