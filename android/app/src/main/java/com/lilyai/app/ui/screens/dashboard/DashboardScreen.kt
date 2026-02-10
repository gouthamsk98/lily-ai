package com.lilyai.app.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
