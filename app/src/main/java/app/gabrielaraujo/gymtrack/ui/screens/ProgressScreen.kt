package app.gabrielaraujo.gymtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.ExerciseMetric
import app.gabrielaraujo.gymtrack.domain.ExerciseTrend
import app.gabrielaraujo.gymtrack.domain.MIN_SESSIONS_FOR_TREND
import app.gabrielaraujo.gymtrack.domain.ReportPeriod
import app.gabrielaraujo.gymtrack.domain.availableMetrics
import app.gabrielaraujo.gymtrack.domain.consistencyReport
import app.gabrielaraujo.gymtrack.domain.exerciseDisplayName
import app.gabrielaraujo.gymtrack.domain.exerciseHistory
import app.gabrielaraujo.gymtrack.domain.exerciseSummaries
import app.gabrielaraujo.gymtrack.domain.metric
import app.gabrielaraujo.gymtrack.domain.normalizeForSearch
import app.gabrielaraujo.gymtrack.domain.progressOverview
import app.gabrielaraujo.gymtrack.domain.recordProgression
import app.gabrielaraujo.gymtrack.domain.toLocalDate
import app.gabrielaraujo.gymtrack.domain.weekStart
import app.gabrielaraujo.gymtrack.domain.weeklyVolume
import app.gabrielaraujo.gymtrack.ui.GymTrackViewModel
import app.gabrielaraujo.gymtrack.ui.UiState
import app.gabrielaraujo.gymtrack.ui.components.BarChart
import app.gabrielaraujo.gymtrack.ui.components.BarItem
import app.gabrielaraujo.gymtrack.ui.components.ChartPoint
import app.gabrielaraujo.gymtrack.ui.components.ChipRow
import app.gabrielaraujo.gymtrack.ui.components.ProgressChart
import app.gabrielaraujo.gymtrack.ui.components.SectionLabel
import app.gabrielaraujo.gymtrack.ui.components.SegmentedTabs
import app.gabrielaraujo.gymtrack.ui.formatBodyWeight
import app.gabrielaraujo.gymtrack.ui.formatDate
import app.gabrielaraujo.gymtrack.ui.formatDecimal
import app.gabrielaraujo.gymtrack.ui.formatDelta
import app.gabrielaraujo.gymtrack.ui.formatSet
import app.gabrielaraujo.gymtrack.ui.formatShortDateTime
import app.gabrielaraujo.gymtrack.ui.formatVolume
import app.gabrielaraujo.gymtrack.ui.theme.TC
import app.gabrielaraujo.gymtrack.ui.timeAgo
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ProgressTab(val label: String) {
    OVERVIEW("Visão geral"),
    EXERCISES("Exercícios"),
    RECORDS("Recordes"),
    CONSISTENCY("Consistência"),
    BODY("Corpo"),
}

@Composable
fun ProgressScreen(vm: GymTrackViewModel, onOpenExercise: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ProgressTab.OVERVIEW) }
    var period by rememberSaveable { mutableStateOf(ReportPeriod.DAYS_90) }
    val catalog = remember(state.exercises) { state.exercises.associateBy { it.id } }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Progresso", color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item { SegmentedTabs(ProgressTab.entries, tab, { it.label }, { tab = it }) }

        if (state.sessions.isEmpty() && tab != ProgressTab.BODY) {
            item { EmptyState(Icons.Default.FitnessCenter, "Nenhum treino concluído ainda", "Os relatórios aparecem depois do primeiro treino") }
            return@LazyColumn
        }

        if (tab == ProgressTab.OVERVIEW || tab == ProgressTab.CONSISTENCY || tab == ProgressTab.BODY) {
            item { ChipRow(ReportPeriod.entries, period, { it.label }, { period = it }) }
        }

        when (tab) {
            ProgressTab.OVERVIEW -> overviewItems(state.sessions, catalog, period, today, zone, onOpenExercise)
            ProgressTab.EXERCISES -> item { ExerciseList(state.sessions, catalog, onOpenExercise) }
            ProgressTab.RECORDS -> recordItems(state, catalog, onOpenExercise)
            ProgressTab.CONSISTENCY -> consistencyItems(state.sessions, period, today, zone)
            ProgressTab.BODY -> bodyItems(state.bodyWeights, state.sessions, catalog, period, today, zone)
        }
    }
}

// ---------------- Visão geral ----------------

