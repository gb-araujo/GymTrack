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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                Column(
                    Modifier.fillMaxSize().background(TC.Background).statusBarsPadding()
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                ) {
                    Text("GymTrack e Health Connect", color = TC.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Enviar treinos: o GymTrack grava no Health Connect as sessões de treino que você conclui " +
                            "no app (início, fim, tipo \"treino de força\" e as séries feitas), para que apareçam no " +
                            "Samsung Health, no Google Fit e em outros apps de saúde.\n\n" +
                            "Importar do relógio: o GymTrack lê a frequência cardíaca e as calorias registradas durante " +
                            "cada treino (ex.: pelo Galaxy Watch, via Samsung Health) e guarda no treino, para mostrar no " +
                            "resumo e no histórico. Também lê o seu peso corporal, para o progresso e as metas. Nenhum " +
                            "outro dado de saúde é lido. Com o login ligado, os treinos (inclusive essas medidas) são " +
                            "sincronizados só com a sua conta do GymTrack.\n\n" +
                            "As duas opções são independentes. Você pode desligá-las a qualquer momento na tela de conta " +
                            "do app ou revogar as permissões nas configurações do Health Connect.",
                        color = TC.Slate300, fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    GymTrackButton("Entendi", onClick = { finish() })
                }
            }
        }
    }
}
