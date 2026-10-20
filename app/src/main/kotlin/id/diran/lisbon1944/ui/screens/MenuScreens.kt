package id.diran.lisbon1944.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.Screen
import id.diran.lisbon1944.ui.Speed
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.PrimaryButton
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.Stamp
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.common.WrapGrid
import id.diran.lisbon1944.ui.theme.LisbonColors
import java.text.DateFormat
import java.util.Date

@Composable
fun Backdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LisbonColors.Ink, LisbonColors.InkSoft, LisbonColors.Ink)))
            .safeDrawingPadding(),
    ) { content() }
}

@Composable
fun PosterTitle(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Stamp(stringResource(R.string.stamp_secret))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.title_main),
            style = MaterialTheme.typography.displayLarge,
            color = LisbonColors.OldPaper,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.subtitle_main),
            style = MaterialTheme.typography.headlineMedium,
            color = LisbonColors.StreetLamp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SplashScreen(vm: AppViewModel) {
    Backdrop {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PosterTitle()
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.splash_tagline), color = LisbonColors.PaperShade, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.credit), color = LisbonColors.PaperShade.copy(alpha = 0.7f), fontSize = 12.sp)
            vm.loadError?.let { Text(stringResource(R.string.dossier_error, it), color = LisbonColors.FolderRed) }
        }
    }
}

@Composable
fun MenuScreen(vm: AppViewModel) {
    val cont = vm.continueSlot
    Backdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PosterTitle()
            Spacer(Modifier.height(36.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cont != null) PrimaryButton(stringResource(R.string.menu_continue), { vm.loadSlot(cont) }, enabled = !vm.busy)
                PrimaryButton(stringResource(R.string.menu_new_game), { vm.go(Screen.SETUP) }, enabled = vm.content != null)
                SecondaryButton(stringResource(R.string.menu_load), { vm.go(Screen.LOAD) }, enabled = vm.content != null)
                SecondaryButton(stringResource(R.string.menu_dossier), { vm.go(Screen.DOSSIER) })
                SecondaryButton(stringResource(R.string.menu_settings), { vm.go(Screen.SETTINGS) })
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.credit), color = LisbonColors.PaperShade.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        ErrorDialog(vm)
    }
}

@Composable
fun ErrorDialog(vm: AppViewModel) {
    val err = vm.loadError ?: return
    AlertDialog(
        onDismissRequest = vm::dismissError,
        confirmButton = { TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.ok)) } },
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(err) },
    )
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
                        Text(stringResource(R.string.slot_empty), color = LisbonColors.InkSoft)
                    } else {
                        Text(info.meta.players.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.slot_detail, info.meta.warStep, info.meta.round, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(info.savedAt))),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LisbonColors.InkSoft,
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
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                confirmButton = { TextButton(onClick = { vm.deleteSlot(n); confirmDelete = null }) { Text(stringResource(R.string.slot_delete)) } },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
                title = { Text(stringResource(R.string.slot_delete_confirm, n)) },
            )
        }
        ErrorDialog(vm)
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
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
                WrapGrid(labels, 2) { (sp, label), m ->
                    ToggleTag(label, s.speed == sp, { vm.updateSettings(s.copy(speed = sp)) }, m)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_secrets), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_secrets_hint), style = MaterialTheme.typography.bodyMedium, color = LisbonColors.InkSoft)
                    }
                    Switch(checked = s.showSecretsInLog, onCheckedChange = { vm.updateSettings(s.copy(showSecretsInLog = it)) })
                }
            }
            SecondaryButton(stringResource(R.string.back), { vm.go(Screen.MENU) })
        }
    }
}
