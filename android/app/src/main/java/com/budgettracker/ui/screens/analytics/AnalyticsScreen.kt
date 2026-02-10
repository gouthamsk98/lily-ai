package com.budgettracker.ui.screens.analytics

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
import com.budgettracker.ui.components.SummaryChart
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Analytics", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { period ->
                FilterChip(
                    selected = state.period == period,
                    onClick = { viewModel.setPeriod(period) },
                    label = { Text(period.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            state.summary?.let { summary ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Total Spent", style = MaterialTheme.typography.bodyMedium)
                        Text(fmt.format(summary.total), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("${summary.count} expense${if (summary.count != 1) "s" else ""}")
                    }
                }
                Spacer(Modifier.height(24.dp))

                if (summary.byCategory.isNotEmpty()) {
                    Text("By Category", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    SummaryChart(summary.byCategory)
                    Spacer(Modifier.height(16.dp))

                    summary.byCategory.forEach { cat ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(cat.category.replaceFirstChar { it.uppercase() })
                            Text(fmt.format(cat.total), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
