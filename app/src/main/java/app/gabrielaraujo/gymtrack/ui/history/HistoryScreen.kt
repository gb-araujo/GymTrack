package app.gabrielaraujo.gymtrack.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.domain.HistoryFilter
import app.gabrielaraujo.gymtrack.domain.ReportPeriod
import app.gabrielaraujo.gymtrack.domain.filterSessions
import app.gabrielaraujo.gymtrack.ui.components.Chip
import app.gabrielaraujo.gymtrack.ui.components.ChipRow
import app.gabrielaraujo.gymtrack.ui.components.GymTrackCard
import app.gabrielaraujo.gymtrack.ui.components.Chip
import app.gabrielaraujo.gymtrack.ui.components.ChipRow
import app.gabrielaraujo.gymtrack.ui.components.SearchField
import app.gabrielaraujo.gymtrack.ui.progress.EmptyHint
import app.gabrielaraujo.gymtrack.ui.components.SectionLabel
import app.gabrielaraujo.gymtrack.ui.formatDuration
import app.gabrielaraujo.gymtrack.ui.formatMonthYear
import app.gabrielaraujo.gymtrack.ui.formatShortDateTime
import app.gabrielaraujo.gymtrack.ui.theme.TC
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(vm: HistoryViewModel, onOpenSession: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(ReportPeriod.ALL) }
    var planId by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyRecords by rememberSaveable { mutableStateOf(false) }
    val filter = HistoryFilter(query, period, planId, onlyRecords)

    // Fichas que aparecem no histórico; as excluídas continuam filtráveis.
    val planOptions = remember(state.sessions, state.plans) {
        state.sessions.map { it.workoutPlanId }.distinct().map { id -> id to (state.plans.find { it.id == id }?.name ?: "Ficha excluída") }
    }
    val filtered = remember(state.sessions, filter) { filterSessions(state.sessions, filter, LocalDate.now(), ZoneId.systemDefault()) }
    val grouped = filtered.groupBy { formatMonthYear(it.startTime) }

    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Histórico", color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) }

        if (state.sessions.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CalendarMonth, null, tint = TC.Slate700, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nenhum treino registrado ainda", color = TC.Slate400)
                    Text("Complete seu primeiro treino para ver o histórico", color = TC.Slate600, fontSize = 13.sp)
                }
            }
            return@LazyColumn
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, { query = it }, placeholder = "Buscar treino ou exercício...")
                ChipRow(ReportPeriod.entries, period, { if (it == ReportPeriod.ALL) "Todo o período" else it.label }, { period = it })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Chip("🏆 Com recorde", onlyRecords) { onlyRecords = !onlyRecords } }
                    if (planOptions.size > 1) {
                        item { Chip("Todas as fichas", planId == null) { planId = null } }
                        items(planOptions) { (id, name) -> Chip(name, planId == id) { planId = if (planId == id) null else id } }
                    }
                }
            }
        }

        if (filter.isActive) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${filtered.size} de ${state.sessions.size} treinos", color = TC.Slate500, fontSize = 12.sp)
                    Text(
                        "Limpar filtros", color = TC.Indigo400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { query = ""; period = ReportPeriod.ALL; planId = null; onlyRecords = false }.padding(4.dp),
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item { EmptyHint("Nenhum treino com esses filtros.") }
        }

        grouped.forEach { (month, monthSessions) ->
            item {
                SectionLabel("$month · ${monthSessions.size} treino${if (monthSessions.size != 1) "s" else ""}", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            items(monthSessions, key = { it.id }) { session ->
                GymTrackCard(onClick = { onOpenSession(session.id) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(session.workoutDayLabel, color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Text("— ${session.workoutDayName}", color = TC.Slate300, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                if (session.prsAchieved.isNotEmpty()) Icon(Icons.Default.EmojiEvents, "Recorde pessoal", tint = TC.Yellow400, modifier = Modifier.size(13.dp))
                            }
                            val parts = buildList {
                                add(formatShortDateTime(session.startTime))
                                session.duration?.let { add(formatDuration(it)) }
                                add("${session.exercises.size} ex.")
                                session.totalSets?.let { add("$it séries") }
                            }
                            Text(parts.joinToString(" · "), color = TC.Slate500, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TC.Slate700)
                    }
                }
            }
        }
    }
}

