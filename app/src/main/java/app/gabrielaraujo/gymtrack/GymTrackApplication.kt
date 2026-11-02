package app.gabrielaraujo.gymtrack

import android.app.Application
import app.gabrielaraujo.gymtrack.auth.AuthManager
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.GymTrackRepository
import app.gabrielaraujo.gymtrack.health.HealthConnectManager
import app.gabrielaraujo.gymtrack.session.ServiceRestTimerAlerts
import app.gabrielaraujo.gymtrack.sync.FirestoreRemoteStore
import app.gabrielaraujo.gymtrack.sync.SyncEngine
import app.gabrielaraujo.gymtrack.sync.SyncScheduler
import app.gabrielaraujo.gymtrack.sync.WorkManagerSyncScheduler
import app.gabrielaraujo.gymtrack.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Segura o banco Room, o repositório, o Auth, o SyncEngine e as integrações por toda a vida do processo.
 */
class GymTrackApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: GymTrackDatabase by lazy { GymTrackDatabase.build(this) }
    val repository: GymTrackRepository by lazy { GymTrackRepository(database) }
    val authManager: AuthManager by lazy { AuthManager() }
    val syncEngine: SyncEngine by lazy {
        SyncEngine(database, FirestoreRemoteStore(), recomputeDerived = { uid -> repository.recomputeRecords(uid) })
    }
    val syncScheduler: SyncScheduler by lazy { WorkManagerSyncScheduler(this) }
    val restTimerAlerts: ServiceRestTimerAlerts by lazy { ServiceRestTimerAlerts(this) }
    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(this) }

    override fun onCreate() {
        super.onCreate()
        // Mantém o widget em dia com o histórico e a ficha ativa.
        appScope.launch {
            combine(repository.completedSessions, repository.plans) { sessions, plans -> sessions to plans }
                .collectLatest { (sessions, plans) -> runCatching { WidgetUpdater.update(this@GymTrackApplication, sessions, plans) } }
        }
    }
}