private fun LazyListScope.overviewItems(
    sessions: List<WorkoutSession>,
    catalog: Map<String, Exercise>,
    period: ReportPeriod,
    today: LocalDate,
    zone: ZoneId,
    onOpenExercise: (String) -> Unit,
) {
    val overview = progressOverview(sessions, catalog, period, today, zone)
    val consistency = consistencyReport(sessions.map { it.startTime }, period, today, zone)

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Treinos", overview.workouts.toString(), consistency.previousPeriodWorkouts?.let { "$it no período anterior" }, Modifier.weight(1f))
                Tile("Frequência média", overview.averagePerWeek?.let { "${formatDecimal(it)}/sem." } ?: "—", "semanas completas", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Recordes batidos", overview.prs.toString(), null, Modifier.weight(1f))
                Tile(
                    "Em evolução",
                    if (overview.trends.isEmpty()) "—" else "${overview.improving.size} de ${overview.trends.size}",
                    "exercícios avaliados",
                    Modifier.weight(1f),
                )
            }
        }
    }

    item {
        Card {
            SectionLabel("Principais mudanças")
            Spacer(Modifier.height(8.dp))
            when {
                overview.trends.isEmpty() -> Text(
                    "Ainda não há dados suficientes para apontar tendências. É preciso ter feito o mesmo exercício pelo menos $MIN_SESSIONS_FOR_TREND vezes no período.",
                    color = TC.Slate400, fontSize = 13.sp,
                )
                overview.improving.isEmpty() -> Text(
                    "Nenhum dos ${overview.trends.size} exercícios avaliados teve aumento entre a primeira e a última sessão do período.",
                    color = TC.Slate400, fontSize = 13.sp,
                )
                else -> overview.improving.take(5).forEach { TrendRow(it) { onOpenExercise(it.exerciseId) } }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Compara a primeira e a última sessão de cada exercício no período (1RM estimado, ou a carga quando ele não se aplica). Exercícios com menos de $MIN_SESSIONS_FOR_TREND sessões não são avaliados.",
                color = TC.Slate600, fontSize = 11.sp,
            )
        }
    }

    item {
        val firstDay = period.startDate(today) ?: sessions.minOfOrNull { it.startTime.toLocalDate(zone) } ?: today
        val start = maxOf(firstDay, today.weekStart().minusWeeks(MAX_BARS - 1L))
        val weeks = weeklyVolume(sessions, start, today, zone)
        Card {
            SectionLabel("Volume semanal (complementar)")
            Spacer(Modifier.height(8.dp))
            if (weeks.all { it.volume == 0.0 }) {
                EmptyHint("Sem volume registrado no período.")
            } else {
                BarChart(
                    weeks.map { BarItem(weekLabel(it.weekStart), it.volume, "Semana de ${weekLabel(it.weekStart)}: ${formatVolume(it.volume)} · ${it.sessions} treino(s)") },
                    yAxisLabel = "kg",
                    formatY = { if (it <= 0) "0" else formatVolume(it).removeSuffix(" kg") },
                    labelEvery = (weeks.size + 5) / 6,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Carga × repetições das séries concluídas. Depende de quais exercícios e quantas séries entraram na semana, então subir ou cair não indica, sozinho, evolução.",
                color = TC.Slate600, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TrendRow(trend: ExerciseTrend, onClick: () -> Unit) {
    val unit = if (trend.metric == ExerciseMetric.REPS) "reps" else "kg"
    val metricName = when (trend.metric) {
        ExerciseMetric.ONE_REP_MAX -> "1RM est."
        ExerciseMetric.REPS -> "Repetições"
        else -> "Carga"
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(trend.name, color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("$metricName ${formatDecimal(trend.first)} → ${formatDecimal(trend.last)} $unit · ${trend.sessions} sessões", color = TC.Slate500, fontSize = 12.sp)
        }
        Text(formatDelta(trend.change, unit), color = TC.Emerald400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Exercícios ----------------

@Composable
private fun ExerciseList(sessions: List<WorkoutSession>, catalog: Map<String, Exercise>, onOpenExercise: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val summaries = remember(sessions, catalog) { exerciseSummaries(sessions, catalog) }
    val q = normalizeForSearch(query)
    val filtered = if (q.isEmpty()) summaries else summaries.filter { normalizeForSearch(it.name).contains(q) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(query, { query = it })
        Text("${summaries.size} exercícios com registros · mais recentes primeiro", color = TC.Slate500, fontSize = 12.sp)
        if (filtered.isEmpty()) EmptyHint("Nenhum exercício encontrado.")
        filtered.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TC.Slate900).clickable { onOpenExercise(s.exerciseId) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${s.sessions} ${if (s.sessions == 1) "sessão" else "sessões"} · última ${timeAgo(s.lastDate)}", color = TC.Slate500, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatSet(s.best.weight, s.best.reps), color = TC.Slate300, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("melhor série", color = TC.Slate600, fontSize = 10.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TC.Slate700)
            }
        }
    }
}

// ---------------- Recordes ----------------

private fun LazyListScope.recordItems(state: UiState, catalog: Map<String, Exercise>, onOpenExercise: (String) -> Unit) {
    val prs = state.personalRecords.values.sortedByDescending { it.date }
    if (prs.isEmpty()) {
        item { EmptyState(Icons.Default.EmojiEvents, "Nenhum recorde ainda", "Complete treinos para registrar seus PRs") }
        return
    }
    item { Text("Recorde = série de maior carga (empate decidido pelas repetições). Toque para ver a evolução.", color = TC.Slate500, fontSize = 12.sp) }
    items(prs, key = { it.exerciseId }) { pr ->
        val steps = remember(state.sessions, pr) {
            // Recorde que não veio de nenhuma sessão deste aparelho (ex.: backup do app web) é o ponto de partida.
            val baseline = pr.takeIf { record -> state.sessions.none { it.id == record.sessionId } }
            recordProgression(exerciseHistory(state.sessions, pr.exerciseId), baseline)
        }
        val previous = steps.getOrNull(steps.size - 2)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).clickable { onOpenExercise(pr.exerciseId) }.padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EmojiEvents, null, tint = TC.Yellow400, modifier = Modifier.size(14.dp))
                        Text(exerciseDisplayName(pr.exerciseId, catalog[pr.exerciseId], pr.exerciseName), color = TC.White, fontWeight = FontWeight.SemiBold)
                    }
                    Text(formatDate(pr.date, "d MMM yyyy"), color = TC.Slate500, fontSize = 12.sp)
                }
                Text(formatSet(pr.weight, pr.reps), color = TC.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
            if (previous != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Anterior: ${formatSet(previous.weight, previous.reps)} em ${formatDate(previous.date, "d MMM yy")} · ${steps.size} recordes no total",
                    color = TC.Slate500, fontSize = 12.sp,
                )
            }
        }
    }
}

