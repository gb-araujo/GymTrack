package app.gabrielaraujo.gymtrack.ui.plans

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.ui.components.BtnVariant
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.theme.TC
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PlanExport(val schemaVersion: Int = 1, val workoutPlan: WorkoutPlan)

private val exportJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

@Composable
fun WorkoutPlansScreen(vm: PlansViewModel, onEditPlan: (String) -> Unit, onNewPlan: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val plans = state.plans
    val context = LocalContext.current
    var showImport by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Fichas de Treino", color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Upload, "Importar", tint = TC.Slate400, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TC.Slate800).clickable { showImport = true }.padding(9.dp))
                        Icon(Icons.Default.Add, "Nova", tint = TC.White, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TC.Indigo600).clickable { onNewPlan() }.padding(9.dp))
                    }
                }
            }

            if (plans.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nenhuma ficha criada", color = TC.Slate400)
                        Spacer(Modifier.height(16.dp))
                        GymTrackButton("Criar ficha", onClick = onNewPlan)
                    }
                }
            } else {
                items(plans, key = { it.id }) { plan ->
                    PlanCard(
                        plan = plan,
                        isActive = plan.id == state.activePlanId,
                        onEdit = { onEditPlan(plan.id) },
                        onActivate = { vm.setActive(plan.id) },
                        onDuplicate = { vm.duplicate(plan) },
                        onDelete = { vm.delete(plan.id) },
                        onExport = { sharePlan(context, plan) },
                    )
                }
            }
        }
    }

    if (showImport) {
        ImportDialog(
            onImport = { text ->
                val plan = parseImport(text) ?: return@ImportDialog false
                vm.import(plan)
                showImport = false
                true
            },
            onClose = { showImport = false },
        )
    }
}

@Composable
private fun PlanCard(
    plan: WorkoutPlan,
    isActive: Boolean,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900),
    ) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plan.name, color = TC.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (isActive) Text("ATIVA", color = TC.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(TC.Indigo600).padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text("${plan.days.size} treinos · ${plan.days.joinToString(" / ") { it.label }}", color = TC.Slate500, fontSize = 13.sp)
            }
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionChip("Editar", onEdit)
                if (!isActive) ActionChip("Ativar", onActivate, TC.Indigo400)
                ActionChip("Duplicar", onDuplicate)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionChip("Exportar", onExport)
                ActionChip("Excluir", onDelete, TC.Red400)
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color = TC.Slate300) {
    Text(
        label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TC.Slate800).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ImportDialog(onImport: (String) -> Boolean, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(20.dp)) {
            Text("Importar Ficha (JSON)", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 280.dp).clip(RoundedCornerShape(12.dp)).background(TC.Slate800)
                    .verticalScroll(rememberScrollState()).padding(12.dp),
            ) {
                if (text.isEmpty()) Text("Cole o JSON da ficha aqui...", color = TC.Slate500, fontSize = 13.sp)
                BasicTextField(text, { text = it }, textStyle = TextStyle(color = TC.Slate300, fontSize = 13.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(TC.Indigo400), modifier = Modifier.fillMaxWidth())
            }
            if (error.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(error, color = TC.Red400, fontSize = 12.sp) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GymTrackButton("Cancelar", onClick = onClose, variant = BtnVariant.Ghost, modifier = Modifier.weight(1f))
                GymTrackButton("Importar", onClick = { if (!onImport(text)) error = "JSON inválido. Verifique a formatação." }, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun sharePlan(context: android.content.Context, plan: WorkoutPlan) {
    val data = exportJson.encodeToString(PlanExport.serializer(), PlanExport(workoutPlan = plan))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "${plan.name}.json")
        putExtra(Intent.EXTRA_TEXT, data)
    }
    context.startActivity(Intent.createChooser(intent, "Exportar ficha"))
}

private fun parseImport(text: String): WorkoutPlan? = try {
    val parsed = exportJson.decodeFromString(PlanExport.serializer(), text)
    val now = System.currentTimeMillis()
    parsed.workoutPlan.copy(id = newId(), currentDayIndex = 0, createdAt = now, updatedAt = now, isActive = false)
} catch (_: Exception) {
    null
}
