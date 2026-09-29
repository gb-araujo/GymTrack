package app.gabrielaraujo.gymtrack.data

import androidx.room.withTransaction
import app.gabrielaraujo.gymtrack.domain.BackupCodec
import app.gabrielaraujo.gymtrack.domain.BackupTables
import app.gabrielaraujo.gymtrack.domain.diffRecords
import app.gabrielaraujo.gymtrack.domain.recomputeRecords
import app.gabrielaraujo.gymtrack.domain.sessionTotals
import kotlinx.coroutines.flow.Flow

/**
 * Regras de persistência do app. Toda escrita feita pelo usuário marca o registro como
 * `dirty` (pendente de envio) e atualiza `updatedAt` (base do last-write-wins da sincronização).
 * Exclusões viram tombstones (`deleted = true`) para se propagarem aos outros aparelhos.
 */
class GymTrackRepository(
    private val db: GymTrackDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val dao = db.dao()

    val exercises: Flow<List<Exercise>> = dao.observeExercises()
    val plans: Flow<List<WorkoutPlan>> = dao.observePlans()
    val completedSessions: Flow<List<WorkoutSession>> = dao.observeCompletedSessions()
    val personalRecords: Flow<List<PersonalRecord>> = dao.observePersonalRecords()
    val goals: Flow<List<Goal>> = dao.observeGoals()
    val pendingSyncCount: Flow<Int> = dao.observePendingCount()

    /** Semeia exercícios e ficha exemplo na primeira execução. */
    suspend fun seedIfEmpty() {
        if (dao.exerciseCount() == 0) dao.insertExercises(builtinExercises)
        if (dao.planCount() == 0) dao.upsertPlan(buildSamplePlan(clock()))
    }

    // ---------------- Fichas ----------------

    suspend fun plansSnapshot(): List<WorkoutPlan> = dao.getPlans()

    suspend fun savePlan(plan: WorkoutPlan, uid: String?) {
        dao.upsertPlan(plan.copy(updatedAt = clock(), ownerId = uid ?: plan.ownerId, dirty = true))
    }

    suspend fun setActivePlan(planId: String, uid: String?) = db.withTransaction {
        val now = clock()
        val updated = dao.getPlans().filter { !it.deleted }.mapNotNull { p ->
            val shouldBeActive = p.id == planId
            if (p.isActive != shouldBeActive) {
                p.copy(isActive = shouldBeActive, updatedAt = now, ownerId = uid ?: p.ownerId, dirty = true)
            } else null
        }
        if (updated.isNotEmpty()) dao.upsertPlans(updated)
    }

    suspend fun deletePlan(planId: String, uid: String?) {
        val plan = dao.getPlan(planId) ?: return
        dao.upsertPlan(plan.copy(deleted = true, isActive = false, dirty = true, updatedAt = clock(), ownerId = uid ?: plan.ownerId))
    }

    suspend fun updateExerciseInitialWeight(planId: String, workoutExerciseId: String, weight: Double, uid: String?) {
        val plan = dao.getPlan(planId) ?: return
        val days = plan.days.map { day ->
            day.copy(exercises = day.exercises.map { ex -> if (ex.id == workoutExerciseId) ex.copy(initialWeight = weight) else ex })
        }
        savePlan(plan.copy(days = days), uid)
    }

    // ---------------- Exercícios ----------------

    suspend fun saveCustomExercise(exercise: Exercise, uid: String?) {
        val now = clock()
        dao.upsertExercise(
            exercise.copy(isCustom = true, createdAt = exercise.createdAt ?: now, ownerId = uid ?: exercise.ownerId, updatedAt = now, dirty = true),
        )
    }

    suspend fun deleteCustomExercise(id: String, uid: String?) {
        val ex = dao.getExercise(id) ?: return
        dao.upsertExercise(ex.copy(deleted = true, dirty = true, updatedAt = clock(), ownerId = uid ?: ex.ownerId))
    }

    // ---------------- Metas ----------------

    suspend fun saveGoal(goal: Goal, uid: String?) {
        dao.upsertGoal(goal.copy(ownerId = uid ?: goal.ownerId, updatedAt = clock(), dirty = true))
    }

    suspend fun deleteGoal(id: String, uid: String?) {
        val goal = dao.getGoal(id) ?: return
        dao.upsertGoal(goal.copy(deleted = true, dirty = true, updatedAt = clock(), ownerId = uid ?: goal.ownerId))
    }

    // ---------------- Sessões ----------------

    suspend fun latestIncompleteSession(): WorkoutSession? = dao.getLatestIncompleteSession()

    suspend fun lastCompletedSessionForDay(dayId: String): WorkoutSession? = dao.getLastCompletedSessionForDay(dayId)

    suspend fun getSession(id: String): WorkoutSession? = dao.getSession(id)

    suspend fun completedSessionsSnapshot(): List<WorkoutSession> = dao.getCompletedSessions()

    /** Grava o progresso do treino em andamento. Fica só local (dirty = false) até ser concluído. */
    suspend fun saveSessionInProgress(session: WorkoutSession, uid: String?) {
        if (session.completed) return
        dao.upsertSession(session.copy(updatedAt = clock(), ownerId = uid ?: session.ownerId, dirty = false))
    }

    /** Descarta um treino em andamento. Treinos concluídos nunca são apagados por aqui. */
    suspend fun discardSessionInProgress(id: String) = db.withTransaction {
        val session = dao.getSession(id) ?: return@withTransaction
        if (!session.completed) dao.deleteSession(id)
    }

    /**
     * Conclui o treino: calcula totais, recordes pessoais e avança a ficha para o próximo dia.
     * Tudo numa transação, para o treino nunca ficar salvo pela metade.
     */
    suspend fun completeSession(session: WorkoutSession, uid: String?): WorkoutSession = db.withTransaction {
        val endTime = clock()
        val totals = sessionTotals(session.exercises)
        val completed = session.copy(
            endTime = endTime,
            duration = ((endTime - session.startTime) / 1000 / 60).toInt(),
            completed = true,
            totalVolume = totals.volume,
            totalSets = totals.sets,
            ownerId = uid ?: session.ownerId,
            updatedAt = endTime,
            dirty = true,
        )
        dao.upsertSession(completed)

        // O próximo sugerido segue o treino que foi realmente feito.
        dao.getPlan(session.workoutPlanId)?.takeIf { !it.deleted && it.days.isNotEmpty() }?.let { plan ->
            val doneIndex = plan.days.indexOfFirst { it.id == session.workoutDayId }
            val baseIndex = if (doneIndex >= 0) doneIndex else plan.currentDayIndex
            dao.upsertPlan(
                plan.copy(
                    currentDayIndex = (baseIndex + 1) % plan.days.size,
                    updatedAt = endTime,
                    ownerId = uid ?: plan.ownerId,
                    dirty = true,
                ),
            )
        }

        recomputeRecordsLocked(uid, endTime)
        dao.getSession(completed.id) ?: completed
    }

    /** Salva a edição de um treino do histórico e recalcula totais e recordes. */
    suspend fun updateCompletedSession(session: WorkoutSession, uid: String?) = db.withTransaction {
        val now = clock()
        val totals = sessionTotals(session.exercises)
        dao.upsertSession(
            session.copy(
                completed = true,
                totalVolume = totals.volume,
                totalSets = totals.sets,
                ownerId = uid ?: session.ownerId,
                updatedAt = now,
                dirty = true,
            ),
        )
        recomputeRecordsLocked(uid, now)
    }

    /** Exclui um treino do histórico (tombstone, para propagar) e recalcula os recordes. */
    suspend fun deleteCompletedSession(id: String, uid: String?) = db.withTransaction {
        val session = dao.getSession(id) ?: return@withTransaction
        val now = clock()
        dao.upsertSession(session.copy(deleted = true, dirty = true, updatedAt = now, ownerId = uid ?: session.ownerId))
        recomputeRecordsLocked(uid, now)
    }

    /**
     * Recalcula recordes e os PRs de cada sessão a partir do histórico.
     * Recordes cuja sessão de origem não existe neste aparelho (ex.: importados) são mantidos como base.
     * Deve rodar dentro de uma transação.
     */
    private suspend fun recomputeRecordsLocked(uid: String?, now: Long) {
        val sessions = dao.getCompletedSessions()
        val knownSessionIds = dao.getAllSessionIds().toHashSet()
        val existing = dao.getPersonalRecords()
        val baseline = existing.filter { !it.deleted && it.sessionId !in knownSessionIds }
        val result = recomputeRecords(sessions, baseline)

        val recordChanges = diffRecords(existing, result.records, now, uid)
        if (recordChanges.isNotEmpty()) dao.upsertPersonalRecords(recordChanges)

        val sessionChanges = sessions.mapNotNull { s ->
            val prs = result.prsBySession[s.id].orEmpty()
            if (prs != s.prsAchieved) s.copy(prsAchieved = prs, updatedAt = now, dirty = true, ownerId = uid ?: s.ownerId) else null
        }
        if (sessionChanges.isNotEmpty()) dao.upsertSessions(sessionChanges)
    }

    // ---------------- Backup ----------------

    suspend fun exportBackup(): String {
        val tables = db.withTransaction {
            BackupTables(
                workoutPlans = dao.getPlans(),
                workoutSessions = dao.getSessions(),
                personalRecords = dao.getPersonalRecords(),
                goals = dao.getGoals(),
                exercises = dao.getExercises(),
            )
        }
        return BackupCodec.encode(tables, exportedAt = clock())
    }

    /**
     * Restaura um backup (deste app ou do app web antigo), sobrescrevendo registros com o mesmo id.
     * Com usuário logado, os dados restaurados entram na fila de sincronização.
     * @return quantidade de registros importados.
     * @throws IllegalArgumentException se o texto não for um backup válido.
     */
    suspend fun importBackup(text: String, uid: String?): Int {
        val tables = BackupCodec.decode(text)
        val now = clock()
        val dirty = uid != null
        db.withTransaction {
            val hasActivePlan = dao.getPlans().any { it.isActive && !it.deleted }
            dao.upsertPlans(
                tables.workoutPlans.map {
                    it.copy(ownerId = uid, updatedAt = now, dirty = dirty, deleted = false, isActive = it.isActive && !hasActivePlan)
                },
            )
            dao.upsertSessions(tables.workoutSessions.map { it.copy(ownerId = uid, updatedAt = now, dirty = dirty, deleted = false) })
            dao.upsertPersonalRecords(tables.personalRecords.map { it.copy(ownerId = uid, updatedAt = now, dirty = dirty, deleted = false) })
            dao.upsertGoals(tables.goals.map { it.copy(ownerId = uid, updatedAt = now, dirty = dirty, deleted = false) })
            dao.upsertExercises(tables.exercises.map { it.copy(ownerId = uid, updatedAt = now, dirty = dirty, deleted = false) })
            recomputeRecordsLocked(uid, now)
        }
        return tables.size
    }
}
