package app.gabrielaraujo.gymtrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Modelos de domínio. As entidades de topo (@Entity) espelham as tabelas do Dexie
 * do app web; as estruturas aninhadas (dias, séries) são @Serializable e persistidas
 * como JSON via [Converters].
 */

/**
 * Campos de sincronização (espelham o SyncFields do app web).
 * - ownerId: uid do dono (null = dado local anônimo, ainda não adotado)
 * - updatedAt: base do last-write-wins
 * - dirty: pendente de envio ao Firestore
 * - deleted: tombstone (deleção propagável); filtrado nas leituras
 */
interface Syncable {
    val ownerId: String?
    val updatedAt: Long
    val dirty: Boolean
    val deleted: Boolean
}

@Entity(tableName = "exercises")
@Serializable
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String = "",
    val movementPattern: String = "",
    val category: String = "",
    val difficulty: String = "beginner",
    val instructions: String? = null,
    val isCustom: Boolean = false,
    val createdAt: Long? = null,
    override val ownerId: String? = null,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = false,
    override val deleted: Boolean = false,
) : Syncable

@Serializable
data class WorkoutExercise(
    val id: String,
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val initialWeight: Double = 0.0,
    val notes: String? = null,
    val order: Int,
)

@Serializable
data class WorkoutDay(
    val id: String,
    val label: String,   // A, B, C...
    val name: String,    // Peito, Costas...
    val exercises: List<WorkoutExercise> = emptyList(),
    val estimatedMinutes: Int? = null,
    val order: Int,
)

@Entity(tableName = "workout_plans")
@Serializable
data class WorkoutPlan(
    @PrimaryKey val id: String,
    val name: String,
    val days: List<WorkoutDay> = emptyList(),
    val currentDayIndex: Int = 0,
    val createdAt: Long,
    override val updatedAt: Long = 0,
    val isActive: Boolean = false,
    override val ownerId: String? = null,
    override val dirty: Boolean = false,
    override val deleted: Boolean = false,
) : Syncable

@Serializable
data class SetRecord(
    val id: String,
    val weight: Double,
    val reps: Int,
    val completed: Boolean = false,
    val notes: String? = null,
    val timestamp: Long? = null,
)

@Serializable
data class SessionExercise(
    val id: String,
    val exerciseId: String,
    val workoutExerciseId: String,
    val name: String,
    val sets: List<SetRecord> = emptyList(),
    val completed: Boolean = false,
    val substitutedFrom: String? = null,
    val order: Int,
)

@Entity(tableName = "workout_sessions")
@Serializable
data class WorkoutSession(
    @PrimaryKey val id: String,
    val workoutPlanId: String,
    val workoutDayId: String,
    val workoutDayLabel: String,
    val workoutDayName: String,
    val exercises: List<SessionExercise> = emptyList(),
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Int? = null,
    val completed: Boolean = false,
    val totalVolume: Double? = null,
    val totalSets: Int? = null,
    val prsAchieved: List<String> = emptyList(),
    override val ownerId: String? = null,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = false,
    override val deleted: Boolean = false,
) : Syncable

@Entity(tableName = "personal_records")
@Serializable
data class PersonalRecord(
    @PrimaryKey val exerciseId: String,
    val exerciseName: String,
    val weight: Double,
    val reps: Int,
    val volume: Double,
    val date: Long,
    val sessionId: String,
    override val ownerId: String? = null,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = false,
    override val deleted: Boolean = false,
) : Syncable

@Entity(tableName = "goals")
@Serializable
data class Goal(
    @PrimaryKey val id: String,
    val type: String, // workout_count | weekly_frequency | exercise_weight | body_weight | volume
    val title: String,
    val target: Double,
    val current: Double = 0.0,
    val unit: String,
    val exerciseId: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
    override val ownerId: String? = null,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = false,
    override val deleted: Boolean = false,
) : Syncable

@Entity(tableName = "sync_meta")
data class SyncMeta(
    @PrimaryKey val key: String,
    val value: Long,
)
