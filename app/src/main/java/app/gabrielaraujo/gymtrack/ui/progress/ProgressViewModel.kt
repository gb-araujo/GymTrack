package app.gabrielaraujo.gymtrack.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.repository.PersonalRecordRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutSessionRepository
import app.gabrielaraujo.gymtrack.domain.health.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.progression.bestRecordsByExercise
import app.gabrielaraujo.gymtrack.domain.statistics.loadHistory
import app.gabrielaraujo.gymtrack.domain.statistics.weeklyCounts
import app.gabrielaraujo.gymtrack.domain.statistics.workoutsInMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    /** Recordes, do mais recente para o mais antigo. */
    val records: List<PersonalRecord> = emptyList(),
    val totalWorkouts: Int = 0,
    val workoutsThisMonth: Int = 0,
    /** Evolução de carga (maior carga por treino, cronológica) de cada exercício com recorde. */
    val loadHistory: Map<String, List<Double>> = emptyMap(),
    /** Volume dos últimos treinos, em toneladas (o mais antigo primeiro). */
    val recentVolumesTons: List<Double> = emptyList(),
    /** Treinos por semana nas últimas semanas (a mais antiga primeiro). */
    val weeklyCounts: List<Int> = emptyList(),
    val bodyWeights: List<BodyWeightEntry> = emptyList(),
)

/** Progresso: recordes, volume, frequência e peso corporal. */
class ProgressViewModel(
    sessions: WorkoutSessionRepository,
    records: PersonalRecordRepository,
    bodyWeights: Flow<List<BodyWeightEntry>>,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val state: StateFlow<ProgressUiState> =
        combine(sessions.completedSessions, records.personalRecords, bodyWeights) { done, recordList, weights ->
            val best = bestRecordsByExercise(recordList).values.sortedByDescending { it.date }
            val times = done.map { it.startTime }
            ProgressUiState(
                records = best,
                totalWorkouts = done.size,
                workoutsThisMonth = workoutsInMonth(times),
                loadHistory = best.associate { it.exerciseId to loadHistory(done, it.exerciseId) },
                recentVolumesTons = done.take(RECENT_SESSIONS).reversed().map { (it.totalVolume ?: 0.0) / 1000 },
                weeklyCounts = weeklyCounts(times, now = clock()),
                bodyWeights = weights,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    private companion object {
        const val RECENT_SESSIONS = 10
    }
}
