package app.gabrielaraujo.gymtrack.ui.account

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.auth.AccountCoordinator
import app.gabrielaraujo.gymtrack.auth.AppUser
import app.gabrielaraujo.gymtrack.auth.AuthGateway
import app.gabrielaraujo.gymtrack.auth.SignOutResult
import app.gabrielaraujo.gymtrack.data.repository.BackupRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutSessionRepository
import app.gabrielaraujo.gymtrack.health.HealthConnectManager
import app.gabrielaraujo.gymtrack.health.HealthImporter
import app.gabrielaraujo.gymtrack.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

data class AccountUiState(
    val user: AppUser? = null,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val pendingSync: Int = 0,
    val signingIn: Boolean = false,
    val authError: String? = null,
    /** Alterações que não subiram quando o usuário pediu para sair (aguardando confirmação), ou null. */
    val signOutPending: Int? = null,
    /** Aviso curto para o usuário (ex.: resultado do backup); some com [AccountViewModel.messageShown]. */
    val message: String? = null,
)

/** Conta, sincronização, backup e ajustes do Health Connect. */
class AccountViewModel(
    private val auth: AuthGateway,
    private val account: AccountCoordinator,
    syncStatus: StateFlow<SyncStatus>,
    pendingSync: Flow<Int>,
    private val backup: BackupRepository,
    private val sessions: WorkoutSessionRepository,
    private val healthImporter: HealthImporter,
    /** Ajustes do Health Connect (null onde não há integração, como nos testes). */
    val healthConnect: HealthConnectManager?,
    /** Escopo para o que deve terminar mesmo se a tela fechar (ex.: importar um backup). */
    private val appScope: CoroutineScope,
) : ViewModel() {
    private data class Local(
        val signingIn: Boolean = false,
        val authError: String? = null,
        val signOutPending: Int? = null,
        val message: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<AccountUiState> =
        combine(auth.user, syncStatus, pendingSync, local) { user, status, pending, l ->
            AccountUiState(user, status, pending, l.signingIn, l.authError, l.signOutPending, l.message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun signIn(activity: Activity) = viewModelScope.launch {
        local.update { it.copy(signingIn = true, authError = null) }
        val result = auth.signInWithGoogle(activity)
        local.update { it.copy(signingIn = false, authError = result.exceptionOrNull()?.let { e -> e.message ?: "Falha ao entrar." }) }
    }

    fun clearAuthError() = local.update { it.copy(authError = null) }

    /** Sai da conta; se algo não subiu para a nuvem, pede confirmação ([AccountUiState.signOutPending]). */
    fun signOut() = viewModelScope.launch {
        val result = account.signOut()
        if (result is SignOutResult.PendingChanges) local.update { it.copy(signOutPending = result.count) }
    }

    /** Sai mesmo com pendências (elas são perdidas). */
    fun confirmSignOut() {
        local.update { it.copy(signOutPending = null) }
        appScope.launch { account.signOut(discardPending = true) }
    }

    fun cancelSignOut() = local.update { it.copy(signOutPending = null) }

    fun syncNow() = viewModelScope.launch { account.syncNow() }

    fun forceFullResync() = viewModelScope.launch { account.forceFullResync() }

    /** Importa um backup (deste app ou do app web). */
    fun importBackup(text: String) = appScope.launch {
        val count = runCatching { backup.importBackup(text) }.getOrDefault(-1)
        showMessage(if (count < 0) "Falha ao importar: backup inválido." else "Importados $count registros.")
    }

    /** Gera o backup completo e grava em [open] (ex.: um arquivo escolhido pelo usuário). */
    fun exportBackup(open: () -> OutputStream?) = viewModelScope.launch {
        val ok = runCatching {
            val json = backup.exportBackup()
            withContext(Dispatchers.IO) {
                (open() ?: error("sem destino")).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        }.isSuccess
        showMessage(if (ok) "Backup salvo." else "Falha ao salvar o backup.")
    }

    /** A importação do relógio foi ligada: busca também os treinos do último mês. */
    fun onHealthImportEnabled() = appScope.launch { healthImporter.refresh(backfill = true) }

    /** Envia ao Health Connect os treinos já concluídos. @return quantos foram enviados. */
    suspend fun exportPreviousToHealth(): Int = healthConnect?.exportAll(sessions.completedSessionsSnapshot()) ?: 0

    fun showMessage(text: String) = local.update { it.copy(message = text) }

    fun messageShown() = local.update { it.copy(message = null) }
}
