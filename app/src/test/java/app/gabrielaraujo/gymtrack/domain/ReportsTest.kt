package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Visão geral, filtros do histórico e auditoria de dados. */
class ReportsTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 3, 18)
    private val day = 86_400_000L
    private val now = today.atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3_600_000L
    private var seq = 0

    private fun set(w: Double, r: Int, done: Boolean = true) = SetRecord(id = "s${seq++}", weight = w, reps = r, completed = done)
    private fun ex(id: String, vararg sets: SetRecord, name: String = id) =
        SessionExercise(id = "se${seq++}", exerciseId = id, workoutExerciseId = "we", name = name, sets = sets.toList(), order = 0)

    private fun session(
        id: String, start: Long, vararg exercises: SessionExercise,
        dayName: String = "Peito", planId: String = "p", dayId: String = "d",
        prs: List<String> = emptyList(), duration: Int? = 60,
    ) = WorkoutSession(
        id = id, workoutPlanId = planId, workoutDayId = dayId, workoutDayLabel = "A", workoutDayName = dayName,
        exercises = exercises.toList(), startTime = start, endTime = start + 1, completed = true, prsAchieved = prs,
        duration = duration, totalVolume = sessionTotals(exercises.toList()).volume, totalSets = sessionTotals(exercises.toList()).sets,
    )

    // ---------------- Tendência ----------------

    @Test
    fun `tendencia exige um minimo de sessoes`() {
        val history = exerciseHistory(listOf(session("1", 1, ex("a", set(60.0, 10))), session("2", 2, ex("a", set(70.0, 10)))), "a")
        assertNull(exerciseTrend("a", "A", history))
    }

    @Test
    fun `tendencia compara a primeira e a ultima sessao`() {
        val history = exerciseHistory(
            listOf(session("1", 1, ex("a", set(60.0, 10))), session("2", 2, ex("a", set(55.0, 10))), session("3", 3, ex("a", set(65.0, 8)))),
            "a",
        )
        val trend = exerciseTrend("a", "A", history)!!
        assertEquals(ExerciseMetric.ONE_REP_MAX, trend.metric)
        assertEquals(TrendDirection.UP, trend.direction)
        assertEquals(3, trend.sessions)
    }

    @Test
    fun `tendencia de peso corporal usa repeticoes`() {
        val history = exerciseHistory((1..3).map { session("$it", it.toLong(), ex("barra", set(0.0, 10 - it))) }, "barra")
        val trend = exerciseTrend("barra", "Barra", history)!!
        assertEquals(ExerciseMetric.REPS, trend.metric)
        assertEquals(TrendDirection.DOWN, trend.direction)
    }

    @Test
    fun `visao geral conta so o periodo e ignora exercicios sem dados suficientes`() {
        val sessions = listOf(
            session("old", now - 100 * day, ex("a", set(50.0, 10)), prs = listOf("a")),
            session("1", now - 20 * day, ex("a", set(60.0, 10)), ex("b", set(20.0, 10)), prs = listOf("a", "b")),
            session("2", now - 10 * day, ex("a", set(62.5, 10))),
            session("3", now - 1 * day, ex("a", set(65.0, 10)), prs = listOf("a")),
        )
        val overview = progressOverview(sessions, emptyMap(), ReportPeriod.DAYS_30, today, zone)
        assertEquals(3, overview.workouts)
        assertEquals(3, overview.prs)
        assertEquals(listOf("a"), overview.trends.map { it.exerciseId })
        assertEquals(listOf("a"), overview.improving.map { it.exerciseId })
    }

    // ---------------- Filtros do histórico ----------------

    @Test
    fun `busca ignora acentos e acha pelo exercicio`() {
        val s = session("1", now, ex("rosca", set(10.0, 10), name = "Rosca Martelo"), dayName = "Bíceps")
        assertTrue(s.matchesQuery("biceps"))
        assertTrue(s.matchesQuery("MARTELO"))
        assertTrue(s.matchesQuery("a"))
        assertFalse(s.matchesQuery("supino"))
    }

    @Test
    fun `filtros combinados`() {
        val sessions = listOf(
            session("recente_pr", now - day, ex("a", set(60.0, 10)), planId = "p1", prs = listOf("a")),
            session("recente", now - 2 * day, ex("a", set(60.0, 10)), planId = "p2"),
            session("antiga_pr", now - 60 * day, ex("a", set(60.0, 10)), planId = "p1", prs = listOf("a")),
        )
        fun ids(f: HistoryFilter) = filterSessions(sessions, f, today, zone).map { it.id }
        assertEquals(listOf("recente_pr", "recente"), ids(HistoryFilter(period = ReportPeriod.DAYS_30)))
        assertEquals(listOf("recente_pr", "antiga_pr"), ids(HistoryFilter(planId = "p1")))
        assertEquals(listOf("recente_pr"), ids(HistoryFilter(period = ReportPeriod.DAYS_30, onlyWithRecords = true)))
        assertFalse(HistoryFilter().isActive)
    }

    // ---------------- Auditoria ----------------

    @Test
    fun `auditoria aponta problemas sem alterar os dados`() {
        val catalog = mapOf(
            "supino" to Exercise("supino", "Supino", "Peito", equipment = "Barra"),
            "barra" to Exercise("barra", "Barra Fixa", "Costas", equipment = "Peso Corporal"),
        )
        val longa = session("longa", now, ex("supino", set(60.0, 10)), duration = 9 * 60)
        val semCarga = session("semCarga", now - day, ex("supino", set(0.0, 10)), ex("barra", set(0.0, 10)))
        val pesoCorporal = session("pesoCorporal", now - 2 * day, ex("barra", set(0.0, 10)))
        val divergente = session("divergente", now - 3 * day, ex("supino", set(60.0, 10))).copy(totalVolume = 9_900.0)
        val dup1 = session("dup1", now - 4 * day, ex("supino", set(60.0, 10)), dayId = "x")
        val dup2 = session("dup2", now - 4 * day + 60_000, ex("supino", set(60.0, 10)), dayId = "x")

        val issues = auditSessions(listOf(longa, semCarga, pesoCorporal, divergente, dup1, dup2), catalog)
        assertEquals(setOf(DataIssue.LONG_DURATION), issues["longa"])
        assertEquals(setOf(DataIssue.ZERO_LOAD), issues["semCarga"])
        assertNull(issues["pesoCorporal"])
        assertEquals(setOf(DataIssue.STORED_TOTALS_MISMATCH), issues["divergente"])
        assertEquals(setOf(DataIssue.POSSIBLE_DUPLICATE), issues["dup1"])
        assertEquals(setOf(DataIssue.POSSIBLE_DUPLICATE), issues["dup2"])
        assertEquals(9_900.0, divergente.totalVolume!!, 0.0)
    }
}
