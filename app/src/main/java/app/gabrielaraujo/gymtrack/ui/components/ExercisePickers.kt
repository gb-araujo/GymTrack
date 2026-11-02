package app.gabrielaraujo.gymtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.getSuggestions
import app.gabrielaraujo.gymtrack.ui.components.BtnVariant
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.theme.TC

private val muscles = listOf("Todos", "Peito", "Costas", "Perna", "Ombro", "Bíceps", "Tríceps", "Abdômen")

/** Catálogo completo com busca e filtro por músculo. [onSelect] recebe id e nome. */
@Composable
fun ExercisePickerDialog(exercises: List<Exercise>, onSelect: (String, String) -> Unit, onClose: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("Todos") }

    val filtered = exercises.filter { ex ->
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
                        m, color = if (sel) TC.White else TC.Slate400, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (sel) TC.Indigo600 else TC.Slate800).clickable { muscle = m }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { ex ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate800).clickable { onSelect(ex.id, ex.name) }.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExerciseIllustration(ex.id, Modifier.size(44.dp))
                        Column(Modifier.weight(1f)) {
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

/** Substituição de um exercício durante o treino: sugestões equivalentes ou qualquer outro do catálogo. */
@Composable
fun ExerciseSubstituteDialog(
    exercises: List<Exercise>,
    exerciseId: String,
    exerciseName: String,
    onSubstitute: (String) -> Unit,
    onClose: () -> Unit,
) {
    val suggestions = getSuggestions(exerciseId, exercises)
    val target = exercises.find { it.id == exerciseId }
    var showPicker by remember { mutableStateOf(false) }

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
                                .clickable { onSubstitute(ex.id); onClose() }.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseIllustration(ex.id, Modifier.size(48.dp))
                            Column(Modifier.weight(1f)) {
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
                GymTrackButton(
                    "Escolher outro exercício",
                    onClick = { showPicker = true },
                    variant = BtnVariant.Ghost,
                    leading = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    // Seletor completo, para quando nenhuma sugestão serve.
    if (showPicker) {
        ExercisePickerDialog(
            exercises,
            onSelect = { id, _ ->
                if (id != exerciseId) onSubstitute(id)
                showPicker = false
                onClose()
            },
            onClose = { showPicker = false },
        )
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
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TC.Indigo400),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
