package app.gabrielaraujo.gymtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MonitorWeight
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.domain.BodyWeightEntry
import app.gabrielaraujo.gymtrack.data.exerciseDisplayName
import app.gabrielaraujo.gymtrack.ui.GymTrackViewModel
import app.gabrielaraujo.gymtrack.ui.components.LineChart
import app.gabrielaraujo.gymtrack.ui.formatBodyWeight
import app.gabrielaraujo.gymtrack.ui.formatDate
import app.gabrielaraujo.gymtrack.ui.formatShortDateTime
import app.gabrielaraujo.gymtrack.ui.formatWeight
import app.gabrielaraujo.gymtrack.ui.theme.TC
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private enum class ProgressTab { RECORDS, VOLUME, FREQUENCY, WEIGHT }

@Composable
fun ProgressScreen(vm: GymTrackViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(ProgressTab.RECORDS) }
    var selectedExercise by remember { mutableStateOf<String?>(null) }

    val prs = state.personalRecords.values.sortedByDescending { it.date }
    val byId = remember(state.exercises) { state.exercises.associateBy { it.id } }

    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Progresso", color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Summary("Treinos", state.totalWorkouts.toString(), Modifier.weight(1f))
                Summary("Este mês", monthCount(state.sessions.map { it.startTime }).toString(), Modifier.weight(1f))
                Summary("PRs", state.personalRecords.size.toString(), Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TabBtn("PRs", Icons.Default.EmojiEvents, tab == ProgressTab.RECORDS, Modifier.weight(1f)) { tab = ProgressTab.RECORDS }
                TabBtn("Volume", Icons.Default.BarChart, tab == ProgressTab.VOLUME, Modifier.weight(1f)) { tab = ProgressTab.VOLUME }
                TabBtn("Frequência", Icons.AutoMirrored.Filled.TrendingUp, tab == ProgressTab.FREQUENCY, Modifier.weight(1f)) { tab = ProgressTab.FREQUENCY }
                // Pesagens do Health Connect (balança, relógio ou Samsung Health), quando a importação está ligada.
                if (state.bodyWeights.isNotEmpty() || tab == ProgressTab.WEIGHT) {
                    TabBtn("Peso", Icons.Default.MonitorWeight, tab == ProgressTab.WEIGHT, Modifier.weight(1f)) { tab = ProgressTab.WEIGHT }
                }
            }
        }

        when (tab) {
            ProgressTab.RECORDS -> {
                if (prs.isEmpty()) {
                    item { EmptyState(Icons.Default.EmojiEvents, "Nenhum recorde ainda", "Complete treinos para registrar seus PRs") }
                } else {
                    items(prs, key = { it.exerciseId }) { pr ->
                        val expanded = selectedExercise == pr.exerciseId
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).clickable { selectedExercise = if (expanded) null else pr.exerciseId }.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.EmojiEvents, null, tint = TC.Yellow400, modifier = Modifier.size(14.dp))
                                    Text(exerciseDisplayName(pr.exerciseId, byId, pr.exerciseName), color = TC.White, fontWeight = FontWeight.SemiBold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (pr.weight > 0) "${formatWeight(pr.weight)} kg" else "Peso Corp.", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                    Text("× ${pr.reps} reps", color = TC.Slate500, fontSize = 12.sp)
                                }
                            }
                            if (expanded) {
                                val history = vm.exerciseHistory(pr.exerciseId)
                                    .reversed()
                                    .mapNotNull { (_, sets, _) -> sets.filter { it.completed }.maxByOrNull { it.weight }?.weight?.toFloat() }
                                    .filter { it > 0f }
                                if (history.size > 1) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Evolução de carga", color = TC.Slate500, fontSize = 12.sp)
                                    LineChart(history, height = 110.dp)
                                }
                            }
                        }
                    }
                }
            }
            ProgressTab.VOLUME -> {
                val volumeData = state.sessions.take(10).reversed().map { ((it.totalVolume ?: 0.0) / 1000).toFloat() }
                item {
                    if (volumeData.isEmpty()) {
                        EmptyState(Icons.Default.BarChart, "Sem dados de volume ainda", "")
                    } else {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(16.dp)) {
                            Text("Volume por Treino (toneladas)", color = TC.Slate300, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            LineChart(volumeData, height = 200.dp)
                        }
                    }
                }
            }
            ProgressTab.FREQUENCY -> {
                val weekly = weeklyCounts(state.sessions.map { it.startTime })
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(16.dp)) {
                        Text("Treinos por Semana", color = TC.Slate300, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        LineChart(weekly.map { it.toFloat() }, height = 180.dp, color = TC.Emerald500)
                    }
                }
                item {
                    val activeWeeks = max(1, weekly.count { it > 0 })
                    StatRow("Média semanal", "${(weekly.sum().toDouble() / activeWeeks * 10).roundToInt() / 10.0} treinos")
                }
                item { StatRow("Total de treinos", state.totalWorkouts.toString()) }
                item { StatRow("Melhor semana", "${weekly.maxOrNull() ?: 0} treinos") }
            }
            ProgressTab.WEIGHT -> {
                val weights = state.bodyWeights
                if (weights.isEmpty()) {
                    item { EmptyState(Icons.Default.MonitorWeight, "Sem pesagens", "Ligue \"Importar do relógio\" na tela de conta") }
                } else {
                    weightItems(weights)
                }
            }
        }
    }
}

