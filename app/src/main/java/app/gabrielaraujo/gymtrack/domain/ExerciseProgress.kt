package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession

/*
 * Regras de cálculo da evolução por exercício.
 *
 * - Série válida: concluída, com pelo menos 1 repetição e carga >= 0. Carga 0 = peso corporal.
 *   Séries não concluídas não entram em nenhum cálculo (nem como zero).
 * - Melhor série: a de maior carga; empatando, a de mais repetições. É a mesma regra dos
 *   recordes pessoais ([bestSet]), por isso "maior carga" e "melhor série" coincidem.
 * - 1RM estimado: fórmula de Epley, carga × (1 + reps / 30), só para cargas > 0 e de 1 a
 *   [MAX_REPS_FOR_1RM] repetições (acima disso a estimativa perde precisão). O valor da sessão
 *   é o maior entre as séries.
 * - Volume: soma de carga × repetições das séries válidas, na unidade em que a carga foi registrada.
 * - Exercícios diferentes nunca se misturam: o histórico é sempre por exerciseId. Uma substituição
 *   conta para o exercício que foi de fato executado.
 * - Sessão sem nenhuma série válida do exercício não entra no histórico (ausência de registro não é zero).
 */

const val MAX_REPS_FOR_1RM = 12

fun SetRecord.isValidForReports(): Boolean = completed && reps > 0 && weight >= 0.0 && weight.isFinite()

/** 1RM estimado (Epley), ou null quando a estimativa não se aplica. */
fun estimateOneRepMax(weight: Double, reps: Int): Double? = when {
    weight <= 0.0 || reps < 1 || reps > MAX_REPS_FOR_1RM -> null
    reps == 1 -> weight
    else -> weight * (1 + reps / 30.0)
}

/** Melhor série de uma lista de séries já válidas (mesma regra dos recordes). */
fun bestOf(sets: List<SetRecord>): BestSet? {
    if (sets.isEmpty()) return null
    val maxWeight = sets.maxOf { it.weight }
    return BestSet(maxWeight, sets.filter { it.weight == maxWeight }.maxOf { it.reps })
}

/** Desempenho de um exercício numa sessão. */
data class ExerciseSessionEntry(
    val sessionId: String,
    val date: Long,
    /** Nome gravado na sessão (preserva o que foi executado, mesmo se o catálogo mudar). */
    val name: String,
    val substituted: Boolean,
    /** Séries válidas, na ordem em que foram registradas. */
    val sets: List<SetRecord>,
    /** Séries planejadas que não foram concluídas. */
    val skippedSets: Int,
) {
    val best: BestSet = bestOf(sets) ?: BestSet(0.0, 0)
    val topWeight: Double get() = best.weight
    val maxReps: Int = sets.maxOfOrNull { it.reps } ?: 0
    val totalReps: Int = sets.sumOf { it.reps }
    val volume: Double = sets.sumOf { it.weight * it.reps }
    val estimatedOneRepMax: Double? = sets.mapNotNull { estimateOneRepMax(it.weight, it.reps) }.maxOrNull()

    /** Todas as séries sem carga: o exercício foi feito com o peso do corpo. */
    val bodyweightOnly: Boolean get() = topWeight == 0.0
}

/** Histórico de um exercício, da sessão mais antiga para a mais recente. */
fun exerciseHistory(sessions: Collection<WorkoutSession>, exerciseId: String): List<ExerciseSessionEntry> =
    sessions
        .filter { it.completed && !it.deleted }
        .mapNotNull { session ->
            val matches = session.exercises.filter { it.exerciseId == exerciseId }
            if (matches.isEmpty()) return@mapNotNull null
            // O mesmo exercício pode aparecer duas vezes no treino: as séries são somadas.
            val all = matches.flatMap { it.sets }
            val valid = all.filter { it.isValidForReports() }
            if (valid.isEmpty()) return@mapNotNull null
            ExerciseSessionEntry(
                sessionId = session.id,
                date = session.startTime,
                name = matches.first().name,
                substituted = matches.any { it.substitutedFrom != null },
                sets = valid,
                skippedSets = all.count { !it.completed },
            )
        }
        .sortedBy { it.date }

/** Métricas do gráfico de evolução. */
enum class ExerciseMetric(val label: String, val unit: String) {
    BEST_SET("Carga", "kg"),
    REPS("Repetições", "reps"),
    ONE_REP_MAX("1RM estimado", "kg"),
    VOLUME("Volume", "kg"),
}

/** Valor da métrica na sessão, ou null quando ela não se aplica (ex.: 1RM de exercício sem carga). */
fun ExerciseSessionEntry.metric(metric: ExerciseMetric): Double? = when (metric) {
    ExerciseMetric.BEST_SET -> topWeight.takeIf { it > 0.0 }
    ExerciseMetric.REPS -> maxReps.toDouble()
    ExerciseMetric.ONE_REP_MAX -> estimatedOneRepMax
    ExerciseMetric.VOLUME -> volume.takeIf { it > 0.0 }
}

