package app.gabrielaraujo.track

import android.app.Application
import app.gabrielaraujo.track.auth.AuthManager
import app.gabrielaraujo.track.data.TrackDatabase
import app.gabrielaraujo.track.data.TrackRepository
import app.gabrielaraujo.track.sync.SyncEngine

/**
 * Segura o banco Room, o repositório, o Auth e o SyncEngine por toda a vida do processo.
 * Substitui o `db` singleton + stores globais do app web.
 */
class TrackApplication : Application() {
    val database: TrackDatabase by lazy { TrackDatabase.build(this) }
    val repository: TrackRepository by lazy { TrackRepository(database) }
    val authManager: AuthManager by lazy { AuthManager() }
    val syncEngine: SyncEngine by lazy { SyncEngine(repository) }
}
