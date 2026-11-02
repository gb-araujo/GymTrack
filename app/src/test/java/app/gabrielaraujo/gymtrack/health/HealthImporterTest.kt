package app.gabrielaraujo.gymtrack.health

import android.app.Application
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.SAMPLE_PLAN_ID
import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.data.repository.Repositories
import app.gabrielaraujo.gymtrack.domain.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.WorkoutHealthMetrics
import app.gabrielaraujo.gymtrack.testing.FakeHealthData
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HealthImporterTest {
    private lateinit var db: GymTrackDatabase
    private lateinit var repos: Repositories
    private val clock = TestClock()
    private val health = FakeHealthData()

    @Before
    fun setUp() {
        db = inMemoryDb()
        repos = Repositories(db, clock = clock)
    }

    @After
    fun tearDown() = db.close()

    private fun completeWorkout(): WorkoutSession = runBlocking {
        val session = WorkoutSession(
            id = newId(), workoutPlanId = SAMPLE_PLAN_ID, workoutDayId = "day_a", workoutDayLabel = "A", workoutDayName = "Peito",
            exercises = listOf(
                SessionExercise(
                    id = newId(), exerciseId = "supino_reto", workoutExerciseId = "we", name = "Supino", order = 0,
                    sets = listOf(SetRecord(newId(), 60.0, 10, completed = true)),
                ),
            ),
            startTime = clock.now,
        )
        clock.advanceMinutes(45)
        repos.sessions.completeSession(session)
    }

    @Test
    fun `frequencia cardiaca e calorias do relogio entram no treino`() = runBlocking {
        health.metrics = WorkoutHealthMetrics(avgHeartRate = 128, maxHeartRate = 171, calories = 315.0)
        val importer = HealthImporter(health, repos.sessions, clock)
        val done = completeWorkout()

        val enriched = importer.enrich(done)!!
        assertEquals(171, enriched.maxHeartRate)
        assertEquals(315.0, enriched.calories!!, 0.0)
        // Entra na fila de sincronização sem mexer nos totais nem nos recordes.
        assertTrue(enriched.dirty)
        assertEquals(done.totalVolume, enriched.totalVolume)
        assertEquals(done.prsAchieved, enriched.prsAchieved)
    }

    @Test
    fun `dados do relogio que chegam depois sao buscados ao voltar ao app`() = runBlocking {
        val importer = HealthImporter(health, repos.sessions, clock)
        val done = completeWorkout()
        importer.refresh()
        assertNull(repos.sessions.getSession(done.id)!!.avgHeartRate)

        // O Samsung Health sincronizou depois; voltar logo em seguida não consulta de novo...
        health.metrics = WorkoutHealthMetrics(avgHeartRate = 120)
        health.weights = listOf(BodyWeightEntry(clock.now - 86_400_000, 81.2), BodyWeightEntry(clock.now, 80.6))
        importer.refresh()
        assertNull(repos.sessions.getSession(done.id)!!.avgHeartRate)

        // ...mas depois do intervalo mínimo, sim.
        clock.advanceMinutes(10)
        importer.refresh()
        assertEquals(120, repos.sessions.getSession(done.id)!!.avgHeartRate)
        assertEquals(80.6, importer.bodyWeights.first().last().kg, 0.0)
    }

    @Test
    fun `importacao desligada esconde as pesagens`() = runBlocking {
        health.weights = listOf(BodyWeightEntry(clock.now, 80.0))
        val importer = HealthImporter(health, repos.sessions, clock)
        importer.refresh()
        assertEquals(1, importer.bodyWeights.first().size)

        health.importEnabled.value = false
        assertTrue(importer.bodyWeights.first().isEmpty())
        clock.advanceMinutes(10)
        importer.refresh()
        health.importEnabled.value = true
        assertTrue(importer.bodyWeights.first().isEmpty())
    }
}
