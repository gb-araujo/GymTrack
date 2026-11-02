package app.gabrielaraujo.gymtrack.auth

import android.app.Application
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.SAMPLE_PLAN_ID
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.data.repository.Repositories
import app.gabrielaraujo.gymtrack.session.ActiveWorkoutManager
import app.gabrielaraujo.gymtrack.sync.SyncResult
import app.gabrielaraujo.gymtrack.testing.FakeAuth
import app.gabrielaraujo.gymtrack.testing.FakeSync
import app.gabrielaraujo.gymtrack.testing.FakeSyncScheduler
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class AccountCoordinatorTest {
    private lateinit var db: GymTrackDatabase
    private lateinit var repos: Repositories
    private val clock = TestClock()
    private val auth = FakeAuth()
    private val sync = FakeSync()
    private val scheduler = FakeSyncScheduler()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var workout: ActiveWorkoutManager
    private lateinit var coordinator: AccountCoordinator

    @Before
    fun setUp() {
        db = inMemoryDb()
        repos = Repositories(db, currentUid = { auth.currentUid }, clock = clock)
        runBlocking { repos.userData.seedIfEmpty() }
        workout = ActiveWorkoutManager(repos.sessions, scope, clock = clock)
        coordinator = AccountCoordinator(auth, sync, scheduler, repos.userData, workout, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun eventually(condition: () -> Boolean) = runBlocking {
        withTimeout(10_000) { while (!condition()) delay(20) }
    }

    private fun addGoal() = runBlocking {
        repos.goals.saveGoal(Goal(id = newId(), type = "workout_count", title = "Meta", target = 10.0, unit = "treinos", createdAt = clock.now))
    }

    @Test
    fun `entrar sincroniza e sem rede agenda nova tentativa`() {
        coordinator.start()
        sync.result = SyncResult.Failure("sem conexão")
        auth.user.value = AppUser("u1", "Teste", null)
        eventually { sync.logins == 1 }
        eventually { scheduler.requests == 1 }
    }

    @Test
    fun `voltar ao app so pede sincronizacao com usuario logado`() {
        coordinator.onForeground()
        assertEquals(0, scheduler.requests)
        auth.user.value = AppUser("u1", "Teste", null)
        coordinator.onForeground()
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `sair sem pendencias apaga os dados da conta deste aparelho`() = runBlocking {
        auth.user.value = AppUser("u1", "Teste", null)
        addGoal()
        // Tudo já sincronizado.
        db.dao().getGoals().forEach { db.dao().clearGoalDirty(it.id, it.updatedAt) }
        workout.start(db.dao().getPlan(SAMPLE_PLAN_ID)!!, 0) { null }.join()

        assertEquals(SignOutResult.Done, coordinator.signOut())
        assertNull(auth.user.value)
        assertEquals(1, scheduler.cancels)
        assertNull(workout.active.value)
        assertTrue(db.dao().getGoals().isEmpty())
        assertTrue(db.dao().getSessions().isEmpty())
        // A ficha exemplo volta, sem dono, para o próximo uso.
        assertEquals(listOf(SAMPLE_PLAN_ID), db.dao().getPlans().map { it.id })
    }

    @Test
    fun `sair com pendencias pede confirmacao antes de apagar`() = runBlocking {
        auth.user.value = AppUser("u1", "Teste", null)
        addGoal()

        val result = coordinator.signOut()
        assertEquals(SignOutResult.PendingChanges(1), result)
        assertEquals(1, sync.syncs) // tentou enviar antes
        assertNotNull(auth.user.value)
        assertEquals(1, db.dao().getGoals().size)

        assertEquals(SignOutResult.Done, coordinator.signOut(discardPending = true))
        assertNull(auth.user.value)
        assertTrue(db.dao().getGoals().isEmpty())
    }

    @Test
    fun `reenviar tudo marca os dados da conta e sincroniza`() = runBlocking {
        auth.user.value = AppUser("u1", "Teste", null)
        addGoal()
        db.dao().getGoals().forEach { db.dao().clearGoalDirty(it.id, it.updatedAt) }

        sync.result = SyncResult.Failure("sem conexão")
        coordinator.forceFullResync()
        assertTrue(db.dao().getGoals().single().dirty)
        assertEquals(1, sync.syncs)
        assertEquals(1, scheduler.requests)
    }
}
