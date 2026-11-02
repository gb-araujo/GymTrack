package app.gabrielaraujo.gymtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.domain.ExerciseMetric
import app.gabrielaraujo.gymtrack.domain.ExerciseSessionEntry
import app.gabrielaraujo.gymtrack.domain.MAX_REPS_FOR_1RM
import app.gabrielaraujo.gymtrack.domain.ReportPeriod
import app.gabrielaraujo.gymtrack.domain.availableMetrics
import app.gabrielaraujo.gymtrack.domain.beats
import app.gabrielaraujo.gymtrack.domain.compareLastSessions
import app.gabrielaraujo.gymtrack.domain.exerciseDisplayName
import app.gabrielaraujo.gymtrack.domain.exerciseHistory
import app.gabrielaraujo.gymtrack.domain.isDumbbellExercise
import app.gabrielaraujo.gymtrack.domain.metric
import app.gabrielaraujo.gymtrack.domain.recordProgression
import app.gabrielaraujo.gymtrack.ui.GymTrackViewModel
import app.gabrielaraujo.gymtrack.ui.components.ChartPoint
import app.gabrielaraujo.gymtrack.ui.components.ChipRow
import app.gabrielaraujo.gymtrack.ui.components.ProgressChart
import app.gabrielaraujo.gymtrack.ui.components.SectionLabel
import app.gabrielaraujo.gymtrack.ui.formatDate
import app.gabrielaraujo.gymtrack.ui.formatDecimal
import app.gabrielaraujo.gymtrack.ui.formatDelta
import app.gabrielaraujo.gymtrack.ui.formatSet
import app.gabrielaraujo.gymtrack.ui.formatVolume
import app.gabrielaraujo.gymtrack.ui.formatWeight
import app.gabrielaraujo.gymtrack.ui.theme.TC
import app.gabrielaraujo.gymtrack.ui.timeAgo
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ExerciseProgressScreen(vm: GymTrackViewModel, exerciseId: String, onBack: () -> Unit, onOpenSession: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val catalogEntry = state.exercises.find { it.id == exerciseId }
    val history = remember(state.sessions, exerciseId) { exerciseHistory(state.sessions, exerciseId) }
    val name = exerciseDisplayName(exerciseId, catalogEntry, history.lastOrNull()?.name ?: state.personalRecords[exerciseId]?.exerciseName)

    var period by rememberSaveable { mutableStateOf(ReportPeriod.DAYS_90) }
    val metrics = remember(history) { availableMetrics(history) }
    var chosenMetric by rememberSaveable(exerciseId) { mutableStateOf<ExerciseMetric?>(null) }
    val metric = chosenMetric?.takeIf { it in metrics } ?: metrics.firstOrNull() ?: ExerciseMetric.BEST_SET

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    Column(Modifier.fillMaxSize().background(TC.Background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TC.Slate400, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onBack() }.padding(6.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                catalogEntry?.let { ex ->
                    Text(listOf(ex.primaryMuscle, ex.equipment).filter { it.isNotBlank() }.joinToString(" · "), color = TC.Slate500, fontSize = 12.sp)
                }
            }
        }

        if (history.isEmpty()) {
            EmptyHint("Nenhuma série concluída deste exercício ainda.", Modifier.padding(16.dp))
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isDumbbellExercise(catalogEntry)) {
                item {
                    Note("Halteres: as cargas são exibidas como foram registradas. Para a comparação valer, registre sempre do mesmo jeito (ex.: o peso de cada halter).")
                }
            }

            item { Highlights(history) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipRow(ReportPeriod.entries, period, { it.label }, { period = it })
                    if (metrics.size > 1) ChipRow(metrics, metric, { it.label }, { chosenMetric = it })
                }
            }

            item {
                val inPeriod = history.filter { period.contains(it.date, today, zone) }
                val points = inPeriod.mapNotNull { e -> e.metric(metric)?.let { ChartPoint(e.date, it, chartDetail(e, metric)) } }
                Card {
                    ProgressChart(
                        points = points,
                        yAxisLabel = metric.unit,
                        formatY = { if (metric == ExerciseMetric.VOLUME) formatVolume(it).removeSuffix(" kg") else formatDecimal(it) },
                        formatX = { formatDate(it, "d MMM yy") },
                        insufficientText = when {
                            inPeriod.isEmpty() -> "Sem registros deste exercício em ${period.label.lowercase()}. Isso não indica perda de progresso, só falta de dados."
                            else -> "Só 1 registro em ${period.label.lowercase()}: são necessários pelo menos 2 para o gráfico."
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(metricDefinition(metric), color = TC.Slate500, fontSize = 11.sp)
                }
            }

            compareLastSessions(history)?.let { cmp ->
                item {
                    Card {
                        SectionLabel("Última sessão × anterior")
                        Spacer(Modifier.height(4.dp))
                        Text("${formatDate(cmp.previous.date, "d MMM")} → ${formatDate(cmp.current.date, "d MMM")}", color = TC.Slate500, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        CompareRow("Melhor série", formatSet(cmp.previous.best.weight, cmp.previous.best.reps), formatSet(cmp.current.best.weight, cmp.current.best.reps),
                            if (cmp.weightDelta != 0.0) formatDelta(cmp.weightDelta, "kg") else formatDelta(cmp.repsAtTopDelta.toDouble(), "reps"))
                        cmp.oneRepMaxDelta?.let {
                            CompareRow("1RM estimado", "${formatDecimal(cmp.previous.estimatedOneRepMax!!)} kg", "${formatDecimal(cmp.current.estimatedOneRepMax!!)} kg", formatDelta(it, "kg"))
                        }
                        CompareRow("Séries", cmp.previous.sets.size.toString(), cmp.current.sets.size.toString(), formatDelta(cmp.setsDelta.toDouble(), ""))
                        CompareRow("Repetições", cmp.previous.totalReps.toString(), cmp.current.totalReps.toString(), formatDelta((cmp.current.totalReps - cmp.previous.totalReps).toDouble(), ""))
                        if (cmp.current.volume > 0 || cmp.previous.volume > 0) {
                            CompareRow("Volume", formatVolume(cmp.previous.volume), formatVolume(cmp.current.volume), formatDelta(cmp.volumeDelta, "kg"))
                        }
                    }
                }
            }

            item {
                // Recorde que não veio de nenhuma sessão deste aparelho (ex.: backup do app web).
                val baseline = state.personalRecords[exerciseId]?.takeIf { pr -> state.sessions.none { it.id == pr.sessionId } }
                val steps = recordProgression(history, baseline)
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.EmojiEvents, null, tint = TC.Yellow400, modifier = Modifier.size(14.dp))
                        SectionLabel("Recordes pessoais")
                    }
                    Spacer(Modifier.height(8.dp))
                    steps.reversed().forEachIndexed { i, step ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                formatDate(step.date, "d MMM yyyy") + if (step.sessionId == null) " (importado)" else "",
                                color = if (i == 0) TC.Slate300 else TC.Slate500, fontSize = 13.sp,
                            )
                            Text(formatSet(step.weight, step.reps), color = if (i == 0) TC.Yellow400 else TC.Slate400, fontSize = 13.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            item { SectionLabel("Últimas execuções", modifier = Modifier.padding(top = 4.dp)) }
            items(history.reversed().take(RECENT_LIMIT), key = { it.sessionId }) { entry ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate900).clickable { onOpenSession(entry.sessionId) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDate(entry.date, "d MMM yyyy"), color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (entry.substituted) Text("substituição", color = TC.Slate500, fontSize = 11.sp)
                        }
                        Text(entry.sets.joinToString("  ·  ") { "${formatWeight(it.weight)}×${it.reps}" }, color = TC.Slate400, fontSize = 12.sp)
                        if (entry.skippedSets > 0) Text("${entry.skippedSets} série(s) não concluída(s)", color = TC.Slate600, fontSize = 11.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TC.Slate700)
                }
            }
        }
    }
}

