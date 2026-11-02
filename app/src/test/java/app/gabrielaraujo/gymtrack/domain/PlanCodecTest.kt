package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.WorkoutDay
import app.gabrielaraujo.gymtrack.data.WorkoutExercise
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.builtinExercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanCodecTest {
    private val knownIds = builtinExercises.map { it.id }.toSet()
    private var ids = 0
    private fun newId() = "novo${++ids}"

    private fun plan(vararg exerciseIds: String) = WorkoutPlan(
        id = "original", name = "Importada", createdAt = 1, updatedAt = 1, isActive = true, currentDayIndex = 2,
        days = listOf(
            WorkoutDay(
                id = "d", label = "A", name = "Perna", order = 0,
                exercises = exerciseIds.mapIndexed { i, id -> WorkoutExercise("we$i", id, 3, 8, 12, 90, order = i) },
            ),
        ),
    )

    private fun decode(text: String) = PlanCodec.decode(text, knownIds, now = 500, newId = ::newId)

    @Test
    fun `liga grafias e apelidos ao exercicio da biblioteca e mantem o id desconhecido`() {
        val text = PlanCodec.encode(plan("Agachamento-Hack", "hack_squat", "cadeira_extensora", "exercicio_inventado"))
        val imported = decode(text)!!
        assertEquals(
            listOf("agachamento_hack", "agachamento_hack", "extensao_joelho", "exercicio_inventado"),
            imported.days.single().exercises.map { it.exerciseId },
        )
    }

    @Test
    fun `importa como ficha nova e inativa`() {
        val imported = decode(PlanCodec.encode(plan("supino_reto")))!!
        assertNotEquals("original", imported.id)
        assertFalse(imported.isActive)
        assertEquals(0, imported.currentDayIndex)
        assertEquals(500L, imported.createdAt)
        assertEquals(500L, imported.updatedAt)
    }

    @Test
    fun `exportar e importar preserva os exercicios da ficha`() {
        val original = plan("supino_reto", "agachamento_hack")
        val imported = decode(PlanCodec.encode(original))!!
        assertEquals(original.days.single().exercises, imported.days.single().exercises)
    }

    @Test
    fun `texto que nao e ficha da null`() {
        assertNull(decode("não é json"))
        assertNull(decode("""{ "outra": 1 }"""))
    }
}
