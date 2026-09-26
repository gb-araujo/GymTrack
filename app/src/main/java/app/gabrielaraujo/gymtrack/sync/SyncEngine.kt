package app.gabrielaraujo.gymtrack.sync

import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.Syncable
import app.gabrielaraujo.gymtrack.data.SyncMeta
import app.gabrielaraujo.gymtrack.data.GymTrackRepository
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Sincronização offline-first com o Firestore (espelha o syncEngine do app web).
 * Estrutura: users/{uid}/{coleção}/{id}, cada doc = { updatedAt, deleted, json }.
 * O JSON é a entidade serializada (kotlinx); o Room continua sendo a fonte local.
 */
class SyncEngine(private val repo: GymTrackRepository) {
    private val dao = repo.dao
    private val db = Firebase.firestore
    private val json = Json { ignoreUnknownKeys = true }

    /** Chamado após um pull trazer mudanças, para o VM recarregar o estado. */
    var onRemoteChange: (() -> Unit)? = null

    suspend fun onLogin(uid: String) {
        adoptLocalData(uid)
        fullSync(uid)
    }

    suspend fun fullSync(uid: String) {
        pushDirty(uid)
        val changed = pull(uid)
        if (changed) onRemoteChange?.invoke()
    }

    /** Adota dados locais anônimos (ownerId == null) no login, marcando-os para envio. */
    private suspend fun adoptLocalData(uid: String) {
        val now = System.currentTimeMillis()
        fun ts(v: Long) = if (v == 0L) now else v

        dao.getPlans().filter { it.ownerId == null }.forEach {
            dao.upsertPlan(it.copy(ownerId = uid, dirty = true, updatedAt = ts(it.updatedAt)))
        }
        dao.getSessions().filter { it.ownerId == null }.forEach {
            dao.upsertSession(it.copy(ownerId = uid, dirty = true, updatedAt = ts(if (it.updatedAt == 0L) it.endTime ?: it.startTime else it.updatedAt)))
        }
        dao.getPersonalRecords().filter { it.ownerId == null }.forEach {
            dao.upsertPersonalRecord(it.copy(ownerId = uid, dirty = true, updatedAt = ts(if (it.updatedAt == 0L) it.date else it.updatedAt)))
        }
        dao.getGoals().filter { it.ownerId == null }.forEach {
            dao.upsertGoal(it.copy(ownerId = uid, dirty = true, updatedAt = ts(if (it.updatedAt == 0L) it.createdAt else it.updatedAt)))
        }
        // Só exercícios personalizados sobem para a nuvem.
        dao.getExercises().filter { it.isCustom && it.ownerId == null }.forEach {
            dao.upsertExercise(it.copy(ownerId = uid, dirty = true, updatedAt = ts(if (it.updatedAt == 0L) it.createdAt ?: now else it.updatedAt)))
        }
    }

    suspend fun pushDirty(uid: String) {
        push(uid, "plans", WorkoutPlan.serializer(), dao.getDirtyPlans(), { it.id }) {
            dao.upsertPlan(it.copy(dirty = false))
        }
        push(uid, "sessions", WorkoutSession.serializer(), dao.getDirtySessions(), { it.id }) {
            dao.upsertSession(it.copy(dirty = false))
        }
        push(uid, "prs", PersonalRecord.serializer(), dao.getDirtyPersonalRecords(), { it.exerciseId }) {
            dao.upsertPersonalRecord(it.copy(dirty = false))
        }
        push(uid, "goals", Goal.serializer(), dao.getDirtyGoals(), { it.id }) {
            dao.upsertGoal(it.copy(dirty = false))
        }
        push(uid, "exercises", Exercise.serializer(), dao.getDirtyExercises().filter { it.isCustom }, { it.id }) {
            dao.upsertExercise(it.copy(dirty = false))
        }
    }

    suspend fun pull(uid: String): Boolean {
        var changed = false
        changed = pull(uid, "plans", WorkoutPlan.serializer(), { it.id }, { dao.getPlan(it) }) {
            dao.upsertPlan(it.copy(dirty = false))
        } || changed
        changed = pull(uid, "sessions", WorkoutSession.serializer(), { it.id }, { dao.getSession(it) }) {
            dao.upsertSession(it.copy(dirty = false))
        } || changed
        changed = pull(uid, "prs", PersonalRecord.serializer(), { it.exerciseId }, { dao.getPersonalRecord(it) }) {
            dao.upsertPersonalRecord(it.copy(dirty = false))
        } || changed
        changed = pull(uid, "goals", Goal.serializer(), { it.id }, { dao.getGoal(it) }) {
            dao.upsertGoal(it.copy(dirty = false))
        } || changed
        changed = pull(uid, "exercises", Exercise.serializer(), { it.id }, { dao.getExercise(it) }) {
            dao.upsertExercise(it.copy(dirty = false))
        } || changed
        return changed
    }

    // ---------------- helpers genéricos ----------------

    private suspend fun <T : Syncable> push(
        uid: String,
        name: String,
        serializer: KSerializer<T>,
        dirty: List<T>,
        idOf: (T) -> String,
        clearDirty: suspend (T) -> Unit,
    ) {
        if (dirty.isEmpty()) return
        val col = db.collection("users").document(uid).collection(name)
        for (item in dirty) {
            val payload = mapOf(
                "updatedAt" to item.updatedAt,
                "deleted" to item.deleted,
                "json" to json.encodeToString(serializer, item),
            )
            col.document(idOf(item)).set(payload).await()
            clearDirty(item)
        }
    }

    private suspend fun <T : Syncable> pull(
        uid: String,
        name: String,
        serializer: KSerializer<T>,
        idOf: (T) -> String,
        localById: suspend (String) -> T?,
        upsertClean: suspend (T) -> Unit,
    ): Boolean {
        val cursorKey = "$name:lastPulledAt"
        val lastPulled = dao.getSyncCursor(cursorKey) ?: 0L
        val snap = db.collection("users").document(uid).collection(name)
            .whereGreaterThan("updatedAt", lastPulled)
            .get().await()
        var maxUpdated = lastPulled
        var changed = false
        for (doc in snap.documents) {
            val jsonStr = doc.getString("json") ?: continue
            val remote = try { json.decodeFromString(serializer, jsonStr) } catch (_: Exception) { continue }
            val ua = doc.getLong("updatedAt") ?: remote.updatedAt
            if (ua > maxUpdated) maxUpdated = ua
            val local = localById(idOf(remote))
            // Last-write-wins: ignora se o local for mais recente.
            if (local != null && local.updatedAt > remote.updatedAt) continue
            upsertClean(remote)
            changed = true
        }
        if (maxUpdated > lastPulled) dao.setSyncMeta(SyncMeta(cursorKey, maxUpdated))
        return changed
    }
}
