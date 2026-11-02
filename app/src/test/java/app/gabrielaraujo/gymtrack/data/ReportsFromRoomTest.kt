package app.gabrielaraujo.gymtrack.data

import android.app.Application
import app.gabrielaraujo.gymtrack.domain.ExerciseMetric
import app.gabrielaraujo.gymtrack.domain.ReportPeriod
import app.gabrielaraujo.gymtrack.domain.averageRestSeconds
import app.gabrielaraujo.gymtrack.domain.consistencyReport
import app.gabrielaraujo.gymtrack.domain.exerciseHistory
import app.gabrielaraujo.gymtrack.domain.metric
import app.gabrielaraujo.gymtrack.domain.recordProgression
import app.gabrielaraujo.gymtrack.domain.progression.sessionTotals
import app.gabrielaraujo.gymtrack.data.repository.PersonalRecordRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutSessionRepository
import app.gabrielaraujo.gymtrack.testing.TestClock
import app.gabrielaraujo.gymtrack.testing.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.ZoneId

/** Os relatórios calculados a partir do que o Room realmente grava e devolve (séries em JSON). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ReportsFromRoomTest {
    private lateinit var db: GymTrackDatabase
    private lateinit var repo: WorkoutSessionRepository
    private lateinit var records: PersonalRecordRepository
    private val clock = TestClock()

    @Before
    fun setUp() {
        db = inMemoryDb()
        records = PersonalRecordRepository(db, clock)
        repo = WorkoutSessionRepository(db, records, clock = clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun train(vararg sets: SetRecord) {
        val s = WorkoutSession(
            id = newId(), workoutPlanId = "sample_hipertrofia", workoutDayId = "day_a", workoutDayLabel = "A", workoutDayName = "Peito",
            exercises = listOf(SessionExercise(newId(), "supino_reto", "we", "Supino Reto", sets.toList(), order = 0)),
            startTime = clock.now,
        )
        repo.saveSessionInProgress(s)
        clock.advanceMinutes(60)
        repo.completeSession(s)
        clock.advanceDays(3)
    }

    @Test
    fun `evolucao do exercicio a partir das sessoes gravadas`() = runBlocking {
        val start = clock.now
        train(SetRecord(newId(), 60.0, 10, completed = true, timestamp = start), SetRecord(newId(), 60.0, 8, completed = true, timestamp = start + 120_000))
        train(SetRecord(newId(), 62.5, 8, completed = true), SetRecord(newId(), 70.0, 3, completed = false))
        train(SetRecord(newId(), 65.0, 6, completed = true))

        val sessions = repo.completedSessions.first()
        val history = exerciseHistory(sessions, "supino_reto")

        assertEquals(listOf(60.0, 62.5, 65.0), history.map { it.metric(ExerciseMetric.BEST_SET) })
        assertEquals(1, history[1].skippedSets)
        // O horário das séries sobrevive à gravação (JSON) e permite calcular o descanso.
        assertEquals(120L, averageRestSeconds(history[0].sets))

        // A progressão termina no recorde que o repositório gravou.
        val record = db.dao().getPersonalRecord("supino_reto")!!
        val last = recordProgression(history).last()
        assertEquals(record.weight, last.weight, 0.0)
        assertEquals(record.reps, last.reps)

        // Volume recalculado bate com o total gravado na conclusão do treino.
        sessions.forEach { assertEquals(it.totalVolume!!, sessionTotals(it.exercises).volume, 1e-9) }

        val zone = ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(clock.now).atZone(zone).toLocalDate()
        assertEquals(3, consistencyReport(sessions.map { it.startTime }, ReportPeriod.DAYS_30, today, zone).workouts)
    }

    @Test
    fun `treino excluido sai dos relatorios`() = runBlocking {
        train(SetRecord(newId(), 60.0, 10, completed = true))
        train(SetRecord(newId(), 80.0, 10, completed = true))
        val heavy = repo.completedSessions.first().maxBy { it.startTime }
        repo.deleteCompletedSession(heavy.id)

        val history = exerciseHistory(repo.completedSessions.first(), "supino_reto")
        assertEquals(listOf(60.0), history.map { it.topWeight })
        assertEquals(60.0, db.dao().getPersonalRecord("supino_reto")!!.weight, 0.0)
    }
}
