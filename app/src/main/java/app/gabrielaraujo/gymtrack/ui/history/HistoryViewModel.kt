package app.gabrielaraujo.gymtrack.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.repository.ExerciseRepository
import app.gabrielaraujo.gymtrack.data.repository.PersonalRecordRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutSessionRepository
import app.gabrielaraujo.gymtrack.domain.progression.bestRecordsByExercise
import app.gabrielaraujo.gymtrack.health.WorkoutExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loaded: Boolean = false,
    /** Treinos concluídos, do mais recente para o mais antigo. */
    val sessions: List<WorkoutSession> = emptyList(),
    /** Recorde atual por exerciseId. */
    val records: Map<String, PersonalRecord> = emptyMap(),
    /** Biblioteca por id, para mostrar o nome atual de cada exercício (ver exerciseDisplayName). */
    val exercisesById: Map<String, Exercise> = emptyMap(),
)

/** Histórico e detalhe de treino: edição e exclusão (que recalculam totais e recordes). */
class HistoryViewModel(
    private val sessions: WorkoutSessionRepository,
    records: PersonalRecordRepository,
    exercises: ExerciseRepository,
    private val exporter: WorkoutExporter,
    /** Escopo para gravações que devem terminar mesmo se a tela fechar. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<HistoryUiState> =
        combine(sessions.completedSessions, records.personalRecords, exercises.exercises) { done, recordList, library ->
            HistoryUiState(
                loaded = true,
                sessions = done,
                records = bestRecordsByExercise(recordList),
                exercisesById = library.associateBy { it.id },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun update(session: WorkoutSession) = appScope.launch {
        sessions.updateCompletedSession(session)
        sessions.getSession(session.id)?.let { exporter.export(it) }
    }

    fun delete(sessionId: String) = appScope.launch {
        sessions.deleteCompletedSession(sessionId)
        exporter.remove(sessionId)
    }
}
