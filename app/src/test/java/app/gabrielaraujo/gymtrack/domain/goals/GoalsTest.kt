package app.gabrielaraujo.gymtrack.domain.goals

import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.health.BodyWeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsTest {
    private val day = 86_400_000L
    private val now = 1_750_000_000_000L

    private fun goal(type: String, target: Double, exerciseId: String? = null, createdAt: Long = now - 30 * day) =
        Goal(id = type, type = type, title = type, target = target, unit = "", exerciseId = exerciseId, createdAt = createdAt)

    private fun session(start: Long, volume: Double) = WorkoutSession(
        id = "s$start", workoutPlanId = "p", workoutDayId = "d", workoutDayLabel = "A", workoutDayName = "A",
        startTime = start, completed = true, totalVolume = volume,
    )

    private val sessions = listOf(session(now - 1 * day, 5_000.0), session(now - 3 * day, 4_000.0), session(now - 10 * day, 6_000.0))

    @Test
    fun `contagem, frequencia e volume saem do historico`() {
        assertEquals(30, goalProgress(goal(GoalTypes.WORKOUT_COUNT, 10.0), sessions, emptyMap(), emptyList(), now).percent)
        val weekly = goalProgress(goal(GoalTypes.WEEKLY_FREQUENCY, 3.0), sessions, emptyMap(), emptyList(), now)
        assertEquals(2.0, weekly.current, 0.0)
        assertEquals(67, weekly.percent)
        val volume = goalProgress(goal(GoalTypes.VOLUME, 10_000.0), sessions, emptyMap(), emptyList(), now)
        assertEquals(15_000.0, volume.current, 0.0)
        assertEquals(100, volume.percent)
        assertTrue(volume.done)
    }

    @Test
    fun `meta de carga acompanha o recorde do exercicio`() {
        val record = PersonalRecord("supino", "Supino", 90.0, 8, 720.0, now, "s")
        val progress = goalProgress(goal(GoalTypes.EXERCISE_WEIGHT, 100.0, "supino"), sessions, mapOf("supino" to record), emptyList(), now)
        assertEquals(90.0, progress.current, 0.0)
        assertEquals(90, progress.percent)
        // Sem recorde do exercício ainda: zero.
        assertEquals(0, goalProgress(goal(GoalTypes.EXERCISE_WEIGHT, 100.0, "agachamento"), sessions, emptyMap(), emptyList(), now).percent)
    }

    @Test
    fun `meta de peso parte da pesagem de quando foi criada`() {
        val weights = listOf(BodyWeightEntry(now - 40 * day, 92.0), BodyWeightEntry(now - 31 * day, 90.0), BodyWeightEntry(now, 85.0))
        val progress = goalProgress(goal(GoalTypes.BODY_WEIGHT, 80.0), sessions, emptyMap(), weights, now)
        assertEquals(85.0, progress.current, 0.0)
        assertEquals(50, progress.percent)
    }
}
