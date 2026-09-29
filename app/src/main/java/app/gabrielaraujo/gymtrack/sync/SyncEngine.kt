package app.gabrielaraujo.gymtrack.sync

import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.GymTrackDao
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.SyncMeta
import app.gabrielaraujo.gymtrack.data.Syncable
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.Date

/**
 * Sincronização offline-first com o Firestore.
 * Estrutura: users/{uid}/{coleção}/{id}, cada doc = { updatedAt, serverUpdatedAt, deleted, json }.
 *
 * - `updatedAt` (relógio do aparelho) decide conflitos (last-write-wins).
 * - `serverUpdatedAt` (relógio do servidor) é o cursor do pull incremental: não depende do
 *   relógio de cada aparelho, então uma mudança feita num celular com hora atrasada não é perdida.
 */
class SyncEngine(
    private val dao: GymTrackDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : CloudSync {
    private val db: FirebaseFirestore by lazy { Firebase.firestore }
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    override suspend fun onLogin(uid: String) {
        runReportingErrors {
            adoptLocalData(uid)
            pushDirty(uid)
            pull(uid)
        }
    }

    override suspend fun sync(uid: String) {
        runReportingErrors {
            pushDirty(uid)
            pull(uid)
        }
    }

    override fun reset() {
        _status.value = SyncStatus.Idle
    }

    private suspend fun runReportingErrors(block: suspend () -> Unit) = lock.withLock {
        _status.value = SyncStatus.Syncing
        _status.value = try {
            block()
            SyncStatus.Synced(clock())
        } catch (e: CancellationException) {
            _status.value = SyncStatus.Idle
            throw e
        } catch (e: Exception) {
            SyncStatus.Error(e.message ?: e.javaClass.simpleName, clock())
        }
    }

    /** Adota dados locais anônimos (ownerId == null) no login, marcando-os para envio. */
    private suspend fun adoptLocalData(uid: String) {
        val now = clock()
        fun ts(v: Long) = if (v == 0L) now else v

        dao.getPlans().filter { it.ownerId == null }.forEach {
            dao.upsertPlan(it.copy(ownerId = uid, dirty = true, updatedAt = ts(it.updatedAt)))
        }
        // Treinos em andamento continuam só locais.
        dao.getSessions().filter { it.ownerId == null && it.completed }.forEach {
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

    private suspend fun pushDirty(uid: String) {
        push(uid, "plans", WorkoutPlan.serializer(), dao.getDirtyPlans(), { it.id }) { dao.clearPlanDirty(it.id, it.updatedAt) }
        push(uid, "sessions", WorkoutSession.serializer(), dao.getDirtySessions(), { it.id }) { dao.clearSessionDirty(it.id, it.updatedAt) }
        push(uid, "prs", PersonalRecord.serializer(), dao.getDirtyPersonalRecords(), { it.exerciseId }) {
            dao.clearPersonalRecordDirty(it.exerciseId, it.updatedAt)
        }
        push(uid, "goals", Goal.serializer(), dao.getDirtyGoals(), { it.id }) { dao.clearGoalDirty(it.id, it.updatedAt) }
        push(uid, "exercises", Exercise.serializer(), dao.getDirtyExercises().filter { it.isCustom }, { it.id }) {
            dao.clearExerciseDirty(it.id, it.updatedAt)
        }
    }

    private suspend fun pull(uid: String) {
        pull(uid, "plans", WorkoutPlan.serializer(), { it.id }, { dao.getPlan(it) }) { dao.upsertPlan(it.copy(dirty = false)) }
        pull(uid, "sessions", WorkoutSession.serializer(), { it.id }, { dao.getSession(it) }) { dao.upsertSession(it.copy(dirty = false)) }
        pull(uid, "prs", PersonalRecord.serializer(), { it.exerciseId }, { dao.getPersonalRecord(it) }) {
            dao.upsertPersonalRecord(it.copy(dirty = false))
        }
        pull(uid, "goals", Goal.serializer(), { it.id }, { dao.getGoal(it) }) { dao.upsertGoal(it.copy(dirty = false)) }
        pull(uid, "exercises", Exercise.serializer(), { it.id }, { dao.getExercise(it) }) { dao.upsertExercise(it.copy(dirty = false)) }
    }

    // ---------------- helpers genéricos ----------------

    /** Envia em lotes (WriteBatch): uma ida à rede por lote em vez de uma por documento. */
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
        for (chunk in dirty.chunked(BATCH_LIMIT)) {
            val batch = db.batch()
            for (item in chunk) {
                batch.set(
                    col.document(idOf(item)),
                    mapOf(
                        "updatedAt" to item.updatedAt,
                        "serverUpdatedAt" to FieldValue.serverTimestamp(),
                        "deleted" to item.deleted,
                        "json" to json.encodeToString(serializer, item),
                    ),
                )
            }
            batch.commit().await()
            // Só limpa o dirty de quem não mudou durante o envio.
            chunk.forEach { clearDirty(it) }
        }
    }

    private suspend fun <T : Syncable> pull(
        uid: String,
        name: String,
        serializer: KSerializer<T>,
        idOf: (T) -> String,
        localById: suspend (String) -> T?,
        upsertClean: suspend (T) -> Unit,
    ) {
        // Cursor por usuário; sem cursor (primeira vez ou docs antigos sem serverUpdatedAt) baixa tudo.
        val cursorKey = "$uid:$name:serverCursor"
        val cursor = dao.getSyncCursor(cursorKey)
        val col = db.collection("users").document(uid).collection(name)
        val query: Query = if (cursor == null) col else col.whereGreaterThan("serverUpdatedAt", Timestamp(Date(cursor)))
        val snap = query.get().await()

        var maxServer = cursor ?: 0L
        for (doc in snap.documents) {
            doc.getTimestamp("serverUpdatedAt")?.toDate()?.time?.let { if (it > maxServer) maxServer = it }
            val jsonStr = doc.getString("json") ?: continue
            val remote = try { json.decodeFromString(serializer, jsonStr) } catch (_: Exception) { continue }
            val local = localById(idOf(remote))
            // Last-write-wins: ignora se o local for mais recente.
            if (local != null && local.updatedAt > remote.updatedAt) continue
            upsertClean(remote)
        }
        if (cursor == null || maxServer > cursor) dao.setSyncMeta(SyncMeta(cursorKey, maxServer))
    }

    private companion object {
        /** Limite do Firestore é 500 operações por lote. */
        const val BATCH_LIMIT = 400
    }
}
