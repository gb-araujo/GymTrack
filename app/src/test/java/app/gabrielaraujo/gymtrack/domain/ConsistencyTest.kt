package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ConsistencyTest {
    private val zone = ZoneId.of("America/Sao_Paulo")

    // Quarta-feira
    private val today = LocalDate.of(2026, 3, 18)

    private fun at(date: LocalDate, hour: Int = 18, minute: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `semana comeca na segunda`() {
        assertEquals(LocalDate.of(2026, 3, 16), today.weekStart())
        assertEquals(LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 22).weekStart()) // domingo
    }

    @Test
    fun `semanas sem treino entram com zero`() {
        val weeks = weeklyCounts(listOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 17)), LocalDate.of(2026, 3, 2), today)
        assertEquals(listOf(1, 0, 1), weeks.map { it.count })
    }

    @Test
    fun `treino de domingo a noite conta no domingo do fuso local e nao na segunda UTC`() {
        // 22h de domingo em São Paulo = 01h de segunda em UTC
        val sundayNight = at(LocalDate.of(2026, 3, 15), hour = 22)
        assertEquals(LocalDate.of(2026, 3, 15), sundayNight.toLocalDate(zone))
        val report = consistencyReport(listOf(sundayNight), ReportPeriod.DAYS_30, today, zone)
        assertEquals(1, report.weeks.single { it.weekStart == LocalDate.of(2026, 3, 9) }.count)
        assertEquals(0, report.weeks.single { it.weekStart == LocalDate.of(2026, 3, 16) }.count)
    }

    @Test
    fun `media semanal inclui semanas vazias e ignora a semana em andamento`() {
        val times = listOf(
            at(LocalDate.of(2026, 3, 2)), at(LocalDate.of(2026, 3, 4)), // semana 1: 2
            // semana 2 (9/3): nada
            at(LocalDate.of(2026, 3, 16)), at(LocalDate.of(2026, 3, 17)), at(LocalDate.of(2026, 3, 18)), // semana atual: 3
        )
        val report = consistencyReport(times, ReportPeriod.ALL, today, zone)
        assertEquals(5, report.workouts)
        // Semanas completas: 2/3 (2 treinos) e 9/3 (0) → 1 por semana
        assertEquals(1.0, report.averagePerWeek!!, 1e-9)
    }

    @Test
    fun `dois treinos no mesmo dia contam como dois treinos e um dia`() {
        val d = LocalDate.of(2026, 3, 17)
        val report = consistencyReport(listOf(at(d, 8), at(d, 19)), ReportPeriod.DAYS_30, today, zone)
        assertEquals(2, report.workouts)
        assertEquals(setOf(d), report.trainedDays)
    }

    @Test
    fun `sequencia de semanas consistentes e semana atual sem treino nao quebra`() {
        val times = listOf(
            at(LocalDate.of(2026, 2, 16)),
            // 23/2: nada → quebra
            at(LocalDate.of(2026, 3, 2)),
            at(LocalDate.of(2026, 3, 10)),
            // semana atual (16/3) ainda sem treino
        )
        val report = consistencyReport(times, ReportPeriod.ALL, LocalDate.of(2026, 3, 16), zone)
        assertEquals(2, report.currentStreakWeeks)
        assertEquals(2, report.bestStreakWeeks)
    }

    @Test
    fun `semana sem treino quebra a sequencia atual`() {
        val times = listOf(at(LocalDate.of(2026, 3, 2)))
        // 9/3 sem treino e já terminou
        val report = consistencyReport(times, ReportPeriod.ALL, today, zone)
        assertEquals(0, report.currentStreakWeeks)
        assertEquals(1, report.bestStreakWeeks)
    }

    @Test
    fun `sem treinos o relatorio fica zerado`() {
        val report = consistencyReport(emptyList(), ReportPeriod.DAYS_30, today, zone)
        assertEquals(0, report.workouts)
        assertEquals(0, report.currentStreakWeeks)
        assertTrue(report.weeks.all { it.count == 0 })
        assertEquals(0.0, report.averagePerWeek!!, 0.0)
    }

    @Test
    fun `compara com o periodo anterior de mesmo tamanho`() {
        val times = listOf(
            at(today.minusDays(5)), at(today.minusDays(10)), // período atual (30 dias)
            at(today.minusDays(40)), // período anterior
            at(today.minusDays(70)), // fora dos dois
        )
        val report = consistencyReport(times, ReportPeriod.DAYS_30, today, zone)
        assertEquals(2, report.workouts)
        assertEquals(1, report.previousPeriodWorkouts)
        assertNull(consistencyReport(times, ReportPeriod.ALL, today, zone).previousPeriodWorkouts)
    }

    @Test
    fun `periodo de 30 dias comeca no inicio do dia`() {
        val start = ReportPeriod.DAYS_30.startDate(today)!!
        assertEquals(today.minusDays(29), start)
        assertTrue(ReportPeriod.DAYS_30.contains(at(start, 0, 0), today, zone))
        assertTrue(!ReportPeriod.DAYS_30.contains(at(start.minusDays(1), 23, 59), today, zone))
    }

    @Test
    fun `volume semanal recalcula a partir das series validas`() {
        val s = WorkoutSession(
            id = "s", workoutPlanId = "p", workoutDayId = "d", workoutDayLabel = "A", workoutDayName = "A",
            exercises = listOf(
                SessionExercise(
                    "e", "a", "we", "A", order = 0,
                    sets = listOf(SetRecord("1", 100.0, 10, completed = true), SetRecord("2", 100.0, 10, completed = false)),
                ),
            ),
            startTime = at(LocalDate.of(2026, 3, 17)), completed = true, totalVolume = 99_999.0,
        )
        val weeks = weeklyVolume(listOf(s), LocalDate.of(2026, 3, 9), today, zone)
        assertEquals(listOf(0.0, 1000.0), weeks.map { it.volume })
    }
}
