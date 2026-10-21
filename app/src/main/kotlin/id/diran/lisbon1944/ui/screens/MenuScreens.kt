package id.diran.lisbon1944.ui.screens

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.ui.AppLanguage
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.Screen
import id.diran.lisbon1944.ui.Settings
import id.diran.lisbon1944.ui.Speed
import id.diran.lisbon1944.ui.common.LisbonDialog
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.PrimaryButton
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.Stamp
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.common.WrapGrid
import id.diran.lisbon1944.ui.theme.LisbonColors
import id.diran.lisbon1944.ui.theme.LocalUiPrefs
import id.diran.lisbon1944.ui.theme.PosterFamily
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun Backdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LisbonColors.Ink, LisbonColors.InkSoft, LisbonColors.Ink)))
            .safeDrawingPadding(),
    ) { content() }
}

/** Meja kerja agen: kayu gelap, garis peta kota, cahaya lampu meja, dan siluet mesin ketik. */
@Composable
fun DeskBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF241A13))) {
        Canvas(Modifier.fillMaxSize()) {
            // Serat kayu.
            var y = 0f
            var k = 0
            while (y < size.height) {
                drawLine(Color(0xFF2E2118).copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y + 12f * ((k % 3) - 1)), 2f)
                y += 22f
                k++
            }
            // Peta kota (kertas miring dengan garis jalan & sungai Tejo).
            rotate(-8f, Offset(size.width * 0.3f, size.height * 0.62f)) {
                val tl = Offset(size.width * 0.02f, size.height * 0.48f)
                val sz = Size(size.width * 0.62f, size.height * 0.34f)
                drawRect(Color(0xFFD9CDB2).copy(alpha = 0.22f), tl, sz)
                for (i in 1..6) drawLine(Color(0xFF14110F).copy(alpha = 0.18f), Offset(tl.x, tl.y + sz.height * i / 7f), Offset(tl.x + sz.width, tl.y + sz.height * (i + 0.5f) / 7f), 1.5f)
                for (i in 1..8) drawLine(Color(0xFF14110F).copy(alpha = 0.14f), Offset(tl.x + sz.width * i / 9f, tl.y), Offset(tl.x + sz.width * (i - 0.4f) / 9f, tl.y + sz.height), 1.5f)
                val river = Path().apply {
                    moveTo(tl.x, tl.y + sz.height * 0.85f)
                    cubicTo(tl.x + sz.width * 0.3f, tl.y + sz.height * 0.7f, tl.x + sz.width * 0.6f, tl.y + sz.height * 0.95f, tl.x + sz.width, tl.y + sz.height * 0.78f)
                }
                drawPath(river, Color(0xFF2E4A62).copy(alpha = 0.45f), style = Stroke(9f))
            }
            // Cahaya lampu meja.
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFE0A526).copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width * 0.8f, size.height * 0.12f), radius = size.minDimension * 0.9f),
                radius = size.minDimension * 0.9f,
                center = Offset(size.width * 0.8f, size.height * 0.12f),
            )
            // Mesin ketik (siluet) di pojok kanan bawah.
            val bx = size.width * 0.66f
            val by = size.height * 0.86f
            drawRoundRectSilhouette(bx, by)
        }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) { content() }
    }
}

private fun DrawScope.drawRoundRectSilhouette(bx: Float, by: Float) {
    val c = Color(0xFF0E0B09).copy(alpha = 0.8f)
    drawRect(c, Offset(bx, by), Size(size.width * 0.3f, size.height * 0.08f))
    drawRect(c, Offset(bx + size.width * 0.03f, by - size.height * 0.05f), Size(size.width * 0.24f, size.height * 0.05f))
    drawRect(c, Offset(bx + size.width * 0.06f, by - size.height * 0.09f), Size(size.width * 0.18f, size.height * 0.04f))
    for (i in 0 until 8) drawCircle(Color(0xFF3A2C22), size.width * 0.009f, Offset(bx + size.width * (0.04f + i * 0.03f), by + size.height * 0.03f))
}

@Composable
fun PosterTitle(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Stamp(stringResource(R.string.stamp_secret))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.title_main), style = MaterialTheme.typography.displayLarge, color = LisbonColors.OldPaper, textAlign = TextAlign.Center)
        Text(stringResource(R.string.subtitle_main), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.StreetLamp, textAlign = TextAlign.Center)
    }
}

