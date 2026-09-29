package app.gabrielaraujo.gymtrack.health

import app.gabrielaraujo.gymtrack.data.WorkoutSession

/** Destino externo para treinos concluídos (ex.: Health Connect). Nunca lança exceção. */
interface WorkoutExporter {
    suspend fun export(session: WorkoutSession)

    object None : WorkoutExporter {
        override suspend fun export(session: WorkoutSession) = Unit
    }
}
