package app.gabrielaraujo.gymtrack.domain.statistics

import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.progression.suggestedNextWeight
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsTest {
    private val day = 86_400_000L
    private val now = 1_750_000_000_000L

    @Test
    fun `semanas terminam agora e incluem a semana atual`() {
        val times = listOf(now - 1 * day, now - 2 * day, now - 8 * day, now - 60 * day)
        val weeks = weeklyCounts(times, now, weeks = 8)
        assertEquals(8, weeks.size)
        assertEquals(2, weeks.last()) // últimos 7 dias
        assertEquals(1, weeks[weeks.size - 2]) // semana anterior
        assertEquals(3, weeks.sum()) // o de 60 dias atrás fica fora da janela
    }

    @Test
    fun `treinos do mes contam so o mes corrente`() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 11, 2)
        val inMonth = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val lastMonth = LocalDate.of(2026, 10, 31).atStartOfDay(zone).toInstant().toEpochMilli()
        val lastYear = LocalDate.of(2025, 11, 2).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(1, workoutsInMonth(listOf(inMonth, lastMonth, lastYear), today, zone))
    }

    @Test
    fun `evolucao de carga e cronologica e ignora series nao concluidas`() {
        fun session(start: Long, vararg sets: Pair<Double, Boolean>) = WorkoutSession(
            id = "s$start", workoutPlanId = "p", workoutDayId = "d", workoutDayLabel = "A", workoutDayName = "A", startTime = start,
            exercises = listOf(
                SessionExercise(
                    id = "e$start", exerciseId = "supino", workoutExerciseId = "we", name = "Supino", order = 0,
                    sets = sets.mapIndexed { i, (w, done) -> SetRecord("r$start$i", w, 10, completed = done) },
                ),
            ),
        )
        val sessions = listOf(
            session(300, 70.0 to true, 90.0 to false),
            session(100, 60.0 to true),
            session(200, 0.0 to true), // peso corporal: fica de fora
        )
        assertEquals(listOf(60.0, 70.0), loadHistory(sessions, "supino"))
    }

    @Test
    fun `progressao so com todas as series concluidas no topo da faixa`() {
        val top = listOf(SetRecord("1", 60.0, 12, completed = true), SetRecord("2", 62.5, 12, completed = true))
        assertEquals(65.0, suggestedNextWeight(top, repMax = 12)!!, 0.0)
        assertEquals(null, suggestedNextWeight(top.map { it.copy(reps = 11) }, repMax = 12))
        assertEquals(null, suggestedNextWeight(top + SetRecord("3", 60.0, 12, completed = false), repMax = 12))
        assertEquals(null, suggestedNextWeight(top, repMax = 0))
    }
}
