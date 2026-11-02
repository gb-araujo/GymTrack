package app.gabrielaraujo.gymtrack.session

import android.app.Application
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.SAMPLE_PLAN_ID
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.repository.Repositories
import app.gabrielaraujo.gymtrack.testing.InMemoryRestTimerStore
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ActiveWorkoutManagerTest {
    private lateinit var db: GymTrackDatabase
    private lateinit var repos: Repositories
    private val clock = TestClock()
    private val restStore = InMemoryRestTimerStore()
    private val scopes = mutableListOf<CoroutineScope>()
    private val completed: MutableList<WorkoutSession> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setUp() {
        db = inMemoryDb()
        repos = Repositories(db, clock = clock)
        runBlocking { repos.userData.seedIfEmpty() }
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        db.close()
    }

    /** Um "processo": manager novo sobre o mesmo banco. */
    private fun newManager(): Pair<ActiveWorkoutManager, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        return ActiveWorkoutManager(repos.sessions, scope, restStore = restStore, afterCompleted = { completed += it }, clock = clock) to scope
    }

    /** Espera as gravações em segundo plano terminarem. */
    private fun CoroutineScope.idle() = runBlocking {
        while (true) {
            val children = this@idle.coroutineContext.job.children.toList()
            if (children.isEmpty()) break
            children.joinAll()
        }
    }

    private fun startWorkout(manager: ActiveWorkoutManager): ActiveSession = runBlocking {
        val plan = db.dao().getPlan(SAMPLE_PLAN_ID)!!
        manager.start(plan, dayIndex = 0) { id -> "Nome de $id" }.join()
        manager.active.value!!
    }

    @Test
    fun `treino em andamento e retomado depois que o processo morre`() {
        val (manager, scope) = newManager()
        val started = startWorkout(manager)
        assertEquals("Nome de supino_reto", started.session.exercises.first().name)
        manager.updateSet(0, 0) { it.copy(weight = 72.5, completed = true) }
        manager.addSet(1)
        manager.startRest(90, started.session.exercises.first().id)
        scope.idle()

        // Processo encerrado: um manager novo retoma do banco, com a série editada e o descanso.
        val (reopened, _) = newManager()
        runBlocking { reopened.restore() }
        val restored = reopened.active.value!!
        assertTrue(reopened.restored.value)
        assertEquals(started.session.id, restored.session.id)
        assertEquals(72.5, restored.session.exercises[0].sets[0].weight, 0.0)
        assertEquals(started.session.exercises[1].sets.size + 1, restored.session.exercises[1].sets.size)
        assertEquals(clock.now + 90_000, restored.restEndsAt)
    }

    @Test
    fun `descanso que ja acabou nao e retomado`() {
        val (manager, scope) = newManager()
        val started = startWorkout(manager)
        manager.startRest(60, started.session.exercises.first().id)
        scope.idle()
        clock.advanceMinutes(2)

        val (reopened, _) = newManager()
        runBlocking { reopened.restore() }
        assertNull(reopened.active.value!!.restEndsAt)
    }

    @Test
    fun `muitas edicoes seguidas gravam sempre a versao mais recente`() {
        val (manager, scope) = newManager()
        val started = startWorkout(manager)
        repeat(30) { i -> manager.updateSet(0, 0) { it.copy(reps = i + 1) } }
        scope.idle()
        val stored = runBlocking { repos.sessions.getSession(started.session.id) }!!
        assertEquals(30, stored.exercises[0].sets[0].reps)
    }

    @Test
    fun `concluir grava no historico e mantem o resumo aberto`() {
        val (manager, scope) = newManager()
        startWorkout(manager)
        manager.completeExercise(0)
        clock.advanceMinutes(50)
        runBlocking { manager.complete().join() }
        scope.idle()

        val summary = manager.active.value!!
        assertTrue(summary.session.completed)
        assertEquals(50, summary.session.duration)
        assertEquals(summary.session.id, completed.single().id)
        assertTrue(runBlocking { repos.sessions.getSession(summary.session.id) }!!.completed)

        // Edições atrasadas não reabrem o treino concluído.
        manager.updateSet(0, 0) { it.copy(weight = 999.0) }
        scope.idle()
        assertTrue(runBlocking { repos.sessions.getSession(summary.session.id) }!!.exercises[0].sets.none { it.weight == 999.0 })

        manager.dismissSummary()
        assertNull(manager.active.value)
    }

    @Test
    fun `abandonar descarta so o treino em andamento`() {
        val (manager, scope) = newManager()
        val started = startWorkout(manager)
        manager.abandon()
        scope.idle()
        assertNull(manager.active.value)
        assertNull(runBlocking { repos.sessions.getSession(started.session.id) })

        // Um concluído (resumo aberto) nunca é apagado ao sair.
        startWorkout(manager)
        runBlocking { manager.complete().join() }
        val done = manager.active.value!!.session
        manager.abandon()
        scope.idle()
        assertNotNull(runBlocking { repos.sessions.getSession(done.id) })
    }

    @Test
    fun `iniciar duas vezes nao cria dois treinos`() {
        val (manager, scope) = newManager()
        val plan = runBlocking { db.dao().getPlan(SAMPLE_PLAN_ID)!! }
        val jobs = listOf(manager.start(plan, 0) { null }, manager.start(plan, 1) { null })
        runBlocking { jobs.joinAll() }
        scope.idle()
        assertEquals(1, runBlocking { db.dao().getSessions() }.size)
    }

    @Test
    fun `descanso pode ser ajustado e parado`() {
        val (manager, _) = newManager()
        val started = startWorkout(manager)
        manager.startRest(90, started.session.exercises.first().id)
        assertEquals(clock.now + 90_000, manager.active.value!!.restEndsAt)
        manager.adjustRest(15)
        assertEquals(clock.now + 105_000, manager.active.value!!.restEndsAt)
        manager.stopRest()
        assertNull(manager.active.value!!.restEndsAt)
        assertNull(restStore.load())
    }

    @Test
    fun `limpar esquece o treino sem regravar nada`() {
        val (manager, scope) = newManager()
        startWorkout(manager)
        manager.updateSet(0, 0) { it.copy(weight = 50.0) }
        runBlocking { manager.clear() }
        runBlocking { repos.userData.clearUserData() }
        scope.idle()
        assertNull(manager.active.value)
        assertFalse(runBlocking { db.dao().getSessions() }.isNotEmpty())
    }
}
