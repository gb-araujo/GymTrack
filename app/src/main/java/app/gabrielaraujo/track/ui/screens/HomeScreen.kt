package app.gabrielaraujo.track.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.gabrielaraujo.track.ui.TrackViewModel
import app.gabrielaraujo.track.ui.components.BtnVariant
import app.gabrielaraujo.track.ui.components.SectionLabel
import app.gabrielaraujo.track.ui.components.TrackButton
import app.gabrielaraujo.track.ui.components.TrackCard
import app.gabrielaraujo.track.ui.findActivity
import app.gabrielaraujo.track.ui.formatDuration
import app.gabrielaraujo.track.ui.theme.TC
import app.gabrielaraujo.track.ui.timeAgo
import java.util.Calendar

@Composable
fun HomeScreen(
    vm: TrackViewModel,
    onStart: () -> Unit,
    onGoPlans: () -> Unit,
    onGoProgress: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val plan = state.activePlan
    val nextDay = plan?.days?.getOrNull(if (plan.days.isEmpty()) 0 else plan.currentDayIndex % plan.days.size)
    val lastSession = state.sessions.firstOrNull()
    val prs = state.personalRecords.values.take(3)
    val recentCount = state.sessions.take(7).size
    var showAccount by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
                    Text("Track", color = TC.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.LocalFireDepartment, null, tint = TC.Orange400, modifier = Modifier.size(20.dp))
                    Text("${state.totalWorkouts}", color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("treinos", color = TC.Slate400, fontSize = 10.sp)
                }
            }
        }

        if (recentCount > 0) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(7) { i ->
                        Box(
                            Modifier.weight(1f).height(8.dp).clip(CircleShape)
                                .background(if (i < recentCount) TC.Indigo500 else TC.Slate800),
                        )
                    }
                    Text("últimos treinos", color = TC.Slate500, fontSize = 11.sp)
                }
            }
        }

        item {
            if (nextDay != null && plan != null) {
                TrackCard {
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
                        TrackButton(
                            "Iniciar Treino",
                            onClick = { vm.startSession(onStarted = onStart) },
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
                TrackCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nenhuma ficha ativa", color = TC.Slate400)
                        Spacer(Modifier.height(12.dp))
                        TrackButton("Criar Ficha", onClick = onGoPlans)
                    }
                }
            }
        }

        if (lastSession != null) {
            item {
                TrackCard(onClick = { onOpenSession(lastSession.id) }) {
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
                TrackCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(pr.exerciseName, color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(timeAgo(pr.date), color = TC.Slate500, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (pr.weight > 0) "${app.gabrielaraujo.track.ui.formatWeight(pr.weight)} kg" else "Peso Corp.", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text("× ${pr.reps} reps", color = TC.Slate500, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (state.sessions.isNotEmpty()) {
            item {
                TrackCard {
                    Column {
                        Text("Estatísticas", color = TC.Slate300, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCol(state.totalWorkouts.toString(), "Total")
                            StatCol(monthCount(state.sessions.map { it.startTime }).toString(), "Este mês")
                            StatCol(state.personalRecords.size.toString(), "PRs")
                        }
                    }
                }
            }
        }
    }

    if (showAccount) AccountDialog(vm) { showAccount = false }
    if (showDayPicker && plan != null) {
        DayPickerDialog(
            plan = plan,
            onPick = { index -> showDayPicker = false; vm.startSession(dayIndex = index, onStarted = onStart) },
            onClose = { showDayPicker = false },
        )
    }
    }
}

@Composable
private fun DayPickerDialog(
    plan: app.gabrielaraujo.track.data.WorkoutPlan,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(20.dp)) {
            Text("Escolher treino", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("O próximo sugerido continua sendo ${plan.days.getOrNull(plan.currentDayIndex % plan.days.size.coerceAtLeast(1))?.label ?: "—"}.", color = TC.Slate500, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            plan.days.forEachIndexed { index, day ->
                val isNext = index == plan.currentDayIndex % plan.days.size
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
private fun AccountDialog(vm: TrackViewModel, onClose: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val signingIn by vm.signingIn.collectAsStateWithLifecycle()
    val authError by vm.authError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "Não consegui ler o arquivo.", Toast.LENGTH_LONG).show()
            } else {
                vm.importBackup(text) { count ->
                    val msg = if (count < 0) "Falha ao importar: JSON inválido." else "Importados $count registros."
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(20.dp)) {
            Text("Conta", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val u = user
            if (u != null) {
                Text(u.displayName ?: "Usuário", color = TC.White, fontWeight = FontWeight.SemiBold)
                Text(u.email ?: "", color = TC.Slate500, fontSize = 12.sp)
                Text("Sincronização ativa", color = TC.Emerald400, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(16.dp))
                TrackButton("Sair", onClick = { vm.signOut(); onClose() }, variant = BtnVariant.Ghost, modifier = Modifier.fillMaxWidth())
            } else {
                Text("Entre com o Google para sincronizar seus treinos na nuvem e acessá-los em outros dispositivos.", color = TC.Slate400, fontSize = 14.sp)
                if (authError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(authError ?: "", color = TC.Red400, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                TrackButton(
                    if (signingIn) "Entrando..." else "Entrar com Google",
                    onClick = { context.findActivity()?.let { vm.signIn(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Migração", color = TC.Slate500, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            TrackButton(
                "Importar dados (JSON)",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                variant = BtnVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
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

private fun monthCount(times: List<Long>): Int {
    val now = Calendar.getInstance()
    return times.count {
        val c = Calendar.getInstance().apply { timeInMillis = it }
        c.get(Calendar.MONTH) == now.get(Calendar.MONTH) && c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    }
}
