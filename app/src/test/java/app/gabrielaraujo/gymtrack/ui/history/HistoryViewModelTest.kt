package app.gabrielaraujo.gymtrack.ui.history

import android.app.Application
import app.gabrielaraujo.gymtrack.ui.ViewModelTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HistoryViewModelTest : ViewModelTestBase() {

    @Test
    fun `excluir treino recalcula recordes e vai para o Health Connect`() {
        val heavy = doWorkout(firstWeight = 200.0)
        app.clock.advanceMinutes(60)
        val light = doWorkout(firstWeight = 50.0)
        val exerciseId = heavy.exercises.first().exerciseId

        val vm = track(app.historyVm())
        vm.state.await { it.sessions.size == 2 && it.records[exerciseId]?.sessionId == heavy.id }

        vm.delete(heavy.id)
        val state = vm.state.await { it.sessions.size == 1 && it.records[exerciseId]?.sessionId == light.id }
        assertEquals(light.id, state.sessions.single().id)
        assertTrue(runBlocking { app.repos.sessions.getSession(heavy.id) }!!.deleted)
        assertEquals(heavy.id, eventually { app.exporter.removed.singleOrNull() })
    }

    @Test
    fun `editar treino recalcula volume e recorde`() {
        val done = doWorkout(firstWeight = 50.0)
        val exerciseId = done.exercises.first().exerciseId
        val vm = track(app.historyVm())
        val saved = vm.state.await { it.sessions.size == 1 }.sessions.single()

        val first = saved.exercises.first()
        val edited = saved.copy(
            exercises = listOf(first.copy(sets = first.sets.mapIndexed { i, s -> if (i == 0) s.copy(weight = 300.0) else s })) +
                saved.exercises.drop(1),
        )
        vm.update(edited)

        // Recordes e histórico chegam por fluxos separados: espera os dois refletirem a edição.
        val state = vm.state.await { it.records[exerciseId]?.weight == 300.0 && it.sessions.single().totalVolume != saved.totalVolume }
        val updated = state.sessions.single()
        assertEquals((saved.totalVolume ?: 0.0) + (300.0 - 50.0) * first.sets.first().reps, updated.totalVolume!!, 0.001)
        assertTrue(exerciseId in updated.prsAchieved)
        // A versão editada também vai para o Health Connect.
        assertEquals(300.0, eventually { app.exporter.exported.getOrNull(1) }.exercises.first().sets.first().weight, 0.0)
    }
}
