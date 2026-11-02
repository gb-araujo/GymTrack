package app.gabrielaraujo.gymtrack.ui.account

import android.app.Application
import app.gabrielaraujo.gymtrack.ui.ViewModelTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AccountViewModelTest : ViewModelTestBase() {
    override val initialUid: String = "u1"

    @Test
    fun `sair com pendencias pede confirmacao e so apaga ao confirmar`() {
        doWorkout()
        val vm = track(app.accountVm())
        vm.state.await { it.user != null && it.pendingSync > 0 }

        vm.signOut()
        val pending = vm.state.await { it.signOutPending != null }.signOutPending!!
        assertTrue(pending > 0)
        assertNotNull(app.auth.user.value)

        vm.cancelSignOut()
        vm.state.await { it.signOutPending == null }
        assertNotNull(app.auth.user.value)

        vm.signOut()
        vm.state.await { it.signOutPending != null }
        vm.confirmSignOut()
        vm.state.await { it.user == null && it.signOutPending == null }
        app.idle()
        assertTrue(runBlocking { app.repos.sessions.completedSessionsSnapshot() }.isEmpty())
    }

    @Test
    fun `sair sem pendencias apaga os dados na hora`() {
        doWorkout()
        runBlocking {
            app.db.dao().getSessions().forEach { app.db.dao().clearSessionDirty(it.id, it.updatedAt) }
            app.db.dao().getPersonalRecords().forEach { app.db.dao().clearPersonalRecordDirty(it.exerciseId, it.updatedAt) }
        }
        val vm = track(app.accountVm())
        vm.state.await { it.user != null && it.pendingSync == 0 }

        vm.signOut()
        vm.state.await { it.user == null }
        app.idle()
        assertNull(vm.state.value.signOutPending)
        assertTrue(runBlocking { app.repos.sessions.completedSessionsSnapshot() }.isEmpty())
        assertEquals(1, app.scheduler.cancels)
    }

    @Test
    fun `backup invalido vira mensagem de erro`() {
        val vm = track(app.accountVm())
        vm.importBackup("isto não é json")
        assertEquals("Falha ao importar: backup inválido.", vm.state.await { it.message != null }.message)
        vm.messageShown()
        vm.state.await { it.message == null }
    }

    @Test
    fun `backup exportado pode ser importado de volta`() {
        doWorkout()
        val vm = track(app.accountVm())
        val out = java.io.ByteArrayOutputStream()
        vm.exportBackup { out }
        assertEquals("Backup salvo.", vm.state.await { it.message != null }.message)
        vm.messageShown()

        vm.importBackup(out.toString(Charsets.UTF_8.name()))
        assertTrue(vm.state.await { it.message?.startsWith("Importados") == true }.message!!.isNotEmpty())
    }
}