// ---------------- Consistência ----------------

private fun LazyListScope.consistencyItems(sessions: List<WorkoutSession>, period: ReportPeriod, today: LocalDate, zone: ZoneId) {
    val times = sessions.map { it.startTime }
    val report = consistencyReport(times, period, today, zone)

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Treinos", report.workouts.toString(), report.previousPeriodWorkouts?.let { "$it no período anterior" }, Modifier.weight(1f))
                Tile("Média por semana", report.averagePerWeek?.let { formatDecimal(it) } ?: "—", "semanas completas", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Dias treinados", report.trainedDays.size.toString(), null, Modifier.weight(1f))
                Tile("Semanas seguidas", report.currentStreakWeeks.toString(), "melhor: ${report.bestStreakWeeks}", Modifier.weight(1f))
            }
        }
    }

    item {
        val weeks = report.weeks.takeLast(MAX_BARS)
        Card {
            SectionLabel("Treinos por semana")
            Spacer(Modifier.height(8.dp))
            BarChart(
                weeks.map { BarItem(weekLabel(it.weekStart), it.count.toDouble(), "Semana de ${weekLabel(it.weekStart)}: ${it.count} treino(s)") },
                yAxisLabel = "treinos",
                formatY = { formatDecimal(it) },
                color = TC.Emerald500,
                labelEvery = (weeks.size + 5) / 6,
            )
            if (report.weeks.size > weeks.size) {
                Text("Mostrando as últimas $MAX_BARS semanas.", color = TC.Slate600, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    item {
        Card {
            SectionLabel("Calendário (últimas $CALENDAR_WEEKS semanas)")
            Spacer(Modifier.height(8.dp))
            ActivityCalendar(times.map { it.toLocalDate(zone) }.toSet(), today)
        }
    }

    item {
        Text(
            "Semanas de segunda a domingo, no fuso do aparelho. Semana seguida = pelo menos 1 treino; a semana atual só entra na sequência quando já tem treino. A média considera só semanas completas, incluindo as sem treino.",
            color = TC.Slate600, fontSize = 11.sp,
        )
    }
}

@Composable
private fun ActivityCalendar(trained: Set<LocalDate>, today: LocalDate) {
    val firstWeek = today.weekStart().minusWeeks(CALENDAR_WEEKS - 1L)
    val dayLabels = listOf("S", "T", "Q", "Q", "S", "S", "D")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            dayLabels.forEach { Box(Modifier.size(width = 12.dp, height = 14.dp), contentAlignment = Alignment.Center) { Text(it, color = TC.Slate600, fontSize = 9.sp) } }
        }
        (0 until CALENDAR_WEEKS).forEach { w ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until 7).forEach { d ->
                    val date = firstWeek.plusWeeks(w.toLong()).plusDays(d.toLong())
                    val color = when {
                        date.isAfter(today) -> TC.Background
                        date in trained -> TC.Emerald500
                        else -> TC.Slate800
                    }
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(3.dp)).background(color))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 15.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(weekLabel(firstWeek), color = TC.Slate600, fontSize = 10.sp)
        Text("hoje", color = TC.Slate600, fontSize = 10.sp)
    }
}

