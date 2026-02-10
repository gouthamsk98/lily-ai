package com.budgettracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DailyReminderBanner(onAddExpense: () -> Unit, onZeroExpense: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "⚠️ Daily Expense Entry Required",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF92400E),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "You haven't logged any expenses today.",
                color = Color(0xFF78350F),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddExpense) { Text("Add Expense") }
                OutlinedButton(onClick = onZeroExpense) { Text("No Expenses Today") }
            }
        }
    }
}
