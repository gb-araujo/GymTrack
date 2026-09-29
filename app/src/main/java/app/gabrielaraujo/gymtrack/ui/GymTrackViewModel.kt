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
import app.gabrielaraujo.gymtrack.domain.StreakInfo
import app.gabrielaraujo.gymtrack.domain.bestRecordsByExercise
import app.gabrielaraujo.gymtrack.domain.computeStreak
import app.gabrielaraujo.gymtrack.health.WorkoutExporter
import app.gabrielaraujo.gymtrack.session.RestTimerAlerts
import app.gabrielaraujo.gymtrack.sync.CloudSync
import app.gabrielaraujo.gymtrack.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val sessions: List<WorkoutSession> = emptyList(),
    val personalRecords: Map<String, PersonalRecord> = emptyMap(),
    val goals: List<Goal> = emptyList(),
    val totalWorkouts: Int = 0,
    val streak: StreakInfo = computeStreak(emptyList()),
    val activeSession: ActiveSession? = null,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val pendingSync: Int = 0,
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
    private val restAlerts: RestTimerAlerts = RestTimerAlerts.None,
    private val workoutExporter: WorkoutExporter = WorkoutExporter.None,
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

    val state: StateFlow<UiState> = combine(library, activeSession, syncInfo, loaded) { lib, active, (status, pending), isLoaded ->
        UiState(
            loaded = isLoaded,
            exercises = lib.exercises,
            plans = lib.plans,
            activePlan = lib.plans.find { it.isActive } ?: lib.plans.firstOrNull(),
            sessions = lib.sessions,
            personalRecords = lib.personalRecords,
            goals = lib.goals,
            totalWorkouts = lib.sessions.size,
            streak = computeStreak(lib.sessions.map { it.startTime }),
            activeSession = active,
            syncStatus = status,
            pendingSync = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    val user: StateFlow<AppUser?> = auth.user

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var syncJob: Job? = null

    /** Serializa as gravações da sessão ativa para que um snapshot antigo nunca sobrescreva a sessão concluída. */
    private val sessionWriteLock = Mutex()

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
                if (id != null) sync.onLogin(id) else sync.reset()
            }
        }
    }

    /** Dispara uma sincronização (debounced) se houver usuário logado. */
    private fun scheduleSync() {
        val u = uid ?: return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(SYNC_DEBOUNCE_MS)
            sync.sync(u)
        }
    }

    fun syncNow() {
        val u = uid ?: return
        syncJob?.cancel()
        syncJob = viewModelScope.launch { sync.sync(u) }
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

    fun signOut() = auth.signOut()

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

    fun updateExerciseInitialWeight(workoutExerciseId: String, newWeight: Double) {
        val plan = state.value.activePlan ?: return
        write { repo.updateExerciseInitialWeight(plan.id, workoutExerciseId, newWeight, uid) }
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

    /** @param dayIndex dia escolhido; se null, usa o próximo sugerido (currentDayIndex). */
    fun startSession(dayIndex: Int? = null, onStarted: () -> Unit = {}) = viewModelScope.launch {
        val s = state.value
        if (activeSession.value != null) return@launch
        val plan = s.activePlan ?: return@launch
        if (plan.days.isEmpty()) return@launch
        val day = plan.days[(dayIndex ?: plan.currentDayIndex) % plan.days.size]
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

    fun updateCompletedSession(session: WorkoutSession) = write { repo.updateCompletedSession(session, uid) }

    fun deleteCompletedSession(id: String) = write { repo.deleteCompletedSession(id, uid) }

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
        const val SYNC_DEBOUNCE_MS = 1200L

        fun factory(
            repo: GymTrackRepository,
            auth: AuthGateway,
            sync: CloudSync,
            restAlerts: RestTimerAlerts,
            workoutExporter: WorkoutExporter,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GymTrackViewModel(repo, auth, sync, restAlerts, workoutExporter) as T
        }
    }
}

/** Nova série copiando carga/reps da última (ou valores padrão). */
fun SessionExercise.withAddedSet(): SessionExercise {
    val last = sets.lastOrNull()
    return copy(sets = sets + SetRecord(id = newId(), weight = last?.weight ?: 0.0, reps = last?.reps ?: 10))
}
