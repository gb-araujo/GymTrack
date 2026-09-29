package app.gabrielaraujo.gymtrack.sync

import kotlinx.coroutines.flow.StateFlow

sealed interface SyncStatus {
    /** Nunca sincronizou nesta execução (ou usuário deslogado). */
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Error(val message: String, val at: Long) : SyncStatus
}

/** Sincronização com a nuvem. Abstraída para o ViewModel ser testável sem Firebase. */
interface CloudSync {
    val status: StateFlow<SyncStatus>

    /** Adota os dados locais anônimos para o usuário e sincroniza. */
    suspend fun onLogin(uid: String)

    /** Envia pendências e baixa mudanças. Falhas viram [SyncStatus.Error], nunca exceção. */
    suspend fun sync(uid: String)

    /** Volta ao estado inicial (ex.: após logout). */
    fun reset()
}
