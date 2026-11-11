package app.gabrielaraujo.gymtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gabrielaraujo.gymtrack.ui.RepStackApp
import app.gabrielaraujo.gymtrack.ui.RepStackViewModel
import app.gabrielaraujo.gymtrack.ui.theme.RepStackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as RepStackApplication
        setContent {
            RepStackTheme {
                val vm: RepStackViewModel = viewModel(
                    factory = RepStackViewModel.factory(app.repository, app.authManager, app.syncEngine, app.restTimerAlerts, app.healthConnect, app.healthConnect),
                )
                RepStackApp(vm)
            }
        }
    }
}
