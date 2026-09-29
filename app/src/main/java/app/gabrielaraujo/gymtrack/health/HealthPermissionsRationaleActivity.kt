package app.gabrielaraujo.gymtrack.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.theme.GymTrackTheme
import app.gabrielaraujo.gymtrack.ui.theme.TC

/** Explica o uso dos dados do Health Connect (exigido pelo Health Connect para pedir permissão). */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackTheme {
                Column(Modifier.fillMaxSize().background(TC.Background).statusBarsPadding().padding(24.dp)) {
                    Text("GymTrack e Health Connect", color = TC.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "O GymTrack só grava no Health Connect as sessões de treino que você conclui no app " +
                            "(início, fim, tipo \"treino de força\" e os exercícios feitos), para que apareçam no " +
                            "Google Fit e em outros apps de saúde. O GymTrack não lê nenhum dado de saúde seu.\n\n" +
                            "Você pode desligar a integração a qualquer momento na tela de conta do app ou revogar " +
                            "a permissão nas configurações do Health Connect.",
                        color = TC.Slate300, fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    GymTrackButton("Entendi", onClick = { finish() })
                }
            }
        }
    }
}
