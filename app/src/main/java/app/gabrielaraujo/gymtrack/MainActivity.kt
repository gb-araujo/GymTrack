package app.gabrielaraujo.gymtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gabrielaraujo.gymtrack.ui.GymTrackApp
import app.gabrielaraujo.gymtrack.ui.GymTrackViewModel
import app.gabrielaraujo.gymtrack.ui.theme.GymTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val c = (application as GymTrackApplication).container
        setContent {
            GymTrackTheme {
                val vm: GymTrackViewModel = viewModel(
                    factory = GymTrackViewModel.factory(
                        c.repositories, c.authManager, c.syncEngine, c.syncScheduler,
                        c.restTimerAlerts, c.healthConnect, c.healthConnect,
                    ),
                )
                GymTrackApp(vm)
            }
        }
    }
}
