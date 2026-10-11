package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.WorkoutSession
import kotlin.math.abs

/** Medidas do relógio durante um treino, lidas do Health Connect (null = sem medição). */
data class WorkoutHealthMetrics(
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Double? = null,
) {
    val isEmpty: Boolean get() = avgHeartRate == null && maxHeartRate == null && calories == null
}

/** Uma pesagem registrada no Health Connect (balança, relógio ou manual). */
data class BodyWeightEntry(val time: Long, val kg: Double)

/** Gasto calórico de um intervalo, como gravado por um app (ex.: Samsung Health). */
data class EnergySample(val start: Long, val end: Long, val kcal: Double, val origin: String)

/**
 * Calorias gastas entre [start] e [end]: cada registro conta na proporção em que se sobrepõe ao treino.
 * Usa uma única origem (a com mais calorias no período) para não somar o mesmo gasto gravado por dois apps.
 * @return null se nenhum registro cobre o período.
 */
fun caloriesDuring(samples: List<EnergySample>, start: Long, end: Long): Double? {
    if (end <= start) return null
    return samples
        .mapNotNull { s ->
            val overlap = minOf(end, s.end) - maxOf(start, s.start)
            if (overlap <= 0 || s.end <= s.start) null else s.origin to s.kcal * overlap / (s.end - s.start)
        }
        .groupBy({ it.first }, { it.second })
        .values
        .maxOfOrNull { it.sum() }
        ?.takeIf { it > 0 }
}

/** Peso de referência num instante: a última pesagem até [time] ou, se não houver, a primeira depois. */
fun weightAt(weights: List<BodyWeightEntry>, time: Long): Double? =
    (weights.filter { it.time <= time }.maxByOrNull { it.time } ?: weights.minByOrNull { it.time })?.kg

/**
 * Progresso (0..1) de uma meta de peso corporal partindo de [start], na direção da meta:
 * serve tanto para perder (meta abaixo do peso inicial) quanto para ganhar peso.
 */
fun bodyWeightProgress(start: Double, current: Double, target: Double): Double {
    val total = target - start
    if (abs(total) < 0.05) return if (abs(current - target) < 0.5) 1.0 else 0.0
    return ((current - start) / total).coerceIn(0.0, 1.0)
}

/**
 * Resumo do treino para as notas do Health Connect: uma linha por exercício com as séries feitas
 * (ex.: "Supino reto: 60kg×10, 62.5kg×8"). Limitado a [maxLength] caracteres.
 */
fun workoutNotes(session: WorkoutSession, maxLength: Int = 1000): String {
    val text = session.exercises
        .sortedBy { it.order }
        .mapNotNull { ex ->
            val sets = ex.sets.filter { it.completed }
            if (sets.isEmpty()) null
            else "${ex.name}: " + sets.joinToString(", ") { s -> if (s.weight > 0) "${kg(s.weight)}kg×${s.reps}" else "${s.reps} reps" }
        }
        .joinToString("\n")
    return if (text.length <= maxLength) text else text.take(maxLength - 1).trimEnd() + "…"
}

private fun kg(weight: Double): String =
    if (weight == weight.toLong().toDouble()) weight.toLong().toString() else weight.toString()
