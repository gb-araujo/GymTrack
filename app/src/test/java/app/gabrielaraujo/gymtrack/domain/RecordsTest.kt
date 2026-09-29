package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsTest {
    private fun set(w: Double, r: Int, done: Boolean = true) = SetRecord(id = "s$w$r", weight = w, reps = r, completed = done)

    private fun ex(id: String, vararg sets: SetRecord) =
        SessionExercise(id = "se_$id", exerciseId = id, workoutExerciseId = "we_$id", name = "Ex $id", sets = sets.toList(), order = 0)

    private fun session(id: String, start: Long, vararg exercises: SessionExercise) = WorkoutSession(
        id = id, workoutPlanId = "p", workoutDayId = "d", workoutDayLabel = "A", workoutDayName = "A",
        exercises = exercises.toList(), startTime = start, endTime = start + 1, completed = true,
    )

    @Test
    fun `totais contam so series concluidas`() {
        val t = sessionTotals(listOf(ex("a", set(50.0, 10), set(60.0, 8), set(100.0, 1, done = false))))
        assertEquals(980.0, t.volume, 0.0)
        assertEquals(2, t.sets)
    }

    @Test
    fun `melhor serie usa maior carga e desempata por reps`() {
        assertEquals(BestSet(60.0, 9), bestSet(ex("a", set(60.0, 8), set(60.0, 9), set(50.0, 12))))
        assertNull(bestSet(ex("a", set(60.0, 8, done = false))))
    }

    @Test
    fun `recalculo percorre as sessoes em ordem cronologica`() {
        val s1 = session("s1", 100, ex("a", set(50.0, 10)))
        val s2 = session("s2", 200, ex("a", set(55.0, 8)), ex("b", set(20.0, 10)))
        val s3 = session("s3", 300, ex("a", set(52.0, 12)))
        val r = recomputeRecords(listOf(s3, s1, s2))
        assertEquals(55.0, r.records.getValue("a").weight, 0.0)
        assertEquals("s2", r.records.getValue("a").sessionId)
        assertEquals(listOf("a"), r.prsBySession["s1"])
        assertEquals(listOf("a", "b"), r.prsBySession["s2"])
        assertEquals(emptyList<String>(), r.prsBySession["s3"])
    }

    @Test
    fun `baseline importado so e superado por serie melhor`() {
        val imported = PersonalRecord("a", "Ex a", 80.0, 5, 400.0, date = 1, sessionId = "old")
        val r = recomputeRecords(listOf(session("s1", 100, ex("a", set(70.0, 10)))), listOf(imported))
        assertEquals("old", r.records.getValue("a").sessionId)
        assertEquals(emptyList<String>(), r.prsBySession["s1"])
    }

    @Test
    fun `diff grava alterados e cria tombstone dos que sumiram`() {
        val keep = PersonalRecord("a", "Ex a", 50.0, 10, 500.0, date = 101, sessionId = "s1")
        val gone = PersonalRecord("b", "Ex b", 20.0, 10, 200.0, date = 201, sessionId = "s2")
        val computed = mapOf(
            "a" to keep.copy(),
            "c" to PersonalRecord("c", "Ex c", 30.0, 5, 150.0, date = 301, sessionId = "s3"),
        )
        val changes = diffRecords(listOf(keep, gone), computed, now = 999, ownerId = "u1")
        assertEquals(setOf("b", "c"), changes.map { it.exerciseId }.toSet())
        val tomb = changes.first { it.exerciseId == "b" }
        assertTrue(tomb.deleted && tomb.dirty)
        assertEquals(999, tomb.updatedAt)
        val added = changes.first { it.exerciseId == "c" }
        assertTrue(added.dirty && !added.deleted)
        assertEquals("u1", added.ownerId)
    }
}
