package app.gabrielaraujo.gymtrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.ui.RepStackViewModel
import app.gabrielaraujo.gymtrack.ui.components.SectionLabel
import app.gabrielaraujo.gymtrack.ui.components.RepStackCard
import app.gabrielaraujo.gymtrack.ui.formatDuration
import app.gabrielaraujo.gymtrack.ui.formatMonthYear
import app.gabrielaraujo.gymtrack.ui.formatShortDateTime
import app.gabrielaraujo.gymtrack.ui.formatVolume
import app.gabrielaraujo.gymtrack.ui.theme.TC

@Composable
fun HistoryScreen(vm: RepStackViewModel, onOpenSession: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val grouped = state.sessions.groupBy { formatMonthYear(it.startTime) }

    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Histórico", color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp)) }

        if (state.sessions.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CalendarMonth, null, tint = TC.Slate700, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nenhum treino registrado ainda", color = TC.Slate400)
                    Text("Complete seu primeiro treino para ver o histórico", color = TC.Slate600, fontSize = 13.sp)
                }
            }
        } else {
            grouped.forEach { (month, monthSessions) ->
                item {
                    SectionLabel("$month · ${monthSessions.size} treino${if (monthSessions.size != 1) "s" else ""}", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                monthSessions.forEach { session ->
                    item {
                        RepStackCard(onClick = { onOpenSession(session.id) }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(session.workoutDayLabel, color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                        Text("— ${session.workoutDayName}", color = TC.Slate300, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        if (session.prsAchieved.isNotEmpty()) Icon(Icons.Default.EmojiEvents, null, tint = TC.Yellow400, modifier = Modifier.size(13.dp))
                                    }
                                    val parts = buildList {
                                        add(formatShortDateTime(session.startTime))
                                        session.duration?.let { add(formatDuration(it)) }
                                        add("${session.exercises.size} ex.")
                                        session.totalSets?.let { add("$it séries") }
                                    }
                                    Text(parts.joinToString(" · "), color = TC.Slate500, fontSize = 12.sp)
                                    if ((session.totalVolume ?: 0.0) > 0) Text("Volume: ${formatVolume(session.totalVolume)}", color = TC.Slate600, fontSize = 12.sp)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TC.Slate700)
                            }
                        }
                    }
                }
            }
        }
    }
}
