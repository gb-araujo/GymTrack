package app.gabrielaraujo.gymtrack.ui.progress

import android.app.Application
import app.gabrielaraujo.gymtrack.ui.ViewModelTestBase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ProgressViewModelTest : ViewModelTestBase() {

    @Test
    fun `progresso mostra recordes, evolucao de carga e a semana atual`() {
        val first = doWorkout(firstWeight = 60.0)
        app.clock.advanceMinutes(60)
        doWorkout(firstWeight = 70.0)
        val exerciseId = first.exercises.first().exerciseId

        val state = track(app.progressVm()).state.await { it.totalWorkouts == 2 }
        assertEquals(70.0, state.records.single { it.exerciseId == exerciseId }.weight, 0.0)
        assertEquals(listOf(60.0, 70.0), state.loadHistory.getValue(exerciseId))
        assertEquals(2, state.recentVolumesTons.size)
        // Os dois treinos são desta semana: a última janela do gráfico os inclui.
        assertEquals(2, state.weeklyCounts.last())
        assertEquals(2, state.workoutsThisMonth)
    }
}
