package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecTest {
    private fun session(id: String, completed: Boolean = true, deleted: Boolean = false) = WorkoutSession(
        id = id, workoutPlanId = "p", workoutDayId = "d", workoutDayLabel = "A", workoutDayName = "Peito",
        startTime = 1000, completed = completed, deleted = deleted, ownerId = "u1", dirty = true,
    )

    @Test
    fun `exportar e importar preserva os dados e remove campos de sync`() {
        val tables = BackupTables(
            workoutPlans = listOf(WorkoutPlan(id = "p", name = "Ficha", createdAt = 1, ownerId = "u1")),
            workoutSessions = listOf(session("s1"), session("s2", completed = false), session("s3", deleted = true)),
            goals = listOf(Goal(id = "g", type = "workout_count", title = "100", target = 100.0, unit = "treinos", createdAt = 1)),
            exercises = listOf(
                Exercise(id = "c", name = "Custom", primaryMuscle = "chest", isCustom = true),
                Exercise(id = "b", name = "Builtin", primaryMuscle = "chest"),
            ),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(tables, exportedAt = 42))
        assertEquals(listOf("s1"), decoded.workoutSessions.map { it.id })
        assertNull(decoded.workoutSessions.single().ownerId)
        assertFalse(decoded.workoutSessions.single().dirty)
        assertEquals("Ficha", decoded.workoutPlans.single().name)
        assertEquals(listOf("c"), decoded.exercises.map { it.id })
        assertEquals(1, decoded.goals.size)
    }

    @Test
    fun `aceita o dump do app web e ignora linhas invalidas`() {
        val web = """
            { "workoutSessions": [
                { "id": "ok", "workoutPlanId": "p", "workoutDayId": "d", "workoutDayLabel": "A",
                  "workoutDayName": "X", "startTime": 5, "completed": true, "extra": 1 },
                { "id": "tomb", "workoutPlanId": "p", "workoutDayId": "d", "workoutDayLabel": "A",
                  "workoutDayName": "X", "startTime": 5, "completed": true, "_deleted": true },
                { "id": "broken", "completed": true }
            ] }
        """.trimIndent()
        assertEquals(listOf("ok"), BackupCodec.decode(web).workoutSessions.map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `texto que nao e JSON gera erro`() {
        BackupCodec.decode("não é json")
    }
}
