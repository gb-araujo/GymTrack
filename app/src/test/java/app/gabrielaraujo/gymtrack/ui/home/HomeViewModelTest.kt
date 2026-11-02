package app.gabrielaraujo.gymtrack.ui.home

import android.app.Application
import app.gabrielaraujo.gymtrack.data.SAMPLE_PLAN_ID
import app.gabrielaraujo.gymtrack.ui.ViewModelTestBase
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
class HomeViewModelTest : ViewModelTestBase() {

    @Test
    fun `estado inicial com a ficha exemplo e sem treinos`() {
        val state = track(app.homeVm()).state.await { it.loaded }
        assertEquals(SAMPLE_PLAN_ID, state.activePlan!!.id)
        assertEquals(0, state.nextDayIndex)
        assertNull(state.lastSession)
        assertEquals(0, state.totalWorkouts)
        assertEquals(0, state.streak.count)
    }

    @Test
    fun `cada treino soma um na ofensiva e avanca o proximo treino`() {
        val first = doWorkout()
        val vm = track(app.homeVm())
        var state = vm.state.await { it.totalWorkouts == 1 }
        assertEquals(1, state.streak.count)
        assertEquals(1, state.nextDayIndex)
        assertEquals(first.id, state.lastSession!!.id)

        app.clock.advanceMinutes(60)
        doWorkout(dayIndex = 1)
        state = vm.state.await { it.totalWorkouts == 2 }
        assertEquals(2, state.streak.count)
        assertEquals(2, state.nextDayIndex)
        assertEquals(2, state.workoutsThisMonth)
    }

    @Test
    fun `recordes do inicio mostram os tres primeiros e a contagem total`() {
        doWorkout(firstWeight = 100.0)
        val state = track(app.homeVm()).state.await { it.recordCount > 0 }
        assertTrue(state.topRecords.size <= 3)
        assertTrue(state.recordCount >= state.topRecords.size)
    }
}
