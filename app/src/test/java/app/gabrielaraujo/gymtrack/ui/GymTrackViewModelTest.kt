package app.gabrielaraujo.gymtrack.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.GymTrackRepository
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.WorkoutHealthMetrics
import app.gabrielaraujo.gymtrack.health.HealthDataSource
import app.gabrielaraujo.gymtrack.testing.FakeAuth
import app.gabrielaraujo.gymtrack.testing.FakeExporter
import app.gabrielaraujo.gymtrack.testing.FakeHealthData
import app.gabrielaraujo.gymtrack.testing.FakeSync
import app.gabrielaraujo.gymtrack.testing.FakeSyncScheduler
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class GymTrackViewModelTest {
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var db: GymTrackDatabase
    private lateinit var repo: GymTrackRepository
    private val clock = TestClock()
    private val vms = mutableListOf<GymTrackViewModel>()

    @Before
    fun setUp() {
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(main)
        db = inMemoryDb()
        repo = GymTrackRepository(db, clock)
    }

    @After
    fun tearDown() {
        // Espera as corrotinas dos ViewModels terminarem: uma ainda rodando poderia ler o
        // Dispatchers.Main enquanto o próximo teste o troca (IllegalStateException no setMain).
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    private fun newVm(
        exporter: FakeExporter = FakeExporter(),
        health: HealthDataSource = HealthDataSource.None,
        auth: FakeAuth = FakeAuth(),
        scheduler: FakeSyncScheduler = FakeSyncScheduler(),
    ) = GymTrackViewModel(repo, auth, FakeSync(), scheduler, workoutExporter = exporter, healthData = health, clock = clock)
        .also { vms += it }

    private fun GymTrackViewModel.await(predicate: (UiState) -> Boolean): UiState =
        runBlocking { withTimeout(10_000) { state.first(predicate) } }

    private fun <T : Any> eventually(block: suspend () -> T?): T = runBlocking {
        withTimeout(10_000) {
            var value = block()
            while (value == null) { delay(20); value = block() }
            value!!
        }
    }

    /** Faz um treino completo do dia sugerido, com a carga informada na 1ª série do 1º exercício. */
    private fun GymTrackViewModel.doWorkout(firstWeight: Double = 50.0): WorkoutSession {
        await { it.loaded && it.activePlan != null }
        startSession(dayIndex = 0)
        val active = await { it.activeSession != null }.activeSession!!
        updateSetRecord(0, 0) { it.copy(weight = firstWeight) }
        active.session.exercises.indices.forEach { completeExercise(it) }
        clock.advanceMinutes(45)
        completeSession()
        val done = await { it.activeSession?.session?.completed == true }.activeSession!!.session
        dismissSummary()
        await { it.activeSession == null }
        return done
    }

    @Test
    fun `treino concluido continua no historico apos fechar o resumo e soma na ofensiva`() {
        val vm = newVm()
        val done = vm.doWorkout()

        val state = vm.await { it.sessions.size == 1 }
        assertEquals(done.id, state.sessions.single().id)
        assertEquals(1, state.streak.count)
        assertEquals(45, state.sessions.single().duration)
        assertTrue(runBlocking { repo.getSession(done.id) }!!.completed)

        // Reabrir o app (novo ViewModel sobre o mesmo banco) mantém o treino.
        val reopened = newVm().await { it.loaded && it.sessions.size == 1 }
        assertEquals(1, reopened.streak.count)
        assertNull(reopened.activeSession)
    }

    @Test
    fun `cada treino soma um na ofensiva`() {
        val vm = newVm()
        vm.doWorkout()
        vm.doWorkout()
        assertEquals(2, vm.await { it.sessions.size == 2 }.streak.count)
    }

    @Test
    fun `abandonar treino em andamento descarta so ele`() {
        val vm = newVm()
        vm.doWorkout()
        vm.startSession()
        val inProgress = vm.await { it.activeSession != null }.activeSession!!.session
        vm.abandonSession()
        eventually { if (runBlocking { repo.getSession(inProgress.id) } == null) true else null }
        assertEquals(1, vm.await { it.activeSession == null }.sessions.size)
    }

    @Test
    fun `treino em andamento e retomado ao reabrir o app`() {
        val vm = newVm()
        vm.await { it.loaded && it.activePlan != null }
        vm.startSession()
        vm.await { it.activeSession != null }
        vm.completeExercise(0)
        val id = vm.state.value.activeSession!!.session.id
        eventually { runBlocking { repo.getSession(id) }?.exercises?.first()?.completed?.takeIf { it } }

        val reopened = newVm().await { it.loaded }
        assertEquals(id, reopened.activeSession?.session?.id)
        assertEquals(1, reopened.activeSession?.currentExerciseIndex)
    }

    @Test
    fun `excluir treino recalcula recordes e ofensiva`() {
        val vm = newVm()
        val light = vm.doWorkout(firstWeight = 50.0)
        val heavy = vm.doWorkout(firstWeight = 200.0)
        val exerciseId = heavy.exercises.first().exerciseId
        assertEquals(200.0, vm.await { it.personalRecords[exerciseId]?.weight == 200.0 }.personalRecords[exerciseId]!!.weight, 0.0)

        vm.deleteCompletedSession(heavy.id)
        val state = vm.await { it.sessions.size == 1 }
        assertEquals(light.id, state.sessions.single().id)
        assertEquals(1, state.streak.count)
        val pr = vm.await { it.personalRecords[exerciseId]?.sessionId == light.id }.personalRecords.getValue(exerciseId)
        assertEquals(light.id, pr.sessionId)
        assertTrue(runBlocking { repo.getSession(heavy.id) }!!.deleted)
    }

    @Test
    fun `editar treino recalcula volume e recorde`() {
        val vm = newVm()
        val done = vm.doWorkout(firstWeight = 50.0)
        val exerciseId = done.exercises.first().exerciseId
        val saved = vm.await { it.sessions.size == 1 }.sessions.single()

        val first = saved.exercises.first()
        val edited = saved.copy(
            exercises = listOf(first.copy(sets = first.sets.mapIndexed { i, s -> if (i == 0) s.copy(weight = 300.0) else s })) +
                saved.exercises.drop(1),
        )
        vm.updateCompletedSession(edited)

        // Recordes e histórico chegam por fluxos separados: espera os dois refletirem a edição.
        val state = vm.await { it.personalRecords[exerciseId]?.weight == 300.0 && it.sessions.single().totalVolume != saved.totalVolume }
        val updated = state.sessions.single()
        assertEquals((saved.totalVolume ?: 0.0) + (300.0 - 50.0) * first.sets.first().reps, updated.totalVolume!!, 0.001)
        assertTrue(exerciseId in updated.prsAchieved)
    }

    @Test
    fun `timer de descanso guarda o fim e pode ser ajustado`() {
        val vm = newVm()
        vm.await { it.loaded && it.activePlan != null }
        vm.startSession()
        val active = vm.await { it.activeSession != null }.activeSession!!
        vm.startRestTimer(90, active.session.exercises.first().id)
        vm.await { it.activeSession?.restEndsAt == clock.now + 90_000 }
        vm.adjustRestTimer(15)
        vm.await { it.activeSession?.restEndsAt == clock.now + 105_000 }
        vm.stopRestTimer()
        val stopped = vm.await { it.activeSession?.restEndsAt == null }
        assertNotNull(stopped.activeSession)
    }

    @Test
    fun `treino concluido vai para o Health Connect e edicao e exclusao tambem`() {
        val exporter = FakeExporter()
        val vm = newVm(exporter = exporter)
        val done = vm.doWorkout()
        assertEquals(done.id, eventually { exporter.exported.singleOrNull() }.id)

        val saved = vm.await { it.sessions.size == 1 }.sessions.single()
        vm.updateCompletedSession(saved.copy(exercises = saved.exercises.drop(1)))
        val edited = eventually { exporter.exported.getOrNull(1) }
        assertEquals(done.id, edited.id)
        assertEquals(saved.exercises.size - 1, edited.exercises.size)

        vm.deleteCompletedSession(done.id)
        assertEquals(done.id, eventually { exporter.removed.singleOrNull() })
    }

    @Test
    fun `frequencia cardiaca e calorias do relogio entram no treino`() {
        val health = FakeHealthData(metrics = WorkoutHealthMetrics(avgHeartRate = 128, maxHeartRate = 171, calories = 315.0))
        val vm = newVm(health = health)
        val done = vm.doWorkout()

        val saved = vm.await { it.sessions.singleOrNull()?.avgHeartRate != null }.sessions.single()
        assertEquals(done.id, saved.id)
        assertEquals(171, saved.maxHeartRate)
        assertEquals(315.0, saved.calories!!, 0.0)
        // Entra na fila de sincronização sem mexer nos totais nem nos recordes.
        val stored = runBlocking { repo.getSession(done.id) }!!
        assertTrue(stored.dirty)
        assertEquals(done.totalVolume, stored.totalVolume)
        assertEquals(done.prsAchieved, stored.prsAchieved)
    }

    @Test
    fun `dados do relogio que chegam depois sao buscados ao voltar ao app`() {
        val health = FakeHealthData(metrics = null)
        val vm = newVm(health = health)
        val done = vm.doWorkout()
        assertNull(vm.await { it.sessions.size == 1 }.sessions.single().avgHeartRate)

        // O Samsung Health sincronizou depois; a próxima volta ao app (após o intervalo mínimo) busca de novo.
        health.metrics = WorkoutHealthMetrics(avgHeartRate = 120)
        health.weights = listOf(BodyWeightEntry(clock.now - 86_400_000, 81.2), BodyWeightEntry(clock.now, 80.6))
        clock.advanceMinutes(10)
        vm.refreshHealthData()

        val state = vm.await { it.sessions.singleOrNull()?.avgHeartRate == 120 && it.bodyWeights.size == 2 }
        assertEquals(done.id, state.sessions.single().id)
        assertEquals(80.6, state.bodyWeights.last().kg, 0.0)
        assertTrue(state.healthImportEnabled)
    }

    @Test
    fun `edicoes agendam sincronizacao em segundo plano so com usuario logado`() {
        val anonymous = FakeSyncScheduler()
        newVm(scheduler = anonymous).doWorkout()
        assertEquals(0, anonymous.requests)

        val logged = FakeSyncScheduler()
        newVm(auth = FakeAuth("u1"), scheduler = logged).doWorkout()
        assertTrue(logged.requests > 0)
    }

    @Test
    fun `proximo treino sugerido avanca com o historico`() {
        val vm = newVm()
        assertEquals(0, vm.await { it.loaded && it.activePlan != null }.nextDayIndex)
        vm.doWorkout()
        assertEquals(1, vm.await { it.sessions.size == 1 }.nextDayIndex)
    }

    @Test
    fun `sair sem pendencias apaga os dados da conta deste aparelho`() {
        val auth = FakeAuth("u1")
        val scheduler = FakeSyncScheduler()
        val vm = newVm(auth = auth, scheduler = scheduler)
        vm.doWorkout()
        // Tudo já sincronizado.
        runBlocking { repo.dao.getSessions().forEach { repo.dao.clearSessionDirty(it.id, it.updatedAt) } }
        runBlocking { repo.dao.getPersonalRecords().forEach { repo.dao.clearPersonalRecordDirty(it.exerciseId, it.updatedAt) } }

        vm.signOut()
        val state = vm.await { it.sessions.isEmpty() && it.personalRecords.isEmpty() }
        assertNull(auth.user.value)
        assertEquals(1, scheduler.cancels)
        assertNull(vm.signOutPending.value)
        // A ficha exemplo volta, sem dono, para o próximo uso.
        assertEquals(listOf("sample_hipertrofia"), state.plans.map { it.id })
    }

    @Test
    fun `sair com pendencias pede confirmacao antes de apagar`() {
        val auth = FakeAuth("u1")
        val vm = newVm(auth = auth)
        vm.doWorkout()

        vm.signOut()
        val pending = eventually { vm.signOutPending.value }
        assertTrue(pending > 0)
        assertNotNull(auth.user.value)
        assertEquals(1, vm.await { it.sessions.size == 1 }.sessions.size)

        vm.cancelSignOut()
        assertNull(vm.signOutPending.value)
        assertNotNull(auth.user.value)

        vm.signOut()
        eventually { vm.signOutPending.value }
        vm.confirmSignOut()
        vm.await { it.sessions.isEmpty() }
        assertNull(auth.user.value)
    }
}
