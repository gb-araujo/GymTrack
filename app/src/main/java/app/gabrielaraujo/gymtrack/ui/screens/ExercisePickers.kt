package app.gabrielaraujo.gymtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.ui.RepStackViewModel
import app.gabrielaraujo.gymtrack.data.getSuggestions
import app.gabrielaraujo.gymtrack.ui.theme.TC

private val muscles = listOf("Todos", "Peito", "Costas", "Perna", "Ombro", "Bíceps", "Tríceps", "Abdômen")

@Composable
fun ExercisePickerDialog(vm: RepStackViewModel, onSelect: (String, String) -> Unit, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("Todos") }

    val filtered = state.exercises.filter { ex ->
        val matchSearch = ex.name.contains(search, ignoreCase = true)
        val matchMuscle = muscle == "Todos" || ex.primaryMuscle.contains(muscle)
        matchSearch && matchMuscle
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().padding(top = 60.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(TC.Slate900),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Selecionar Exercício", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Close, "Fechar", tint = TC.Slate400, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClose() }.padding(6.dp))
            }
            SearchField(search, { search = it }, Modifier.padding(horizontal = 16.dp))
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(muscles) { m ->
                    val sel = m == muscle
                    Text(
                        m, color = if (sel) TC.OnLime else TC.Slate400, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (sel) TC.Lime else TC.Slate800).clickable { muscle = m }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { ex ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate800).clickable { onSelect(ex.id, ex.name) }.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(ex.name, color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${ex.primaryMuscle} · ${ex.equipment}${if (ex.isCustom) " · Personalizado" else ""}", color = TC.Slate400, fontSize = 12.sp)
                        }
                        Text(ex.category, color = TC.Slate600, fontSize = 12.sp)
                    }
                }
                if (filtered.isEmpty()) {
                    items(listOf(Unit)) { Text("Nenhum exercício encontrado", color = TC.Slate500, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
fun ExerciseSubstituteDialog(vm: RepStackViewModel, exerciseId: String, exerciseName: String, exerciseIndex: Int, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val suggestions = getSuggestions(exerciseId, state.exercises)
    val target = state.exercises.find { it.id == exerciseId }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.7f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(TC.Slate900),
            ) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Substituir exercício", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(exerciseName, color = TC.Slate400, fontSize = 14.sp)
                    }
                    Icon(Icons.Default.Close, "Fechar", tint = TC.Slate400, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClose() }.padding(6.dp))
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { ex ->
                        val equivalent = ex.primaryMuscle == target?.primaryMuscle && ex.movementPattern == target.movementPattern
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate800)
                                .clickable { vm.substituteExercise(exerciseIndex, ex.id); onClose() }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(ex.name, color = TC.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("${ex.primaryMuscle} · ${ex.equipment}", color = TC.Slate400, fontSize = 12.sp)
                            }
                            Text(
                                if (equivalent) "Equivalente" else "Alternativa",
                                color = if (equivalent) TC.Emerald400 else TC.Slate400, fontSize = 11.sp,
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (equivalent) TC.Emerald600.copy(alpha = 0.3f) else TC.Slate700).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (suggestions.isEmpty()) {
                        items(listOf(Unit)) { Text("Nenhuma sugestão disponível", color = TC.Slate500, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate800).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Search, null, tint = TC.Slate500, modifier = Modifier.size(16.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Buscar exercício...", color = TC.Slate500, fontSize = 14.sp)
            BasicTextField(
                value = value, onValueChange = onValueChange,
                textStyle = TextStyle(color = TC.White, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TC.LimeSoft),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
