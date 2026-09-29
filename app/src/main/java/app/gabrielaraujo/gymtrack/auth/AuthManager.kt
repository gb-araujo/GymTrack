package app.gabrielaraujo.gymtrack.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Login com Google via Credential Manager + Firebase Auth.
 * O id do cliente web (`default_web_client_id`) é gerado pelo plugin google-services
 * a partir do `google-services.json` — só existe se o provedor Google estiver habilitado
 * no console e um SHA-1 cadastrado.
 */
class AuthManager : AuthGateway {
    private val auth: FirebaseAuth = Firebase.auth
    private val _user = MutableStateFlow(auth.currentUser?.toAppUser())
    override val user: StateFlow<AppUser?> = _user.asStateFlow()

    override val currentUid: String? get() = auth.currentUser?.uid

    init {
        auth.addAuthStateListener { fb -> _user.value = fb.currentUser?.toAppUser() }
    }

    private fun FirebaseUser.toAppUser() = AppUser(uid, displayName, email)

    private fun webClientId(context: Context): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId == 0) null else context.getString(resId)
    }

    override suspend fun signInWithGoogle(activity: Activity): Result<Unit> {
        val clientId = webClientId(activity)
            ?: return Result.failure(
                IllegalStateException(
                    "Login Google não configurado: habilite o provedor Google no Firebase, " +
                        "cadastre o SHA-1 e rebaixe o google-services.json.",
                ),
            )
        return try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                auth.signInWithCredential(firebaseCred).await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Credencial inesperada retornada."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun signOut() = auth.signOut()
}
