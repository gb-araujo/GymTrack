package app.gabrielaraujo.gymtrack.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.formatDuration
import app.gabrielaraujo.gymtrack.ui.formatVolume
import app.gabrielaraujo.gymtrack.ui.formatWeight
import app.gabrielaraujo.gymtrack.ui.theme.TC
import app.gabrielaraujo.gymtrack.ui.watchStats

@Composable
fun SessionSummaryScreen(vm: WorkoutViewModel, onFinish: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session = state.active?.session ?: return
    val prs = session.prsAchieved

    Column(
        Modifier.fillMaxSize().background(TC.Background).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("🎉", fontSize = 56.sp)
        Text("Treino Concluído!", color = TC.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Treino ${session.workoutDayLabel} — ${session.workoutDayName}", color = TC.Slate400, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStat("Duração", formatDuration(session.duration), Modifier.weight(1f))
            SummaryStat("Exercícios", session.exercises.size.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStat("Séries", (session.totalSets ?: 0).toString(), Modifier.weight(1f))
            SummaryStat("Volume", formatVolume(session.totalVolume), Modifier.weight(1f))
        }

        val watch = watchStats(session.avgHeartRate, session.maxHeartRate, session.calories)
        if (watch.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                watch.forEach { (label, value) -> SummaryStat(label, value, Modifier.weight(1f), small = watch.size > 2) }
            }
        } else if (state.healthImportEnabled) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Treinou com relógio? A frequência cardíaca e as calorias aparecem aqui e no histórico quando o Samsung Health sincronizar.",
                color = TC.Slate500, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
        }

        if (prs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Yellow500.copy(alpha = 0.12f)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.EmojiEvents, null, tint = TC.Yellow400, modifier = Modifier.size(18.dp))
                    Text("${prs.size} novo${if (prs.size > 1) "s" else ""} PR${if (prs.size > 1) "s" else ""}!", color = TC.Yellow400, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                prs.forEach { id ->
                    val ex = state.exercises.find { it.id == id }
                    val sessionEx = session.exercises.find { it.exerciseId == id }
                    val best = sessionEx?.sets?.filter { it.completed }?.maxByOrNull { it.weight }
                    val suffix = if (best != null && best.weight > 0) " · ${formatWeight(best.weight)}kg × ${best.reps}" else ""
                    Text("🏆 ${ex?.name ?: id}$suffix", color = TC.Yellow400.copy(alpha = 0.9f), fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        session.exercises.forEach { ex ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = TC.Emerald400, modifier = Modifier.size(14.dp))
                    Text(ex.name, color = TC.White, fontSize = 14.sp)
                }
                Text("${ex.sets.count { it.completed }} séries", color = TC.Slate500, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        GymTrackButton("Finalizar", onClick = onFinish, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(16.dp)) {
        Text(label, color = TC.Slate400, fontSize = 12.sp)
        Text(value, color = TC.White, fontSize = if (small) 18.sp else 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Start)
    }
}
