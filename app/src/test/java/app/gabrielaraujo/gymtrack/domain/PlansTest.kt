package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.WorkoutDay
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlansTest {
    private fun plan(id: String, active: Boolean = false, updatedAt: Long = 0, currentDayIndex: Int = 0, deleted: Boolean = false) = WorkoutPlan(
        id = id, name = id, createdAt = 0, updatedAt = updatedAt, isActive = active, currentDayIndex = currentDayIndex, deleted = deleted,
        days = listOf("a", "b", "c").mapIndexed { i, d -> WorkoutDay(id = d, label = d.uppercase(), name = d, order = i) },
    )

    private fun session(planId: String, dayId: String, end: Long, completed: Boolean = true, deleted: Boolean = false) = WorkoutSession(
        id = "$planId-$dayId-$end", workoutPlanId = planId, workoutDayId = dayId, workoutDayLabel = dayId, workoutDayName = dayId,
        startTime = end - 3_600_000, endTime = end, completed = completed, deleted = deleted,
    )

    @Test
    fun `duas fichas ativas vindas de aparelhos diferentes resolvem para a ativada por ultimo`() {
        val older = plan("p1", active = true, updatedAt = 10)
        val newer = plan("p2", active = true, updatedAt = 20)
        assertEquals("p2", activePlanOf(listOf(older, newer))!!.id)
        assertEquals("p2", activePlanOf(listOf(newer, older))!!.id)
    }

    @Test
    fun `sem ficha ativa usa a primeira e ignora as excluidas`() {
        assertEquals("p2", activePlanOf(listOf(plan("p1", active = true, deleted = true), plan("p2"), plan("p3")))!!.id)
        assertNull(activePlanOf(emptyList()))
    }

    @Test
    fun `proximo treino e o dia seguinte ao ultimo concluido da ficha`() {
        val p = plan("p1")
        val sessions = listOf(session("p1", "a", end = 100), session("p1", "b", end = 200))
        assertEquals(2, nextDayIndex(p, sessions))
        // Depois do último dia, volta ao primeiro.
        assertEquals(0, nextDayIndex(p, sessions + session("p1", "c", end = 300)))
    }

    @Test
    fun `proximo treino ignora outras fichas, treinos excluidos e em andamento`() {
        val p = plan("p1", currentDayIndex = 1)
        val sessions = listOf(
            session("p1", "a", end = 100),
            session("p2", "c", end = 500),
            session("p1", "c", end = 400, deleted = true),
            session("p1", "b", end = 300, completed = false),
        )
        assertEquals(1, nextDayIndex(p, sessions))
    }

    @Test
    fun `sem treinos da ficha usa o indice gravado`() {
        assertEquals(2, nextDayIndex(plan("p1", currentDayIndex = 2), emptyList()))
        assertEquals(1, nextDayIndex(plan("p1", currentDayIndex = 4), emptyList()))
        // Dia do último treino removido da ficha: cai no índice gravado.
        assertEquals(0, nextDayIndex(plan("p1"), listOf(session("p1", "x", end = 100))))
    }
}
