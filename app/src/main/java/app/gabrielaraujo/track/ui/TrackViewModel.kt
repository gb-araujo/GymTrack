package app.gabrielaraujo.track.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.track.auth.AuthManager
import app.gabrielaraujo.track.data.Exercise
import app.gabrielaraujo.track.data.Goal
import app.gabrielaraujo.track.data.PersonalRecord
import app.gabrielaraujo.track.data.SessionExercise
import app.gabrielaraujo.track.data.SetRecord
import app.gabrielaraujo.track.data.TrackRepository
import app.gabrielaraujo.track.data.WorkoutPlan
import app.gabrielaraujo.track.data.WorkoutSession
import app.gabrielaraujo.track.sync.SyncEngine
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/** Sessão em andamento mantida em memória (equivale ao ActiveSession do store zustand). */
data class ActiveSession(
    val session: WorkoutSession,
    val currentExerciseIndex: Int = 0,
    val restTimer: Int? = null,
    val restTimerExerciseId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
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
    val activeSession: ActiveSession? = null,
)

class TrackViewModel(
    private val repo: TrackRepository,
    private val authManager: AuthManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val user: StateFlow<FirebaseUser?> = authManager.user

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var restJob: Job? = null
    private var syncJob: Job? = null

    private val nowMs get() = System.currentTimeMillis()
    private val uid get() = authManager.currentUid

    init {
        syncEngine.onRemoteChange = { viewModelScope.launch { reload() } }
        viewModelScope.launch {
            repo.seedIfEmpty()
            reload()
            restoreActiveSession()
            // Ao logar (ou já logado no start), adota dados locais e sincroniza.
            authManager.user.collect { u ->
                if (u != null) {
                    runCatching { syncEngine.onLogin(u.uid) }
                    reload()
                }
            }
        }
    }

    /** Recarrega o estado em memória a partir do banco (ignora tombstones). */
    private suspend fun reload() {
        val exercises = repo.getExercises().filter { !it.deleted }
        val allPlans = repo.getPlans().filter { !it.deleted }
        val activePlan = allPlans.find { it.isActive } ?: allPlans.firstOrNull()
        val sessions = repo.getSessions()
            .filter { it.completed && !it.deleted }
            .sortedByDescending { it.startTime }
        val prs = repo.getPersonalRecords().filter { !it.deleted }
        val prMap = LinkedHashMap<String, PersonalRecord>()
        prs.forEach { pr ->
            val existing = prMap[pr.exerciseId]
            if (existing == null || pr.weight > existing.weight ||
                (pr.weight == existing.weight && pr.reps > existing.reps)
            ) prMap[pr.exerciseId] = pr
        }
        val goals = repo.getGoals().filter { !it.deleted }
        _state.update {
            it.copy(
                loaded = true,
                exercises = exercises,
                plans = allPlans,
                activePlan = activePlan,
                sessions = sessions,
                personalRecords = prMap,
                goals = goals,
                totalWorkouts = sessions.size,
            )
        }
    }

    /** Dispara uma sincronização (debounced) se houver usuário logado. */
    private fun scheduleSync() {
        val u = uid ?: return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(1200)
            runCatching { syncEngine.fullSync(u) }
            reload()
        }
    }

    // ---------------- Autenticação ----------------

    fun signIn(activity: Activity) = viewModelScope.launch {
        _signingIn.value = true
        _authError.value = null
        val result = authManager.signInWithGoogle(activity)
        _signingIn.value = false
        result.onFailure { _authError.value = it.message ?: "Falha ao entrar." }
        // onLogin/sync são disparados pelo coletor de authManager.user.
    }

    fun signOut() = authManager.signOut()

    fun clearAuthError() { _authError.value = null }

    // ---------------- Migração / importação de backup ----------------

    private val importJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Importa um backup do app antigo (dump do IndexedDB/Dexie) para o Room.
     * Aceita `{ "tables": { workoutPlans: [...], ... } }` ou o objeto de tabelas cru.
     * Retorna a quantidade de registros importados, ou -1 em erro de parse.
     */
    private fun JsonObject.flag(name: String): Boolean =
        (this[name] as? JsonPrimitive)?.booleanOrNull == true

    private fun isTombstone(o: JsonObject) = o.flag("_deleted") || o.flag("deleted")

    fun importBackup(text: String, onResult: (Int) -> Unit) = viewModelScope.launch {
        val count = runCatching {
            val root = importJson.parseToJsonElement(text).jsonObject
            val tables = (root["tables"] as? JsonObject) ?: root
            var n = 0
            fun rows(name: String): List<JsonObject> =
                (tables[name] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

            for (o in rows("workoutPlans")) {
                if (isTombstone(o)) continue
                val p = importJson.decodeFromJsonElement(WorkoutPlan.serializer(), o)
                repo.upsertPlan(p.copy(dirty = false, deleted = false, ownerId = null)); n++
            }
            for (o in rows("workoutSessions")) {
                if (isTombstone(o) || !o.flag("completed")) continue // só treinos concluídos
                val s = importJson.decodeFromJsonElement(WorkoutSession.serializer(), o)
                repo.upsertSession(s.copy(dirty = false, deleted = false, ownerId = null)); n++
            }
            for (o in rows("personalRecords")) {
                if (isTombstone(o)) continue
                val r = importJson.decodeFromJsonElement(PersonalRecord.serializer(), o)
                repo.upsertPersonalRecord(r.copy(dirty = false, deleted = false, ownerId = null)); n++
            }
            for (o in rows("goals")) {
                if (isTombstone(o)) continue
                val g = importJson.decodeFromJsonElement(Goal.serializer(), o)
                repo.upsertGoal(g.copy(dirty = false, deleted = false, ownerId = null)); n++
            }
            for (o in rows("exercises")) {
                if (isTombstone(o)) continue
                val e = importJson.decodeFromJsonElement(Exercise.serializer(), o)
                if (e.isCustom) { repo.upsertExercise(e.copy(dirty = false, deleted = false, ownerId = null)); n++ }
            }
            n
        }.getOrDefault(-1)
        reload()
        onResult(count)
    }

    /** Retoma uma sessão não finalizada que ficou salva no banco (app reaberto no meio do treino). */
    private suspend fun restoreActiveSession() {
        if (_state.value.activeSession != null) return
        val incomplete = repo.getSessions()
            .filter { !it.completed && !it.deleted }
            .maxByOrNull { it.startTime } ?: return
        val lastForDay = repo.getSessions()
            .filter { it.completed && !it.deleted && it.workoutDayId == incomplete.workoutDayId }
            .maxByOrNull { it.startTime }
        val firstIncomplete = incomplete.exercises.indexOfFirst { !it.completed }.coerceAtLeast(0)
        _state.update {
            it.copy(
                activeSession = ActiveSession(
                    session = incomplete,
                    currentExerciseIndex = firstIncomplete,
                    lastSessionForDay = lastForDay,
                ),
            )
        }
    }

    fun getExerciseById(id: String): Exercise? = _state.value.exercises.find { it.id == id }

    // ---------------- Planos ----------------

    fun savePlan(plan: WorkoutPlan) = viewModelScope.launch {
        repo.upsertPlan(plan.copy(updatedAt = nowMs, ownerId = uid ?: plan.ownerId, dirty = true))
        reload()
        scheduleSync()
    }

    fun setActivePlan(planId: String) = viewModelScope.launch {
        val all = repo.getPlans()
        val updated = all.mapNotNull { p ->
            val shouldBeActive = p.id == planId
            if (p.isActive != shouldBeActive) {
                p.copy(isActive = shouldBeActive, updatedAt = nowMs, ownerId = uid ?: p.ownerId, dirty = true)
            } else null
        }
        if (updated.isNotEmpty()) repo.upsertPlans(updated)
        reload()
        scheduleSync()
    }

    fun deletePlan(planId: String) = viewModelScope.launch {
        // Tombstone: propaga a exclusão na sincronização.
        repo.getPlan(planId)?.let {
            repo.upsertPlan(it.copy(deleted = true, dirty = true, updatedAt = nowMs, ownerId = uid ?: it.ownerId))
        }
        reload()
        scheduleSync()
    }

    // ---------------- Sessão ----------------

    /** @param dayIndex dia escolhido; se null, usa o próximo sugerido (currentDayIndex). */
    fun startSession(dayIndex: Int? = null, onStarted: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        val plan = s.activePlan ?: return@launch
        if (plan.days.isEmpty()) return@launch
        val idx = (dayIndex ?: plan.currentDayIndex) % plan.days.size
        val day = plan.days[idx]
        val now = nowMs

        val sessionExercises = day.exercises.sortedBy { it.order }.mapIndexed { i, we ->
            val exercise = s.exercises.find { it.id == we.exerciseId }
            val sets = (0 until we.sets).map { j ->
                SetRecord(id = "set_${now}_${i}_$j", weight = we.initialWeight, reps = we.repMax)
            }
            SessionExercise(
                id = "se_${now}_${we.id}",
                exerciseId = we.exerciseId,
                workoutExerciseId = we.id,
                name = exercise?.name ?: we.exerciseId,
                sets = sets,
                order = we.order,
            )
        }

        // Sessão iniciada fica local (dirty=false) até ser concluída.
        val session = WorkoutSession(
            id = "session_$now",
            workoutPlanId = plan.id,
            workoutDayId = day.id,
            workoutDayLabel = day.label,
            workoutDayName = day.name,
            exercises = sessionExercises,
            startTime = now,
            ownerId = uid,
            updatedAt = now,
        )
        repo.upsertSession(session)

        val lastForDay = repo.getSessions()
            .filter { it.completed && !it.deleted && it.workoutDayId == day.id }
            .maxByOrNull { it.startTime }

        _state.update {
            it.copy(activeSession = ActiveSession(session = session, lastSessionForDay = lastForDay))
        }
        onStarted()
    }

    private fun mutateSession(block: (WorkoutSession) -> WorkoutSession) {
        _state.update { st ->
            val active = st.activeSession ?: return@update st
            st.copy(activeSession = active.copy(session = block(active.session)))
        }
        persistActive()
    }

    /** Salva a sessão em andamento no Room a cada mudança, para não perder progresso. */
    private fun persistActive() {
        val session = _state.value.activeSession?.session ?: return
        if (session.completed) return
        viewModelScope.launch {
            repo.upsertSession(session.copy(updatedAt = nowMs, ownerId = uid, dirty = false))
        }
    }

    fun updateSetRecord(exerciseIndex: Int, setIndex: Int, transform: (SetRecord) -> SetRecord) {
        mutateSession { session ->
            val exercises = session.exercises.toMutableList()
            val ex = exercises[exerciseIndex]
            val sets = ex.sets.toMutableList()
            sets[setIndex] = transform(sets[setIndex])
            exercises[exerciseIndex] = ex.copy(sets = sets)
            session.copy(exercises = exercises)
        }
    }

    fun addSet(exerciseIndex: Int) {
        mutateSession { session ->
            val exercises = session.exercises.toMutableList()
            val ex = exercises[exerciseIndex]
            val last = ex.sets.lastOrNull()
            val newSet = SetRecord(
                id = "set_${System.currentTimeMillis()}",
                weight = last?.weight ?: 0.0,
                reps = last?.reps ?: 10,
            )
            exercises[exerciseIndex] = ex.copy(sets = ex.sets + newSet)
            session.copy(exercises = exercises)
        }
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        mutateSession { session ->
            val exercises = session.exercises.toMutableList()
            val ex = exercises[exerciseIndex]
            exercises[exerciseIndex] = ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != setIndex })
            session.copy(exercises = exercises)
        }
    }

    fun completeExercise(exerciseIndex: Int) {
        mutateSession { session ->
            val exercises = session.exercises.toMutableList()
            val ex = exercises[exerciseIndex]
            exercises[exerciseIndex] = ex.copy(
                completed = true,
                sets = ex.sets.map { it.copy(completed = true) },
            )
            session.copy(exercises = exercises)
        }
    }

    fun uncompleteExercise(exerciseIndex: Int) {
        mutateSession { session ->
            val exercises = session.exercises.toMutableList()
            exercises[exerciseIndex] = exercises[exerciseIndex].copy(completed = false)
            session.copy(exercises = exercises)
        }
    }

    fun setCurrentExercise(index: Int) {
        _state.update { st ->
            val active = st.activeSession ?: return@update st
            st.copy(activeSession = active.copy(currentExerciseIndex = index))
        }
    }

    fun substituteExercise(exerciseIndex: Int, newExerciseId: String) {
        val newExercise = getExerciseById(newExerciseId) ?: return
        mutateSession { session ->
            val exercises = session.exercises.toMutableList()
            val old = exercises[exerciseIndex]
            exercises[exerciseIndex] = old.copy(
                exerciseId = newExerciseId,
                name = newExercise.name,
                substitutedFrom = old.substitutedFrom ?: old.exerciseId,
            )
            session.copy(exercises = exercises)
        }
    }

    fun abandonSession() {
        stopRestTimer()
        val id = _state.value.activeSession?.session?.id
        _state.update { it.copy(activeSession = null) }
        if (id != null) viewModelScope.launch { repo.deleteSession(id) }
    }

    fun completeSession() = viewModelScope.launch {
        val st = _state.value
        val active = st.activeSession ?: return@launch
        val plan = st.activePlan ?: return@launch
        val endTime = nowMs
        val duration = ((endTime - active.session.startTime) / 1000 / 60).toInt()

        var totalVolume = 0.0
        var totalSets = 0
        val prsAchieved = mutableListOf<String>()
        val newPrMap = LinkedHashMap(st.personalRecords)

        for (ex in active.session.exercises) {
            val completedSets = ex.sets.filter { it.completed }
            for (s in completedSets) {
                totalVolume += s.weight * s.reps
                totalSets++
            }
            if (completedSets.isNotEmpty()) {
                val maxWeight = completedSets.maxOf { it.weight }
                val maxReps = completedSets.filter { it.weight == maxWeight }.maxOf { it.reps }
                val existing = st.personalRecords[ex.exerciseId]
                val isPR = existing == null || maxWeight > existing.weight ||
                    (maxWeight == existing.weight && maxReps > existing.reps)
                if (isPR) {
                    prsAchieved += ex.exerciseId
                    val pr = PersonalRecord(
                        exerciseId = ex.exerciseId,
                        exerciseName = ex.name,
                        weight = maxWeight,
                        reps = maxReps,
                        volume = maxWeight * maxReps,
                        date = endTime,
                        sessionId = active.session.id,
                        ownerId = uid,
                        updatedAt = endTime,
                        dirty = true,
                    )
                    repo.upsertPersonalRecord(pr)
                    newPrMap[ex.exerciseId] = pr
                }
            }
        }

        val completed = active.session.copy(
            endTime = endTime,
            duration = duration,
            completed = true,
            totalVolume = totalVolume,
            totalSets = totalSets,
            prsAchieved = prsAchieved,
            ownerId = uid ?: active.session.ownerId,
            updatedAt = endTime,
            dirty = true,
        )
        repo.upsertSession(completed)

        // Próximo sugerido segue o treino que foi realmente feito.
        val doneIndex = plan.days.indexOfFirst { it.id == completed.workoutDayId }
        val baseIndex = if (doneIndex >= 0) doneIndex else plan.currentDayIndex
        val newDayIndex = (baseIndex + 1) % plan.days.size
        val updatedPlan = plan.copy(currentDayIndex = newDayIndex, updatedAt = endTime, ownerId = uid ?: plan.ownerId, dirty = true)
        repo.upsertPlan(updatedPlan)

        _state.update {
            it.copy(
                activeSession = active.copy(session = completed),
                activePlan = updatedPlan,
                plans = it.plans.map { p -> if (p.id == updatedPlan.id) updatedPlan else p },
                sessions = listOf(completed) + it.sessions,
                totalWorkouts = it.sessions.size + 1,
                personalRecords = newPrMap,
            )
        }
        scheduleSync()
    }

    // ---------------- Timer de descanso ----------------

    fun startRestTimer(seconds: Int, exerciseId: String) {
        restJob?.cancel()
        _state.update { st ->
            val a = st.activeSession ?: return@update st
            st.copy(activeSession = a.copy(restTimer = seconds, restTimerExerciseId = exerciseId))
        }
        restJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                var stop = false
                _state.update { st ->
                    val a = st.activeSession ?: run { stop = true; return@update st }
                    val t = a.restTimer ?: run { stop = true; return@update st }
                    val next = t - 1
                    if (next <= 0) {
                        stop = true
                        st.copy(activeSession = a.copy(restTimer = null, restTimerExerciseId = null))
                    } else {
                        st.copy(activeSession = a.copy(restTimer = next))
                    }
                }
                if (stop) break
            }
        }
    }

    fun stopRestTimer() {
        restJob?.cancel()
        restJob = null
        _state.update { st ->
            val a = st.activeSession ?: return@update st
            st.copy(activeSession = a.copy(restTimer = null, restTimerExerciseId = null))
        }
    }

    // ---------------- Exercícios ----------------

    fun saveCustomExercise(exercise: Exercise) = viewModelScope.launch {
        repo.upsertExercise(
            exercise.copy(
                isCustom = true,
                createdAt = exercise.createdAt ?: nowMs,
                ownerId = uid ?: exercise.ownerId,
                updatedAt = nowMs,
                dirty = true,
            ),
        )
        reload()
        scheduleSync()
    }

    fun deleteCustomExercise(id: String) = viewModelScope.launch {
        repo.dao.getExercise(id)?.let {
            repo.upsertExercise(it.copy(deleted = true, dirty = true, updatedAt = nowMs, ownerId = uid ?: it.ownerId))
        }
        reload()
        scheduleSync()
    }

    fun updateExerciseInitialWeight(workoutExerciseId: String, newWeight: Double) = viewModelScope.launch {
        val plan = _state.value.activePlan ?: return@launch
        val updatedDays = plan.days.map { day ->
            day.copy(exercises = day.exercises.map { ex ->
                if (ex.id == workoutExerciseId) ex.copy(initialWeight = newWeight) else ex
            })
        }
        val updated = plan.copy(days = updatedDays, updatedAt = nowMs, ownerId = uid ?: plan.ownerId, dirty = true)
        repo.upsertPlan(updated)
        _state.update { it.copy(activePlan = updated, plans = it.plans.map { p -> if (p.id == updated.id) updated else p }) }
        scheduleSync()
    }

    /** Histórico de um exercício em todas as sessões concluídas (mais recentes primeiro). */
    fun exerciseHistory(exerciseId: String): List<Triple<Long, List<SetRecord>, String>> =
        _state.value.sessions
            .mapNotNull { session ->
                session.exercises.find { it.exerciseId == exerciseId }?.let { ex ->
                    Triple(session.startTime, ex.sets, session.id)
                }
            }
            .sortedByDescending { it.first }

    // ---------------- Metas ----------------

    fun addGoal(goal: Goal) = viewModelScope.launch {
        repo.upsertGoal(goal.copy(ownerId = uid ?: goal.ownerId, updatedAt = nowMs, dirty = true))
        _state.update { it.copy(goals = repo.getGoals().filter { g -> !g.deleted }) }
        scheduleSync()
    }

    fun deleteGoal(id: String) = viewModelScope.launch {
        repo.dao.getGoal(id)?.let {
            repo.upsertGoal(it.copy(deleted = true, dirty = true, updatedAt = nowMs, ownerId = uid ?: it.ownerId))
        }
        _state.update { it.copy(goals = repo.getGoals().filter { g -> !g.deleted }) }
        scheduleSync()
    }

    companion object {
        fun factory(repo: TrackRepository, authManager: AuthManager, syncEngine: SyncEngine) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TrackViewModel(repo, authManager, syncEngine) as T
            }
    }
}
