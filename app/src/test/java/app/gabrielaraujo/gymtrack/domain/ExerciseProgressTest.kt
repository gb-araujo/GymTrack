package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.progression.BestSet
import app.gabrielaraujo.gymtrack.domain.progression.recomputeRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseProgressTest {
    private var seq = 0
    private fun set(w: Double, r: Int, done: Boolean = true) = SetRecord(id = "s${seq++}", weight = w, reps = r, completed = done)

    private fun ex(id: String, vararg sets: SetRecord, name: String = "Ex $id", substitutedFrom: String? = null) =
        SessionExercise(id = "se${seq++}", exerciseId = id, workoutExerciseId = "we_$id", name = name, sets = sets.toList(), order = 0, substitutedFrom = substitutedFrom)

    private fun session(id: String, start: Long, vararg exercises: SessionExercise, completed: Boolean = true, deleted: Boolean = false) = WorkoutSession(
        id = id, workoutPlanId = "p", workoutDayId = "d", workoutDayLabel = "A", workoutDayName = "Peito",
        exercises = exercises.toList(), startTime = start, endTime = start + 1, completed = completed, deleted = deleted,
    )

    // ---------------- 1RM ----------------

    @Test
    fun `1RM usa Epley e uma repeticao e a propria carga`() {
        assertEquals(100.0, estimateOneRepMax(100.0, 1)!!, 1e-9)
        assertEquals(100.0 * (1 + 10 / 30.0), estimateOneRepMax(100.0, 10)!!, 1e-9)
    }

    @Test
    fun `1RM nao se aplica sem carga ou com repeticoes demais`() {
        assertNull(estimateOneRepMax(0.0, 10))
        assertNull(estimateOneRepMax(60.0, 0))
        assertNull(estimateOneRepMax(60.0, MAX_REPS_FOR_1RM + 1))
    }

    // ---------------- Histórico de um exercício ----------------

    @Test
    fun `historico ordena do mais antigo e ignora sessoes sem serie valida`() {
        val sessions = listOf(
            session("s2", 2_000, ex("supino", set(62.5, 8))),
            session("s1", 1_000, ex("supino", set(60.0, 10))),
            session("s3", 3_000, ex("supino", set(65.0, 6, done = false))),
            session("s4", 4_000, ex("rosca", set(12.0, 10))),
        )
        val history = exerciseHistory(sessions, "supino")
        assertEquals(listOf("s1", "s2"), history.map { it.sessionId })
    }

    @Test
    fun `historico ignora sessoes nao concluidas ou excluidas`() {
        val sessions = listOf(
            session("aberta", 1_000, ex("supino", set(60.0, 10)), completed = false),
            session("excluida", 2_000, ex("supino", set(60.0, 10)), deleted = true),
        )
        assertTrue(exerciseHistory(sessions, "supino").isEmpty())
    }

    @Test
    fun `mesmo exercicio duas vezes na sessao soma as series`() {
        val s = session("s1", 1_000, ex("supino", set(60.0, 10)), ex("supino", set(70.0, 5)))
        val entry = exerciseHistory(listOf(s), "supino").single()
        assertEquals(2, entry.sets.size)
        assertEquals(70.0, entry.topWeight, 0.0)
    }

    @Test
    fun `substituicao conta para o exercicio executado e fica marcada`() {
        val s = session("s1", 1_000, ex("supino_halteres", set(24.0, 10), substitutedFrom = "supino"))
        assertTrue(exerciseHistory(listOf(s), "supino").isEmpty())
        assertTrue(exerciseHistory(listOf(s), "supino_halteres").single().substituted)
    }

    @Test
    fun `series incompletas e com zero repeticoes ficam fora dos calculos`() {
        val s = session("s1", 1_000, ex("supino", set(60.0, 10), set(100.0, 5, done = false), set(80.0, 0)))
        val entry = exerciseHistory(listOf(s), "supino").single()
        assertEquals(1, entry.sets.size)
        assertEquals(1, entry.skippedSets)
        assertEquals(600.0, entry.volume, 0.0)
    }

    // ---------------- Melhor série e evolução de carga ----------------

    @Test
    fun `melhor serie e a de maior carga com desempate por repeticoes`() {
        val entry = exerciseHistory(listOf(session("s1", 1, ex("a", set(60.0, 12), set(70.0, 6), set(70.0, 8)))), "a").single()
        assertEquals(BestSet(70.0, 8), entry.best)
        assertEquals(12, entry.maxReps)
        assertEquals(26, entry.totalReps)
    }

    @Test
    fun `metricas da sessao`() {
        val entry = exerciseHistory(listOf(session("s1", 1, ex("a", set(60.0, 10), set(70.0, 5)))), "a").single()
        assertEquals(70.0, entry.metric(ExerciseMetric.BEST_SET)!!, 0.0)
        assertEquals(10.0, entry.metric(ExerciseMetric.REPS)!!, 0.0)
        assertEquals(950.0, entry.metric(ExerciseMetric.VOLUME)!!, 0.0)
        // 60 × (1 + 10/30) = 80; 70 × (1 + 5/30) = 81.67 → o maior
        assertEquals(70.0 * (1 + 5 / 30.0), entry.metric(ExerciseMetric.ONE_REP_MAX)!!, 1e-9)
    }

    @Test
    fun `exercicio de peso corporal so oferece repeticoes`() {
        val history = exerciseHistory(listOf(session("s1", 1, ex("barra", set(0.0, 8))), session("s2", 2, ex("barra", set(0.0, 10)))), "barra")
        assertTrue(history.all { it.bodyweightOnly })
        assertEquals(listOf(ExerciseMetric.REPS), availableMetrics(history))
        assertNull(history.first().metric(ExerciseMetric.BEST_SET))
        assertNull(history.first().metric(ExerciseMetric.ONE_REP_MAX))
    }

    // ---------------- Comparação entre sessões ----------------

    @Test
    fun `compara a ultima sessao com a anterior`() {
        val history = exerciseHistory(
            listOf(
                session("s1", 1, ex("a", set(60.0, 10), set(60.0, 10))),
                session("s2", 2, ex("a", set(62.5, 8), set(62.5, 8), set(62.5, 6))),
            ),
            "a",
        )
        val cmp = compareLastSessions(history)!!
        assertEquals("s2", cmp.current.sessionId)
        assertEquals(2.5, cmp.weightDelta, 1e-9)
        assertEquals(-2, cmp.repsAtTopDelta)
        assertEquals(1, cmp.setsDelta)
        assertEquals(62.5 * 22 - 1200.0, cmp.volumeDelta, 1e-9)
    }

    @Test
    fun `sem comparacao com uma sessao so`() {
        assertNull(compareLastSessions(exerciseHistory(listOf(session("s1", 1, ex("a", set(60.0, 10)))), "a")))
    }

    // ---------------- Recordes ----------------

    @Test
    fun `progressao de recordes segue a mesma regra dos PRs`() {
        val sessions = listOf(
            session("s1", 1, ex("a", set(60.0, 10))),
            session("s2", 2, ex("a", set(60.0, 8))),  // não bate
            session("s3", 3, ex("a", set(60.0, 12))), // mais reps na mesma carga
            session("s4", 4, ex("a", set(65.0, 5))),  // mais carga
        )
        val steps = recordProgression(exerciseHistory(sessions, "a"))
        assertEquals(listOf("s1", "s3", "s4"), steps.map { it.sessionId })
        // O último passo é o recorde que o recálculo oficial grava.
        val official = recomputeRecords(sessions).records.getValue("a")
        assertEquals(official.weight, steps.last().weight, 0.0)
        assertEquals(official.reps, steps.last().reps)
    }

    @Test
    fun `progressao parte do recorde importado`() {
        val imported = PersonalRecord("a", "A", 70.0, 5, 350.0, date = 0, sessionId = "web")
        val steps = recordProgression(exerciseHistory(listOf(session("s1", 1, ex("a", set(65.0, 10)))), "a"), imported)
        assertEquals(1, steps.size)
        assertNull(steps.single().sessionId)
    }

    // ---------------- Descanso ----------------

    @Test
    fun `descanso medio usa o horario das series e ignora pausas longas e series sem horario`() {
        val sets = listOf(
            set(60.0, 10).copy(timestamp = 0),
            set(60.0, 10).copy(timestamp = 90_000),      // 90 s
            set(60.0, 10).copy(timestamp = 210_000),     // 120 s
            set(60.0, 10).copy(timestamp = 210_000 + 30 * 60_000), // 30 min: pausa, ignorada
            set(60.0, 10), // sem horário
        )
        assertEquals(105L, averageRestSeconds(sets))
        assertNull(averageRestSeconds(listOf(set(60.0, 10), set(60.0, 10))))
    }

    // ---------------- Nomes ----------------

    @Test
    fun `nome amigavel prefere catalogo depois o gravado e por fim o id formatado`() {
        val catalog = Exercise("supino_reto", "Supino Reto", "Peito")
        assertEquals("Supino Reto", exerciseDisplayName("supino_reto", catalog, "supino_reto"))
        assertEquals("Supino antigo", exerciseDisplayName("supino_reto", null, "Supino antigo"))
        assertEquals("Supino reto", exerciseDisplayName("supino_reto", null, "supino_reto"))
    }

    @Test
    fun `lista de exercicios tem so os com dados e do mais recente ao mais antigo`() {
        val sessions = listOf(
            session("s1", 1, ex("a", set(60.0, 10)), ex("b", set(10.0, 10, done = false))),
            session("s2", 2, ex("c", set(20.0, 10))),
        )
        val summaries = exerciseSummaries(sessions, emptyMap())
        assertEquals(listOf("c", "a"), summaries.map { it.exerciseId })
        assertFalse(summaries.any { it.exerciseId == "b" })
    }
}
