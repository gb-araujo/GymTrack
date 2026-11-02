package app.gabrielaraujo.gymtrack.data.repository

import androidx.room.withTransaction
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.buildSamplePlan
import app.gabrielaraujo.gymtrack.data.builtinExercises
import kotlinx.coroutines.flow.Flow

/** Os dados locais do usuário como um todo: semente inicial, pendências de envio, reenvio e limpeza. */
class UserDataRepository(
    private val db: GymTrackDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    /** Registros esperando envio à nuvem (para o indicador de sincronização). */
    val pendingSyncCount: Flow<Int> = dao.observePendingCount()

    /**
     * Semeia a ficha exemplo na primeira execução e mantém a biblioteca nativa igual à de [builtinExercises]
     * (nomes revisados chegam a quem já tinha o app). Só grava exercício nativo novo ou alterado, e nunca
     * sobrescreve um personalizado. Os nativos não são do usuário: não ficam pendentes nem sobem para a nuvem.
     */
    suspend fun seedIfEmpty() {
        val existing = dao.getExercises().associateBy { it.id }
        val outdated = builtinExercises.filter { builtin ->
            val current = existing[builtin.id]
            current == null || (!current.isCustom && current != builtin)
        }
        if (outdated.isNotEmpty()) dao.insertExercises(outdated)
        if (dao.planCount() == 0) dao.upsertPlan(buildSamplePlan(clock()))
    }

    /** Marca tudo que é do usuário como pendente, para reenviar mesmo o que já estava (supostamente) sincronizado. */
    suspend fun forceFullResync(uid: String) = db.withTransaction {
        dao.markAllPlansDirty(uid)
        dao.markAllSessionsDirty(uid)
        dao.markAllPersonalRecordsDirty(uid)
        dao.markAllGoalsDirty(uid)
        dao.markAllExercisesDirty(uid)
    }

    /** Apaga os dados do usuário deste aparelho (logout) e semeia de novo a ficha exemplo. Exercícios nativos ficam. */
    suspend fun clearUserData() = db.withTransaction {
        dao.deleteAllSessions()
        dao.deleteAllPlans()
        dao.deleteAllPersonalRecords()
        dao.deleteAllGoals()
        dao.deleteCustomExercises()
        dao.deleteSyncMeta()
        seedIfEmpty()
    }
}
