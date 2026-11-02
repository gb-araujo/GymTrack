package app.gabrielaraujo.gymtrack.sync

import kotlinx.coroutines.flow.StateFlow

sealed interface SyncStatus {
    /** Nunca sincronizou nesta execução (ou usuário deslogado). */
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Error(val message: String, val at: Long) : SyncStatus
}

/** Resultado de uma rodada de sincronização (para o agendador decidir se tenta de novo). */
sealed interface SyncResult {
    data object Success : SyncResult
    data class Failure(val message: String) : SyncResult
}

/** Sincronização com a nuvem. Abstraída para o ViewModel ser testável sem Firebase. */
interface CloudSync {
    val status: StateFlow<SyncStatus>

    /** Adota os dados locais anônimos para o usuário e sincroniza. */
    suspend fun onLogin(uid: String): SyncResult

    /** Envia pendências e baixa mudanças. Falhas viram [SyncResult.Failure] e [SyncStatus.Error], nunca exceção. */
    suspend fun sync(uid: String): SyncResult

    /** Volta ao estado inicial (ex.: após logout). */
    fun reset()

    /** Roda [block] sem nenhuma sincronização em andamento (ex.: apagar os dados locais no logout). */
    suspend fun runExclusive(block: suspend () -> Unit)
}
