package app.gabrielaraujo.gymtrack.domain.health

import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {
    private val min = 60_000L

    @Test
    fun `calorias contam so a parte do registro que cai dentro do treino`() {
        val samples = listOf(
            EnergySample(start = 0, end = 60 * min, kcal = 300.0, origin = "shealth"),
            // Metade dentro do treino (que vai de 0 a 60 min).
            EnergySample(start = 50 * min, end = 70 * min, kcal = 40.0, origin = "shealth"),
            // Fora do treino.
            EnergySample(start = 90 * min, end = 100 * min, kcal = 50.0, origin = "shealth"),
        )
        assertEquals(320.0, caloriesDuring(samples, start = 0, end = 60 * min)!!, 0.001)
    }

    @Test
    fun `calorias de dois apps nao sao somadas`() {
        val samples = listOf(
            EnergySample(0, 60 * min, 300.0, "shealth"),
            EnergySample(0, 60 * min, 280.0, "fitbit"),
        )
        assertEquals(300.0, caloriesDuring(samples, 0, 60 * min)!!, 0.001)
    }

    @Test
    fun `sem registros no periodo nao ha calorias`() {
        assertNull(caloriesDuring(emptyList(), 0, 60 * min))
        assertNull(caloriesDuring(listOf(EnergySample(70 * min, 80 * min, 30.0, "a")), 0, 60 * min))
    }

    @Test
    fun `peso de referencia e a ultima pesagem ate a data ou a primeira depois`() {
        val weights = listOf(BodyWeightEntry(10, 82.0), BodyWeightEntry(20, 81.0), BodyWeightEntry(30, 80.0))
        assertEquals(81.0, weightAt(weights, 25)!!, 0.0)
        assertEquals(82.0, weightAt(weights, 5)!!, 0.0)
        assertEquals(80.0, weightAt(weights, 99)!!, 0.0)
        assertNull(weightAt(emptyList(), 5))
    }

    @Test
    fun `meta de peso serve para perder e para ganhar`() {
        // Perder: de 80 para 75, está em 78.
        assertEquals(0.4, bodyWeightProgress(start = 80.0, current = 78.0, target = 75.0), 0.001)
        // Ganhar: de 70 para 75, está em 72.
        assertEquals(0.4, bodyWeightProgress(start = 70.0, current = 72.0, target = 75.0), 0.001)
        // Indo na direção errada não fica negativo; passar da meta conta como concluída.
        assertEquals(0.0, bodyWeightProgress(start = 80.0, current = 81.0, target = 75.0), 0.0)
        assertEquals(1.0, bodyWeightProgress(start = 80.0, current = 74.0, target = 75.0), 0.0)
    }

    @Test
    fun `notas do treino listam as series feitas`() {
        val session = WorkoutSession(
            id = "s", workoutPlanId = "p", workoutDayId = "d", workoutDayLabel = "A", workoutDayName = "Peito",
            startTime = 0,
            exercises = listOf(
                SessionExercise(
                    id = "e2", exerciseId = "barra", workoutExerciseId = "w2", name = "Barra fixa", order = 1,
                    sets = listOf(SetRecord("1", 0.0, 10, completed = true)),
                ),
                SessionExercise(
                    id = "e1", exerciseId = "supino", workoutExerciseId = "w1", name = "Supino reto", order = 0,
                    sets = listOf(
                        SetRecord("1", 60.0, 10, completed = true),
                        SetRecord("2", 62.5, 8, completed = true),
                        SetRecord("3", 65.0, 6, completed = false),
                    ),
                ),
                SessionExercise(id = "e3", exerciseId = "x", workoutExerciseId = "w3", name = "Pulado", order = 2),
            ),
        )
        assertEquals("Supino reto: 60kg×10, 62.5kg×8\nBarra fixa: 10 reps", workoutNotes(session))

        val short = workoutNotes(session, maxLength = 12)
        assertEquals(12, short.length)
        assertTrue(short.endsWith("…"))
    }
}
