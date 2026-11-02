package app.gabrielaraujo.gymtrack.ui.history

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.sessionTotals
import app.gabrielaraujo.gymtrack.session.withAddedSet
import app.gabrielaraujo.gymtrack.ui.components.BtnVariant
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.components.NumberStepper
import app.gabrielaraujo.gymtrack.ui.formatDuration
import app.gabrielaraujo.gymtrack.ui.formatFullDate
import app.gabrielaraujo.gymtrack.ui.formatVolume
import app.gabrielaraujo.gymtrack.ui.formatWeight
import app.gabrielaraujo.gymtrack.ui.theme.TC
import app.gabrielaraujo.gymtrack.ui.watchStats
import kotlin.math.roundToInt

@Composable
fun HistoryDetailScreen(vm: HistoryViewModel, sessionId: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val session = state.sessions.find { it.id == sessionId }
    var draft by remember(sessionId) { mutableStateOf<WorkoutSession?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(TC.Background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TC.Slate400, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (draft != null) draft = null else onBack() }.padding(6.dp))
            Column(Modifier.weight(1f)) {
                if (session != null) {
                    Text(formatFullDate(session.startTime), color = TC.Slate500, fontSize = 11.sp)
                    Text("Treino ${session.workoutDayLabel} — ${session.workoutDayName}", color = TC.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Treino não encontrado", color = TC.Slate400)
                }
            }
            if (session != null && draft == null) {
                Icon(Icons.Default.Edit, "Editar treino", tint = TC.Slate400, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { draft = session }.padding(6.dp))
                Icon(Icons.Default.Delete, "Excluir treino", tint = TC.Red400, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { confirmDelete = true }.padding(6.dp))
            }
        }

        if (session == null) return@Column

        draft?.let { editing ->
            SessionEditor(
                session = editing,
                onChange = { draft = it },
                onCancel = { draft = null },
                onSave = { vm.update(editing); draft = null },
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("Duração", formatDuration(session.duration), Modifier.weight(1f))
                    MiniStat("Exercícios", session.exercises.size.toString(), Modifier.weight(1f))
                    MiniStat("Séries", (session.totalSets ?: 0).toString(), Modifier.weight(1f))
                    MiniStat("Volume", formatVolume(session.totalVolume), Modifier.weight(1f))
                }
            }

            val watch = watchStats(session.avgHeartRate, session.maxHeartRate, session.calories)
            if (watch.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        watch.forEach { (label, value) -> MiniStat(label, value, Modifier.weight(1f)) }
                    }
                }
            }

            if (session.prsAchieved.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Yellow500.copy(alpha = 0.12f)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.EmojiEvents, null, tint = TC.Yellow400, modifier = Modifier.size(16.dp))
                            Text("Recordes Pessoais", color = TC.Yellow400, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        session.prsAchieved.forEach { id ->
                            val pr = state.records[id]
                            Text("🏆 ${pr?.exerciseName ?: id}${if (pr != null) " · ${formatWeight(pr.weight)}kg × ${pr.reps}" else ""}", color = TC.Yellow400.copy(alpha = 0.9f), fontSize = 14.sp)
                        }
                    }
                }
            }

            for (ex in session.exercises) {
                item {
                    val completedSets = ex.sets.filter { it.completed }
                    val volume = sessionTotals(listOf(ex)).volume
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(ex.name, color = TC.White, fontWeight = FontWeight.Bold)
                                if (ex.substitutedFrom != null) Text("Substituiu outro exercício", color = TC.Slate500, fontSize = 12.sp)
                            }
                            if (volume > 0) Text("${volume.roundToInt()} kg vol.", color = TC.Slate500, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (completedSets.isEmpty()) {
                            Text("Nenhuma série registrada", color = TC.Slate600, fontSize = 12.sp)
                        } else {
                            completedSets.forEachIndexed { i, set ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(TC.Emerald600.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, null, tint = TC.Emerald400, modifier = Modifier.size(12.dp))
                                    }
                                    Text("Série ${i + 1}", color = TC.Slate300, fontSize = 14.sp)
                                    Text(
                                        (if (set.weight > 0) "${formatWeight(set.weight)} kg" else "Peso corp.") + " × ${set.reps}",
                                        color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmDelete = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(24.dp)) {
                Text("Excluir treino?", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("O treino sai do histórico e os recordes pessoais são recalculados. Não dá para desfazer.", color = TC.Slate400, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GymTrackButton("Cancelar", onClick = { confirmDelete = false }, variant = BtnVariant.Ghost, modifier = Modifier.weight(1f))
                    GymTrackButton("Excluir", onClick = { confirmDelete = false; vm.delete(sessionId); onBack() }, variant = BtnVariant.Danger, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TC.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(label, color = TC.Slate500, fontSize = 10.sp)
    }
}

@Composable
private fun SessionEditor(
    session: WorkoutSession,
    onChange: (WorkoutSession) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun updateExercise(index: Int, transform: (SessionExercise) -> SessionExercise) =
        onChange(session.copy(exercises = session.exercises.toMutableList().also { it[index] = transform(it[index]) }))

    fun updateSet(exIndex: Int, setIndex: Int, transform: (SetRecord) -> SetRecord) =
        updateExercise(exIndex) { ex -> ex.copy(sets = ex.sets.toMutableList().also { it[setIndex] = transform(it[setIndex]) }) }

    Column(modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Toque no ✓ para marcar/desmarcar a série. Recordes e volume são recalculados ao salvar.", color = TC.Slate500, fontSize = 12.sp)
            }
            session.exercises.forEachIndexed { exIndex, ex ->
                item(key = ex.id) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(16.dp)) {
                        Text(ex.name, color = TC.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        ex.sets.forEachIndexed { setIndex, set ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (set.completed) TC.Emerald600 else TC.Slate800)
                                        .clickable { updateSet(exIndex, setIndex) { it.copy(completed = !it.completed) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (set.completed) Icon(Icons.Default.Check, "Série concluída", tint = TC.White, modifier = Modifier.size(14.dp))
                                    else Text("${setIndex + 1}", color = TC.Slate400, fontSize = 13.sp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("KG", color = TC.Slate500, fontSize = 10.sp)
                                    NumberStepper(set.weight, { w -> updateSet(exIndex, setIndex) { it.copy(weight = w) } }, step = 2.5, min = 0.0, max = 500.0)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("REPS", color = TC.Slate500, fontSize = 10.sp)
                                    NumberStepper(set.reps.toDouble(), { r -> updateSet(exIndex, setIndex) { it.copy(reps = r.toInt()) } }, step = 1.0, min = 1.0, max = 100.0, isInt = true)
                                }
                                if (ex.sets.size > 1) {
                                    Icon(
                                        Icons.Default.Close, "Remover série", tint = TC.Slate500,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                            .clickable { updateExercise(exIndex) { e -> e.copy(sets = e.sets.filterIndexed { i, _ -> i != setIndex }) } }
                                            .padding(4.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            "+ Adicionar série", color = TC.Indigo400, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { updateExercise(exIndex) { e -> e.withAddedSet(completed = true) } }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GymTrackButton("Cancelar", onClick = onCancel, variant = BtnVariant.Ghost, modifier = Modifier.weight(1f))
            GymTrackButton("Salvar", onClick = onSave, modifier = Modifier.weight(1f))
        }
    }
}
