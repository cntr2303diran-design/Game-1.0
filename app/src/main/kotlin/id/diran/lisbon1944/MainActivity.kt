package id.diran.lisbon1944

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.diran.lisbon1944.ui.dossier.ContentDossierScreen
import id.diran.lisbon1944.ui.theme.LisbonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LisbonTheme {
                ContentDossierScreen()
            }
        }
    }
}
