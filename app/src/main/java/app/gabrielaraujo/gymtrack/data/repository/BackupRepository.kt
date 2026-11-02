package app.gabrielaraujo.gymtrack.data.repository

import androidx.room.withTransaction
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.domain.backup.BackupCodec
import app.gabrielaraujo.gymtrack.domain.backup.BackupTables

/** Backup completo em JSON (fichas, histórico, recordes, metas e exercícios personalizados). */
class BackupRepository(
    private val db: GymTrackDatabase,
    private val records: PersonalRecordRepository,
    private val currentUid: () -> String? = { null },
    /** Chamado após cada escrita que entra na fila de envio (agenda a sincronização). */
    private val onPendingChange: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

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
    suspend fun importBackup(text: String): Int {
        val tables = BackupCodec.decode(text)
        val uid = currentUid()
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
            records.recomputeLocked(uid, now)
        }
        if (dirty) onPendingChange()
        return tables.size
    }
}
