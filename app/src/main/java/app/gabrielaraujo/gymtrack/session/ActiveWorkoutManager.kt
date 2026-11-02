package app.gabrielaraujo.gymtrack.session

import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.humanizeExerciseId
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.data.repository.WorkoutSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Treino em andamento (ou recém-concluído, enquanto o resumo está aberto). */
data class ActiveSession(
    val session: WorkoutSession,
    val currentExerciseIndex: Int = 0,
    /** Fim do descanso atual (epoch ms), ou null se não há descanso rodando. */
    val restEndsAt: Long? = null,
    val restExerciseId: String? = null,
    val lastSessionForDay: WorkoutSession? = null,
)

/**
 * Dono do treino em andamento, no escopo do app: não depende de nenhuma tela e as gravações não são
 * canceladas quando um ViewModel morre. Cada mudança vai para o Room na hora (só local até concluir),
 * então um treino interrompido pelo fim do processo é retomado do banco por [restore], com o descanso.
 */
class ActiveWorkoutManager(
    private val sessions: WorkoutSessionRepository,
    private val scope: CoroutineScope,
    private val restAlerts: RestTimerAlerts = RestTimerAlerts.None,
    private val restStore: RestTimerStore = RestTimerStore.None,
    /** Depois de concluir (ex.: enviar ao Health Connect e buscar os dados do relógio). */
    private val afterCompleted: suspend (WorkoutSession) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    private val _restored = MutableStateFlow(false)

    /** true depois de procurar um treino interrompido no banco (antes disso a tela inicial não deve aparecer). */
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    /** Serializa as gravações: um snapshot antigo nunca sobrescreve a sessão concluída ou descartada. */
    private val writeLock = Mutex()

    /** Retoma o treino não finalizado que ficou salvo no banco (app reaberto no meio do treino). */
    suspend fun restore() {
        try {
            writeLock.withLock {
                if (_active.value != null) return
                val incomplete = sessions.latestIncompleteSession() ?: return
                val rest = restStore.load()?.takeIf { it.sessionId == incomplete.id && it.endsAt > clock() }
                _active.value = ActiveSession(
                    session = incomplete,
                    currentExerciseIndex = incomplete.exercises.indexOfFirst { !it.completed }.coerceAtLeast(0),
                    restEndsAt = rest?.endsAt,
                    restExerciseId = rest?.exerciseId,
                    lastSessionForDay = sessions.lastCompletedSessionForDay(incomplete.workoutDayId),
                )
            }
        } finally {
            _restored.value = true
        }
    }

    /**
     * Começa o treino do dia [dayIndex] da ficha. Não faz nada se já houver um treino em andamento.
     * @param exerciseName nome de exibição de cada exerciseId.
     */
    fun start(plan: WorkoutPlan, dayIndex: Int, exerciseName: (String) -> String?): Job = scope.launch {
        writeLock.withLock {
            if (_active.value != null || plan.days.isEmpty()) return@launch
            val day = plan.days[dayIndex.mod(plan.days.size)]
            val now = clock()
            val session = WorkoutSession(
                id = newId(),
                workoutPlanId = plan.id,
                workoutDayId = day.id,
                workoutDayLabel = day.label,
                workoutDayName = day.name,
                exercises = day.exercises.sortedBy { it.order }.map { we ->
                    SessionExercise(
                        id = newId(),
                        exerciseId = we.exerciseId,
                        workoutExerciseId = we.id,
                        name = exerciseName(we.exerciseId) ?: humanizeExerciseId(we.exerciseId),
                        sets = List(we.sets) { SetRecord(id = newId(), weight = we.initialWeight, reps = we.repMax) },
                        order = we.order,
                    )
                },
                startTime = now,
                updatedAt = now,
            )
            sessions.saveSessionInProgress(session)
            _active.value = ActiveSession(session = session, lastSessionForDay = sessions.lastCompletedSessionForDay(day.id))
        }
    }

    // ---------------- Séries e exercícios ----------------

    fun updateSet(exerciseIndex: Int, setIndex: Int, transform: (SetRecord) -> SetRecord) =
        updateExercise(exerciseIndex) { ex ->
            if (setIndex !in ex.sets.indices) ex
            else ex.copy(sets = ex.sets.toMutableList().also { it[setIndex] = transform(it[setIndex]) })
        }

    fun addSet(exerciseIndex: Int) = updateExercise(exerciseIndex) { it.withAddedSet() }

    fun removeSet(exerciseIndex: Int, setIndex: Int) =
        updateExercise(exerciseIndex) { ex -> ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != setIndex }) }

    fun completeExercise(exerciseIndex: Int) =
        updateExercise(exerciseIndex) { ex -> ex.copy(completed = true, sets = ex.sets.map { it.copy(completed = true) }) }

    fun uncompleteExercise(exerciseIndex: Int) = updateExercise(exerciseIndex) { it.copy(completed = false) }

    fun substituteExercise(exerciseIndex: Int, newExerciseId: String, newName: String) =
        updateExercise(exerciseIndex) { old ->
            old.copy(exerciseId = newExerciseId, name = newName, substitutedFrom = old.substitutedFrom ?: old.exerciseId)
        }

    fun setCurrentExercise(index: Int) {
        _active.update { it?.copy(currentExerciseIndex = index) }
    }

    private fun updateExercise(exerciseIndex: Int, transform: (SessionExercise) -> SessionExercise) {
        _active.update { active ->
            if (active == null || active.session.completed || exerciseIndex !in active.session.exercises.indices) return@update active
            val exercises = active.session.exercises.toMutableList().also { it[exerciseIndex] = transform(it[exerciseIndex]) }
            active.copy(session = active.session.copy(exercises = exercises))
        }
        persist()
    }

    /** Grava o estado mais recente da sessão em andamento (as gravações podem rodar fora de ordem). */
    private fun persist() {
        scope.launch {
            writeLock.withLock {
                val current = _active.value?.session ?: return@withLock
                // A sessão pode ter sido concluída ou descartada enquanto esta gravação aguardava.
                if (!current.completed) sessions.saveSessionInProgress(current)
            }
        }
    }

    // ---------------- Fim do treino ----------------

    /** Conclui o treino (o resumo fica aberto até [dismissSummary]). */
    fun complete(): Job = scope.launch {
        stopRest()
        val done = writeLock.withLock {
            val latest = _active.value?.session?.takeIf { !it.completed } ?: return@launch
            val done = sessions.completeSession(latest)
            // Marca como concluída ainda dentro do lock: gravações pendentes da sessão em andamento são ignoradas.
            _active.update { it?.copy(session = done) }
            done
        }
        afterCompleted(done)
    }

    /** Abandona o treino em andamento. Um treino já concluído nunca é apagado: ele pertence ao histórico. */
    fun abandon() {
        stopRest()
        val session = _active.value?.session
        _active.value = null
        if (session != null && !session.completed) {
            scope.launch { writeLock.withLock { sessions.discardSessionInProgress(session.id) } }
        }
    }

    /** Fecha a tela de resumo, mantendo o treino concluído no histórico. */
    fun dismissSummary() {
        _active.update { if (it?.session?.completed == true) null else it }
    }

    /** Esquece o treino em memória sem gravar mais nada (ex.: logout, que apaga os dados). */
    suspend fun clear() = writeLock.withLock {
        stopRest()
        _active.value = null
    }

    // ---------------- Descanso ----------------

    fun startRest(seconds: Int, exerciseId: String) {
        val active = _active.value ?: return
        val endsAt = clock() + seconds.coerceAtLeast(0) * 1000L
        _active.update { it?.copy(restEndsAt = endsAt, restExerciseId = exerciseId) }
        restStore.save(RestState(active.session.id, endsAt, exerciseId))
        restAlerts.schedule(endsAt, active.session.exercises.find { it.id == exerciseId }?.name.orEmpty())
    }

    /** Soma (ou subtrai) segundos ao descanso em andamento. */
    fun adjustRest(deltaSeconds: Int) {
        val active = _active.value ?: return
        val endsAt = active.restEndsAt ?: return
        val exerciseId = active.restExerciseId ?: return
        val remaining = ((endsAt - clock()) / 1000).toInt().coerceAtLeast(0)
        startRest((remaining + deltaSeconds).coerceAtLeast(0), exerciseId)
    }

    fun stopRest() {
        restAlerts.cancel()
        restStore.clear()
        _active.update { it?.copy(restEndsAt = null, restExerciseId = null) }
    }
}

/** Nova série copiando carga/reps da última (ou valores padrão). */
fun SessionExercise.withAddedSet(completed: Boolean = false): SessionExercise {
    val last = sets.lastOrNull()
    return copy(sets = sets + SetRecord(id = newId(), weight = last?.weight ?: 0.0, reps = last?.reps ?: 10, completed = completed))
}
