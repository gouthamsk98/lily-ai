package com.lilyai.app.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