/** Métricas que fazem sentido para o histórico (sem carga, só repetições). */
fun availableMetrics(history: List<ExerciseSessionEntry>): List<ExerciseMetric> =
    if (history.isNotEmpty() && history.all { it.bodyweightOnly }) listOf(ExerciseMetric.REPS)
    else ExerciseMetric.entries.filter { m -> history.any { it.metric(m) != null } }

/** Comparação entre duas sessões do mesmo exercício (atual − anterior). */
data class SessionComparison(
    val current: ExerciseSessionEntry,
    val previous: ExerciseSessionEntry,
) {
    val weightDelta: Double = current.topWeight - previous.topWeight
    val repsAtTopDelta: Int = current.best.reps - previous.best.reps
    val volumeDelta: Double = current.volume - previous.volume
    val setsDelta: Int = current.sets.size - previous.sets.size
    val oneRepMaxDelta: Double? = current.estimatedOneRepMax?.let { c -> previous.estimatedOneRepMax?.let { c - it } }
}

/** Compara a última sessão com a anterior; null com menos de duas sessões. */
fun compareLastSessions(history: List<ExerciseSessionEntry>): SessionComparison? =
    if (history.size < 2) null else SessionComparison(history.last(), history[history.size - 2])

/** Um recorde batido ao longo do tempo. */
data class RecordStep(val date: Long, val weight: Double, val reps: Int, val sessionId: String?)

/**
 * Sequência de recordes do exercício, na mesma regra de [recomputeRecords]:
 * cada sessão cuja melhor série supera o recorde vigente gera um passo.
 * @param baseline recorde que não veio de nenhuma sessão conhecida (ex.: importado), ponto de partida.
 */
fun recordProgression(history: List<ExerciseSessionEntry>, baseline: PersonalRecord? = null): List<RecordStep> {
    val steps = mutableListOf<RecordStep>()
    var current: BestSet? = baseline?.let {
        steps += RecordStep(it.date, it.weight, it.reps, null)
        BestSet(it.weight, it.reps)
    }
    for (entry in history) {
        if (current == null || entry.best.beats(current.weight, current.reps)) {
            current = entry.best
            steps += RecordStep(entry.date, entry.best.weight, entry.best.reps, entry.sessionId)
        }
    }
    return steps
}

/** Resumo de um exercício para a lista de seleção. */
data class ExerciseSummary(
    val exerciseId: String,
    val name: String,
    val sessions: Int,
    val lastDate: Long,
    val best: BestSet,
)

/** Exercícios com pelo menos uma sessão válida, do treinado mais recentemente ao mais antigo. */
fun exerciseSummaries(sessions: Collection<WorkoutSession>, catalog: Map<String, Exercise>): List<ExerciseSummary> {
    val ids = sessions.filter { it.completed && !it.deleted }.flatMap { s -> s.exercises.map { it.exerciseId } }.distinct()
    return ids.mapNotNull { id ->
        val history = exerciseHistory(sessions, id)
        if (history.isEmpty()) return@mapNotNull null
        val best = history.map { it.best }.reduce { a, b -> if (b.beats(a.weight, a.reps)) b else a }
        ExerciseSummary(id, exerciseDisplayName(id, catalog[id], history.last().name), history.size, history.last().date, best)
    }.sortedByDescending { it.lastDate }
}

/**
 * Nome para exibição: o do catálogo; senão o gravado na sessão; senão o id formatado
 * ("supino_reto" → "Supino reto"), para nunca mostrar um identificador cru.
 */
fun exerciseDisplayName(exerciseId: String, catalogEntry: Exercise?, storedName: String?): String {
    catalogEntry?.name?.takeIf { it.isNotBlank() }?.let { return it }
    storedName?.takeIf { it.isNotBlank() && it != exerciseId }?.let { return it }
    return exerciseId.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }
}

/** Intervalo acima do qual a diferença entre séries não é descanso (pausa, outro exercício no meio). */
const val MAX_REST_SECONDS = 15 * 60L

/**
 * Descanso médio (s) entre séries concluídas consecutivas, pelo horário registrado em cada série.
 * Séries sem horário (treinos antigos) são ignoradas; null quando não há pelo menos um intervalo válido.
 */
fun averageRestSeconds(sets: List<SetRecord>): Long? {
    val times = sets.filter { it.completed }.mapNotNull { it.timestamp }.sorted()
    val gaps = times.zipWithNext { a, b -> (b - a) / 1000 }.filter { it in 1..MAX_REST_SECONDS }
    return if (gaps.isEmpty()) null else gaps.sum() / gaps.size
}

/** Convenção da carga em exercícios com halteres. */
fun isDumbbellExercise(exercise: Exercise?): Boolean = exercise?.equipment.equals("Halteres", ignoreCase = true)
