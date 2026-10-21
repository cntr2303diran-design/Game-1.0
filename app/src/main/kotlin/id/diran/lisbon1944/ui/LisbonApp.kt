package id.diran.lisbon1944.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.ui.common.SecondaryButton
import id.diran.lisbon1944.ui.dossier.ContentDossierScreen
import id.diran.lisbon1944.ui.game.GameScreen
import id.diran.lisbon1944.ui.screens.ArchiveScreen
import id.diran.lisbon1944.ui.screens.EndScreen
import id.diran.lisbon1944.ui.screens.HowToScreen
import id.diran.lisbon1944.ui.screens.LoadScreen
import id.diran.lisbon1944.ui.screens.MenuScreen
import id.diran.lisbon1944.ui.screens.SettingsScreen
import id.diran.lisbon1944.ui.screens.SetupScreen
import id.diran.lisbon1944.ui.screens.SplashScreen

@Composable
fun LisbonApp(vm: AppViewModel) {
    when (vm.screen) {
        Screen.SPLASH -> SplashScreen(vm)
        Screen.MENU -> MenuScreen(vm)
        Screen.SETUP -> {
            BackHandler { vm.go(Screen.MENU) }
            SetupScreen(vm)
        }
        Screen.LOAD -> {
            BackHandler { vm.go(Screen.MENU) }
            LoadScreen(vm)
        }
        Screen.SETTINGS -> {
            BackHandler { vm.go(Screen.MENU) }
            SettingsScreen(vm)
        }
        Screen.DOSSIER -> {
            BackHandler { vm.go(Screen.MENU) }
            Box(Modifier.fillMaxSize()) {
                ContentDossierScreen()
                SecondaryButton(
                    stringResource(R.string.back),
                    { vm.go(Screen.MENU) },
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp).width(200.dp),
                )
            }
        }
        Screen.ARCHIVE -> {
            BackHandler { vm.go(Screen.MENU) }
            ArchiveScreen(vm)
        }
        Screen.HOW_TO -> {
            BackHandler { vm.go(Screen.MENU) }
            HowToScreen(vm)
        }
        Screen.GAME -> GameScreen(vm)
        Screen.END -> {
            BackHandler { vm.quitToMenu() }
            EndScreen(vm)
        }
    }
}