private fun LazyListScope.weightItems(weights: List<BodyWeightEntry>) {
    val latest = weights.last()
    val recent = weights.takeLast(30)
    item {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(16.dp)) {
            Text("Peso corporal (kg)", color = TC.Slate300, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            LineChart(
                recent.map { it.kg.toFloat() },
                height = 180.dp,
                color = TC.Emerald500,
                baseline = floor(recent.minOf { it.kg }.toFloat()) - 1f,
            )
        }
    }
    item { StatRow("Atual", formatBodyWeight(latest.kg)) }
    // Compara com a pesagem de ~30 dias atrás (ou a mais antiga disponível).
    val reference = weights.lastOrNull { it.time <= latest.time - 30L * 86_400_000 } ?: weights.first()
    if (reference !== latest) {
        val diff = latest.kg - reference.kg
        val sign = if (diff > 0) "+" else if (diff < 0) "−" else ""
        item { StatRow("Desde ${formatDate(reference.time, "d MMM")}", "$sign${formatBodyWeight(abs(diff))}") }
    }
    item { StatRow("Última pesagem", formatShortDateTime(latest.time)) }
}

@Composable
private fun Summary(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TC.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = TC.Slate500, fontSize = 12.sp)
    }
}

@Composable
private fun TabBtn(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) TC.Indigo600 else androidx.compose.ui.graphics.Color.Transparent).clickable { onClick() }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) TC.White else TC.Slate400, modifier = Modifier.size(13.dp))
        Spacer(Modifier.size(4.dp))
        Text(label, color = if (selected) TC.White else TC.Slate400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TC.Slate400, fontSize = 14.sp)
        Text(value, color = TC.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = TC.Slate700, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = TC.Slate400)
        if (subtitle.isNotEmpty()) Text(subtitle, color = TC.Slate600, fontSize = 13.sp)
    }
}

private fun monthCount(times: List<Long>): Int {
    val now = Calendar.getInstance()
    return times.count {
        val c = Calendar.getInstance().apply { timeInMillis = it }
        c.get(Calendar.MONTH) == now.get(Calendar.MONTH) && c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    }
}

private fun weeklyCounts(times: List<Long>): List<Int> {
    val now = System.currentTimeMillis()
    val week = 7L * 24 * 3600_000
    return (7 downTo 0).map { i ->
        val start = now - i * week
        val end = start + week
        times.count { it in start until end }
    }
}
