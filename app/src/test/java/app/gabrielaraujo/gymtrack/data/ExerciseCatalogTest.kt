package app.gabrielaraujo.gymtrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseCatalogTest {
    private val knownIds = builtinExercises.map { it.id }.toSet()

    @Test
    fun `normaliza acento, hifen, espaco e maiusculas`() {
        assertEquals("agachamento_hack", normalizeExerciseId(" Agachamento-Hack "))
        assertEquals("extensao_de_joelho", normalizeExerciseId("Extensão de Joelho"))
        assertEquals("leg_press_45", normalizeExerciseId("Leg Press 45°"))
    }

    @Test
    fun `resolve id exato, grafia diferente e apelido`() {
        assertEquals("agachamento_hack", resolveExerciseId("agachamento_hack", knownIds))
        assertEquals("agachamento_hack", resolveExerciseId("Agachamento-Hack", knownIds))
        assertEquals("agachamento_hack", resolveExerciseId("hack_squat", knownIds))
        assertEquals("extensao_joelho", resolveExerciseId("Cadeira Extensora", knownIds))
    }

    @Test
    fun `id desconhecido continua igual`() {
        assertEquals("exercicio_inventado", resolveExerciseId("exercicio_inventado", knownIds))
    }

    @Test
    fun `humaniza o id com acentos e preposicoes em minuscula`() {
        assertEquals("Agachamento Hack", humanizeExerciseId("agachamento_hack"))
        assertEquals("Extensão de Joelho", humanizeExerciseId("extensao_de_joelho"))
        assertEquals("Tríceps Francês", humanizeExerciseId("triceps_frances"))
    }

    @Test
    fun `nome de exibicao prefere a biblioteca, depois a copia gravada, depois o id humanizado`() {
        val byId = builtinExercises.associateBy { it.id }
        assertEquals("Agachamento Hack", exerciseDisplayName("agachamento_hack", byId, snapshot = "Hack Squat"))
        assertEquals("Meu Exercício", exerciseDisplayName("apagado", byId, snapshot = "Meu Exercício"))
        assertEquals("Remada Unilateral", exerciseDisplayName("remada_unilateral", emptyMap(), snapshot = null))
        // Treinos antigos gravaram o id cru como nome.
        assertEquals("Exercicio Inventado", exerciseDisplayName("exercicio_inventado", byId, snapshot = "exercicio_inventado"))
    }

    @Test
    fun `ids nativos sao unicos e todo apelido aponta para um nativo`() {
        assertEquals(builtinExercises.size, knownIds.size)
        val broken = exerciseAliases.filterValues { it !in knownIds }
        assertTrue("apelidos sem exercício nativo: $broken", broken.isEmpty())
        val shadowing = exerciseAliases.keys.filter { it in knownIds }
        assertTrue("apelidos iguais a ids nativos: $shadowing", shadowing.isEmpty())
    }

    @Test
    fun `todo exercicio nativo tem ilustracao e toda ilustracao e de um nativo`() {
        // Testes de unidade rodam com o diretório do módulo (app/) como diretório atual.
        val dir = File("src/main/assets/exercises")
        val illustrated = dir.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toSet()
        assertEquals(knownIds - illustrated, emptySet<String>())
        assertEquals(illustrated - knownIds, emptySet<String>())
        val missingFrames = illustrated.filter { id -> (0..1).any { !File(dir, "$id/$it.webp").isFile } }
        assertTrue("quadros faltando: $missingFrames", missingFrames.isEmpty())
    }
}
