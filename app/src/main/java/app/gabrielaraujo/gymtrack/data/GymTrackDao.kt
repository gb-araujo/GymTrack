package app.gabrielaraujo.gymtrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface GymTrackDao {
    // Exercises
    @Query("SELECT * FROM exercises")
    suspend fun getExercises(): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<Exercise>)

    @Upsert
    suspend fun upsertExercise(exercise: Exercise)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteExercise(id: String)

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: String): Exercise?

    @Query("SELECT * FROM exercises WHERE dirty = 1")
    suspend fun getDirtyExercises(): List<Exercise>

    // Plans
    @Query("SELECT * FROM workout_plans")
    suspend fun getPlans(): List<WorkoutPlan>

    @Query("SELECT COUNT(*) FROM workout_plans")
    suspend fun planCount(): Int

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun getPlan(id: String): WorkoutPlan?

    @Upsert
    suspend fun upsertPlan(plan: WorkoutPlan)

    @Upsert
    suspend fun upsertPlans(plans: List<WorkoutPlan>)

    @Query("DELETE FROM workout_plans WHERE id = :id")
    suspend fun deletePlan(id: String)

    @Query("SELECT * FROM workout_plans WHERE dirty = 1")
    suspend fun getDirtyPlans(): List<WorkoutPlan>

    // Sessions
    @Query("SELECT * FROM workout_sessions")
    suspend fun getSessions(): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSession?

    @Upsert
    suspend fun upsertSession(session: WorkoutSession)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM workout_sessions WHERE dirty = 1")
    suspend fun getDirtySessions(): List<WorkoutSession>

    // Personal records
    @Query("SELECT * FROM personal_records")
    suspend fun getPersonalRecords(): List<PersonalRecord>

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId")
    suspend fun getPersonalRecord(exerciseId: String): PersonalRecord?

    @Upsert
    suspend fun upsertPersonalRecord(record: PersonalRecord)

    @Query("SELECT * FROM personal_records WHERE dirty = 1")
    suspend fun getDirtyPersonalRecords(): List<PersonalRecord>

    // Goals
    @Query("SELECT * FROM goals")
    suspend fun getGoals(): List<Goal>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: String): Goal?

    @Upsert
    suspend fun upsertGoal(goal: Goal)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: String)

    @Query("SELECT * FROM goals WHERE dirty = 1")
    suspend fun getDirtyGoals(): List<Goal>

    // Cursores de sincronização
    @Query("SELECT value FROM sync_meta WHERE key = :key")
    suspend fun getSyncCursor(key: String): Long?

    @Upsert
    suspend fun setSyncMeta(meta: SyncMeta)
}
