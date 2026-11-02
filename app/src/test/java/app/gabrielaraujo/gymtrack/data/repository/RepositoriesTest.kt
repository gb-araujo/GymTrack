package app.gabrielaraujo.gymtrack.data.repository

import android.app.Application
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.SAMPLE_PLAN_ID
import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.domain.health.WorkoutHealthMetrics
import app.gabrielaraujo.gymtrack.domain.workout.nextDayIndex
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.flow.first
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
class RepositoriesTest {
    private lateinit var db: GymTrackDatabase
    private lateinit var repos: Repositories
    private val clock = TestClock()
    private var uid: String? = "u1"
    private var pendingChanges = 0
    private val dao get() = db.dao()

    @Before
    fun setUp() {
        db = inMemoryDb()
        repos = Repositories(db, currentUid = { uid }, onPendingChange = { pendingChanges++ }, clock = clock)
        runBlocking { repos.userData.seedIfEmpty() }
    }

    @After
    fun tearDown() = db.close()

    private fun session(id: String = newId(), weight: Double = 60.0) = WorkoutSession(
        id = id, workoutPlanId = SAMPLE_PLAN_ID, workoutDayId = "day_a", workoutDayLabel = "A", workoutDayName = "Peito",
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
        repos.sessions.saveSessionInProgress(inProgress)
        assertTrue(dao.getDirtySessions("u1").isEmpty())
        assertEquals(0, pendingChanges)
        repos.sessions.completeSession(repos.sessions.getSession(inProgress.id)!!)
        assertEquals(listOf(inProgress.id), dao.getDirtySessions("u1").map { it.id })
    }

    @Test
    fun `concluir treino avanca a ficha e registra recorde`() = runBlocking {
        val planBefore = dao.getPlan(SAMPLE_PLAN_ID)!!
        val s = session(weight = 80.0)
        repos.sessions.saveSessionInProgress(s)
        val done = repos.sessions.completeSession(s)
        assertTrue(done.completed)
        assertEquals(listOf("supino_reto"), done.prsAchieved)
        assertEquals(80.0, dao.getPersonalRecord("supino_reto")!!.weight, 0.0)
        // O próximo treino sai do histórico; a ficha em si não é regravada (nem entra na fila de envio).
        assertEquals(1, nextDayIndex(planBefore, repos.sessions.completedSessionsSnapshot()))
        assertEquals(planBefore, dao.getPlan(SAMPLE_PLAN_ID))
    }

    @Test
    fun `escritas do usuario avisam pendencia e o recalculo da sincronizacao nao`() = runBlocking {
        repos.goals.saveGoal(Goal(id = newId(), type = "workout_count", title = "Meta", target = 10.0, unit = "treinos", createdAt = clock.now))
        repos.plans.setActivePlan(SAMPLE_PLAN_ID)
        repos.sessions.completeSession(session())
        assertEquals(3, pendingChanges)

        // O recálculo após o pull roda dentro da sincronização: avisar reagendaria a própria sincronização.
        repos.records.recompute("u1")
        assertEquals(3, pendingChanges)
    }

    @Test
    fun `recalcular recordes nao marca treinos antigos como editados`() = runBlocking {
        val first = session(weight = 60.0)
        repos.sessions.completeSession(first)
        clock.advanceMinutes(60)
        val second = session(weight = 70.0)
        repos.sessions.completeSession(second)
        // Tudo sincronizado.
        dao.getSessions().forEach { dao.clearSessionDirty(it.id, it.updatedAt) }
        val secondBefore = repos.sessions.getSession(second.id)!!
        assertEquals(listOf("supino_reto"), secondBefore.prsAchieved)

        // Corrigir o primeiro treino para 80 kg tira o PR do segundo: isso é derivado, não edição do segundo.
        clock.advanceMinutes(60)
        val edited = repos.sessions.getSession(first.id)!!.let { s ->
            s.copy(exercises = s.exercises.map { ex -> ex.copy(sets = ex.sets.map { it.copy(weight = 80.0) }) })
        }
        repos.sessions.updateCompletedSession(edited)

        val secondAfter = repos.sessions.getSession(second.id)!!
        assertEquals(emptyList<String>(), secondAfter.prsAchieved)
        assertEquals(secondBefore.updatedAt, secondAfter.updatedAt)
        assertFalse(secondAfter.dirty)
        assertEquals(listOf(first.id), dao.getDirtySessions("u1").map { it.id })
    }

