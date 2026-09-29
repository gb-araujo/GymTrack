package app.gabrielaraujo.gymtrack.data

import android.app.Application
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(repo.dao.getDirtySessions().isEmpty())
        repo.completeSession(repo.getSession(inProgress.id)!!, uid = "u1")
        assertEquals(listOf(inProgress.id), repo.dao.getDirtySessions().map { it.id })
    }

    @Test
    fun `concluir treino avanca a ficha e registra recorde`() = runBlocking {
        val s = session(weight = 80.0)
        repo.saveSessionInProgress(s, null)
        val done = repo.completeSession(s, null)
        assertTrue(done.completed)
        assertEquals(listOf("supino_reto"), done.prsAchieved)
        assertEquals(80.0, repo.dao.getPersonalRecord("supino_reto")!!.weight, 0.0)
        assertEquals(1, repo.dao.getPlan("sample_hipertrofia")!!.currentDayIndex)
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
}
