package app.gabrielaraujo.gymtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.gabrielaraujo.gymtrack.ui.navigation.GymTrackApp
import app.gabrielaraujo.gymtrack.ui.theme.GymTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackTheme {
                GymTrackApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Ao abrir ou voltar ao app: sincroniza e busca os dados do relógio (Samsung Health etc.).
        (application as GymTrackApplication).container.onForeground()
    }
}
