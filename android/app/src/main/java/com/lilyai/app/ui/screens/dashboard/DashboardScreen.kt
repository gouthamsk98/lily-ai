package com.lilyai.app.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lilyai.app.domain.model.MeetingNote
import com.lilyai.app.ui.components.DailyReminderBanner
import com.lilyai.app.ui.components.SummaryChart
import com.lilyai.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    onAddExpense: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (state.dailyStatus?.submitted == false) {
            DailyReminderBanner(
                onAddExpense = onAddExpense,
                onZeroExpense = { viewModel.submitZeroExpense() },
            )
            Spacer(Modifier.height(16.dp))
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
        } else {
            // Budget Card
            state.budget?.let { budget ->
                val dailyVal = budget.dailyBudget.toDoubleOrNull() ?: 0.0
                if (dailyVal > 0) {
                    BudgetCard(budget, currencyFormat)
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Budget Setting
            var showBudgetDialog by remember { mutableStateOf(false) }
            val budgetVal = state.budget?.dailyBudget?.toDoubleOrNull() ?: 0.0
            if (state.budget == null || budgetVal == 0.0) {
                OutlinedButton(
                    onClick = { showBudgetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Set Daily Budget Limit")
                }
                Spacer(Modifier.height(16.dp))
            } else {
                TextButton(onClick = { showBudgetDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Change Budget", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
            }

            if (showBudgetDialog) {
                BudgetSettingDialog(
                    currentBudget = budgetVal,
                    onDismiss = { showBudgetDialog = false },
                    onSave = { amount ->
                        viewModel.setBudget(amount)
                        showBudgetDialog = false
                    }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                state.dailySummary?.let {
                    SummaryCardItem("Today", currencyFormat.format(it.total), it.count, Primary, Modifier.weight(1f))
                }
                state.weeklySummary?.let {
                    SummaryCardItem("Week", currencyFormat.format(it.total), it.count, EntertainmentColor, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            state.monthlySummary?.let {
                SummaryCardItem("Month", currencyFormat.format(it.total), it.count, Secondary, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(24.dp))
            state.monthlySummary?.let {
                if (it.byCategory.isNotEmpty()) {
                    Text("This Month by Category", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    SummaryChart(it.byCategory)
                }
            }

            // Recent Meeting Notes
            if (state.recentMeetings.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Recent Meeting Notes", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                state.recentMeetings.forEach { note ->
                    MeetingNotePreviewCard(note)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MeetingNotePreviewCard(note: MeetingNote) {
    val date = note.createdAt.take(10)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(note.meetingTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(date)
                        append(" • ")
                        val m = note.durationSecs / 60
                        val s = note.durationSecs % 60
                        append(String.format("%d:%02d", m, s))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
                if (note.transcriptText != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        note.transcriptText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            val (badge, badgeColor) = when (note.transcriptionStatus) {
                "completed" -> "✓" to Primary
                "transcribing" -> "⏳" to EntertainmentColor
                "failed" -> "✗" to Error
                else -> "○" to OnSurfaceVariant
            }
            Text(badge, color = badgeColor, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SummaryCardItem(
    title: String, amount: String, count: Int,
    color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            Text(amount, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("$count expense${if (count != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun BudgetCard(
    budget: com.lilyai.app.data.remote.dto.EffectiveBudgetResponse,
    currencyFormat: NumberFormat,
) {
    val dailyBudget = budget.dailyBudget.toDoubleOrNull() ?: 0.0
    val effectiveToday = budget.effectiveBudgetToday.toDoubleOrNull() ?: 0.0
    val spentToday = budget.spentToday.toDoubleOrNull() ?: 0.0
    val remainingToday = budget.remainingToday.toDoubleOrNull() ?: 0.0
    val carriedOver = budget.carriedOver.toDoubleOrNull() ?: 0.0

    val progress = if (effectiveToday > 0)
        (spentToday / effectiveToday).toFloat().coerceIn(0f, 1.5f)
    else 0f
    val isOverBudget = remainingToday < 0
    val barColor = if (isOverBudget) Error else Primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverBudget) Error.copy(alpha = 0.08f) else Primary.copy(alpha = 0.08f)
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's Budget", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    currencyFormat.format(effectiveToday),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = barColor,
                )
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.15f),
            )

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Spent", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Text(currencyFormat.format(spentToday), fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Remaining", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Text(
                        currencyFormat.format(remainingToday),
                        fontWeight = FontWeight.Medium,
                        color = if (isOverBudget) Error else Primary,
                    )
                }
            }

            if (carriedOver != 0.0) {
                Spacer(Modifier.height(4.dp))
                val sign = if (carriedOver > 0) "+" else ""
                Text(
                    "Carryover: $sign${currencyFormat.format(carriedOver)} (base: ${currencyFormat.format(dailyBudget)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BudgetSettingDialog(
    currentBudget: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(if (currentBudget > 0) currentBudget.toLong().toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Daily Budget") },
        text = {
            Column {
                Text("Enter your daily budget limit in ₹", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = text.toDoubleOrNull()
                    if (amount != null && amount >= 0) onSave(amount)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