private const val RECENT_LIMIT = 15

@Composable
private fun Highlights(history: List<ExerciseSessionEntry>) {
    val bestEntry = history.reduce { a, b -> if (b.best.beats(a.best.weight, a.best.reps)) b else a }
    val best1rm = history.filter { it.estimatedOneRepMax != null }.maxByOrNull { it.estimatedOneRepMax!! }
    val last = history.last()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Highlight("Melhor série", formatSet(bestEntry.best.weight, bestEntry.best.reps), formatDate(bestEntry.date, "d MMM yy"), Modifier.weight(1f))
        if (best1rm != null) {
            Highlight("1RM estimado", "${formatDecimal(best1rm.estimatedOneRepMax!!)} kg", formatDate(best1rm.date, "d MMM yy"), Modifier.weight(1f))
        }
        Highlight("Sessões", history.size.toString(), "última ${timeAgo(last.date)}", Modifier.weight(1f))
    }
}

@Composable
private fun Highlight(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(12.dp)) {
        Text(label, color = TC.Slate500, fontSize = 11.sp)
        Text(value, color = TC.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(sub, color = TC.Slate600, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun CompareRow(label: String, before: String, after: String, delta: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TC.Slate400, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("$before → $after", color = TC.White, fontSize = 13.sp)
        Text(
            delta,
            color = when {
                delta.startsWith("+") -> TC.Emerald400
                delta.startsWith("−") -> TC.Orange400
                else -> TC.Slate500
            },
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun chartDetail(e: ExerciseSessionEntry, metric: ExerciseMetric): String = when (metric) {
    ExerciseMetric.BEST_SET -> formatSet(e.best.weight, e.best.reps)
    ExerciseMetric.REPS -> "${e.maxReps} reps"
    ExerciseMetric.ONE_REP_MAX -> "${formatDecimal(e.estimatedOneRepMax ?: 0.0)} kg"
    ExerciseMetric.VOLUME -> "${formatVolume(e.volume)} · ${e.sets.size} séries"
}

private fun metricDefinition(metric: ExerciseMetric): String = when (metric) {
    ExerciseMetric.BEST_SET -> "Carga da melhor série da sessão (maior carga; empate decidido pelas repetições). É a mesma regra dos recordes."
    ExerciseMetric.REPS -> "Maior número de repetições numa série concluída da sessão."
    ExerciseMetric.ONE_REP_MAX -> "Estimativa pela fórmula de Epley, usando séries de 1 a $MAX_REPS_FOR_1RM repetições. É uma aproximação, não um teste de carga máxima."
    ExerciseMetric.VOLUME -> "Soma de carga × repetições das séries concluídas. Métrica complementar: muda com o número de séries, não só com a força."
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(16.dp)) { content() }
}

@Composable
internal fun Note(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate800.copy(alpha = 0.6f)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Info, null, tint = TC.Slate400, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Text(text, color = TC.Slate400, fontSize = 12.sp)
    }
}

@Composable
internal fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TC.Slate500, fontSize = 13.sp, modifier = modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
