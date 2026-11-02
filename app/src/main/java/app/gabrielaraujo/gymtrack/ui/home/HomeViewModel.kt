package app.gabrielaraujo.gymtrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.repository.ExerciseRepository
import app.gabrielaraujo.gymtrack.data.repository.PersonalRecordRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutPlanRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutSessionRepository
import app.gabrielaraujo.gymtrack.domain.StreakInfo
import app.gabrielaraujo.gymtrack.domain.activePlanOf
import app.gabrielaraujo.gymtrack.domain.bestRecordsByExercise
import app.gabrielaraujo.gymtrack.domain.computeStreak
import app.gabrielaraujo.gymtrack.domain.nextDayIndex
import app.gabrielaraujo.gymtrack.domain.workoutsInMonth
import app.gabrielaraujo.gymtrack.session.ActiveWorkoutManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loaded: Boolean = false,
    val activePlan: WorkoutPlan? = null,
    /** Índice do próximo treino sugerido da ficha ativa (derivado do histórico). */
    val nextDayIndex: Int = 0,
    val lastSession: WorkoutSession? = null,
    val topRecords: List<PersonalRecord> = emptyList(),
    val recordCount: Int = 0,
    val streak: StreakInfo = computeStreak(emptyList()),
    val totalWorkouts: Int = 0,
    val workoutsThisMonth: Int = 0,
)

/** Início: próximo treino, ofensiva, último treino, recordes e estatísticas. */
class HomeViewModel(
    plans: WorkoutPlanRepository,
    sessions: WorkoutSessionRepository,
    records: PersonalRecordRepository,
    private val exercises: ExerciseRepository,
    private val workout: ActiveWorkoutManager,
) : ViewModel() {
    val state: StateFlow<HomeUiState> =
        combine(plans.plans, sessions.completedSessions, records.personalRecords) { planList, done, recordList ->
            val activePlan = activePlanOf(planList)
            val best = bestRecordsByExercise(recordList)
            val times = done.map { it.startTime }
            HomeUiState(
                loaded = true,
                activePlan = activePlan,
                nextDayIndex = activePlan?.let { nextDayIndex(it, done) } ?: 0,
                lastSession = done.firstOrNull(),
                topRecords = best.values.take(3),
                recordCount = best.size,
                streak = computeStreak(times),
                totalWorkouts = done.size,
                workoutsThisMonth = workoutsInMonth(times),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Começa o treino do dia [dayIndex] da ficha ativa, ou o próximo sugerido. */
    fun startSession(dayIndex: Int? = null) {
        val current = state.value
        val plan = current.activePlan ?: return
        viewModelScope.launch {
            val names = exercises.exercises.first().associate { it.id to it.name }
            workout.start(plan, dayIndex ?: current.nextDayIndex, names::get)
        }
    }
}