/**
 * Splash (BAGIAN 12.2 no. 1): siluet trem Lisbon dan atap Alfama, lampu sorot menyapu, amplop merah berstempel
 * "RAHASIA" jatuh ke meja. Ketuk untuk melewati.
 */
@Composable
fun SplashScreen(vm: AppViewModel) {
    val reduce = LocalUiPrefs.current.reduceMotion
    val sweep = remember { Animatable(-40f) }
    val drop = remember { Animatable(if (reduce) 0f else -600f) }
    val tram = remember { Animatable(if (reduce) 0.45f else -0.3f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        coroutineScope {
            launch { sweep.animateTo(40f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse)) }
            launch { tram.animateTo(1.3f, tween(5200, easing = LinearEasing)) }
            delay(500)
            drop.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0A0E)).clickable { vm.skipSplash() }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Langit malam & lampu sorot.
            drawRect(Brush.verticalGradient(listOf(Color(0xFF14202C), Color(0xFF0B0A0E))))
            rotate(sweep.value, Offset(w * 0.5f, h * 0.72f)) {
                val beam = Path().apply {
                    moveTo(w * 0.5f, h * 0.72f)
                    lineTo(w * 0.38f, -h * 0.1f)
                    lineTo(w * 0.62f, -h * 0.1f)
                    close()
                }
                drawPath(beam, Brush.verticalGradient(listOf(Color(0xFFE0A526).copy(alpha = 0.0f), Color(0xFFE0A526).copy(alpha = 0.22f)), startY = 0f, endY = h * 0.72f))
            }
            // Atap Alfama.
            val roofs = Path().apply {
                moveTo(0f, h * 0.74f)
                val xs = listOf(0.06f, 0.12f, 0.16f, 0.22f, 0.27f, 0.33f, 0.37f, 0.42f, 0.47f, 0.52f, 0.58f, 0.63f, 0.68f, 0.74f, 0.79f, 0.85f, 0.9f, 0.95f, 1f)
                val ys = listOf(0.66f, 0.62f, 0.67f, 0.58f, 0.64f, 0.6f, 0.52f, 0.6f, 0.63f, 0.57f, 0.62f, 0.55f, 0.61f, 0.59f, 0.65f, 0.6f, 0.63f, 0.66f, 0.7f)
                xs.zip(ys).forEach { (x, yy) -> lineTo(w * x, h * yy) }
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(roofs, Color(0xFF07060A))
            // Menara gereja.
            drawRect(Color(0xFF07060A), Offset(w * 0.37f, h * 0.44f), Size(w * 0.035f, h * 0.1f))
            drawPath(Path().apply { moveTo(w * 0.365f, h * 0.44f); lineTo(w * 0.3875f, h * 0.4f); lineTo(w * 0.41f, h * 0.44f); close() }, Color(0xFF07060A))
            // Jendela menyala.
            listOf(0.14f to 0.7f, 0.29f to 0.68f, 0.55f to 0.66f, 0.71f to 0.69f, 0.88f to 0.7f).forEach { (x, yy) ->
                drawRect(Color(0xFFE0A526).copy(alpha = 0.7f), Offset(w * x, h * yy), Size(w * 0.012f, h * 0.014f))
            }
            // Trem Lisbon.
            val tx = w * tram.value
            val ty = h * 0.8f
            val tw = w * 0.26f
            val th = h * 0.06f
            drawRect(Color(0xFF07060A), Offset(tx, ty), Size(tw, th))
            drawRect(Color(0xFF07060A), Offset(tx + tw * 0.08f, ty - th * 0.25f), Size(tw * 0.84f, th * 0.25f))
            for (i in 0 until 5) drawRect(Color(0xFFE0A526).copy(alpha = 0.55f), Offset(tx + tw * (0.08f + i * 0.17f), ty + th * 0.18f), Size(tw * 0.11f, th * 0.35f))
            drawLine(Color(0xFF07060A), Offset(tx + tw * 0.5f, ty - th * 0.25f), Offset(tx + tw * 0.7f, ty - th * 1.2f), 3f)
            drawLine(Color(0xFF2A2420), Offset(0f, ty - th * 1.2f), Offset(w, ty - th * 1.25f), 2f)
            drawCircle(Color(0xFF07060A), th * 0.22f, Offset(tx + tw * 0.2f, ty + th))
            drawCircle(Color(0xFF07060A), th * 0.22f, Offset(tx + tw * 0.8f, ty + th))
            drawRect(Color(0xFF07060A), Offset(0f, ty + th * 1.15f), Size(w, h))
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(stringResource(R.string.title_main), style = MaterialTheme.typography.displayLarge, color = LisbonColors.OldPaper, textAlign = TextAlign.Center)
            Text(stringResource(R.string.subtitle_main), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.StreetLamp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            // Amplop merah jatuh ke meja.
            Box(
                Modifier
                    .graphicsLayer { translationY = drop.value; rotationZ = -6f + drop.value / 60f }
                    .size(200.dp, 120.dp)
                    .background(LisbonColors.FolderRed, RoundedCornerShape(4.dp))
                    .border(2.dp, Color(0xFF6E1712), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.stamp_secret),
                    color = LisbonColors.OldPaper,
                    fontFamily = PosterFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    letterSpacing = 4.sp,
                    modifier = Modifier.rotate(-12f).border(2.dp, LisbonColors.OldPaper).padding(horizontal = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.splash_tagline), color = LisbonColors.PaperMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.credit), color = LisbonColors.PaperMuted, fontSize = 12.sp)
            vm.loadError?.let { Text(stringResource(R.string.dossier_error, it), color = LisbonColors.FolderRed) }
        }
    }
}

