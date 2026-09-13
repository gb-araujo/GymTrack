package app.gabrielaraujo.track.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.track.data.Goal
import app.gabrielaraujo.track.ui.TrackViewModel
import app.gabrielaraujo.track.ui.components.BtnVariant
import app.gabrielaraujo.track.ui.components.NumberStepper
import app.gabrielaraujo.track.ui.components.ProgressBar
import app.gabrielaraujo.track.ui.components.TrackButton
import app.gabrielaraujo.track.ui.nanoid
import app.gabrielaraujo.track.ui.theme.TC
import kotlin.math.min
import kotlin.math.roundToInt

private data class GoalType(val value: String, val label: String, val unit: String)

private val goalTypes = listOf(
    GoalType("workout_count", "Número de treinos", "treinos"),
    GoalType("weekly_frequency", "Frequência semanal", "treinos/semana"),
    GoalType("exercise_weight", "Carga em exercício", "kg"),
    GoalType("body_weight", "Peso corporal", "kg"),
    GoalType("volume", "Volume total", "kg"),
)

@Composable
fun GoalsScreen(vm: TrackViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }

    fun currentValue(goal: Goal): Double = when (goal.type) {
        "workout_count" -> state.totalWorkouts.toDouble()
        "weekly_frequency" -> {
            val weekStart = System.currentTimeMillis() - 7L * 24 * 3600_000
            state.sessions.count { it.startTime >= weekStart }.toDouble()
        }
        "volume" -> state.sessions.sumOf { it.totalVolume ?: 0.0 }
        else -> goal.current
    }

    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Metas", color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Add, "Nova", tint = TC.White, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TC.Indigo600).clickable { showNew = true }.padding(9.dp))
            }
        }

        if (state.goals.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Flag, null, tint = TC.Slate700, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nenhuma meta criada", color = TC.Slate400)
                    Text("Defina metas para manter o foco", color = TC.Slate600, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    TrackButton("Criar meta", onClick = { showNew = true })
                }
            }
        } else {
            items(state.goals, key = { it.id }) { goal ->
                val current = currentValue(goal)
                val pct = if (goal.target <= 0) 0 else min(100, ((current / goal.target) * 100).roundToInt())
                val done = pct >= 100
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (done) Icon(Icons.Default.EmojiEvents, null, tint = TC.Yellow400, modifier = Modifier.size(14.dp))
                                Text(goal.title, color = TC.White, fontWeight = FontWeight.SemiBold)
                            }
                            Text(goalTypes.find { it.value == goal.type }?.label ?: goal.type, color = TC.Slate500, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(current.roundToInt().toString(), color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Text("/ ${goal.target.roundToInt()} ${goal.unit}", color = TC.Slate500, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.Delete, "Excluir", tint = TC.Slate600, modifier = Modifier.size(18.dp).clickable { vm.deleteGoal(goal.id) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(fraction = pct / 100f, color = if (done) TC.Yellow500 else TC.Indigo500)
                    Text("$pct%${if (done) " — Concluída! 🎉" else ""}", color = if (done) TC.Yellow400 else TC.Slate500, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }

    if (showNew) {
        NewGoalDialog(
            onCreate = { title, type, target ->
                val gt = goalTypes.find { it.value == type }
                vm.addGoal(Goal(id = nanoid(), type = type, title = title, target = target, unit = gt?.unit ?: "", createdAt = System.currentTimeMillis()))
                showNew = false
            },
            onClose = { showNew = false },
        )
    }
}

@Composable
private fun NewGoalDialog(onCreate: (String, String, Double) -> Unit, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("workout_count") }
    var target by remember { mutableStateOf(10.0) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(20.dp)) {
            Text("Nova Meta", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Título", color = TC.Slate500, fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(TC.Slate800).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (title.isEmpty()) Text("Ex: Chegar a 100 treinos", color = TC.Slate500, fontSize = 14.sp)
                BasicTextField(title, { title = it }, textStyle = TextStyle(color = TC.White, fontSize = 14.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(TC.Indigo400), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Text("Tipo", color = TC.Slate500, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            goalTypes.forEach { gt ->
                val sel = gt.value == type
                Text(
                    gt.label,
                    color = if (sel) TC.White else TC.Slate300, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) TC.Indigo600 else TC.Slate800).clickable { type = gt.value }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Meta (${goalTypes.find { it.value == type }?.unit ?: ""})", color = TC.Slate500, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            NumberStepper(target, { target = it }, step = 1.0, min = 1.0, max = 100000.0, isInt = true)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrackButton("Cancelar", onClick = onClose, variant = BtnVariant.Ghost, modifier = Modifier.weight(1f))
                TrackButton("Criar", onClick = { if (title.isNotBlank()) onCreate(title.trim(), type, target) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
