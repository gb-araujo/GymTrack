package app.gabrielaraujo.gymtrack.health

import app.gabrielaraujo.gymtrack.data.WorkoutSession

/** Destino externo para treinos concluídos (ex.: Health Connect). Nunca lança exceção. */
interface WorkoutExporter {
    /** Grava o treino ou, se já foi enviado antes, atualiza com a versão editada. */
    suspend fun export(session: WorkoutSession)

    /** Apaga um treino enviado antes (ex.: excluído do histórico). */
    suspend fun remove(sessionId: String)

    object None : WorkoutExporter {
        override suspend fun export(session: WorkoutSession) = Unit
        override suspend fun remove(sessionId: String) = Unit
    }
}
