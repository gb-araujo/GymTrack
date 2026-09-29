package app.gabrielaraujo.gymtrack.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.GymTrackApplication
import app.gabrielaraujo.gymtrack.ui.components.BtnVariant
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.theme.TC
import kotlinx.coroutines.launch

/** Liga/desliga o envio dos treinos para o Health Connect. */
@Composable
fun HealthConnectSection() {
    val context = LocalContext.current
    val app = context.applicationContext as GymTrackApplication
    val health = app.healthConnect
    val enabled by health.enabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(health.sdkStatus()) }
    var granted by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, status) { granted = health.hasPermissions() }

    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { result ->
        val ok = result.containsAll(health.permissions)
        granted = ok
        health.setEnabled(ok)
        if (!ok) Toast.makeText(context, "Permissão não concedida.", Toast.LENGTH_LONG).show()
    }

    when (status) {
        HealthConnectClient.SDK_UNAVAILABLE -> {
            Text("Health Connect não está disponível neste aparelho.", color = TC.Slate600, fontSize = 12.sp)
            return
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
            Text("Instale ou atualize o Health Connect para registrar seus treinos no Google Fit e similares.", color = TC.Slate400, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            GymTrackButton(
                "Abrir Play Store",
                onClick = {
                    val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending")) }
                    status = health.sdkStatus()
                },
                variant = BtnVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
            return
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Registrar treinos concluídos no Health Connect (Google Fit etc.)",
                color = TC.Slate300, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled && granted,
                onCheckedChange = { on ->
                    if (!on) {
                        health.setEnabled(false)
                    } else if (granted) {
                        health.setEnabled(true)
                    } else {
                        permissionLauncher.launch(health.permissions)
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = TC.Indigo600),
            )
        }
        if (enabled && granted) {
            Spacer(Modifier.height(6.dp))
            GymTrackButton(
                "Enviar treinos anteriores",
                onClick = {
                    scope.launch {
                        val sent = health.exportAll(app.repository.completedSessionsSnapshot())
                        Toast.makeText(context, "$sent treinos enviados ao Health Connect.", Toast.LENGTH_LONG).show()
                    }
                },
                variant = BtnVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