@Composable
fun MenuScreen(vm: AppViewModel) {
    val cont = vm.continueSlot
    DeskBackdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PosterTitle()
            Spacer(Modifier.height(32.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cont != null) PrimaryButton(stringResource(R.string.menu_continue), { vm.loadSlot(cont) }, enabled = !vm.busy)
                PrimaryButton(stringResource(R.string.menu_new_game), { vm.go(Screen.SETUP) }, enabled = vm.content != null)
                SecondaryButton(stringResource(R.string.menu_how_to), { vm.go(Screen.HOW_TO) }, enabled = vm.content != null)
                SecondaryButton(stringResource(R.string.menu_load), { vm.go(Screen.LOAD) }, enabled = vm.content != null)
                SecondaryButton(stringResource(R.string.menu_archive), { vm.go(Screen.ARCHIVE) })
                SecondaryButton(stringResource(R.string.menu_dossier), { vm.go(Screen.DOSSIER) })
                SecondaryButton(stringResource(R.string.menu_settings), { vm.go(Screen.SETTINGS) })
            }
            if (!vm.settings.tutorialSeen) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.menu_first_time), color = LisbonColors.StreetLamp, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.credit), color = LisbonColors.PaperMuted, fontSize = 12.sp)
        }
        ErrorDialog(vm)
    }
}

@Composable
fun ErrorDialog(vm: AppViewModel) {
    val err = vm.loadError ?: return
    LisbonDialog(
        title = stringResource(R.string.error_title),
        onDismiss = vm::dismissError,
        confirmText = stringResource(R.string.ok),
        onConfirm = vm::dismissError,
        danger = true,
    ) { Text(err) }
}

@Composable
fun LoadScreen(vm: AppViewModel) {
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    Backdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.menu_load), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.OldPaper)
            vm.slots.forEachIndexed { i, info ->
                val n = i + 1
                PaperCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.slot_title, n), fontWeight = FontWeight.Bold)
                    if (info == null) {
                        Text(stringResource(R.string.slot_empty), color = LisbonColors.InkMuted)
                    } else {
                        Text(info.meta.players.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.slot_detail, info.meta.warStep, info.meta.round, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(info.savedAt))),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LisbonColors.InkMuted,
                        )
                        if (info.meta.finished) Text(stringResource(R.string.slot_finished), color = LisbonColors.FolderRed)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(stringResource(R.string.slot_open), { vm.loadSlot(n) }, Modifier.weight(1f), enabled = !vm.busy)
                            SecondaryButton(stringResource(R.string.slot_delete), { confirmDelete = n }, Modifier.weight(1f))
                        }
                    }
                }
            }
            SecondaryButton(stringResource(R.string.back), { vm.go(Screen.MENU) })
        }
        confirmDelete?.let { n ->
            LisbonDialog(
                title = stringResource(R.string.slot_delete_confirm, n),
                onDismiss = { confirmDelete = null },
                confirmText = stringResource(R.string.slot_delete),
                onConfirm = {
                    vm.deleteSlot(n)
                    confirmDelete = null
                },
                dismissText = stringResource(R.string.cancel),
                danger = true,
            )
        }
        ErrorDialog(vm)
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .semantics(mergeDescendants = true) { role = Role.Switch }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = LisbonColors.InkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LisbonColors.OldPaper,
                checkedTrackColor = LisbonColors.FolderRed,
                uncheckedThumbColor = LisbonColors.InkMuted,
                uncheckedTrackColor = LisbonColors.PaperShade,
            ),
        )
    }
}

