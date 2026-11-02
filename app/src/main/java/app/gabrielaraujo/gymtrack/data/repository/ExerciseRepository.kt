package app.gabrielaraujo.gymtrack.data.repository

import androidx.room.withTransaction
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import kotlinx.coroutines.flow.Flow

/** Catálogo de exercícios: os nativos (semeados, só locais) e os personalizados (sincronizados). */
class ExerciseRepository(
    private val db: GymTrackDatabase,
    private val currentUid: () -> String? = { null },
    /** Chamado após cada escrita que entra na fila de envio (agenda a sincronização). */
    private val onPendingChange: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    val exercises: Flow<List<Exercise>> = dao.observeExercises()

    suspend fun saveCustomExercise(exercise: Exercise) {
        val now = clock()
        dao.upsertExercise(
            exercise.copy(isCustom = true, createdAt = exercise.createdAt ?: now, ownerId = currentUid() ?: exercise.ownerId, updatedAt = now, dirty = true),
        )
        onPendingChange()
    }

    suspend fun deleteCustomExercise(id: String) {
        db.withTransaction {
            val ex = dao.getExercise(id) ?: return@withTransaction
            dao.upsertExercise(ex.copy(deleted = true, dirty = true, updatedAt = clock(), ownerId = currentUid() ?: ex.ownerId))
        }
        onPendingChange()
    }
}