// ---------------- Corpo ----------------

private fun LazyListScope.bodyItems(
    weights: List<BodyWeightEntry>,
    sessions: List<WorkoutSession>,
    catalog: Map<String, Exercise>,
    period: ReportPeriod,
    today: LocalDate,
    zone: ZoneId,
) {
    if (weights.isEmpty()) {
        item { EmptyState(Icons.Default.MonitorWeight, "Sem pesagens", "Ligue \"Importar do relógio\" na tela de conta para trazer o peso do Health Connect") }
        return
    }
    val inPeriod = weights.filter { period.contains(it.time, today, zone) }
    item {
        Card {
            ProgressChart(
                inPeriod.map { ChartPoint(it.time, it.kg, formatBodyWeight(it.kg)) },
                yAxisLabel = "Peso corporal (kg)",
                formatY = { formatDecimal(it) },
                formatX = { formatDate(it, "d MMM yy") },
                color = TC.Emerald500,
                insufficientText = if (inPeriod.isEmpty()) "Sem pesagens em ${period.label.lowercase()}." else "Só 1 pesagem no período.",
            )
        }
    }
    val latest = weights.last()
    item { StatRow("Atual", formatBodyWeight(latest.kg)) }
    if (inPeriod.size >= 2) {
        item { StatRow("Variação no período", formatDelta(inPeriod.last().kg - inPeriod.first().kg, "kg")) }
    }
    item { StatRow("Última pesagem", formatShortDateTime(latest.time)) }
    item { WeightVsExercise(sessions, catalog, period, today, zone) }
}

/** Peso e desempenho num exercício, no mesmo período, um sobre o outro (sem sugerir causa). */
@Composable
private fun WeightVsExercise(sessions: List<WorkoutSession>, catalog: Map<String, Exercise>, period: ReportPeriod, today: LocalDate, zone: ZoneId) {
    val options = remember(sessions, catalog) { exerciseSummaries(sessions, catalog).take(8) }
    if (options.isEmpty()) return
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = options.find { it.exerciseId == selectedId } ?: options.first()
    val history = remember(sessions, selected.exerciseId) { exerciseHistory(sessions, selected.exerciseId) }
    val metric = availableMetrics(history).let { if (ExerciseMetric.ONE_REP_MAX in it) ExerciseMetric.ONE_REP_MAX else it.firstOrNull() ?: ExerciseMetric.REPS }
    val points = history.filter { period.contains(it.date, today, zone) }
        .mapNotNull { e -> e.metric(metric)?.let { ChartPoint(e.date, it, "${formatDecimal(it)} ${metric.unit}") } }

    Card {
        SectionLabel("Comparar com um exercício")
        Spacer(Modifier.height(8.dp))
        ChipRow(options, selected, { it.name }, { selectedId = it.exerciseId })
        Spacer(Modifier.height(12.dp))
        ProgressChart(
            points,
            yAxisLabel = "${metric.label} (${metric.unit})",
            formatY = { formatDecimal(it) },
            formatX = { formatDate(it, "d MMM yy") },
            insufficientText = "Menos de 2 registros deste exercício no período.",
        )
        Spacer(Modifier.height(8.dp))
        Text("Os gráficos mostram o mesmo período, um sobre o outro. Mudarem juntos não quer dizer que um causou o outro.", color = TC.Slate600, fontSize = 11.sp)
    }
}

// ---------------- Componentes ----------------

private const val MAX_BARS = 26
private const val CALENDAR_WEEKS = 16

private val weekFormatter = DateTimeFormatter.ofPattern("d/M")
private fun weekLabel(date: LocalDate): String = weekFormatter.format(date)

@Composable
private fun Tile(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(12.dp)) {
        Text(label, color = TC.Slate500, fontSize = 12.sp)
        Text(value, color = TC.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        if (sub != null) Text(sub, color = TC.Slate600, fontSize = 11.sp)
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
