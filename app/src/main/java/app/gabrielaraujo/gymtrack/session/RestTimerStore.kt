package app.gabrielaraujo.gymtrack.session

import android.content.Context

/** Descanso em andamento num treino. */
data class RestState(val sessionId: String, val endsAt: Long, val exerciseId: String)

/**
 * Guarda o descanso em andamento fora da memória: se o processo for encerrado no meio do descanso,
 * a contagem volta junto com o treino. É estado passageiro, por isso fica fora do Room.
 */
interface RestTimerStore {
    fun save(rest: RestState)
    fun load(): RestState?
    fun clear()

    object None : RestTimerStore {
        override fun save(rest: RestState) = Unit
        override fun load(): RestState? = null
        override fun clear() = Unit
    }
}

class PrefsRestTimerStore(context: Context) : RestTimerStore {
    private val prefs = context.applicationContext.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)

    override fun save(rest: RestState) {
        prefs.edit()
            .putString(KEY_SESSION, rest.sessionId)
            .putLong(KEY_ENDS_AT, rest.endsAt)
            .putString(KEY_EXERCISE, rest.exerciseId)
            .apply()
    }

    override fun load(): RestState? {
        val session = prefs.getString(KEY_SESSION, null) ?: return null
        val exercise = prefs.getString(KEY_EXERCISE, null) ?: return null
        return RestState(session, prefs.getLong(KEY_ENDS_AT, 0L), exercise)
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_SESSION = "session_id"
        const val KEY_ENDS_AT = "ends_at"
        const val KEY_EXERCISE = "exercise_id"
    }
}
