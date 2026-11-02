package app.gabrielaraujo.gymtrack.data.repository

import androidx.room.withTransaction
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.WorkoutHealthMetrics
import app.gabrielaraujo.gymtrack.domain.sessionTotals
import kotlinx.coroutines.flow.Flow

/**
 * Treinos: o em andamento (só local) e o histórico de concluídos. Toda mudança no histórico recalcula
 * totais e recordes na mesma transação, para o treino nunca ficar salvo pela metade.
 */
class WorkoutSessionRepository(
    private val db: GymTrackDatabase,
    private val records: PersonalRecordRepository,
    private val currentUid: () -> String? = { null },
    /** Chamado após cada escrita que entra na fila de envio (agenda a sincronização). */
    private val onPendingChange: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    val completedSessions: Flow<List<WorkoutSession>> = dao.observeCompletedSessions()

    suspend fun latestIncompleteSession(): WorkoutSession? = dao.getLatestIncompleteSession()

    suspend fun lastCompletedSessionForDay(dayId: String): WorkoutSession? = dao.getLastCompletedSessionForDay(dayId)

    suspend fun getSession(id: String): WorkoutSession? = dao.getSession(id)

    suspend fun completedSessionsSnapshot(): List<WorkoutSession> = dao.getCompletedSessions()

    /** Grava o progresso do treino em andamento. Fica só local (dirty = false) até ser concluído. */
    suspend fun saveSessionInProgress(session: WorkoutSession) {
        if (session.completed) return
        dao.upsertSession(session.copy(updatedAt = clock(), ownerId = currentUid() ?: session.ownerId, dirty = false))
    }

    /** Descarta um treino em andamento. Treinos concluídos nunca são apagados por aqui. */
    suspend fun discardSessionInProgress(id: String) = db.withTransaction {
        val session = dao.getSession(id) ?: return@withTransaction
        if (!session.completed) dao.deleteSession(id)
    }

    /**
     * Conclui o treino: calcula totais e recordes pessoais. O próximo treino da ficha é derivado do
     * histórico (`nextDayIndex`), então a ficha não é regravada (o que disputaria a sincronização com
     * edições feitas nela em outro aparelho).
     */
    suspend fun completeSession(session: WorkoutSession): WorkoutSession {
        val uid = currentUid()
        val done = db.withTransaction {
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
            records.recomputeLocked(uid, endTime)
            dao.getSession(completed.id) ?: completed
        }
        onPendingChange()
        return done
    }

    /** Salva a edição de um treino do histórico e recalcula totais e recordes. */
    suspend fun updateCompletedSession(session: WorkoutSession) {
        val uid = currentUid()
        db.withTransaction {
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
            records.recomputeLocked(uid, now)
        }
        onPendingChange()
    }

    /**
     * Grava a frequência cardíaca e as calorias medidas pelo relógio num treino concluído.
     * Uma medida que sumiu do Health Connect não apaga a que já estava salva.
     *
     * Medidas não são edição do usuário: o `updatedAt` avança só 1 ms (o bastante para o envio
     * condicional notar a mudança), para nunca vencer o LWW contra uma edição feita em outro aparelho.
     * @return o treino atualizado, ou null se nada mudou.
     */
    suspend fun updateSessionHealthMetrics(id: String, metrics: WorkoutHealthMetrics): WorkoutSession? {
        val uid = currentUid()
        val updated = db.withTransaction {
            val session = dao.getSession(id)?.takeIf { it.completed && !it.deleted } ?: return@withTransaction null
            val merged = session.copy(
                avgHeartRate = metrics.avgHeartRate ?: session.avgHeartRate,
                maxHeartRate = metrics.maxHeartRate ?: session.maxHeartRate,
                calories = metrics.calories ?: session.calories,
            )
            if (merged == session) return@withTransaction null
            merged.copy(updatedAt = session.updatedAt + 1, dirty = true, ownerId = uid ?: session.ownerId).also { dao.upsertSession(it) }
        }
        if (updated != null) onPendingChange()
        return updated
    }

    /** Exclui um treino do histórico (tombstone, para propagar) e recalcula os recordes. */
    suspend fun deleteCompletedSession(id: String) {
        val uid = currentUid()
        val deleted = db.withTransaction {
            val session = dao.getSession(id) ?: return@withTransaction false
            val now = clock()
            dao.upsertSession(session.copy(deleted = true, dirty = true, updatedAt = now, ownerId = uid ?: session.ownerId))
            records.recomputeLocked(uid, now)
            true
        }
        if (deleted) onPendingChange()
    }
}
