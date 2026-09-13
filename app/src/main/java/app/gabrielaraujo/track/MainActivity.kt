package app.gabrielaraujo.track

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gabrielaraujo.track.ui.TrackApp
import app.gabrielaraujo.track.ui.TrackViewModel
import app.gabrielaraujo.track.ui.theme.TrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as TrackApplication
        setContent {
            TrackTheme {
                val vm: TrackViewModel = viewModel(
                    factory = TrackViewModel.factory(app.repository, app.authManager, app.syncEngine),
                )
                TrackApp(vm)
            }
        }
    }
}
