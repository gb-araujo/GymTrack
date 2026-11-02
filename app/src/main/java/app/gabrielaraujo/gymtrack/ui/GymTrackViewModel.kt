package app.gabrielaraujo.gymtrack.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.auth.AppUser
import app.gabrielaraujo.gymtrack.auth.AuthGateway
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.GymTrackRepository
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.domain.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.StreakInfo
import app.gabrielaraujo.gymtrack.domain.activePlanOf
import app.gabrielaraujo.gymtrack.domain.bestRecordsByExercise
import app.gabrielaraujo.gymtrack.domain.computeStreak
import app.gabrielaraujo.gymtrack.domain.nextDayIndex
import app.gabrielaraujo.gymtrack.health.HealthDataSource
import app.gabrielaraujo.gymtrack.health.WorkoutExporter
import app.gabrielaraujo.gymtrack.session.RestTimerAlerts
import app.gabrielaraujo.gymtrack.sync.CloudSync
import app.gabrielaraujo.gymtrack.sync.SyncResult
import app.gabrielaraujo.gymtrack.sync.SyncScheduler
import app.gabrielaraujo.gymtrack.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** Sessão em andamento mantida em memória (e espelhada no Room a cada mudança). */
data class ActiveSession(
    val session: WorkoutSession,
    val currentExerciseIndex: Int = 0,
    /** Fim do descanso atual (epoch ms), ou null se não há descanso rodando. */
    val restEndsAt: Long? = null,
    val restExerciseId: String? = null,
    val lastSessionForDay: WorkoutSession? = null,
)

data class UiState(
    val loaded: Boolean = false,
    val exercises: List<Exercise> = emptyList(),
    val plans: List<WorkoutPlan> = emptyList(),
    val activePlan: WorkoutPlan? = null,
    /** Índice do próximo treino sugerido da ficha ativa (derivado do histórico). */
    val nextDayIndex: Int = 0,
    val sessions: List<WorkoutSession> = emptyList(),
    val personalRecords: Map<String, PersonalRecord> = emptyMap(),
    val goals: List<Goal> = emptyList(),
    val totalWorkouts: Int = 0,
    val streak: StreakInfo = computeStreak(emptyList()),
    val activeSession: ActiveSession? = null,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val pendingSync: Int = 0,
    /** Pesagens do Health Connect (mais antiga primeiro); vazio sem a importação ligada. */
    val bodyWeights: List<BodyWeightEntry> = emptyList(),
    val healthImportEnabled: Boolean = false,
)

/** Dados persistidos, derivados dos Flows do Room. */
private data class Library(
    val exercises: List<Exercise>,
    val plans: List<WorkoutPlan>,
    val sessions: List<WorkoutSession>,
    val personalRecords: Map<String, PersonalRecord>,
    val goals: List<Goal>,
)

/**
 * Estado de tela e orquestração. As regras de persistência (recordes, totais, backup)
 * ficam no [GymTrackRepository]; a lógica pura em `domain/`.
 */
