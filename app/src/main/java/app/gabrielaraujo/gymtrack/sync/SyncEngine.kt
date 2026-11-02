package app.gabrielaraujo.gymtrack.sync

import androidx.room.withTransaction
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.SAMPLE_PLAN_ID
import app.gabrielaraujo.gymtrack.data.SyncMeta
import app.gabrielaraujo.gymtrack.data.Syncable
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Sincronização offline-first entre o Room (fonte da verdade) e o [RemoteStore].
 *
 * - A fila de envio é o próprio registro: `dirty = 1` é gravado junto com a edição, na mesma linha,
 *   então sobrevive ao encerramento do app e nunca fica dessincronizado da entidade.
 * - Envio idempotente (id estável por documento) e condicional (LWW por `updatedAt` também na escrita).
 *   O `dirty` só é limpo se o registro não mudou durante o envio.
 * - Pull incremental pelo relógio do servidor; comparação e gravação locais numa transação do Room,
 *   para uma edição do usuário feita no meio do pull nunca ser sobrescrita.
 * - Conflitos por tipo de dado: ver [SyncConflictResolver].
 */
class SyncEngine(
    private val db: GymTrackDatabase,
    private val remote: RemoteStore,
    /** Recalcula os dados derivados do histórico (recordes) depois que chegam mudanças da nuvem. */
    private val recomputeDerived: suspend (uid: String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : CloudSync {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val collections: List<SyncedCollection<*>> = listOf(
        SyncedCollection(
            "plans", WorkoutPlan.serializer(), { it.id }, dao::getDirtyPlans, dao::getPlan, dao::upsertPlan,
            clearDirty = { dao.clearPlanDirty(it.id, it.updatedAt) },
            withDirty = { p, dirty -> p.copy(dirty = dirty) },
        ),
        SyncedCollection(
            "sessions", WorkoutSession.serializer(), { it.id }, dao::getDirtySessions, dao::getSession, dao::upsertSession,
            clearDirty = { dao.clearSessionDirty(it.id, it.updatedAt) },
            withDirty = { s, dirty -> s.copy(dirty = dirty) },
            resolve = SyncConflictResolver::session,
            affectsDerived = true,
        ),
        SyncedCollection(
            "prs", PersonalRecord.serializer(), { it.exerciseId }, dao::getDirtyPersonalRecords, dao::getPersonalRecord, dao::upsertPersonalRecord,
            clearDirty = { dao.clearPersonalRecordDirty(it.exerciseId, it.updatedAt) },
            withDirty = { r, dirty -> r.copy(dirty = dirty) },
            affectsDerived = true,
        ),
        SyncedCollection(
            "goals", Goal.serializer(), { it.id }, dao::getDirtyGoals, dao::getGoal, dao::upsertGoal,
            clearDirty = { dao.clearGoalDirty(it.id, it.updatedAt) },
            withDirty = { g, dirty -> g.copy(dirty = dirty) },
        ),
        // Só exercícios personalizados sobem (a consulta de pendentes já filtra).
        SyncedCollection(
            "exercises", Exercise.serializer(), { it.id }, dao::getDirtyExercises, dao::getExercise, dao::upsertExercise,
            clearDirty = { dao.clearExerciseDirty(it.id, it.updatedAt) },
            withDirty = { e, dirty -> e.copy(dirty = dirty) },
        ),
    )

    override suspend fun onLogin(uid: String): SyncResult = runReportingErrors {
        adoptLocalData(uid)
        syncCollections(uid)
    }

    override suspend fun sync(uid: String): SyncResult = runReportingErrors { syncCollections(uid) }

    override fun reset() {
        _status.value = SyncStatus.Idle
    }

    override suspend fun runExclusive(block: suspend () -> Unit) = lock.withLock { block() }

    private suspend fun runReportingErrors(block: suspend () -> List<Exception>): SyncResult = lock.withLock {
        _status.value = SyncStatus.Syncing
        try {
            val errors = block()
            if (errors.isEmpty()) {
                _status.value = SyncStatus.Synced(clock())
                SyncResult.Success
            } else {
                val e = errors.first()
                val message = e.message ?: e.javaClass.simpleName
                _status.value = SyncStatus.Error(message, clock())
                SyncResult.Failure(message)
            }
        } catch (e: CancellationException) {
            _status.value = SyncStatus.Idle
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            _status.value = SyncStatus.Error(message, clock())
            SyncResult.Failure(message)
        }
    }

    /**
     * Envia, baixa, recalcula o que é derivado do histórico e envia de novo o que o recálculo mudou.
     * Uma coleção com erro não impede as outras de sincronizar.
     * @return os erros encontrados (vazio = tudo sincronizado).
     */
    private suspend fun syncCollections(uid: String): List<Exception> {
        val pushed = eachCollection { c -> c.push(uid) && c.affectsDerived }
        val pulled = eachCollection { c -> c.pull(uid) && c.affectsDerived }
        val errors = pushed.errors + pulled.errors
        if (!pushed.derivedChanged && !pulled.derivedChanged) return errors.distinctBy { it.message }
        recomputeDerived(uid)
        return (errors + eachCollection { c -> c.push(uid); false }.errors).distinctBy { it.message }
    }

    private class Round(val derivedChanged: Boolean, val errors: List<Exception>)

    /**
     * As coleções são independentes: roda em paralelo (sem somar idas à rede) e isola as falhas.
     * @param block devolve true se a coleção mudou dados que exigem recalcular os derivados.
     */
    private suspend fun eachCollection(block: suspend (SyncedCollection<*>) -> Boolean): Round = coroutineScope {
        val outcomes = collections.map { c ->
            async {
                try {
                    Result.success(block(c))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.awaitAll()
        Round(
            derivedChanged = outcomes.any { it.getOrNull() == true },
            errors = outcomes.mapNotNull { it.exceptionOrNull() as? Exception },
        )
    }

    /** Adota dados locais anônimos (ownerId == null) no login, marcando-os para envio. */
    private suspend fun adoptLocalData(uid: String) = db.withTransaction {
        val now = clock()
        fun ts(v: Long) = if (v == 0L) now else v

        dao.getPlans().filter { it.ownerId == null }.forEach {
            // A ficha exemplo intocada não sobe: senão sobrescreveria a versão que o usuário já editou na nuvem.
            val pristineSample = it.id == SAMPLE_PLAN_ID && it.updatedAt == 0L
            dao.upsertPlan(if (pristineSample) it.copy(ownerId = uid) else it.copy(ownerId = uid, dirty = true, updatedAt = ts(it.updatedAt)))
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

    // ---------------- envio e recebimento por coleção ----------------

    /** @return true se alguma versão remota mais nova foi aplicada localmente. */
    private suspend fun <T : Syncable> SyncedCollection<T>.push(uid: String): Boolean {
        val pending = dirty(uid)
        if (pending.isEmpty()) return false
        val docs = pending.map { RemoteDoc(idOf(it), it.updatedAt, it.deleted, json.encodeToString(serializer, withDirty(it, false))) }
        val newerRemote = remote.pushIfNewer(uid, name, docs).associateBy { it.id }
        var applied = false
        db.withTransaction {
            for (item in pending) {
                val winner = newerRemote[idOf(item)]
                if (winner == null) {
                    // Só limpa o dirty de quem não mudou durante o envio.
                    clearDirty(item)
                    continue
                }
                // A nuvem tem uma edição mais nova: vale ela, a menos que o usuário tenha editado de novo nesse meio-tempo.
                val current = get(idOf(item)) ?: continue
                if (current.updatedAt != item.updatedAt) continue
                val remoteItem = decode(winner) ?: continue
                if (applyRemote(remoteItem)) applied = true
            }
        }
        return applied
    }

    /** @return true se alguma mudança remota foi aplicada localmente. */
    private suspend fun <T : Syncable> SyncedCollection<T>.pull(uid: String): Boolean {
        // Cursor por usuário; sem cursor (primeira vez ou docs antigos sem serverUpdatedAt) baixa tudo.
        val cursorKey = "$uid:$name:serverCursor"
        val cursor = dao.getSyncCursor(cursorKey)
        val changes = remote.changesSince(uid, name, cursor)
        var applied = false
        db.withTransaction {
            var maxSeen = cursor ?: 0L
            var firstUnreadable: Long? = null
            for (doc in changes) {
                val item = decode(doc)
                if (item == null) {
                    doc.serverUpdatedAt?.let { firstUnreadable = minOf(firstUnreadable ?: it, it) }
                    continue
                }
                doc.serverUpdatedAt?.let { if (it > maxSeen) maxSeen = it }
                if (applyRemote(item)) applied = true
            }
            // Um doc que este app não conseguiu ler (ex.: gravado por uma versão mais nova) é buscado de novo depois.
            val next = firstUnreadable?.let { minOf(maxSeen, it - 1) } ?: maxSeen
            if (cursor == null || next > cursor) dao.setSyncMeta(SyncMeta(cursorKey, next))
        }
        return applied
    }

    /**
     * Aplica uma versão remota segundo o LWW. Deve rodar dentro de uma transação.
     * @return true se o registro local mudou.
     */
    private suspend fun <T : Syncable> SyncedCollection<T>.applyRemote(remoteItem: T): Boolean {
        val local = get(idOf(remoteItem))
        // Local mais novo fica (e sobe na próxima rodada, se estiver pendente).
        if (local != null && local.updatedAt > remoteItem.updatedAt) return false
        val clean = withDirty(remoteItem, false)
        val merged = if (local == null) clean else withDirty(resolve(local, clean), false)
        // Se a mescla preservou algo que a nuvem não tem (ex.: medidas do relógio), continua pendente de envio.
        val result = withDirty(merged, merged != clean)
        if (result == local) return false
        upsert(result)
        return true
    }

    private fun <T : Syncable> SyncedCollection<T>.decode(doc: RemoteDoc): T? =
        try {
            json.decodeFromString(serializer, doc.json)
        } catch (_: Exception) {
            null
        }

    /** Como cada tabela sincroniza: acesso ao Room e estratégia de conflito. */
    private class SyncedCollection<T : Syncable>(
        val name: String,
        val serializer: KSerializer<T>,
        val idOf: (T) -> String,
        val dirty: suspend (uid: String) -> List<T>,
        val get: suspend (id: String) -> T?,
        val upsert: suspend (T) -> Unit,
        val clearDirty: suspend (T) -> Unit,
        val withDirty: (T, Boolean) -> T,
        /** Versão gravada quando a remota vence o LWW (pode preservar dados que só o local tem). */
        val resolve: (local: T, remote: T) -> T = { _, remote -> remote },
        /** Mudanças nesta coleção exigem recalcular os dados derivados do histórico. */
        val affectsDerived: Boolean = false,
    )
}
