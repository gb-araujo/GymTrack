package app.gabrielaraujo.gymtrack.data

import android.app.Application
import app.gabrielaraujo.gymtrack.domain.WorkoutHealthMetrics
import app.gabrielaraujo.gymtrack.domain.nextDayIndex
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class GymTrackRepositoryTest {
    private lateinit var db: GymTrackDatabase
    private lateinit var repo: GymTrackRepository
    private val clock = TestClock()

    @Before
    fun setUp() {
        db = inMemoryDb()
        repo = GymTrackRepository(db, clock)
        runBlocking { repo.seedIfEmpty() }
    }

    @After
    fun tearDown() = db.close()

    private fun session(id: String = newId(), weight: Double = 60.0) = WorkoutSession(
        id = id, workoutPlanId = "sample_hipertrofia", workoutDayId = "day_a", workoutDayLabel = "A", workoutDayName = "Peito",
        exercises = listOf(
            SessionExercise(
                id = newId(), exerciseId = "supino_reto", workoutExerciseId = "we", name = "Supino", order = 0,
                sets = listOf(SetRecord(newId(), weight, 10, completed = true)),
            ),
        ),
        startTime = clock.now,
    )

    @Test
    fun `clearDirty nao descarta edicao feita durante o envio`() = runBlocking {
        val dao = repo.dao
        val plan = dao.getPlans().first()
        dao.upsertPlan(plan.copy(dirty = true, updatedAt = 1))
        dao.upsertPlan(plan.copy(dirty = true, updatedAt = 2, name = "Editada"))
        dao.clearPlanDirty(plan.id, updatedAt = 1)
        assertTrue(dao.getPlan(plan.id)!!.dirty)
        dao.clearPlanDirty(plan.id, updatedAt = 2)
        assertFalse(dao.getPlan(plan.id)!!.dirty)
    }

    @Test
    fun `treino em andamento nao entra na fila de sincronizacao`() = runBlocking {
        val inProgress = session()
        repo.saveSessionInProgress(inProgress, uid = "u1")
        assertTrue(repo.dao.getDirtySessions("u1").isEmpty())
        repo.completeSession(repo.getSession(inProgress.id)!!, uid = "u1")
        assertEquals(listOf(inProgress.id), repo.dao.getDirtySessions("u1").map { it.id })
    }

    @Test
    fun `concluir treino avanca a ficha e registra recorde`() = runBlocking {
        val planBefore = repo.dao.getPlan(SAMPLE_PLAN_ID)!!
        val s = session(weight = 80.0)
        repo.saveSessionInProgress(s, null)
        val done = repo.completeSession(s, null)
        assertTrue(done.completed)
        assertEquals(listOf("supino_reto"), done.prsAchieved)
        assertEquals(80.0, repo.dao.getPersonalRecord("supino_reto")!!.weight, 0.0)
        // O próximo treino sai do histórico; a ficha em si não é regravada (nem entra na fila de envio).
        assertEquals(1, nextDayIndex(planBefore, repo.completedSessionsSnapshot()))
        assertEquals(planBefore, repo.dao.getPlan(SAMPLE_PLAN_ID))
    }

    @Test
    fun `recalcular recordes nao marca treinos antigos como editados`() = runBlocking {
        val first = session(weight = 60.0)
        repo.completeSession(first, "u1")
        clock.advanceMinutes(60)
        val second = session(weight = 70.0)
        repo.completeSession(second, "u1")
        // Tudo sincronizado.
        repo.dao.getSessions().forEach { repo.dao.clearSessionDirty(it.id, it.updatedAt) }
        val secondBefore = repo.getSession(second.id)!!
        assertEquals(listOf("supino_reto"), secondBefore.prsAchieved)

        // Corrigir o primeiro treino para 80 kg tira o PR do segundo: isso é derivado, não edição do segundo.
        clock.advanceMinutes(60)
        val edited = repo.getSession(first.id)!!.let { s ->
            s.copy(exercises = s.exercises.map { ex -> ex.copy(sets = ex.sets.map { it.copy(weight = 80.0) }) })
        }
        repo.updateCompletedSession(edited, "u1")

        val secondAfter = repo.getSession(second.id)!!
        assertEquals(emptyList<String>(), secondAfter.prsAchieved)
        assertEquals(secondBefore.updatedAt, secondAfter.updatedAt)
        assertFalse(secondAfter.dirty)
        assertEquals(listOf(first.id), repo.dao.getDirtySessions("u1").map { it.id })
    }

    @Test
    fun `salvar ficha do editor preserva o que mudou fora dele`() = runBlocking {
        val opened = repo.dao.getPlan(SAMPLE_PLAN_ID)!!
        // Enquanto o editor estava aberto, a ficha foi desativada (ex.: pela sincronização).
        repo.dao.upsertPlan(opened.copy(isActive = false, currentDayIndex = 2))
        repo.savePlan(opened.copy(name = "Editada"), "u1")
        val saved = repo.dao.getPlan(SAMPLE_PLAN_ID)!!
        assertEquals("Editada", saved.name)
        assertFalse(saved.isActive)
        assertEquals(2, saved.currentDayIndex)
        assertTrue(saved.dirty)
    }

    @Test
    fun `limpar dados da conta apaga tudo do usuario e semeia de novo`() = runBlocking {
        repo.completeSession(session(), "u1")
        repo.saveGoal(Goal(id = newId(), type = "workout_count", title = "Meta", target = 10.0, unit = "treinos", createdAt = clock.now), "u1")
        repo.saveCustomExercise(Exercise(id = newId(), name = "Meu exercício", primaryMuscle = "peito"), "u1")
        val builtin = repo.dao.getExercises().count { !it.isCustom }

        repo.clearUserData()

        assertTrue(repo.dao.getSessions().isEmpty())
        assertTrue(repo.dao.getGoals().isEmpty())
        assertTrue(repo.dao.getPersonalRecords().isEmpty())
        assertEquals(builtin, repo.dao.getExercises().size)
        assertEquals(listOf(SAMPLE_PLAN_ID), repo.dao.getPlans().map { it.id })
        assertNull(repo.dao.getPlans().single().ownerId)
    }

    @Test
    fun `descartar nao apaga treino concluido`() = runBlocking {
        val s = session()
        repo.completeSession(s, null)
        repo.discardSessionInProgress(s.id)
        assertTrue(repo.getSession(s.id)!!.completed)
    }

    @Test
    fun `backup exportado restaura historico em outro aparelho`() = runBlocking {
        repo.completeSession(session(weight = 70.0), null)
        val json = repo.exportBackup()

        val other = inMemoryDb()
        try {
            val otherRepo = GymTrackRepository(other, clock)
            val count = otherRepo.importBackup(json, uid = "u2")
            assertTrue(count >= 2)
            val sessions = other.dao().getCompletedSessions()
            assertEquals(1, sessions.size)
            assertEquals("u2", sessions.single().ownerId)
            assertTrue(sessions.single().dirty)
            assertEquals(70.0, other.dao().getPersonalRecord("supino_reto")!!.weight, 0.0)
        } finally {
            other.close()
        }
    }

    @Test
    fun `medidas do relogio sao gravadas sem apagar as que ja existiam`() = runBlocking {
        val s = session()
        val done = repo.completeSession(s, null)
        repo.dao.upsertSession(done.copy(dirty = false))

        clock.advanceMinutes(5)
        val first = repo.updateSessionHealthMetrics(s.id, WorkoutHealthMetrics(avgHeartRate = 130, maxHeartRate = 170), uid = "u1")!!
        assertEquals(130, first.avgHeartRate)
        assertTrue(first.dirty)
        // Medida do relógio não é edição: avança 1 ms (para o envio notar), sem vencer edições de outro aparelho.
        assertEquals(done.updatedAt + 1, first.updatedAt)

        // Calorias chegam depois; a frequência cardíaca que não veio desta vez continua salva.
        val second = repo.updateSessionHealthMetrics(s.id, WorkoutHealthMetrics(calories = 280.0), uid = "u1")!!
        assertEquals(130, second.avgHeartRate)
        assertEquals(280.0, second.calories!!, 0.0)

        // Nada mudou: não regrava (nem marca para sincronizar de novo).
        assertNull(repo.updateSessionHealthMetrics(s.id, WorkoutHealthMetrics(calories = 280.0), uid = "u1"))
        // Treino em andamento não recebe medidas.
        val inProgress = session()
        repo.saveSessionInProgress(inProgress, null)
        assertNull(repo.updateSessionHealthMetrics(inProgress.id, WorkoutHealthMetrics(avgHeartRate = 100), null))
    }
}
