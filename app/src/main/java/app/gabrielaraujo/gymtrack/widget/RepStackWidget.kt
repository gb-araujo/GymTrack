package app.gabrielaraujo.gymtrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.gabrielaraujo.gymtrack.RepStackApplication
import app.gabrielaraujo.gymtrack.MainActivity
import app.gabrielaraujo.gymtrack.R
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.STREAK_WINDOW_DAYS
import app.gabrielaraujo.gymtrack.domain.computeStreak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Widget com a ofensiva e o próximo treino. Atualizado pelo app a cada mudança e periodicamente. */
open class RepStackWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext as RepStackApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.update(context, app.repository.completedSessionsSnapshot(), app.repository.plansSnapshot())
            } finally {
                pending.finish()
            }
        }
    }
}

/** Mantém o componente usado pelos widgets adicionados antes da troca de marca. */
class GymTrackWidget : RepStackWidget()

object WidgetUpdater {
    fun update(context: Context, sessions: List<WorkoutSession>, plans: List<WorkoutPlan>) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, GymTrackWidget::class.java))
        if (ids.isEmpty()) return
        val views = render(context, sessions, plans)
        manager.updateAppWidget(ids, views)
    }

    private fun render(context: Context, sessions: List<WorkoutSession>, plans: List<WorkoutPlan>): RemoteViews {
        val streak = computeStreak(sessions.filter { it.completed && !it.deleted }.map { it.startTime })
        val live = plans.filter { !it.deleted }
        val plan = live.find { it.isActive } ?: live.firstOrNull()
        val next = plan?.days?.takeIf { it.isNotEmpty() }?.let { it[plan.currentDayIndex % it.size] }

        val status = when {
            streak.lastWorkout == null -> "Faça o primeiro treino!"
            !streak.active -> "Ofensiva zerada"
            streak.daysLeft == 0 -> "Último dia para manter!"
            streak.daysLeft.toLong() == STREAK_WINDOW_DAYS -> "Treino de hoje feito ✓"
            streak.daysLeft == 1 -> "Treine até amanhã"
            else -> "Treine em até ${streak.daysLeft} dias"
        }

        return RemoteViews(context.packageName, R.layout.widget_repstack).apply {
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
