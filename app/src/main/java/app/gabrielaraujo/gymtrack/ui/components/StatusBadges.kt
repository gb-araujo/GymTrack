package app.gabrielaraujo.gymtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gabrielaraujo.gymtrack.domain.STREAK_WINDOW_DAYS
import app.gabrielaraujo.gymtrack.domain.StreakInfo
import app.gabrielaraujo.gymtrack.sync.SyncStatus
import app.gabrielaraujo.gymtrack.ui.formatDate
import app.gabrielaraujo.gymtrack.ui.theme.TC
import app.gabrielaraujo.gymtrack.ui.timeAgo
import java.time.ZoneId

/** Contador da ofensiva (treinos seguidos sem ficar mais de 7 dias parado). */
@Composable
fun StreakBadge(streak: StreakInfo) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Default.LocalFireDepartment, null,
            tint = if (streak.active) TC.Orange400 else TC.Slate600,
            modifier = Modifier.size(20.dp),
        )
        Text("${streak.count}", color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("ofensiva", color = TC.Slate400, fontSize = 10.sp)
    }
}

/** Barra com os dias que restam para manter a ofensiva. */
@Composable
fun StreakBar(streak: StreakInfo) {
    if (streak.lastWorkout == null) return
    val window = STREAK_WINDOW_DAYS.toInt()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(window) { i ->
                val filled = streak.active && i < streak.daysLeft.coerceAtLeast(1)
                val color = when {
                    !filled -> TC.Slate800
                    streak.daysLeft <= 1 -> TC.Red400
                    streak.daysLeft <= 3 -> TC.Orange400
                    else -> TC.Indigo500
                }
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(color))
            }
        }
        Spacer(Modifier.height(6.dp))
        val deadline = streak.deadline
        val text = when {
            !streak.active -> "Ofensiva zerada. Treine para começar uma nova!"
            deadline == null -> ""
            streak.daysLeft == 0 -> "Hoje é o último dia para manter a ofensiva!"
            streak.daysLeft == window -> "Treino feito! Ofensiva garantida até ${formatDeadline(deadline)}."
            else -> "Treine até ${formatDeadline(deadline)} para manter a ofensiva."
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text, color = TC.Slate500, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (streak.best > 0) Text("Recorde: ${streak.best}", color = TC.Slate600, fontSize = 11.sp)
        }
    }
}

private fun formatDeadline(date: java.time.LocalDate): String =
    formatDate(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), "EEE, d/MM")

/** Ícone de nuvem com o estado da sincronização. */
@Composable
fun SyncIndicator(status: SyncStatus, pending: Int, onClick: () -> Unit) {
    val (icon, color) = when {
        status is SyncStatus.Syncing -> Icons.Default.CloudSync to TC.Indigo400
        status is SyncStatus.Error -> Icons.Default.CloudOff to TC.Red400
        pending > 0 -> Icons.Default.Cloud to TC.Orange400
        status is SyncStatus.Synced -> Icons.Default.CloudDone to TC.Emerald400
        else -> Icons.Default.Cloud to TC.Slate500
    }
    Icon(
        icon, syncStatusText(status, pending), tint = color,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(TC.Slate900).clickable { onClick() }.padding(6.dp).size(18.dp),
    )
}

fun syncStatusText(status: SyncStatus, pending: Int): String = when (status) {
    is SyncStatus.Syncing -> "Sincronizando…"
    is SyncStatus.Error -> "Falha ao sincronizar (${timeAgo(status.at)}): ${status.message}"
    is SyncStatus.Synced ->
        if (pending > 0) "$pending alteraç${if (pending == 1) "ão pendente" else "ões pendentes"}"
        else "Sincronizado ${timeAgo(status.at)}"
    SyncStatus.Idle -> if (pending > 0) "$pending alteraç${if (pending == 1) "ão pendente" else "ões pendentes"}" else "Aguardando sincronização"
}
