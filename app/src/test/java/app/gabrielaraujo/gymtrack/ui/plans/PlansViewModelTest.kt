package app.gabrielaraujo.gymtrack.ui.plans

import android.app.Application
import app.gabrielaraujo.gymtrack.data.SAMPLE_PLAN_ID
import app.gabrielaraujo.gymtrack.ui.ViewModelTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PlansViewModelTest : ViewModelTestBase() {

    @Test
    fun `duplicar cria uma ficha independente e inativa que pode ser ativada`() {
        val vm = track(app.plansVm())
        val sample = vm.state.await { it.plans.isNotEmpty() }.plans.single()
        vm.duplicate(sample)
        val copy = vm.state.await { it.plans.size == 2 }.plans.single { it.id != SAMPLE_PLAN_ID }
        assertEquals("${sample.name} (cópia)", copy.name)
        assertFalse(copy.isActive)
        assertNotEquals(sample.days.first().id, copy.days.first().id)
        assertEquals(SAMPLE_PLAN_ID, vm.state.value.activePlanId)

        vm.setActive(copy.id)
        vm.state.await { it.activePlanId == copy.id }
        vm.delete(copy.id)
        vm.state.await { it.plans.size == 1 }
    }

    @Test
    fun `editor carrega a ficha, edita o rascunho e salva`() {
        val editor = track(app.planEditorVm(SAMPLE_PLAN_ID))
        val loaded = editor.state.await { it.plan != null }.plan!!
        assertEquals(SAMPLE_PLAN_ID, loaded.id)

        editor.update { it.copy(name = "Minha ficha", days = it.days.dropLast(1)) }
        // Nada vai para o banco antes de salvar.
        assertEquals("Hipertrofia", runBlocking { app.repos.plans.getPlan(SAMPLE_PLAN_ID) }!!.name)

        editor.save()
        app.idle()
        val saved = runBlocking { app.repos.plans.getPlan(SAMPLE_PLAN_ID) }!!
        assertEquals("Minha ficha", saved.name)
        assertEquals(loaded.days.size - 1, saved.days.size)
        assertTrue(saved.isActive)
    }

    @Test
    fun `editor sem ficha cria uma nova`() {
        val editor = track(app.planEditorVm(null))
        val draft = editor.state.await { it.plan != null }.plan!!
        assertEquals("Nova Ficha", draft.name)
        editor.save()
        app.idle()
        assertEquals(2, runBlocking { app.repos.plans.plansSnapshot() }.size)
    }
}
