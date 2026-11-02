package app.gabrielaraujo.gymtrack.ui.home

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.ui.account.AccountDialog
import app.gabrielaraujo.gymtrack.ui.account.AccountViewModel
import app.gabrielaraujo.gymtrack.ui.account.SignOutConfirmDialog
import app.gabrielaraujo.gymtrack.data.exerciseDisplayName
import app.gabrielaraujo.gymtrack.ui.components.SectionLabel
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.components.GymTrackCard
import app.gabrielaraujo.gymtrack.ui.components.SectionLabel
import app.gabrielaraujo.gymtrack.ui.components.StreakBadge
import app.gabrielaraujo.gymtrack.ui.components.StreakBar
import app.gabrielaraujo.gymtrack.ui.components.SyncIndicator
import app.gabrielaraujo.gymtrack.ui.formatDuration
import app.gabrielaraujo.gymtrack.ui.theme.TC
import app.gabrielaraujo.gymtrack.ui.timeAgo

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    account: AccountViewModel,
    onGoPlans: () -> Unit,
    onGoProgress: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accountState by account.state.collectAsStateWithLifecycle()
    val user = accountState.user
    val plan = state.activePlan
    val nextDay = plan?.days?.getOrNull(state.nextDayIndex)
    val lastSession = state.lastSession
    val prs = state.topRecords
    val byId = state.exercisesById
    var showAccount by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }

    // Avisos da conta (ex.: resultado do backup), mesmo que o diálogo já tenha fechado.
    val context = LocalContext.current
    LaunchedEffect(accountState.message) {
        accountState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            account.messageShown()
        }
    }

    Box(Modifier.fillMaxWidth()) {
    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (user != null) SyncIndicator(accountState.syncStatus, accountState.pendingSync, onClick = { showAccount = true })
                Spacer(Modifier.width(8.dp))
                val label = user?.let { it.displayName ?: it.email } ?: "Entrar"
                Text(
                    label.let { if (it.length > 16) it.take(15) + "…" else it },
                    color = TC.Slate300, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(TC.Slate900).clickable { showAccount = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Bem-vindo de volta", color = TC.Slate400, fontSize = 14.sp)
                    Text("GymTrack", color = TC.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                StreakBadge(state.streak)
            }
        }

        item { StreakBar(state.streak) }

        item {
            if (nextDay != null) {
                GymTrackCard {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SectionLabel("Próximo Treino", color = TC.Indigo400)
                            nextDay.estimatedMinutes?.let { Text("~$it min", color = TC.Slate500, fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(nextDay.label, color = TC.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                            Text("— ${nextDay.name}", color = TC.Slate300, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text("${nextDay.exercises.size} exercícios · ${plan.name}", color = TC.Slate500, fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        GymTrackButton(
                            "Iniciar Treino",
                            onClick = { vm.startSession() },
                            modifier = Modifier.fillMaxWidth(),
                            leading = { Icon(Icons.Default.PlayArrow, null, tint = TC.White, modifier = Modifier.size(20.dp)) },
                        )
                        if (plan.days.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Escolher outro treino",
                                color = TC.Indigo400, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showDayPicker = true }.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            } else {
                GymTrackCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nenhuma ficha ativa", color = TC.Slate400)
                        Spacer(Modifier.height(12.dp))
                        GymTrackButton("Criar Ficha", onClick = onGoPlans)
                    }
                }
            }
        }

        if (lastSession != null) {
            item {
                GymTrackCard(onClick = { onOpenSession(lastSession.id) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Último Treino", color = TC.Slate500)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(lastSession.workoutDayLabel, color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                Text("— ${lastSession.workoutDayName}", color = TC.Slate300, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
                            }
                            val dur = if (lastSession.duration != null) " · ${formatDuration(lastSession.duration)}" else ""
                            Text(
                                "${timeAgo(lastSession.startTime)} · ${lastSession.exercises.size} exercícios$dur",
                                color = TC.Slate500, fontSize = 13.sp,
                            )
                            if (lastSession.prsAchieved.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                                    Icon(Icons.Default.EmojiEvents, null, tint = TC.Yellow400, modifier = Modifier.size(13.dp))
                                    Text("${lastSession.prsAchieved.size} PR${if (lastSession.prsAchieved.size > 1) "s" else ""}!", color = TC.Yellow400, fontSize = 12.sp)
                                }
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TC.Slate600)
                    }
                }
            }
        }

        if (prs.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.EmojiEvents, null, tint = TC.Yellow400, modifier = Modifier.size(14.dp))
                        SectionLabel("Recordes Pessoais")
                    }
                    Text("Ver todos", color = TC.Indigo400, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onGoProgress() }.padding(4.dp))
                }
            }
            items(prs.toList()) { pr ->
                GymTrackCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(exerciseDisplayName(pr.exerciseId, byId, pr.exerciseName), color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(timeAgo(pr.date), color = TC.Slate500, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (pr.weight > 0) "${app.gabrielaraujo.gymtrack.ui.formatWeight(pr.weight)} kg" else "Peso Corp.", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text("× ${pr.reps} reps", color = TC.Slate500, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (state.totalWorkouts > 0) {
            item {
                GymTrackCard {
                    Column {
                        Text("Estatísticas", color = TC.Slate300, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCol(state.totalWorkouts.toString(), "Total")
                            StatCol(state.workoutsThisMonth.toString(), "Este mês")
                            StatCol(state.recordCount.toString(), "PRs")
                        }
                    }
                }
            }
        }
    }

    if (showAccount) AccountDialog(account) { showAccount = false }
    accountState.signOutPending?.let { pending ->
        SignOutConfirmDialog(pending, onConfirm = { account.confirmSignOut() }, onCancel = { account.cancelSignOut() })
    }
    if (showDayPicker && plan != null) {
        DayPickerDialog(
            plan = plan,
            nextIndex = state.nextDayIndex,
            onPick = { index -> showDayPicker = false; vm.startSession(dayIndex = index) },
            onClose = { showDayPicker = false },
        )
    }
    }
}

@Composable
private fun DayPickerDialog(
    plan: app.gabrielaraujo.gymtrack.data.WorkoutPlan,
    nextIndex: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(20.dp)) {
            Text("Escolher treino", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("O próximo sugerido continua sendo ${plan.days.getOrNull(nextIndex)?.label ?: "—"}.", color = TC.Slate500, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            plan.days.forEachIndexed { index, day ->
                val isNext = index == nextIndex
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (isNext) TC.Indigo600.copy(alpha = 0.25f) else TC.Slate800)
                        .clickable { onPick(index) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(day.label, color = TC.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Column {
                            Text(day.name.ifBlank { "Treino ${day.label}" }, color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${day.exercises.size} exercícios", color = TC.Slate500, fontSize = 12.sp)
                        }
                    }
                    if (isNext) Text("PRÓXIMO", color = TC.Indigo400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatCol(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(label, color = TC.Slate500, fontSize = 12.sp)
    }
}
