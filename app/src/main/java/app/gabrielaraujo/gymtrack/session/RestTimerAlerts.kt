package app.gabrielaraujo.gymtrack.session

/** Avisos do timer de descanso fora da tela (notificação + vibração), mesmo com o app em segundo plano. */
interface RestTimerAlerts {
    /** Agenda o aviso para [endsAt] (epoch ms), substituindo qualquer timer anterior. */
    fun schedule(endsAt: Long, exerciseName: String)
    fun cancel()

    object None : RestTimerAlerts {
        override fun schedule(endsAt: Long, exerciseName: String) = Unit
        override fun cancel() = Unit
    }
}
