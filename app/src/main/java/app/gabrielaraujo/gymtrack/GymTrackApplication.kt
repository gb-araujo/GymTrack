package app.gabrielaraujo.gymtrack

import android.app.Application
import app.gabrielaraujo.gymtrack.auth.AuthManager
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.GymTrackRepository
import app.gabrielaraujo.gymtrack.sync.SyncEngine

/**
 * Segura o banco Room, o repositório, o Auth e o SyncEngine por toda a vida do processo.
 * Substitui o `db` singleton + stores globais do app web.
 */
class GymTrackApplication : Application() {
    val database: GymTrackDatabase by lazy { GymTrackDatabase.build(this) }
    val repository: GymTrackRepository by lazy { GymTrackRepository(database) }
    val authManager: AuthManager by lazy { AuthManager() }
    val syncEngine: SyncEngine by lazy { SyncEngine(repository) }
}
