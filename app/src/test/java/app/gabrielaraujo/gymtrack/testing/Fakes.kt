package app.gabrielaraujo.gymtrack.testing

import android.app.Activity
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gabrielaraujo.gymtrack.auth.AppUser
import app.gabrielaraujo.gymtrack.auth.AuthGateway
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.sync.CloudSync
import app.gabrielaraujo.gymtrack.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuth(uid: String? = null) : AuthGateway {
    override val user = MutableStateFlow(uid?.let { AppUser(it, "Teste", "teste@example.com") })
    override val currentUid: String? get() = user.value?.uid
    override suspend fun signInWithGoogle(activity: Activity): Result<Unit> = Result.success(Unit)
    override fun signOut() { user.value = null }
}

class FakeSync : CloudSync {
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)
    var syncs = 0
    override suspend fun onLogin(uid: String) { syncs++ }
    override suspend fun sync(uid: String) { syncs++ }
    override fun reset() = Unit
}

/** Relógio controlável, começando perto de "agora" para a ofensiva (que usa a data de hoje). */
class TestClock(var now: Long = System.currentTimeMillis()) : () -> Long {
    override fun invoke(): Long = now
    fun advanceMinutes(minutes: Long) { now += minutes * 60_000 }
    fun advanceDays(days: Long) { now += days * 86_400_000 }
}

fun inMemoryDb(): GymTrackDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), GymTrackDatabase::class.java).build()
