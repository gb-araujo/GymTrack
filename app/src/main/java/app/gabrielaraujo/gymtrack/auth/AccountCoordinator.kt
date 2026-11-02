package app.gabrielaraujo.gymtrack.auth

import app.gabrielaraujo.gymtrack.data.repository.UserDataRepository
import app.gabrielaraujo.gymtrack.session.ActiveWorkoutManager
import app.gabrielaraujo.gymtrack.sync.CloudSync
import app.gabrielaraujo.gymtrack.sync.SyncResult
import app.gabrielaraujo.gymtrack.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Resultado de um pedido de logout. */
sealed interface SignOutResult {
    data object Done : SignOutResult

    /** Há [count] alterações que não subiram; sair agora as perderia (pede confirmação). */
    data class PendingChanges(val count: Int) : SignOutResult
}

/**
 * Liga a conta aos dados locais: ao entrar, adota os dados anônimos e sincroniza; ao sair, apaga os
 * dados da conta deste aparelho. Roda no escopo do app, então um logout não fica pela metade se a
 * tela fechar no meio.
 */
class AccountCoordinator(
    private val auth: AuthGateway,
    private val sync: CloudSync,
    private val scheduler: SyncScheduler,
    private val userData: UserDataRepository,
    private val workout: ActiveWorkoutManager,
    private val scope: CoroutineScope,
) {
    /** Acompanha o login pela vida do processo (chamado uma vez, na inicialização). */
    fun start(): Job = scope.launch {
        auth.user.map { it?.uid }.distinctUntilChanged().collect { uid ->
            if (uid == null) {
                sync.reset()
            } else if (sync.onLogin(uid) is SyncResult.Failure) {
                // Sem rede agora: tenta de novo em segundo plano quando ela voltar.
                scheduler.request()
            }
        }
    }

    /** O app voltou ao primeiro plano: busca o que mudou em outros aparelhos. */
    fun onForeground() {
        if (auth.currentUid != null) scheduler.request()
    }

    suspend fun syncNow(): SyncResult {
        val uid = auth.currentUid ?: return SyncResult.Success
        return sync.sync(uid).also { if (it is SyncResult.Failure) scheduler.request() }
    }

    /** Reenvia tudo, mesmo o que já estava marcado como sincronizado (ex.: depois de habilitar o Firestore). */
    suspend fun forceFullResync(): SyncResult {
        val uid = auth.currentUid ?: return SyncResult.Success
        userData.forceFullResync(uid)
        return syncNow()
    }

    /**
     * Sai da conta e apaga os dados dela deste aparelho (outra conta que entrar não os vê nem os envia).
     * Antes, tenta enviar as pendências; se alguma não subir e [discardPending] for false, não sai.
     */
    suspend fun signOut(discardPending: Boolean = false): SignOutResult = scope.async {
        val uid = auth.currentUid ?: return@async SignOutResult.Done
        if (!discardPending) {
            sync.sync(uid)
            val pending = userData.pendingSyncCount.first()
            if (pending > 0) return@async SignOutResult.PendingChanges(pending)
        }
        scheduler.cancel()
        workout.clear()
        auth.signOut()
        // Espera uma sincronização em andamento terminar: senão ela regravaria dados depois da limpeza.
        sync.runExclusive { userData.clearUserData() }
        SignOutResult.Done
    }.await()
}
