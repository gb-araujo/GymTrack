package app.gabrielaraujo.gymtrack.ui.account

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.sync.SyncStatus
import app.gabrielaraujo.gymtrack.ui.components.BtnVariant
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.components.SyncIndicator
import app.gabrielaraujo.gymtrack.ui.components.syncStatusText
import app.gabrielaraujo.gymtrack.ui.findActivity
import app.gabrielaraujo.gymtrack.ui.formatDate
import app.gabrielaraujo.gymtrack.ui.theme.TC

/** Confirmação de saída quando há alterações que ainda não subiram para a nuvem. */
@Composable
fun SignOutConfirmDialog(pending: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(24.dp)) {
            Text("Sair sem sincronizar?", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                (if (pending == 1) "1 alteração ainda não foi enviada" else "$pending alterações ainda não foram enviadas") +
                    " para a nuvem. Ao sair, os dados da conta são apagados deste aparelho e essas alterações serão perdidas.",
                color = TC.Slate400, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GymTrackButton("Cancelar", onClick = onCancel, variant = BtnVariant.Ghost, modifier = Modifier.weight(1f))
                GymTrackButton("Sair", onClick = onConfirm, variant = BtnVariant.Danger, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Conta: login, sincronização, Health Connect e backup. */
@Composable
fun AccountDialog(vm: AccountViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val user = state.user
    val signingIn = state.signingIn
    val authError = state.authError
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "Não consegui ler o arquivo.", Toast.LENGTH_LONG).show()
            } else {
                vm.importBackup(text)
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.exportBackup(open = { context.contentResolver.openOutputStream(uri) })
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900)
                .verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text("Conta", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val u = user
            if (u != null) {
                Text(u.displayName ?: "Usuário", color = TC.White, fontWeight = FontWeight.SemiBold)
                Text(u.email ?: "", color = TC.Slate500, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncIndicator(state.syncStatus, state.pendingSync, onClick = { vm.syncNow() })
                    val isError = state.syncStatus is SyncStatus.Error
                    Text(
                        syncStatusText(state.syncStatus, state.pendingSync),
                        color = if (isError) TC.Red400 else TC.Slate400, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                GymTrackButton(
                    "Sincronizar agora",
                    onClick = { vm.syncNow() },
                    variant = BtnVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                GymTrackButton(
                    "Reenviar tudo para a nuvem",
                    onClick = {
                        vm.forceFullResync()
                        Toast.makeText(context, "Reenviando todos os dados…", Toast.LENGTH_SHORT).show()
                    },
                    variant = BtnVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                GymTrackButton("Sair", onClick = { vm.signOut(); onClose() }, variant = BtnVariant.Ghost, modifier = Modifier.fillMaxWidth())
            } else {
                Text("Entre com o Google para sincronizar seus treinos na nuvem e acessá-los em outros dispositivos.", color = TC.Slate400, fontSize = 14.sp)
                if (authError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(authError ?: "", color = TC.Red400, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                GymTrackButton(
                    if (signingIn) "Entrando..." else "Entrar com Google",
                    onClick = { context.findActivity()?.let { vm.signIn(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Health Connect", color = TC.Slate500, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            vm.healthConnect?.let { health ->
                HealthConnectSection(health, onExportPrevious = vm::exportPreviousToHealth, onImportEnabled = { vm.onHealthImportEnabled() })
            }

            Spacer(Modifier.height(16.dp))
            Text("Backup", color = TC.Slate500, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            GymTrackButton(
                "Exportar backup (JSON)",
                onClick = { exportLauncher.launch("gymtrack-backup-${formatDate(System.currentTimeMillis(), "yyyy-MM-dd")}.json") },
                variant = BtnVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            GymTrackButton(
                "Importar backup (JSON)",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                variant = BtnVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Inclui fichas, histórico, recordes, metas e exercícios personalizados. Também aceita o backup do app web antigo.",
                color = TC.Slate600, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
