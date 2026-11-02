package app.gabrielaraujo.gymtrack.ui.workout

import android.app.Application
import app.gabrielaraujo.gymtrack.data.SAMPLE_PLAN_ID
import app.gabrielaraujo.gymtrack.domain.WorkoutHealthMetrics
import app.gabrielaraujo.gymtrack.session.ActiveWorkoutManager
import app.gabrielaraujo.gymtrack.ui.ViewModelTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class WorkoutViewModelTest : ViewModelTestBase() {

    @Test
    fun `estado inicial sem treino em andamento`() {
        val vm = track(app.workoutVm())
        val state = vm.state.await { it.exercises.isNotEmpty() }
        assertNull(state.active)
        assertNull(state.plan)
    }

    @Test
    fun `treino concluido vai para o historico e para o Health Connect`() {
        val done = doWorkout()
        assertTrue(done.completed)
        assertEquals(45, done.duration)
        assertTrue(runBlocking { app.repos.sessions.getSession(done.id) }!!.completed)
        assertEquals(done.id, eventually { app.exporter.exported.singleOrNull() }.id)
    }

    @Test
    fun `treino mostra a ficha dele para descanso e repeticoes`() {
        val home = track(app.homeVm())
        val vm = track(app.workoutVm())
        home.startWhenLoaded(1)
        val state = vm.state.await { it.active != null && it.plan != null }
        assertEquals(SAMPLE_PLAN_ID, state.plan!!.id)
        assertEquals("day_b", state.active!!.session.workoutDayId)
    }

    @Test
    fun `abandonar treino em andamento descarta so ele`() {
        val kept = doWorkout()
        val home = track(app.homeVm())
        val vm = track(app.workoutVm())
        home.startWhenLoaded()
        val active = vm.state.await { it.active != null }.active!!
        vm.abandon()
        vm.state.await { it.active == null }
        app.idle()
        assertNull(runBlocking { app.repos.sessions.getSession(active.session.id) })
        assertTrue(runBlocking { app.repos.sessions.getSession(kept.id) }!!.completed)
    }

    @Test
    fun `treino em andamento e retomado quando o processo volta`() {
        val home = track(app.homeVm())
        val vm = track(app.workoutVm())
        home.startWhenLoaded()
        val active = vm.state.await { it.active != null }.active!!
        vm.updateSet(0, 0) { it.copy(weight = 77.5) }
        vm.startRest(90, active.session.exercises.first().id)
        app.idle()

        // Processo novo: outro manager sobre o mesmo banco (e o mesmo armazenamento do descanso).
        val reopened = ActiveWorkoutManager(app.repos.sessions, app.scope, restStore = app.restStore, clock = app.clock)
        runBlocking { reopened.restore() }
        val restored = reopened.active.value!!
        assertEquals(active.session.id, restored.session.id)
        assertEquals(77.5, restored.session.exercises[0].sets[0].weight, 0.0)
        assertEquals(app.clock.now + 90_000, restored.restEndsAt)
    }

    @Test
    fun `timer de descanso guarda o fim e pode ser ajustado`() {
        val home = track(app.homeVm())
        val vm = track(app.workoutVm())
        home.startWhenLoaded()
        val active = vm.state.await { it.active != null }.active!!
        vm.startRest(90, active.session.exercises.first().id)
        vm.state.await { it.active?.restEndsAt == app.clock.now + 90_000 }
        vm.adjustRest(15)
        vm.state.await { it.active?.restEndsAt == app.clock.now + 105_000 }
        vm.stopRest()
        vm.state.await { it.active != null && it.active.restEndsAt == null }
    }

    @Test
    fun `resumo mostra as medidas do relogio que chegam depois de concluir`() {
        app.health.metrics = WorkoutHealthMetrics(avgHeartRate = 128, maxHeartRate = 171, calories = 315.0)
        val home = track(app.homeVm())
        val vm = track(app.workoutVm())
        home.startWhenLoaded()
        vm.state.await { it.active != null }
        vm.complete()
        val summary = vm.state.await { it.active?.session?.avgHeartRate != null }.active!!.session
        assertTrue(summary.completed)
        assertEquals(171, summary.maxHeartRate)
        assertEquals(315.0, summary.calories!!, 0.0)
    }

    @Test
    fun `aceitar a progressao grava a carga inicial na ficha do treino`() {
        val home = track(app.homeVm())
        val vm = track(app.workoutVm())
        home.startWhenLoaded()
        val exercise = vm.state.await { it.active != null }.active!!.session.exercises.first()
        vm.acceptProgression(exercise.workoutExerciseId, 82.5)
        app.idle()
        val plan = runBlocking { app.repos.plans.getPlan(SAMPLE_PLAN_ID) }!!
        val updated = plan.days.flatMap { it.exercises }.single { it.id == exercise.workoutExerciseId }
        assertEquals(82.5, updated.initialWeight, 0.0)
    }

    @Test
    fun `substituir exercicio usa o nome do catalogo`() {
        val home = track(app.homeVm())
        val vm = track(app.workoutVm())
        home.startWhenLoaded()
        val state = vm.state.await { it.active != null && it.exercises.isNotEmpty() }
        val original = state.active!!.session.exercises.first()
        val other = state.exercises.first { it.id != original.exerciseId }
        vm.substituteExercise(0, other.id)
        val replaced = vm.state.await { it.active?.session?.exercises?.first()?.exerciseId == other.id }.active!!.session.exercises.first()
        assertEquals(other.name, replaced.name)
        assertEquals(original.exerciseId, replaced.substitutedFrom)
    }
}
