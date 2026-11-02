package app.gabrielaraujo.gymtrack.di

import android.content.Context
import app.gabrielaraujo.gymtrack.auth.AuthManager
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.repository.Repositories
import app.gabrielaraujo.gymtrack.health.HealthConnectManager
import app.gabrielaraujo.gymtrack.session.ServiceRestTimerAlerts
import app.gabrielaraujo.gymtrack.sync.FirestoreRemoteStore
import app.gabrielaraujo.gymtrack.sync.SyncEngine
import app.gabrielaraujo.gymtrack.sync.WorkManagerSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    val restTimerAlerts: ServiceRestTimerAlerts by lazy { ServiceRestTimerAlerts(appContext) }

    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(appContext) }
}
