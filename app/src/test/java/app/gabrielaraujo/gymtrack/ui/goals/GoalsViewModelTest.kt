package app.gabrielaraujo.gymtrack.ui.goals

import android.app.Application
import app.gabrielaraujo.gymtrack.domain.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.GoalTypes
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
class GoalsViewModelTest : ViewModelTestBase() {

    @Test
    fun `progresso das metas sai do historico`() {
        val done = doWorkout(firstWeight = 80.0)
        val exerciseId = done.exercises.first().exerciseId
        val vm = track(app.goalsVm())
        vm.add("10 treinos", GoalTypes.WORKOUT_COUNT, 10.0, "treinos")
        vm.add("Supino 100", GoalTypes.EXERCISE_WEIGHT, 100.0, "kg", exerciseId)

        val state = vm.state.await { it.goals.size == 2 }
        val count = state.goals.single { it.goal.type == GoalTypes.WORKOUT_COUNT }
        assertEquals(1.0, count.progress.current, 0.0)
        assertEquals(10, count.progress.percent)
        val load = state.goals.single { it.goal.type == GoalTypes.EXERCISE_WEIGHT }
        assertEquals(80.0, load.progress.current, 0.0)
        assertEquals(80, load.progress.percent)
        assertEquals(state.exercises.single { it.id == exerciseId }.name, load.exerciseName)
    }

    @Test
    fun `meta de peso corporal usa as pesagens do Health Connect`() {
        app.health.weights = listOf(BodyWeightEntry(app.clock.now - 86_400_000, 90.0), BodyWeightEntry(app.clock.now, 85.0))
        val vm = track(app.goalsVm())
        // A meta foi criada quando o peso era 90 kg; o alvo é 80 kg.
        app.clock.advanceMinutes(-60 * 12)
        vm.add("Chegar a 80", GoalTypes.BODY_WEIGHT, 80.0, "kg")
        app.idle()
        app.clock.advanceMinutes(60 * 12)
        runBlocking { app.healthImporter.refresh(backfill = true) }

        val goal = vm.state.await { it.hasBodyWeights && it.goals.isNotEmpty() }.goals.single()
        assertEquals(85.0, goal.progress.current, 0.0)
        assertEquals(50, goal.progress.percent)

        vm.delete(goal.goal.id)
        assertTrue(vm.state.await { it.goals.isEmpty() }.goals.isEmpty())
    }
}
