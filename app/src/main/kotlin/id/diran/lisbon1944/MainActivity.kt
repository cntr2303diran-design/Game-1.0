package id.diran.lisbon1944

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import id.diran.lisbon1944.ui.AppViewModel
import id.diran.lisbon1944.ui.LisbonApp
import id.diran.lisbon1944.ui.theme.LisbonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LisbonTheme {
                val vm: AppViewModel = viewModel()
                LisbonApp(vm)
            }
        }
    }
}
