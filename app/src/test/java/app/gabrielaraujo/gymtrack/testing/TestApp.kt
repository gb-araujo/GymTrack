package app.gabrielaraujo.gymtrack.testing

import app.gabrielaraujo.gymtrack.auth.AccountCoordinator
import app.gabrielaraujo.gymtrack.data.repository.Repositories
import app.gabrielaraujo.gymtrack.health.HealthImporter
import app.gabrielaraujo.gymtrack.session.ActiveWorkoutManager
import app.gabrielaraujo.gymtrack.ui.account.AccountViewModel
import app.gabrielaraujo.gymtrack.ui.goals.GoalsViewModel
import app.gabrielaraujo.gymtrack.ui.history.HistoryViewModel
import app.gabrielaraujo.gymtrack.ui.home.HomeViewModel
import app.gabrielaraujo.gymtrack.ui.plans.PlanEditorViewModel
import app.gabrielaraujo.gymtrack.ui.plans.PlansViewModel
import app.gabrielaraujo.gymtrack.ui.progress.ProgressViewModel
import app.gabrielaraujo.gymtrack.ui.workout.WorkoutViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.io.Closeable

/**
 * O app montado sobre um banco em memória e fakes, do jeito que o AppContainer monta com as
 * implementações reais. Os ViewModels saem daqui com as mesmas dependências da fábrica do app.
 */
class TestApp(
    val clock: TestClock = TestClock(),
    uid: String? = null,
) : Closeable {
    val db = inMemoryDb()
    val auth = FakeAuth(uid)
    val sync = FakeSync()
    val scheduler = FakeSyncScheduler()
    val exporter = FakeExporter()
    val health = FakeHealthData()
    val restStore = InMemoryRestTimerStore()

    /** Escopo do "processo" (o appScope do AppContainer). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repos = Repositories(db, currentUid = { auth.currentUid }, clock = clock)
    val healthImporter = HealthImporter(health, repos.sessions, clock)
    val workout = ActiveWorkoutManager(
        repos.sessions, scope, restStore = restStore,
        afterCompleted = { session -> exporter.export(session); healthImporter.enrich(session) },
        clock = clock,
    )
    val account = AccountCoordinator(auth, sync, scheduler, repos.userData, workout, scope)

    init {
        runBlocking {
            repos.userData.seedIfEmpty()
            workout.restore()
        }
    }

    fun homeVm() = HomeViewModel(repos.plans, repos.sessions, repos.records, repos.exercises, workout)

    fun workoutVm() = WorkoutViewModel(workout, repos.plans, repos.exercises, repos.sessions, healthImporter.importEnabled, scope)

    fun historyVm() = HistoryViewModel(repos.sessions, repos.plans, repos.records, repos.exercises, exporter, scope)

    fun plansVm() = PlansViewModel(repos.plans, repos.exercises, scope, clock)

    fun planEditorVm(planId: String?) = PlanEditorViewModel(planId, repos.plans, repos.exercises, scope, clock)

    fun progressVm() = ProgressViewModel(repos.sessions, repos.records, repos.exercises, healthImporter.bodyWeights, clock)

    fun goalsVm() = GoalsViewModel(repos.goals, repos.sessions, repos.records, repos.exercises, healthImporter.bodyWeights, scope, clock)

    fun accountVm() = AccountViewModel(
        auth, account, sync.status, repos.userData.pendingSyncCount, repos.backup, repos.sessions, healthImporter,
        healthConnect = null, appScope = scope,
    )

    /** Espera o trabalho em segundo plano (gravações no escopo do app) terminar. */
    fun idle() = runBlocking {
        while (true) {
            val children = scope.coroutineContext.job.children.toList()
            if (children.isEmpty()) break
            children.joinAll()
        }
    }

    override fun close() {
        scope.cancel()
        db.close()
    }
}