@Composable
private fun VolumeRow(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = LisbonColors.FolderRed, activeTrackColor = LisbonColors.FolderRed, inactiveTrackColor = LisbonColors.PaperShade),
        )
        Text("${(value * 100).roundToInt()}%", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Pengaturan (BAGIAN 12.2 no. 18). */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val activity = LocalContext.current as? Activity
    fun set(n: Settings) = vm.updateSettings(n)
    Backdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.menu_settings), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.OldPaper)
            PaperCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.settings_speed))
                val labels = listOf(
                    Speed.SLOW to stringResource(R.string.speed_slow),
                    Speed.NORMAL to stringResource(R.string.speed_normal),
                    Speed.FAST to stringResource(R.string.speed_fast),
                    Speed.INSTANT to stringResource(R.string.speed_instant),
                )
                WrapGrid(labels, 2) { (sp, label), m -> ToggleTag(label, s.speed == sp, { set(s.copy(speed = sp)) }, m) }
                SwitchRow(stringResource(R.string.settings_reduce_motion), stringResource(R.string.settings_reduce_motion_hint), s.reduceMotion) { set(s.copy(reduceMotion = it)) }
            }
            PaperCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.settings_audio))
                SwitchRow(stringResource(R.string.settings_music), stringResource(R.string.settings_music_hint), s.music) { set(s.copy(music = it)) }
                VolumeRow("♪", s.musicVolume, s.music) { set(s.copy(musicVolume = it)) }
                SwitchRow(stringResource(R.string.settings_sfx), null, s.sfx) { set(s.copy(sfx = it)) }
                VolumeRow("⚅", s.sfxVolume, s.sfx) { set(s.copy(sfxVolume = it)) }
                SwitchRow(stringResource(R.string.settings_vibration), stringResource(R.string.settings_vibration_hint), s.vibration) { set(s.copy(vibration = it)) }
            }
            PaperCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.settings_display))
                Text(stringResource(R.string.settings_text_size, (s.textScale * 100).roundToInt()), fontWeight = FontWeight.Bold)
                WrapGrid(listOf(1f, 1.25f, 1.5f, 2f), 4) { f, m -> ToggleTag("${(f * 100).roundToInt()}%", s.textScale == f, { set(s.copy(textScale = f)) }, m) }
                SwitchRow(stringResource(R.string.settings_colorblind), stringResource(R.string.settings_colorblind_hint), s.colorBlind) { set(s.copy(colorBlind = it)) }
                SwitchRow(stringResource(R.string.settings_possibilities), stringResource(R.string.settings_possibilities_hint), s.showPossibilities) { set(s.copy(showPossibilities = it)) }
                SwitchRow(stringResource(R.string.settings_secrets), stringResource(R.string.settings_secrets_hint), s.showSecretsInLog) { set(s.copy(showSecretsInLog = it)) }
            }
            PaperCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.settings_language))
                val langs = listOf(
                    AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                    AppLanguage.ID to "Bahasa Indonesia",
                    AppLanguage.EN to "English",
                )
                WrapGrid(langs, 3) { (l, label), m ->
                    ToggleTag(label, s.language == l, {
                        if (s.language != l) {
                            set(s.copy(language = l))
                            activity?.recreate()
                        }
                    }, m)
                }
            }
            SecondaryButton(stringResource(R.string.back), { vm.go(Screen.MENU) })
            Spacer(Modifier.height(24.dp))
        }
    }
}

