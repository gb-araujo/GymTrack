package app.gabrielaraujo.gymtrack.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** Usuário logado, desacoplado do FirebaseUser para a UI e os testes. */
data class AppUser(val uid: String, val displayName: String?, val email: String?)

interface AuthGateway {
    val user: StateFlow<AppUser?>
    val currentUid: String?
    suspend fun signInWithGoogle(activity: Activity): Result<Unit>
    fun signOut()
}
