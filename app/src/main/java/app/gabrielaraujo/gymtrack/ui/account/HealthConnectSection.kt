package app.gabrielaraujo.gymtrack.ui.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.health.ExportStatus
import app.gabrielaraujo.gymtrack.health.HealthConnectManager
import app.gabrielaraujo.gymtrack.health.SamsungHealth
import app.gabrielaraujo.gymtrack.ui.components.BtnVariant
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.formatShortDateTime
import app.gabrielaraujo.gymtrack.ui.theme.TC
import kotlinx.coroutines.launch

/**
 * Health Connect: envio dos treinos, importação dos dados do relógio e o passo a passo
 * para os treinos aparecerem no Samsung Health.
 * @param onExportPrevious envia os treinos já concluídos; devolve quantos foram enviados.
 * @param onImportEnabled chamado quando a importação é ligada (para buscar os dados já existentes).
 */
@Composable
fun HealthConnectSection(health: HealthConnectManager, onExportPrevious: suspend () -> Int, onImportEnabled: () -> Unit) {
    val context = LocalContext.current
    val exportOn by health.exportEnabled.collectAsStateWithLifecycle()
    val importOn by health.importEnabled.collectAsStateWithLifecycle()
    val exportStatus by health.exportStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(health.sdkStatus()) }
    var granted by remember { mutableStateOf(emptySet<String>()) }
    val samsungInstalled = remember { SamsungHealth.isInstalled(context) }

    // Revalida ao voltar ao app: as permissões podem ter mudado no Health Connect.
    LifecycleResumeEffect(health) {
        status = health.sdkStatus()
        val job = scope.launch { granted = health.grantedPermissions() }
        onPauseOrDispose { job.cancel() }
    }

    val canWrite = health.writePermissions.all { it in granted }
    val canRead = health.readPermissions.any { it in granted }

    val writeLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { result ->
        granted = granted + result
        val ok = result.containsAll(health.writePermissions)
        health.setExportEnabled(ok)
        if (!ok) toast(context, "Permissão não concedida.")
    }
    val readLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { result ->
        granted = granted + result
        val ok = result.any { it in health.readPermissions }
        health.setImportEnabled(ok)
        if (ok) onImportEnabled() else toast(context, "Permissão não concedida.")
    }

    when (status) {
        HealthConnectClient.SDK_UNAVAILABLE -> {
            Text("Health Connect não está disponível neste aparelho.", color = TC.Slate600, fontSize = 12.sp)
            return
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
            Text("Instale ou atualize o Health Connect para trocar dados com o Samsung Health, Google Fit e similares.", color = TC.Slate400, fontSize = 12.sp)
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

    val exporting = exportOn && canWrite
    val importing = importOn && canRead

    Column(Modifier.fillMaxWidth()) {
        ToggleRow(
            title = "Enviar treinos concluídos",
            subtitle = "Para aparecerem no Samsung Health, Google Fit e outros apps.",
            checked = exporting,
            onChange = { on ->
                when {
                    !on -> health.setExportEnabled(false)
                    canWrite -> health.setExportEnabled(true)
                    else -> writeLauncher.launch(health.writePermissions)
                }
            },
        )
        if (exporting) {
            exportStatus?.let { ExportStatusText(it) }
            Spacer(Modifier.height(6.dp))
            GymTrackButton(
                "Enviar treinos anteriores",
                onClick = {
                    scope.launch {
                        val sent = onExportPrevious()
                        val error = health.exportStatus.value?.error
                        toast(context, if (sent == 0 && error != null) "Falha ao enviar: $error" else "$sent treinos enviados ao Health Connect.")
                    }
                },
                variant = BtnVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = "Importar do relógio",
            subtitle = "Frequência cardíaca e calorias durante o treino e peso corporal.",
            checked = importing,
            onChange = { on ->
                when {
                    !on -> health.setImportEnabled(false)
                    canRead -> { health.setImportEnabled(true); onImportEnabled() }
                    else -> readLauncher.launch(health.readPermissions)
                }
            },
        )
        if (importing) {
            Text(
                "Os dados chegam quando o relógio sincroniza com o Samsung Health, o que pode levar alguns minutos depois do treino.",
                color = TC.Slate500, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        GymTrackButton(
            "Ver dados no Health Connect",
            onClick = { runCatching { context.startActivity(health.manageDataIntent()) } },
            variant = BtnVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )

        if (samsungInstalled) {
            Spacer(Modifier.height(12.dp))
            SamsungHealthGuide(exporting)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TC.Slate300, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TC.Slate500, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = TC.Indigo600))
    }
}

@Composable
private fun ExportStatusText(status: ExportStatus) {
    val whenText = formatShortDateTime(status.at)
    val error = status.error
    if (error != null) {
        Text("Falha no último envio ($whenText): $error", color = TC.Red400, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    } else {
        val count = if (status.count == 1) "1 treino" else "${status.count} treinos"
        Text("Último envio: $whenText · $count", color = TC.Slate500, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Passo a passo para o Samsung Health importar os treinos do Health Connect. */
@Composable
private fun SamsungHealthGuide(exporting: Boolean) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate800).padding(12.dp)) {
        Text("Samsung Health", color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Para os treinos aparecerem lá:", color = TC.Slate400, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        GuideStep(1, "Ligue \"Enviar treinos concluídos\" acima.", done = exporting)
        GuideStep(2, "No Health Connect, permita que o Samsung Health leia \"Exercício\".")
        GymTrackButton(
            "Liberar no Health Connect",
            onClick = {
                if (!SamsungHealth.openHealthConnectPermissions(context)) toast(context, "Não consegui abrir o Health Connect.")
            },
            variant = BtnVariant.Ghost,
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 8.dp),
        )
        GuideStep(3, "No Samsung Health, abra Configurações › Health Connect e confira se a sincronização está ligada.")
        GymTrackButton(
            "Abrir Samsung Health",
            onClick = { if (!SamsungHealth.open(context)) toast(context, "Não consegui abrir o Samsung Health.") },
            variant = BtnVariant.Ghost,
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 8.dp),
        )
        Text(
            "O Samsung Health importa do Health Connect aos poucos: um treino novo pode levar alguns minutos para aparecer. " +
                "Se nada chegar, confira em \"Ver dados no Health Connect\" se o treino foi gravado lá.",
            color = TC.Slate500, fontSize = 11.sp,
        )
    }
}

@Composable
private fun GuideStep(number: Int, text: String, done: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(if (done) TC.Emerald600 else TC.Slate700),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Default.Check, null, tint = TC.White, modifier = Modifier.size(12.dp))
            else Text("$number", color = TC.Slate300, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = TC.Slate300, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
