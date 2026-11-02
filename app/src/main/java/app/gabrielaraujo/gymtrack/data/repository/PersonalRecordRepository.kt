package app.gabrielaraujo.gymtrack.data.repository

import androidx.room.withTransaction
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.domain.progression.diffRecords
import app.gabrielaraujo.gymtrack.domain.progression.recomputeRecords
import kotlinx.coroutines.flow.Flow

/**
 * Recordes pessoais. São derivados do histórico: recalculados a cada mudança nos treinos (aqui, na
 * mesma transação da mudança) e depois de cada pull da sincronização.
 */
class PersonalRecordRepository(
    private val db: GymTrackDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    val personalRecords: Flow<List<PersonalRecord>> = dao.observePersonalRecords()

    /** Recalcula recordes e PRs a partir do histórico (ex.: depois de chegarem treinos de outro aparelho). */
    suspend fun recompute(uid: String?) = db.withTransaction { recomputeLocked(uid, clock()) }

    /**
     * Recalcula recordes e os PRs de cada sessão a partir do histórico.
     * Recordes cuja sessão de origem não existe neste aparelho (ex.: importados) são mantidos como base.
     * Deve rodar dentro de uma transação.
     */
    internal suspend fun recomputeLocked(uid: String?, now: Long) {
        val sessions = dao.getCompletedSessions()
        val knownSessionIds = dao.getAllSessionIds().toHashSet()
        val existing = dao.getPersonalRecords()
        val baseline = existing.filter { !it.deleted && it.sessionId !in knownSessionIds }
        val result = recomputeRecords(sessions, baseline)

        val recordChanges = diffRecords(existing, result.records, now, uid)
        if (recordChanges.isNotEmpty()) dao.upsertPersonalRecords(recordChanges)

        val sessionChanges = sessions.mapNotNull { s ->
            val prs = result.prsBySession[s.id].orEmpty()
            // Derivado do histórico: não é edição do treino (não avança updatedAt nem entra na fila de envio).
            if (prs != s.prsAchieved) s.copy(prsAchieved = prs) else null
        }
        if (sessionChanges.isNotEmpty()) dao.upsertSessions(sessionChanges)
    }
}