class GymTrackViewModel(
    private val repo: GymTrackRepository,
    private val auth: AuthGateway,
    private val sync: CloudSync,
    private val syncScheduler: SyncScheduler = SyncScheduler.None,
    private val restAlerts: RestTimerAlerts = RestTimerAlerts.None,
    private val workoutExporter: WorkoutExporter = WorkoutExporter.None,
    private val healthData: HealthDataSource = HealthDataSource.None,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val activeSession = MutableStateFlow<ActiveSession?>(null)
    private val loaded = MutableStateFlow(false)

    private val library = combine(
        repo.exercises,
        repo.plans,
        repo.completedSessions,
        repo.personalRecords.map { bestRecordsByExercise(it) },
        repo.goals,
    ) { exercises, plans, sessions, records, goals -> Library(exercises, plans, sessions, records, goals) }
        .distinctUntilChanged()

    private val syncInfo = combine(sync.status, repo.pendingSyncCount) { status, pending -> status to pending }

    private val bodyWeights = MutableStateFlow<List<BodyWeightEntry>>(emptyList())
    private val healthInfo = combine(bodyWeights, healthData.importEnabled) { weights, enabled ->
        (if (enabled) weights else emptyList()) to enabled
    }

    val state: StateFlow<UiState> = combine(library, activeSession, syncInfo, loaded, healthInfo) { lib, active, (status, pending), isLoaded, (weights, importOn) ->
        val activePlan = activePlanOf(lib.plans)
        UiState(
            loaded = isLoaded,
            exercises = lib.exercises,
            plans = lib.plans,
            activePlan = activePlan,
            nextDayIndex = activePlan?.let { nextDayIndex(it, lib.sessions) } ?: 0,
            sessions = lib.sessions,
            personalRecords = lib.personalRecords,
            goals = lib.goals,
            totalWorkouts = lib.sessions.size,
            streak = computeStreak(lib.sessions.map { it.startTime }),
            activeSession = active,
            syncStatus = status,
            pendingSync = pending,
            bodyWeights = weights,
            healthImportEnabled = importOn,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    val user: StateFlow<AppUser?> = auth.user

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    /** Pendências que não subiram quando o usuário pediu para sair (aguardando confirmação), ou null. */
    private val _signOutPending = MutableStateFlow<Int?>(null)
    val signOutPending: StateFlow<Int?> = _signOutPending.asStateFlow()

    private var syncJob: Job? = null

    /** Serializa as gravações da sessão ativa para que um snapshot antigo nunca sobrescreva a sessão concluída. */
    private val sessionWriteLock = Mutex()

    /** Evita duas leituras simultâneas do Health Connect (e consultas repetidas a cada volta ao app). */
    private val healthLock = Mutex()
    private var lastHealthRefresh = 0L

    private val uid get() = auth.currentUid

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            restoreActiveSession()
            loaded.value = true
        }
        viewModelScope.launch {
            // Ao logar (ou já logado no start), adota dados locais e sincroniza.
            auth.user.map { it?.uid }.distinctUntilChanged().collect { id ->
                if (id == null) {
                    sync.reset()
                } else if (sync.onLogin(id) is SyncResult.Failure) {
                    // Sem rede agora: tenta de novo em segundo plano quando ela voltar.
                    syncScheduler.request()
                }
            }
        }
    }

    /** Agenda uma sincronização em segundo plano (agrupada, só com rede) se houver usuário logado. */
    private fun scheduleSync() {
        if (uid != null) syncScheduler.request()
    }

    fun syncNow() {
        val u = uid ?: return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            if (sync.sync(u) is SyncResult.Failure) syncScheduler.request()
        }
    }

    /** Força o reenvio de tudo, mesmo o que já estava marcado como sincronizado (ex: depois de habilitar o Firestore). */
    fun forceFullResync() {
        val u = uid ?: return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            repo.forceFullResync(u)
            if (sync.sync(u) is SyncResult.Failure) syncScheduler.request()
        }
    }

    /** Executa uma escrita no banco e agenda a sincronização. */
    private fun write(block: suspend () -> Unit) = viewModelScope.launch {
        block()
        scheduleSync()
    }

    // ---------------- Autenticação ----------------

    fun signIn(activity: Activity) = viewModelScope.launch {
        _signingIn.value = true
        _authError.value = null
        val result = auth.signInWithGoogle(activity)
        _signingIn.value = false
        result.onFailure { _authError.value = it.message ?: "Falha ao entrar." }
    }

    /**
     * Sai da conta e apaga os dados dela deste aparelho (outra conta que entrar não os vê nem os envia).
     * Antes, tenta enviar as pendências; se alguma não subir, pede confirmação ([signOutPending]).
     */
    fun signOut() = viewModelScope.launch {
        val u = uid ?: return@launch
        sync.sync(u)
        val pending = repo.pendingSyncCount.first()
        if (pending > 0) _signOutPending.value = pending else finishSignOut()
    }

    /** Sai mesmo com pendências (elas são perdidas). */
    fun confirmSignOut() = viewModelScope.launch {
        _signOutPending.value = null
        finishSignOut()
    }

    fun cancelSignOut() {
        _signOutPending.value = null
    }

    private suspend fun finishSignOut() {
        syncScheduler.cancel()
        syncJob?.cancel()
        stopRestTimer()
        activeSession.value = null
        auth.signOut()
        // Espera uma sincronização em andamento terminar: senão ela regravaria dados depois da limpeza.
        sync.runExclusive { sessionWriteLock.withLock { repo.clearUserData() } }
    }

    fun clearAuthError() { _authError.value = null }

    // ---------------- Backup ----------------

    /** Importa um backup (deste app ou do app web). [onResult] recebe a quantidade de registros, ou -1 se inválido. */
    fun importBackup(text: String, onResult: (Int) -> Unit) = viewModelScope.launch {
        val count = runCatching { repo.importBackup(text, uid) }.getOrDefault(-1)
        onResult(count)
        if (count >= 0) scheduleSync()
    }

    /** Gera o backup completo e grava em [open] (ex.: um arquivo escolhido pelo usuário). */
    fun exportBackup(open: () -> OutputStream?, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching {
            val json = repo.exportBackup()
            withContext(Dispatchers.IO) {
                (open() ?: error("sem destino")).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        }.isSuccess
        onDone(ok)
    }

    // ---------------- Planos ----------------

    fun getExerciseById(id: String): Exercise? = state.value.exercises.find { it.id == id }

    fun savePlan(plan: WorkoutPlan) = write { repo.savePlan(plan, uid) }

    fun setActivePlan(planId: String) = write { repo.setActivePlan(planId, uid) }

    fun deletePlan(planId: String) = write { repo.deletePlan(planId, uid) }

    /** Atualiza a carga inicial de um exercício na ficha do treino em andamento. */
    fun updateExerciseInitialWeight(workoutExerciseId: String, newWeight: Double) {
        val planId = activeSession.value?.session?.workoutPlanId ?: state.value.activePlan?.id ?: return
        write { repo.updateExerciseInitialWeight(planId, workoutExerciseId, newWeight, uid) }
    }

    // ---------------- Sessão em andamento ----------------

    /** Retoma uma sessão não finalizada que ficou salva no banco (app reaberto no meio do treino). */
    private suspend fun restoreActiveSession() {
        if (activeSession.value != null) return
        val incomplete = repo.latestIncompleteSession() ?: return
        val firstIncomplete = incomplete.exercises.indexOfFirst { !it.completed }.coerceAtLeast(0)
        activeSession.value = ActiveSession(
            session = incomplete,
            currentExerciseIndex = firstIncomplete,
            lastSessionForDay = repo.lastCompletedSessionForDay(incomplete.workoutDayId),
        )
    }

    /** @param dayIndex dia escolhido; se null, usa o próximo sugerido ([UiState.nextDayIndex]). */
    fun startSession(dayIndex: Int? = null, onStarted: () -> Unit = {}) = viewModelScope.launch {
        val s = state.value
        if (activeSession.value != null) return@launch
        val plan = s.activePlan ?: return@launch
        if (plan.days.isEmpty()) return@launch
        val day = plan.days[(dayIndex ?: s.nextDayIndex) % plan.days.size]
        val now = clock()

        val sessionExercises = day.exercises.sortedBy { it.order }.map { we ->
            SessionExercise(
                id = newId(),
                exerciseId = we.exerciseId,
                workoutExerciseId = we.id,
                name = s.exercises.find { it.id == we.exerciseId }?.name ?: we.exerciseId,
                sets = List(we.sets) { SetRecord(id = newId(), weight = we.initialWeight, reps = we.repMax) },
                order = we.order,
            )
        }
        val session = WorkoutSession(
            id = newId(),
            workoutPlanId = plan.id,
            workoutDayId = day.id,
            workoutDayLabel = day.label,
            workoutDayName = day.name,
            exercises = sessionExercises,
            startTime = now,
            ownerId = uid,
            updatedAt = now,
        )
        sessionWriteLock.withLock { repo.saveSessionInProgress(session, uid) }
        activeSession.value = ActiveSession(session = session, lastSessionForDay = repo.lastCompletedSessionForDay(day.id))
        onStarted()
    }

    private fun mutateSession(block: (WorkoutSession) -> WorkoutSession) {
        activeSession.update { active -> active?.copy(session = block(active.session)) }
        persistActive()
    }

    /** Salva a sessão em andamento no Room a cada mudança, para não perder progresso. */
    private fun persistActive() {
        val session = activeSession.value?.session ?: return
        if (session.completed) return
        viewModelScope.launch {
            sessionWriteLock.withLock {
                // A sessão pode ter sido concluída ou descartada enquanto esta gravação aguardava.
                val current = activeSession.value?.session
                if (current == null || current.id != session.id || current.completed) return@withLock
                repo.saveSessionInProgress(session, uid)
            }
        }
    }

    private fun updateExercise(exerciseIndex: Int, transform: (SessionExercise) -> SessionExercise) {
        mutateSession { session ->
            if (exerciseIndex !in session.exercises.indices) return@mutateSession session
            session.copy(exercises = session.exercises.toMutableList().also { it[exerciseIndex] = transform(it[exerciseIndex]) })
        }
    }

    fun updateSetRecord(exerciseIndex: Int, setIndex: Int, transform: (SetRecord) -> SetRecord) =
        updateExercise(exerciseIndex) { ex ->
            if (setIndex !in ex.sets.indices) ex
            else ex.copy(sets = ex.sets.toMutableList().also { it[setIndex] = transform(it[setIndex]) })
        }

    fun addSet(exerciseIndex: Int) = updateExercise(exerciseIndex) { ex -> ex.withAddedSet() }

    fun removeSet(exerciseIndex: Int, setIndex: Int) =
        updateExercise(exerciseIndex) { ex -> ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != setIndex }) }

    fun completeExercise(exerciseIndex: Int) =
        updateExercise(exerciseIndex) { ex -> ex.copy(completed = true, sets = ex.sets.map { it.copy(completed = true) }) }

    fun uncompleteExercise(exerciseIndex: Int) = updateExercise(exerciseIndex) { it.copy(completed = false) }

    fun setCurrentExercise(index: Int) {
        activeSession.update { it?.copy(currentExerciseIndex = index) }
    }

    fun substituteExercise(exerciseIndex: Int, newExerciseId: String) {
        val newExercise = getExerciseById(newExerciseId) ?: return
        updateExercise(exerciseIndex) { old ->
            old.copy(exerciseId = newExerciseId, name = newExercise.name, substitutedFrom = old.substitutedFrom ?: old.exerciseId)
        }
    }

    fun abandonSession() {
        stopRestTimer()
        val session = activeSession.value?.session
        activeSession.value = null
        // Nunca apaga um treino já concluído: ele pertence ao histórico.
        if (session != null && !session.completed) {
            viewModelScope.launch { sessionWriteLock.withLock { repo.discardSessionInProgress(session.id) } }
        }
    }

    /** Fecha a tela de resumo mantendo o treino concluído salvo no histórico. */
    fun dismissSummary() {
        activeSession.update { if (it?.session?.completed == true) null else it }
    }

    fun completeSession() = viewModelScope.launch {
        val active = activeSession.value ?: return@launch
        if (active.session.completed) return@launch
        stopRestTimer()
        val completed = sessionWriteLock.withLock {
            // Usa o estado mais recente (pode ter mudado enquanto aguardava o lock).
            val latest = activeSession.value?.session?.takeIf { it.id == active.session.id && !it.completed }
                ?: return@launch
            val done = repo.completeSession(latest, uid)
            // Marca como concluída já dentro do lock: gravações pendentes da sessão em andamento são ignoradas.
            activeSession.update { it?.copy(session = done, restEndsAt = null, restExerciseId = null) }
            done
        }
        scheduleSync()
        workoutExporter.export(completed)
        // O relógio pode já ter sincronizado parte da frequência cardíaca; o resto chega nas próximas atualizações.
        healthLock.withLock { enrichWithHealthData(completed) }
    }

    // ---------------- Timer de descanso ----------------

    fun startRestTimer(seconds: Int, exerciseId: String) {
        val active = activeSession.value ?: return
        val endsAt = clock() + seconds.coerceAtLeast(0) * 1000L
        activeSession.value = active.copy(restEndsAt = endsAt, restExerciseId = exerciseId)
        val name = active.session.exercises.find { it.id == exerciseId }?.name.orEmpty()
        restAlerts.schedule(endsAt, name)
    }

    /** Soma (ou subtrai) segundos ao descanso em andamento. */
    fun adjustRestTimer(deltaSeconds: Int) {
        val active = activeSession.value ?: return
        val endsAt = active.restEndsAt ?: return
        val exerciseId = active.restExerciseId ?: return
        val remaining = ((endsAt - clock()) / 1000).toInt().coerceAtLeast(0)
        startRestTimer((remaining + deltaSeconds).coerceAtLeast(0), exerciseId)
    }

    fun stopRestTimer() {
        restAlerts.cancel()
        activeSession.update { it?.copy(restEndsAt = null, restExerciseId = null) }
    }

    // ---------------- Histórico ----------------

    fun updateCompletedSession(session: WorkoutSession) = write {
        repo.updateCompletedSession(session, uid)
        repo.getSession(session.id)?.let { workoutExporter.export(it) }
    }

    fun deleteCompletedSession(id: String) = write {
        repo.deleteCompletedSession(id, uid)
        workoutExporter.remove(id)
    }

    // ---------------- Health Connect (relógio, Samsung Health) ----------------

    /**
     * Busca no Health Connect o peso corporal e a frequência cardíaca e as calorias dos treinos recentes.
     * Os dados do relógio chegam com atraso (o Samsung Health sincroniza aos poucos), então os treinos dos
     * últimos dias são consultados de novo a cada volta ao app, no máximo uma vez a cada poucos minutos.
     * @param backfill busca também os treinos do último mês (ao ligar a importação), ignorando o intervalo.
     */
    fun refreshHealthData(backfill: Boolean = false) = viewModelScope.launch {
        healthLock.withLock {
            val now = clock()
            if (!backfill && now - lastHealthRefresh < HEALTH_REFRESH_INTERVAL_MS) return@launch
            if (!healthData.canRead()) {
                bodyWeights.value = emptyList()
                return@launch
            }
            lastHealthRefresh = now
            bodyWeights.value = healthData.bodyWeights(since = now - WEIGHT_HISTORY_MS)
            val window = if (backfill) HEALTH_BACKFILL_MS else HEALTH_RECENT_MS
            repo.completedSessionsSnapshot()
                .filter { (it.endTime ?: it.startTime) >= now - window }
                .forEach { enrichWithHealthData(it) }
        }
    }

    /** Deve rodar com [healthLock]. */
    private suspend fun enrichWithHealthData(session: WorkoutSession) {
        val end = session.endTime ?: return
        val metrics = healthData.workoutMetrics(session.startTime, end) ?: return
        val updated = repo.updateSessionHealthMetrics(session.id, metrics, uid) ?: return
        // Mantém a tela de resumo em dia se ela ainda estiver aberta.
        activeSession.update { if (it?.session?.id == updated.id) it.copy(session = updated) else it }
        scheduleSync()
    }

    /** Histórico de um exercício em todas as sessões concluídas (mais recentes primeiro). */
    fun exerciseHistory(exerciseId: String): List<Triple<Long, List<SetRecord>, String>> =
        state.value.sessions
            .mapNotNull { session ->
                session.exercises.find { it.exerciseId == exerciseId }?.let { ex -> Triple(session.startTime, ex.sets, session.id) }
            }
            .sortedByDescending { it.first }

    // ---------------- Exercícios ----------------

    fun saveCustomExercise(exercise: Exercise) = write { repo.saveCustomExercise(exercise, uid) }

    fun deleteCustomExercise(id: String) = write { repo.deleteCustomExercise(id, uid) }

    // ---------------- Metas ----------------

    fun addGoal(goal: Goal) = write { repo.saveGoal(goal, uid) }

    fun deleteGoal(id: String) = write { repo.deleteGoal(id, uid) }

    companion object {
        const val HEALTH_REFRESH_INTERVAL_MS = 5 * 60_000L
        const val HEALTH_RECENT_MS = 3 * 86_400_000L
        const val HEALTH_BACKFILL_MS = 30 * 86_400_000L
        const val WEIGHT_HISTORY_MS = 365 * 86_400_000L

        fun factory(
            repo: GymTrackRepository,
            auth: AuthGateway,
            sync: CloudSync,
            syncScheduler: SyncScheduler,
            restAlerts: RestTimerAlerts,
            workoutExporter: WorkoutExporter,
            healthData: HealthDataSource,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GymTrackViewModel(repo, auth, sync, syncScheduler, restAlerts, workoutExporter, healthData) as T
        }
    }
}

/** Nova série copiando carga/reps da última (ou valores padrão). */
fun SessionExercise.withAddedSet(completed: Boolean = false): SessionExercise {
    val last = sets.lastOrNull()
    return copy(sets = sets + SetRecord(id = newId(), weight = last?.weight ?: 0.0, reps = last?.reps ?: 10, completed = completed))
}
