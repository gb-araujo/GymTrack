package app.gabrielaraujo.gymtrack.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.SAMPLE_PLAN_ID
import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.data.repository.Repositories
import app.gabrielaraujo.gymtrack.domain.WorkoutHealthMetrics
import app.gabrielaraujo.gymtrack.testing.FakeRemoteStore
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.IOException

/** Cenários críticos da sincronização: dois "aparelhos" (bancos separados) sobre a mesma nuvem em memória. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SyncEngineTest {
    private val remote = FakeRemoteStore()
    private val opened = mutableListOf<GymTrackDatabase>()
    private val json = Json { ignoreUnknownKeys = true }

    private inner class Device(start: Long = BASE, val db: GymTrackDatabase = inMemoryDb()) {
        val clock = TestClock(start)
        var uid: String? = U
        val repos = Repositories(db, currentUid = { uid }, clock = clock)
        val engine = SyncEngine(db, remote, recomputeDerived = { uid -> repos.records.recompute(uid) }, clock = clock)
        val dao = db.dao()

        init {
            opened += db
            runBlocking { repos.userData.seedIfEmpty() }
        }

        fun goal(id: String) = runBlocking { dao.getGoal(id) }
    }

    @After
    fun tearDown() = opened.forEach { if (it.isOpen) it.close() }

    private fun newGoal(title: String, clock: TestClock) =
        Goal(id = newId(), type = "workout_count", title = title, target = 10.0, unit = "treinos", createdAt = clock.now)

    private fun session(clock: TestClock, weight: Double, exerciseId: String = "supino_reto") = WorkoutSession(
        id = newId(), workoutPlanId = SAMPLE_PLAN_ID, workoutDayId = "day_a", workoutDayLabel = "A", workoutDayName = "Peito",
        exercises = listOf(
            SessionExercise(
                id = newId(), exerciseId = exerciseId, workoutExerciseId = "we", name = "Supino", order = 0,
                sets = listOf(SetRecord(newId(), weight, 10, completed = true)),
            ),
        ),
        startTime = clock.now,
    )

    private fun remoteGoal(id: String): Goal? = remote.doc(U, "goals", id)?.let { json.decodeFromString(Goal.serializer(), it.json) }

    @Test
    fun `envio bem-sucedido limpa a pendencia e chega ao outro aparelho`() = runBlocking {
        val a = Device()
        val goal = newGoal("Meta", a.clock)
        a.repos.goals.saveGoal(goal)

        assertEquals(SyncResult.Success, a.engine.onLogin(U))
        assertFalse(a.goal(goal.id)!!.dirty)
        assertEquals("Meta", remoteGoal(goal.id)!!.title)
        assertTrue(a.engine.status.value is SyncStatus.Synced)

        val b = Device()
        b.engine.onLogin(U)
        assertEquals("Meta", b.goal(goal.id)!!.title)
        assertFalse(b.goal(goal.id)!!.dirty)
    }

    @Test
    fun `sem conexao a pendencia continua e sobe quando a rede volta`() = runBlocking {
        val a = Device()
        val goal = newGoal("Meta", a.clock)
        a.repos.goals.saveGoal(goal)
        remote.offline = true

        assertTrue(a.engine.onLogin(U) is SyncResult.Failure)
        assertTrue(a.engine.status.value is SyncStatus.Error)
        assertTrue(a.goal(goal.id)!!.dirty)
        assertNull(remote.doc(U, "goals", goal.id))

        remote.offline = false
        assertEquals(SyncResult.Success, a.engine.sync(U))
        assertFalse(a.goal(goal.id)!!.dirty)
        assertEquals("Meta", remoteGoal(goal.id)!!.title)
    }

    @Test
    fun `pendencia sobrevive ao app ser encerrado antes de sincronizar`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "restart-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, GymTrackDatabase::class.java, name)
            .addMigrations(*GymTrackDatabase.MIGRATIONS).build()

        val first = Device(db = open())
        val goal = newGoal("Antes de fechar", first.clock)
        first.repos.goals.saveGoal(goal)
        first.db.close() // processo encerrado sem sincronizar

        val reopened = Device(db = open())
        assertTrue(reopened.goal(goal.id)!!.dirty)
        assertEquals(SyncResult.Success, reopened.engine.sync(U))
        assertEquals("Antes de fechar", remoteGoal(goal.id)!!.title)
        assertFalse(reopened.goal(goal.id)!!.dirty)
        reopened.db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `reenvio apos resposta perdida nao duplica`() = runBlocking {
        val a = Device()
        val goal = newGoal("Meta", a.clock)
        a.repos.goals.saveGoal(goal)
        // O envio chega ao servidor, mas a resposta se perde e a conexão cai antes do pull (que confirmaria o envio).
        remote.loseNextPushResponse = true
        remote.onChangesFetched = { collection -> if (collection == "goals") throw IOException("conexão caiu") }

        assertTrue(a.engine.onLogin(U) is SyncResult.Failure)
        assertEquals(1, remote.count(U, "goals"))
        assertTrue(a.goal(goal.id)!!.dirty)

        remote.onChangesFetched = {}
        val pushedBefore = remote.pushedDocs
        assertEquals(SyncResult.Success, a.engine.sync(U))
        assertEquals(pushedBefore + 1, remote.pushedDocs) // reenviou
        assertEquals(1, remote.count(U, "goals")) // sem duplicar
        assertEquals("Meta", remoteGoal(goal.id)!!.title)
        assertFalse(a.goal(goal.id)!!.dirty)
    }

    @Test
    fun `envio confirmado pelo pull da mesma rodada limpa a pendencia`() = runBlocking {
        val a = Device()
        val goal = newGoal("Meta", a.clock)
        a.repos.goals.saveGoal(goal)
        remote.loseNextPushResponse = true

        // A resposta do envio se perdeu, mas o pull trouxe a mesma versão: o registro já está na nuvem.
        assertTrue(a.engine.onLogin(U) is SyncResult.Failure)
        assertEquals(1, remote.count(U, "goals"))
        assertFalse(a.goal(goal.id)!!.dirty)
    }

    @Test
    fun `ficha exemplo de um aparelho novo nao sobrescreve a editada na nuvem`() = runBlocking {
        val a = Device()
        a.engine.onLogin(U)
        val sample = a.dao.getPlan(SAMPLE_PLAN_ID)!!
        a.clock.advanceMinutes(10)
        a.repos.plans.savePlan(sample.copy(name = "Minha ficha"))
        a.engine.sync(U)

        // Instalação nova, mais tarde: semeia a ficha exemplo de fábrica e entra na mesma conta.
        val b = Device(start = a.clock.now + 86_400_000)
        b.engine.onLogin(U)

        assertEquals(1, remote.count(U, "plans"))
        assertEquals("Minha ficha", json.decodeFromString(app.gabrielaraujo.gymtrack.data.WorkoutPlan.serializer(), remote.doc(U, "plans", SAMPLE_PLAN_ID)!!.json).name)
        assertEquals("Minha ficha", b.dao.getPlan(SAMPLE_PLAN_ID)!!.name)
    }

    @Test
    fun `conflito entre aparelhos vale a edicao mais recente mesmo enviada antes`() = runBlocking {
        val a = Device()
        val b = Device()
        val goal = newGoal("Meta", a.clock)
        a.repos.goals.saveGoal(goal)
        a.engine.onLogin(U)
        b.engine.onLogin(U)

        // A edita primeiro (offline); B edita depois e sincroniza antes.
        a.clock.now = BASE + 1_000
        a.repos.goals.saveGoal(a.goal(goal.id)!!.copy(title = "Editada em A"))
        b.clock.now = BASE + 2_000
        b.repos.goals.saveGoal(b.goal(goal.id)!!.copy(title = "Editada em B"))
        b.engine.sync(U)

        // O envio de A é recusado (mais antigo) e A recebe a versão de B.
        assertEquals(SyncResult.Success, a.engine.sync(U))
        assertEquals("Editada em B", remoteGoal(goal.id)!!.title)
        assertEquals("Editada em B", a.goal(goal.id)!!.title)
        assertFalse(a.goal(goal.id)!!.dirty)
    }

    @Test
    fun `conflito entre aparelhos edicao mais recente enviada depois vence`() = runBlocking {
        val a = Device()
        val b = Device()
        val goal = newGoal("Meta", a.clock)
        a.repos.goals.saveGoal(goal)
        a.engine.onLogin(U)
        b.engine.onLogin(U)

        a.clock.now = BASE + 1_000
        a.repos.goals.saveGoal(a.goal(goal.id)!!.copy(title = "Editada em A"))
        b.clock.now = BASE + 2_000
        b.repos.goals.saveGoal(b.goal(goal.id)!!.copy(title = "Editada em B"))
        a.engine.sync(U)
        b.engine.sync(U)
        a.engine.sync(U)

        assertEquals("Editada em B", remoteGoal(goal.id)!!.title)
        assertEquals("Editada em B", a.goal(goal.id)!!.title)
        assertEquals("Editada em B", b.goal(goal.id)!!.title)
    }

    @Test
    fun `exclusao com tombstone propaga e recalcula recordes no outro aparelho`() = runBlocking {
        val a = Device()
        val b = Device()
        val s = session(a.clock, weight = 100.0)
        a.repos.sessions.saveSessionInProgress(s)
        a.repos.sessions.completeSession(s)
        a.engine.onLogin(U)
        b.engine.onLogin(U)
        assertEquals(1, b.dao.getCompletedSessions().size)
        assertEquals(100.0, b.dao.getPersonalRecord("supino_reto")!!.weight, 0.0)

        a.clock.advanceMinutes(10)
        a.repos.sessions.deleteCompletedSession(s.id)
        a.engine.sync(U)
        b.engine.sync(U)

        assertTrue(b.dao.getCompletedSessions().isEmpty())
        assertTrue(b.dao.getSession(s.id)!!.deleted)
        assertTrue(b.dao.getPersonalRecord("supino_reto")!!.deleted)
    }

    @Test
    fun `falha parcial nao impede as outras colecoes`() = runBlocking {
        val a = Device()
        a.engine.onLogin(U)
        val goal = newGoal("Meta", a.clock)
        a.repos.goals.saveGoal(goal)
        a.repos.plans.savePlan(a.dao.getPlan(SAMPLE_PLAN_ID)!!.copy(name = "Renomeada"))
        remote.failingCollections += "goals"

        assertTrue(a.engine.sync(U) is SyncResult.Failure)
        assertFalse(a.dao.getPlan(SAMPLE_PLAN_ID)!!.dirty)
        assertTrue(a.goal(goal.id)!!.dirty)

        remote.failingCollections.clear()
        assertEquals(SyncResult.Success, a.engine.sync(U))
        assertFalse(a.goal(goal.id)!!.dirty)
    }

    @Test
    fun `pull interrompido nao avanca o cursor e recupera na proxima vez`() = runBlocking {
        val a = Device()
        val goal = newGoal("Meta", a.clock)
        a.repos.goals.saveGoal(goal)
        a.engine.onLogin(U)

        val b = Device()
        remote.onChangesFetched = { collection -> if (collection == "goals") throw IOException("conexão caiu") }
        assertTrue(b.engine.onLogin(U) is SyncResult.Failure)
        assertNull(b.goal(goal.id))

        // "Reinício": outro motor sobre o mesmo banco, com a rede de volta.
        remote.onChangesFetched = {}
        val restarted = SyncEngine(b.db, remote, recomputeDerived = { uid -> b.repos.records.recompute(uid) }, clock = b.clock)
        assertEquals(SyncResult.Success, restarted.sync(U))
        assertEquals("Meta", b.goal(goal.id)!!.title)
    }

    @Test
    fun `duas sincronizacoes simultaneas nao duplicam nem perdem pendencias`() = runBlocking {
        val a = Device()
        val goals = List(20) { newGoal("Meta $it", a.clock) }
        goals.forEach { a.repos.goals.saveGoal(it) }

        val results = withContext(Dispatchers.Default) {
            listOf(async { a.engine.onLogin(U) }, async { a.engine.sync(U) }).awaitAll()
        }

        assertTrue(results.all { it == SyncResult.Success })
        assertEquals(20, remote.count(U, "goals"))
        assertTrue(goals.none { a.goal(it.id)!!.dirty })
    }

    @Test
    fun `edicao feita durante o pull nao e sobrescrita`() = runBlocking {
        val a = Device()
        val b = Device()
        val goal = newGoal("Meta", a.clock)
        a.repos.goals.saveGoal(goal)
        a.engine.onLogin(U)
        b.engine.onLogin(U)

        b.clock.now = BASE + 1_000
        b.repos.goals.saveGoal(b.goal(goal.id)!!.copy(title = "Remota"))
        b.engine.sync(U)

        // O usuário edita em A depois de o pull buscar a versão remota e antes de aplicá-la.
        a.clock.now = BASE + 2_000
        remote.onChangesFetched = { collection ->
            if (collection == "goals") a.repos.goals.saveGoal(a.goal(goal.id)!!.copy(title = "Local"))
        }
        a.engine.sync(U)
        remote.onChangesFetched = {}

        assertEquals("Local", a.goal(goal.id)!!.title)
        assertTrue(a.goal(goal.id)!!.dirty)
        a.engine.sync(U)
        assertEquals("Local", remoteGoal(goal.id)!!.title)
    }

    @Test
    fun `recordes convergem quando dois aparelhos treinam sem ter sincronizado`() = runBlocking {
        val a = Device()
        val b = Device()
        a.engine.onLogin(U)
        b.engine.onLogin(U)

        val heavy = session(a.clock, weight = 100.0)
        a.repos.sessions.completeSession(heavy)
        a.engine.sync(U)

        // B treina mais tarde, mais leve, sem ter recebido o treino de A: localmente o recorde é 90.
        b.clock.now = a.clock.now + 3_600_000
        val light = session(b.clock, weight = 90.0)
        b.repos.sessions.completeSession(light)
        assertEquals(90.0, b.dao.getPersonalRecord("supino_reto")!!.weight, 0.0)

        b.engine.sync(U)
        a.engine.sync(U)

        assertEquals(100.0, b.dao.getPersonalRecord("supino_reto")!!.weight, 0.0)
        assertEquals(100.0, a.dao.getPersonalRecord("supino_reto")!!.weight, 0.0)
        val cloud = json.decodeFromString(app.gabrielaraujo.gymtrack.data.PersonalRecord.serializer(), remote.doc(U, "prs", "supino_reto")!!.json)
        assertEquals(100.0, cloud.weight, 0.0)
        assertEquals(listOf("supino_reto"), b.dao.getSession(heavy.id)!!.prsAchieved)
        assertEquals(emptyList<String>(), b.dao.getSession(light.id)!!.prsAchieved)
    }

    @Test
    fun `medidas do relogio nao desfazem a edicao do treino feita no outro aparelho`() = runBlocking {
        val a = Device()
        val b = Device()
        val s = session(a.clock, weight = 60.0)
        a.repos.sessions.completeSession(s)
        a.engine.onLogin(U)
        b.engine.onLogin(U)

        // B corrige as séries do treino e sincroniza; A (desatualizado) recebe a frequência cardíaca depois.
        b.clock.now = a.clock.now + 600_000
        val edited = b.dao.getSession(s.id)!!.let { it.copy(exercises = it.exercises.map { ex -> ex.copy(sets = ex.sets.map { set -> set.copy(weight = 65.0) }) }) }
        b.repos.sessions.updateCompletedSession(edited)
        b.engine.sync(U)
        a.clock.now = b.clock.now + 600_000
        a.repos.sessions.updateSessionHealthMetrics(s.id, WorkoutHealthMetrics(avgHeartRate = 140))

        a.engine.sync(U)
        b.engine.sync(U)

        for (session in listOf(a.dao.getSession(s.id)!!, b.dao.getSession(s.id)!!)) {
            assertEquals(65.0, session.exercises.single().sets.single().weight, 0.0)
            assertEquals(140, session.avgHeartRate)
        }
    }

    @Test
    fun `pendencias de outra conta nunca sobem para a conta logada`() = runBlocking {
        val a = Device()
        val goal = newGoal("De outra conta", a.clock)
        a.uid = null
        a.repos.goals.saveGoal(goal.copy(ownerId = "outra"))
        assertTrue(a.goal(goal.id)!!.dirty)

        a.engine.onLogin(U)
        assertNull(remote.doc(U, "goals", goal.id))
    }

    private companion object {
        const val U = "u1"
        const val BASE = 1_750_000_000_000L
    }
}
