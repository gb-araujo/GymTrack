package app.gabrielaraujo.gymtrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GymTrackDao {
    // Exercises
    @Query("SELECT * FROM exercises")
    suspend fun getExercises(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE deleted = 0")
    fun observeExercises(): Flow<List<Exercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<Exercise>)

    @Upsert
    suspend fun upsertExercise(exercise: Exercise)

    @Upsert
    suspend fun upsertExercises(exercises: List<Exercise>)

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: String): Exercise?

    /** Pendências de envio do usuário [uid] (só exercícios personalizados sobem). */
    @Query("SELECT * FROM exercises WHERE dirty = 1 AND ownerId = :uid AND isCustom = 1")
    suspend fun getDirtyExercises(uid: String): List<Exercise>

    /** Só limpa o dirty se o registro não mudou desde o envio (senão a edição nova seria perdida). */
    @Query("UPDATE exercises SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearExerciseDirty(id: String, updatedAt: Long)

    @Query("UPDATE exercises SET dirty = 1 WHERE ownerId = :uid AND isCustom = 1")
    suspend fun markAllExercisesDirty(uid: String)

    // Plans
    @Query("SELECT * FROM workout_plans")
    suspend fun getPlans(): List<WorkoutPlan>

    @Query("SELECT * FROM workout_plans WHERE deleted = 0")
    fun observePlans(): Flow<List<WorkoutPlan>>

    @Query("SELECT COUNT(*) FROM workout_plans")
    suspend fun planCount(): Int

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun getPlan(id: String): WorkoutPlan?

    @Upsert
    suspend fun upsertPlan(plan: WorkoutPlan)

    @Upsert
    suspend fun upsertPlans(plans: List<WorkoutPlan>)

    @Query("SELECT * FROM workout_plans WHERE dirty = 1 AND ownerId = :uid")
    suspend fun getDirtyPlans(uid: String): List<WorkoutPlan>

    @Query("UPDATE workout_plans SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearPlanDirty(id: String, updatedAt: Long)

    @Query("UPDATE workout_plans SET dirty = 1 WHERE ownerId = :uid")
    suspend fun markAllPlansDirty(uid: String)

    // Sessions
    @Query("SELECT * FROM workout_sessions")
    suspend fun getSessions(): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE completed = 1 AND deleted = 0 ORDER BY startTime DESC")
    fun observeCompletedSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE completed = 1 AND deleted = 0")
    suspend fun getCompletedSessions(): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE completed = 0 AND deleted = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestIncompleteSession(): WorkoutSession?

    @Query(
        "SELECT * FROM workout_sessions WHERE completed = 1 AND deleted = 0 AND workoutDayId = :dayId " +
            "ORDER BY startTime DESC LIMIT 1",
    )
    suspend fun getLastCompletedSessionForDay(dayId: String): WorkoutSession?

    @Query("SELECT id FROM workout_sessions")
    suspend fun getAllSessionIds(): List<String>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSession?

    @Upsert
    suspend fun upsertSession(session: WorkoutSession)

    @Upsert
    suspend fun upsertSessions(sessions: List<WorkoutSession>)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    /** Sessões em andamento nunca sobem para a nuvem. */
    @Query("SELECT * FROM workout_sessions WHERE dirty = 1 AND ownerId = :uid AND (completed = 1 OR deleted = 1)")
    suspend fun getDirtySessions(uid: String): List<WorkoutSession>

    @Query("UPDATE workout_sessions SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearSessionDirty(id: String, updatedAt: Long)

    @Query("UPDATE workout_sessions SET dirty = 1 WHERE ownerId = :uid AND (completed = 1 OR deleted = 1)")
    suspend fun markAllSessionsDirty(uid: String)

    // Personal records
    @Query("SELECT * FROM personal_records")
    suspend fun getPersonalRecords(): List<PersonalRecord>

    @Query("SELECT * FROM personal_records WHERE deleted = 0")
    fun observePersonalRecords(): Flow<List<PersonalRecord>>

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId")
    suspend fun getPersonalRecord(exerciseId: String): PersonalRecord?

    @Upsert
    suspend fun upsertPersonalRecord(record: PersonalRecord)

    @Upsert
    suspend fun upsertPersonalRecords(records: List<PersonalRecord>)

    @Query("SELECT * FROM personal_records WHERE dirty = 1 AND ownerId = :uid")
    suspend fun getDirtyPersonalRecords(uid: String): List<PersonalRecord>

    @Query("UPDATE personal_records SET dirty = 0 WHERE exerciseId = :id AND updatedAt = :updatedAt")
    suspend fun clearPersonalRecordDirty(id: String, updatedAt: Long)

    @Query("UPDATE personal_records SET dirty = 1 WHERE ownerId = :uid")
    suspend fun markAllPersonalRecordsDirty(uid: String)

    // Goals
    @Query("SELECT * FROM goals")
    suspend fun getGoals(): List<Goal>

    @Query("SELECT * FROM goals WHERE deleted = 0")
    fun observeGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: String): Goal?

    @Upsert
    suspend fun upsertGoal(goal: Goal)

    @Upsert
    suspend fun upsertGoals(goals: List<Goal>)

    @Query("SELECT * FROM goals WHERE dirty = 1 AND ownerId = :uid")
    suspend fun getDirtyGoals(uid: String): List<Goal>

    @Query("UPDATE goals SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearGoalDirty(id: String, updatedAt: Long)

    @Query("UPDATE goals SET dirty = 1 WHERE ownerId = :uid")
    suspend fun markAllGoalsDirty(uid: String)

    // Contagem de pendências de envio (para o indicador de sincronização)
    @Query(
        "SELECT (SELECT COUNT(*) FROM workout_plans WHERE dirty = 1)" +
            " + (SELECT COUNT(*) FROM workout_sessions WHERE dirty = 1 AND (completed = 1 OR deleted = 1))" +
            " + (SELECT COUNT(*) FROM personal_records WHERE dirty = 1)" +
            " + (SELECT COUNT(*) FROM goals WHERE dirty = 1)" +
            " + (SELECT COUNT(*) FROM exercises WHERE dirty = 1 AND isCustom = 1)",
    )
    fun observePendingCount(): Flow<Int>

    // Cursores de sincronização
    @Query("SELECT value FROM sync_meta WHERE key = :key")
    suspend fun getSyncCursor(key: String): Long?

    @Upsert
    suspend fun setSyncMeta(meta: SyncMeta)

    // Limpeza dos dados do usuário (logout). Os exercícios nativos ficam.
    @Query("DELETE FROM workout_plans")
    suspend fun deleteAllPlans()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM personal_records")
    suspend fun deleteAllPersonalRecords()

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM exercises WHERE isCustom = 1")
    suspend fun deleteCustomExercises()

    @Query("DELETE FROM sync_meta")
    suspend fun deleteSyncMeta()
}
