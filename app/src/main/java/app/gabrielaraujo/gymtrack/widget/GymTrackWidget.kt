package app.gabrielaraujo.gymtrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.gabrielaraujo.gymtrack.GymTrackApplication
import app.gabrielaraujo.gymtrack.MainActivity
import app.gabrielaraujo.gymtrack.R
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.statistics.STREAK_WINDOW_DAYS
import app.gabrielaraujo.gymtrack.domain.workout.activePlanOf
import app.gabrielaraujo.gymtrack.domain.statistics.computeStreak
import app.gabrielaraujo.gymtrack.domain.workout.nextDayIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Widget com a ofensiva e o próximo treino. Atualizado pelo app a cada mudança e periodicamente. */
class GymTrackWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val repos = (context.applicationContext as GymTrackApplication).container.repositories
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.update(context, repos.sessions.completedSessionsSnapshot(), repos.plans.plansSnapshot())
            } finally {
                pending.finish()
            }
        }
    }
}

object WidgetUpdater {
    fun update(context: Context, sessions: List<WorkoutSession>, plans: List<WorkoutPlan>) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, GymTrackWidget::class.java))
        if (ids.isEmpty()) return
        val views = render(context, sessions, plans)
        manager.updateAppWidget(ids, views)
    }

    private fun render(context: Context, sessions: List<WorkoutSession>, plans: List<WorkoutPlan>): RemoteViews {
        val done = sessions.filter { it.completed && !it.deleted }
        val streak = computeStreak(done.map { it.startTime })
        val plan = activePlanOf(plans)
        val next = plan?.days?.takeIf { it.isNotEmpty() }?.let { it[nextDayIndex(plan, done)] }

        val status = when {
            streak.lastWorkout == null -> "Faça o primeiro treino!"
            !streak.active -> "Ofensiva zerada"
            streak.daysLeft == 0 -> "Último dia para manter!"
            streak.daysLeft.toLong() == STREAK_WINDOW_DAYS -> "Treino de hoje feito ✓"
            streak.daysLeft == 1 -> "Treine até amanhã"
            else -> "Treine em até ${streak.daysLeft} dias"
        }

        return RemoteViews(context.packageName, R.layout.widget_gymtrack).apply {
            setTextViewText(R.id.widget_streak, streak.count.toString())
            setTextViewText(R.id.widget_status, status)
            setTextViewText(
                R.id.widget_next,
                if (next != null) "Próximo: ${next.label} — ${next.name.ifBlank { "Treino ${next.label}" }}" else "Nenhuma ficha ativa",
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setOnClickPendingIntent(R.id.widget_root, open)
        }
    }
}
