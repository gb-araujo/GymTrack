package app.gabrielaraujo.gymtrack.health

import app.gabrielaraujo.gymtrack.domain.health.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.health.WorkoutHealthMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Origem de dados de saúde de outros apps (ex.: Galaxy Watch → Samsung Health → Health Connect).
 * Nunca lança exceção: sem permissão ou sem dados, devolve vazio.
 */
interface HealthDataSource {
    /** Importação ligada pelo usuário (a permissão pode ter sido revogada depois; veja [canRead]). */
    val importEnabled: StateFlow<Boolean>

    suspend fun canRead(): Boolean

    /** Frequência cardíaca e calorias medidas entre [startTime] e [endTime] (epoch ms). */
    suspend fun workoutMetrics(startTime: Long, endTime: Long): WorkoutHealthMetrics?

    /** Pesagens desde [since] (epoch ms), da mais antiga para a mais recente. */
    suspend fun bodyWeights(since: Long): List<BodyWeightEntry>

    object None : HealthDataSource {
        override val importEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override suspend fun canRead() = false
        override suspend fun workoutMetrics(startTime: Long, endTime: Long): WorkoutHealthMetrics? = null
        override suspend fun bodyWeights(since: Long): List<BodyWeightEntry> = emptyList()
    }
}
