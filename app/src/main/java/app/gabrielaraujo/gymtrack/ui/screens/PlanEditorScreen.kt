package app.gabrielaraujo.gymtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.data.WorkoutDay
import app.gabrielaraujo.gymtrack.data.WorkoutExercise
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.ui.RepStackViewModel
import app.gabrielaraujo.gymtrack.ui.components.NumberStepper
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.ui.theme.TC

@Composable
fun PlanEditorScreen(vm: RepStackViewModel, planId: String, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val initial = remember(planId) {
        if (planId == "new") WorkoutPlan(id = newId(), name = "Nova Ficha", createdAt = now, updatedAt = now)
        else state.plans.find { it.id == planId } ?: WorkoutPlan(id = newId(), name = "Nova Ficha", createdAt = now, updatedAt = now)
    }
    var plan by remember { mutableStateOf(initial) }
    var pickingForDay by remember { mutableStateOf<String?>(null) }

    fun updateDay(dayId: String, f: (WorkoutDay) -> WorkoutDay) {
        plan = plan.copy(days = plan.days.map { if (it.id == dayId) f(it) else it })
    }
    fun updateExercise(dayId: String, weId: String, f: (WorkoutExercise) -> WorkoutExercise) {
        updateDay(dayId) { d -> d.copy(exercises = d.exercises.map { if (it.id == weId) f(it) else it }) }
    }

    Column(Modifier.fillMaxSize().background(TC.Background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TC.Slate400, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onDone() }.padding(6.dp))
            Text("Editor de Ficha", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Salvar", color = TC.OnLime, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(TC.Lime).clickable { vm.savePlan(plan); onDone() }.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                EditField(plan.name, { plan = plan.copy(name = it) }, "Nome da ficha", big = true)
            }
            items(plan.days, key = { it.id }) { day ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(44.dp)) { EditField(day.label, { updateDay(day.id) { d -> d.copy(label = it) } }, "A", center = true) }
                        Box(Modifier.weight(1f)) { EditField(day.name, { updateDay(day.id) { d -> d.copy(name = it) } }, "Nome (ex: Peito)") }
                        Icon(Icons.Default.Delete, "Remover treino", tint = TC.Slate500, modifier = Modifier.clickable { plan = plan.copy(days = plan.days.filter { it.id != day.id }.mapIndexed { i, d -> d.copy(order = i) }) })
                    }
                    Spacer(Modifier.height(8.dp))
                    day.exercises.forEach { we ->
                        val ex = state.exercises.find { it.id == we.exerciseId }
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(TC.Slate800).padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(ex?.name ?: we.exerciseId, color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.Delete, "Remover", tint = TC.Slate500, modifier = Modifier.size(18.dp).clickable {
                                    updateDay(day.id) { d -> d.copy(exercises = d.exercises.filter { it.id != we.id }.mapIndexed { i, e -> e.copy(order = i) }) }
                                })
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PropStepper("Séries", we.sets, 1, 10, Modifier.weight(1f)) { v -> updateExercise(day.id, we.id) { it.copy(sets = v) } }
                                PropStepper("Min reps", we.repMin, 1, 50, Modifier.weight(1f)) { v -> updateExercise(day.id, we.id) { it.copy(repMin = v) } }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PropStepper("Max reps", we.repMax, 1, 50, Modifier.weight(1f)) { v -> updateExercise(day.id, we.id) { it.copy(repMax = v) } }
                                PropStepper("Descanso", we.restSeconds, 0, 600, Modifier.weight(1f), step = 15) { v -> updateExercise(day.id, we.id) { it.copy(restSeconds = v) } }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Carga inicial (kg)", color = TC.Slate500, fontSize = 10.sp)
                            Spacer(Modifier.height(2.dp))
                            NumberStepper(value = we.initialWeight, onChange = { v -> updateExercise(day.id, we.id) { it.copy(initialWeight = v) } }, step = 2.5, min = 0.0, max = 500.0)
}
                    }
                    AddRow("Adicionar exercício") { pickingForDay = day.id }
                }
            }
            item {
                AddRow("Adicionar treino") {
                    val labels = listOf("A", "B", "C", "D", "E", "F")
                    val label = labels.getOrElse(plan.days.size) { "${plan.days.size + 1}" }
                    plan = plan.copy(days = plan.days + WorkoutDay(id = newId(), label = label, name = "", order = plan.days.size))
                }
            }
        }
    }

    pickingForDay?.let { dayId ->
        ExercisePickerDialog(vm, onSelect = { id, _ ->
            updateDay(dayId) { d ->
                d.copy(exercises = d.exercises + WorkoutExercise(id = newId(), exerciseId = id, sets = 3, repMin = 8, repMax = 12, restSeconds = 90, initialWeight = 0.0, order = d.exercises.size))
            }
            pickingForDay = null
        }, onClose = { pickingForDay = null })
    }
}

// helper placeholder removed below

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = TC.Slate500, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TC.Slate500, fontSize = 13.sp)
    }
}

@Composable
private fun EditField(value: String, onChange: (String) -> Unit, placeholder: String, big: Boolean = false, center: Boolean = false) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate800).padding(horizontal = 12.dp, vertical = if (big) 14.dp else 8.dp)) {
        if (value.isEmpty()) Text(placeholder, color = TC.Slate500, fontSize = if (big) 18.sp else 14.sp, modifier = if (center) Modifier.fillMaxWidth() else Modifier, textAlign = if (center) TextAlign.Center else TextAlign.Start)
        BasicTextField(
            value, onChange,
            textStyle = TextStyle(color = TC.White, fontSize = if (big) 18.sp else 14.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, textAlign = if (center) TextAlign.Center else TextAlign.Start),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(TC.LimeSoft),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PropStepper(label: String, value: Int, min: Int, max: Int, modifier: Modifier = Modifier, step: Int = 1, onChange: (Int) -> Unit) {
    Column(modifier) {
        Text(label, color = TC.Slate500, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        NumberStepper(
            value = value.toDouble(),
            onChange = { onChange(it.toInt()) },
            step = step.toDouble(),
            min = min.toDouble(),
            max = max.toDouble(),
            isInt = true,
        )
    }
}
