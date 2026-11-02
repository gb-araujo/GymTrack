package app.gabrielaraujo.gymtrack.testing

import android.app.Activity
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gabrielaraujo.gymtrack.auth.AppUser
import app.gabrielaraujo.gymtrack.auth.AuthGateway
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.health.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.health.WorkoutHealthMetrics
import app.gabrielaraujo.gymtrack.health.HealthDataSource
import app.gabrielaraujo.gymtrack.health.WorkoutExporter
import app.gabrielaraujo.gymtrack.session.RestState
import app.gabrielaraujo.gymtrack.session.RestTimerStore
import app.gabrielaraujo.gymtrack.sync.CloudSync
import app.gabrielaraujo.gymtrack.sync.SyncResult
import app.gabrielaraujo.gymtrack.sync.SyncScheduler
import app.gabrielaraujo.gymtrack.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Collections

class FakeAuth(uid: String? = null) : AuthGateway {
    override val user = MutableStateFlow(uid?.let { AppUser(it, "Teste", "teste@example.com") })
    override val currentUid: String? get() = user.value?.uid
    override suspend fun signInWithGoogle(activity: Activity): Result<Unit> = Result.success(Unit)
    override fun signOut() { user.value = null }
}

/** Sincronização que não fala com a nuvem: só conta as chamadas e devolve [result]. */
class FakeSync : CloudSync {
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)
    @Volatile var result: SyncResult = SyncResult.Success
    @Volatile var syncs = 0
    @Volatile var logins = 0
    override suspend fun onLogin(uid: String): SyncResult { logins++; return result }
    override suspend fun sync(uid: String): SyncResult { syncs++; return result }
    override fun reset() = Unit
    override suspend fun runExclusive(block: suspend () -> Unit) = block()
}

/** Conta os pedidos de sincronização em segundo plano. */
class FakeSyncScheduler : SyncScheduler {
    @Volatile var requests = 0
    @Volatile var cancels = 0
    override fun request() { requests++ }
    override fun cancel() { cancels++ }
}

/** Registra o que seria enviado ao Health Connect. */
class FakeExporter : WorkoutExporter {
    val exported: MutableList<WorkoutSession> = Collections.synchronizedList(mutableListOf())
    val removed: MutableList<String> = Collections.synchronizedList(mutableListOf())
    override suspend fun export(session: WorkoutSession) { exported += session }
    override suspend fun remove(sessionId: String) { removed += sessionId }
}

/** Health Connect com a importação ligada, devolvendo as medidas e pesagens configuradas. */
class FakeHealthData(
    @Volatile var metrics: WorkoutHealthMetrics? = null,
    @Volatile var weights: List<BodyWeightEntry> = emptyList(),
) : HealthDataSource {
    override val importEnabled = MutableStateFlow(true)
    override suspend fun canRead() = importEnabled.value
    override suspend fun workoutMetrics(startTime: Long, endTime: Long) = metrics
    override suspend fun bodyWeights(since: Long) = weights.filter { it.time >= since }
}

/** Descanso guardado em memória (sobrevive a um "novo processo" dentro do mesmo teste). */
class InMemoryRestTimerStore : RestTimerStore {
    @Volatile private var rest: RestState? = null
    override fun save(rest: RestState) { this.rest = rest }
    override fun load(): RestState? = rest
    override fun clear() { rest = null }
}

/** Relógio controlável, começando perto de "agora" para a ofensiva (que usa a data de hoje). */
class TestClock(var now: Long = System.currentTimeMillis()) : () -> Long {
    override fun invoke(): Long = now
    fun advanceMinutes(minutes: Long) { now += minutes * 60_000 }
    fun advanceDays(days: Long) { now += days * 86_400_000 }
}

fun inMemoryDb(): GymTrackDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), GymTrackDatabase::class.java).build()
