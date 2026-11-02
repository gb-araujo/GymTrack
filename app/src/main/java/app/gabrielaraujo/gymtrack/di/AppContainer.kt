package app.gabrielaraujo.gymtrack.di

import android.content.Context
import app.gabrielaraujo.gymtrack.auth.AccountCoordinator
import app.gabrielaraujo.gymtrack.auth.AuthManager
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.repository.Repositories
import app.gabrielaraujo.gymtrack.health.HealthConnectManager
import app.gabrielaraujo.gymtrack.health.HealthImporter
import app.gabrielaraujo.gymtrack.session.ActiveWorkoutManager
import app.gabrielaraujo.gymtrack.session.PrefsRestTimerStore
import app.gabrielaraujo.gymtrack.session.ServiceRestTimerAlerts
import app.gabrielaraujo.gymtrack.sync.FirestoreRemoteStore
import app.gabrielaraujo.gymtrack.sync.SyncEngine
import app.gabrielaraujo.gymtrack.sync.WorkManagerSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Injeção de dependências manual: cria e guarda as dependências do app pelo tempo de vida do processo.
 * Um container só basta para o tamanho atual; as implementações reais das interfaces (Firebase,
 * Health Connect, WorkManager) só existem aqui, e os testes montam as suas com fakes.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Trabalho que não pode ser cancelado junto com uma tela (ex.: gravar o treino, sair da conta). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: GymTrackDatabase by lazy { GymTrackDatabase.build(appContext) }

    val authManager: AuthManager by lazy { AuthManager() }

    val syncScheduler: WorkManagerSyncScheduler by lazy { WorkManagerSyncScheduler(appContext) }

    val repositories: Repositories by lazy {
        Repositories(
            database,
            currentUid = { authManager.currentUid },
            // Sem usuário logado não há para onde enviar.
            onPendingChange = { if (authManager.currentUid != null) syncScheduler.request() },
        )
    }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(database, FirestoreRemoteStore(), recomputeDerived = { uid -> repositories.records.recompute(uid) })
    }

    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(appContext) }

    val healthImporter: HealthImporter by lazy { HealthImporter(healthConnect, repositories.sessions) }

    val workout: ActiveWorkoutManager by lazy {
        ActiveWorkoutManager(
            repositories.sessions,
            appScope,
            restAlerts = ServiceRestTimerAlerts(appContext),
            restStore = PrefsRestTimerStore(appContext),
            afterCompleted = { session ->
                healthConnect.export(session)
                // O relógio pode já ter sincronizado parte da frequência cardíaca; o resto chega nas próximas voltas ao app.
                healthImporter.enrich(session)
            },
        )
    }

    val account: AccountCoordinator by lazy {
        AccountCoordinator(authManager, syncEngine, syncScheduler, repositories.userData, workout, appScope)
    }

    /** Inicialização do processo: semente, treino interrompido e acompanhamento do login. */
    fun start() {
        appScope.launch {
            repositories.userData.seedIfEmpty()
            workout.restore()
        }
        account.start()
    }

    /** O app voltou ao primeiro plano (o Health Connect só permite leitura nesse estado). */
    fun onForeground() {
        account.onForeground()
        appScope.launch { healthImporter.refresh() }
    }
}