    @Test
    fun `salvar ficha do editor preserva o que mudou fora dele`() = runBlocking {
        val opened = dao.getPlan(SAMPLE_PLAN_ID)!!
        // Enquanto o editor estava aberto, a ficha foi desativada (ex.: pela sincronização).
        dao.upsertPlan(opened.copy(isActive = false, currentDayIndex = 2))
        repos.plans.savePlan(opened.copy(name = "Editada"))
        val saved = dao.getPlan(SAMPLE_PLAN_ID)!!
        assertEquals("Editada", saved.name)
        assertFalse(saved.isActive)
        assertEquals(2, saved.currentDayIndex)
        assertTrue(saved.dirty)
        assertEquals("u1", saved.ownerId)
    }

    @Test
    fun `limpar dados da conta apaga tudo do usuario e semeia de novo`() = runBlocking {
        repos.sessions.completeSession(session())
        repos.goals.saveGoal(Goal(id = newId(), type = "workout_count", title = "Meta", target = 10.0, unit = "treinos", createdAt = clock.now))
        repos.exercises.saveCustomExercise(Exercise(id = newId(), name = "Meu exercício", primaryMuscle = "peito"))
        val builtin = dao.getExercises().count { !it.isCustom }

        repos.userData.clearUserData()

        assertTrue(dao.getSessions().isEmpty())
        assertTrue(dao.getGoals().isEmpty())
        assertTrue(dao.getPersonalRecords().isEmpty())
        assertEquals(builtin, dao.getExercises().size)
        assertEquals(listOf(SAMPLE_PLAN_ID), dao.getPlans().map { it.id })
        assertNull(dao.getPlans().single().ownerId)
        assertEquals(0, repos.userData.pendingSyncCount.first())
    }

    @Test
    fun `descartar nao apaga treino concluido`() = runBlocking {
        val s = session()
        repos.sessions.completeSession(s)
        repos.sessions.discardSessionInProgress(s.id)
        assertTrue(repos.sessions.getSession(s.id)!!.completed)
    }

    @Test
    fun `backup exportado restaura historico em outro aparelho`() = runBlocking {
        repos.sessions.completeSession(session(weight = 70.0))
        val json = repos.backup.exportBackup()

        val other = inMemoryDb()
        try {
            val count = Repositories(other, currentUid = { "u2" }, clock = clock).backup.importBackup(json)
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
        val done = repos.sessions.completeSession(s)
        dao.upsertSession(done.copy(dirty = false))

        clock.advanceMinutes(5)
        val first = repos.sessions.updateSessionHealthMetrics(s.id, WorkoutHealthMetrics(avgHeartRate = 130, maxHeartRate = 170))!!
        assertEquals(130, first.avgHeartRate)
        assertTrue(first.dirty)
        // Medida do relógio não é edição: avança 1 ms (para o envio notar), sem vencer edições de outro aparelho.
        assertEquals(done.updatedAt + 1, first.updatedAt)

        // Calorias chegam depois; a frequência cardíaca que não veio desta vez continua salva.
        val second = repos.sessions.updateSessionHealthMetrics(s.id, WorkoutHealthMetrics(calories = 280.0))!!
        assertEquals(130, second.avgHeartRate)
        assertEquals(280.0, second.calories!!, 0.0)

        // Nada mudou: não regrava (nem marca para sincronizar de novo).
        val before = pendingChanges
        assertNull(repos.sessions.updateSessionHealthMetrics(s.id, WorkoutHealthMetrics(calories = 280.0)))
        assertEquals(before, pendingChanges)
        // Treino em andamento não recebe medidas.
        val inProgress = session()
        repos.sessions.saveSessionInProgress(inProgress)
        assertNull(repos.sessions.updateSessionHealthMetrics(inProgress.id, WorkoutHealthMetrics(avgHeartRate = 100)))
    }
}
