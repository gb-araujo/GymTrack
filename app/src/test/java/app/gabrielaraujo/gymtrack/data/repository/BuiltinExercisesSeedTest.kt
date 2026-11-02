package app.gabrielaraujo.gymtrack.data.repository

import android.app.Application
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.builtinExercises
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** A biblioteca nativa de quem já tinha o app acompanha as mudanças de [builtinExercises]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BuiltinExercisesSeedTest {
    private lateinit var db: GymTrackDatabase
    private lateinit var userData: UserDataRepository
    private val dao get() = db.dao()

    @Before
    fun setUp() {
        db = inMemoryDb()
        userData = UserDataRepository(db, TestClock())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `atualiza nome antigo e repoe exercicio nativo que faltava`() = runBlocking {
        // Instalação antiga: banco já semeado, com o nome velho e sem um exercício adicionado depois.
        val old = builtinExercises.filter { it.id != "prancha" }
            .map { if (it.id == "agachamento_hack") it.copy(name = "Hack Squat") else it }
        dao.insertExercises(old)

        userData.seedIfEmpty()

        assertEquals("Agachamento Hack", dao.getExercise("agachamento_hack")!!.name)
        assertEquals(builtinExercises.find { it.id == "prancha" }, dao.getExercise("prancha"))
    }

    @Test
    fun `nao mexe em exercicio personalizado nem o marca como pendente`() = runBlocking {
        val custom = Exercise(id = "meu", name = "Meu Exercício", primaryMuscle = "Peito", isCustom = true, ownerId = "u1", updatedAt = 7)
        dao.upsertExercise(custom)

        userData.seedIfEmpty()
        userData.seedIfEmpty()

        assertEquals(custom, dao.getExercise("meu"))
        assertEquals(builtinExercises.size + 1, dao.getExercises().size)
        assertFalse(dao.getExercises().any { it.dirty })
    }
}
