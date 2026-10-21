package id.diran.lisbon1944

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.LisbonApp
import id.diran.lisbon1944.ui.SaveStore
import id.diran.lisbon1944.ui.theme.LisbonTheme
import id.diran.lisbon1944.ui.theme.UiPrefs
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var vmRef: AppViewModel? = null

    /** Bahasa pilihan di Pengaturan menimpa bahasa sistem (BAGIAN 12.2 no. 18). */
    override fun attachBaseContext(newBase: Context) {
        val tag = SaveStore.language(newBase).tag
        if (tag == null) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val cfg = Configuration(newBase.resources.configuration)
        cfg.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[AppViewModel::class.java]
        vmRef = vm
        // Splash sistem tetap tampil sampai konten JSON selesai dimuat & divalidasi.
        splash.setKeepOnScreenCondition { !vm.ready }
        enableEdgeToEdge()
        setContent {
            val s = vm.settings
            LisbonTheme(
                textScale = s.textScale,
                prefs = UiPrefs(colorBlind = s.colorBlind, showPossibilities = s.showPossibilities, reduceMotion = s.reduceMotion),
            ) {
                LisbonApp(vm)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        vmRef?.sound?.pause()
    }

    override fun onResume() {
        super.onResume()
        vmRef?.sound?.resume()
    }
}
