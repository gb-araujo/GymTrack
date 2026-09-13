package app.gabrielaraujo.track.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.track.ui.TrackViewModel
import app.gabrielaraujo.track.ui.formatDuration
import app.gabrielaraujo.track.ui.formatFullDate
import app.gabrielaraujo.track.ui.formatVolume
import app.gabrielaraujo.track.ui.formatWeight
import app.gabrielaraujo.track.ui.theme.TC
import kotlin.math.roundToInt

@Composable
fun HistoryDetailScreen(vm: TrackViewModel, sessionId: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session = state.sessions.find { it.id == sessionId }

    Column(Modifier.fillMaxSize().background(TC.Background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TC.Slate400, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onBack() }.padding(6.dp))
            Column {
                if (session != null) {
                    Text(formatFullDate(session.startTime), color = TC.Slate500, fontSize = 11.sp)
                    Text("Treino ${session.workoutDayLabel} — ${session.workoutDayName}", color = TC.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Treino não encontrado", color = TC.Slate400)
                }
            }
        }

        if (session == null) return@Column

        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("Duração", formatDuration(session.duration), Modifier.weight(1f))
                    MiniStat("Exercícios", session.exercises.size.toString(), Modifier.weight(1f))
                    MiniStat("Séries", (session.totalSets ?: 0).toString(), Modifier.weight(1f))
                    MiniStat("Volume", formatVolume(session.totalVolume), Modifier.weight(1f))
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
                            val pr = state.personalRecords[id]
                            Text("🏆 ${pr?.exerciseName ?: id}${if (pr != null) " · ${formatWeight(pr.weight)}kg × ${pr.reps}" else ""}", color = TC.Yellow400.copy(alpha = 0.9f), fontSize = 14.sp)
                        }
                    }
                }
            }

            for (ex in session.exercises) {
                item {
                    val completedSets = ex.sets.filter { it.completed }
                    val volume = completedSets.sumOf { it.weight * it.reps }
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
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TC.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(label, color = TC.Slate500, fontSize = 10.sp)
    }
}
