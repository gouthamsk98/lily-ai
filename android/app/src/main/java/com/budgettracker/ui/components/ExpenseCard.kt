package com.budgettracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgettracker.domain.model.Expense
import com.budgettracker.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ExpenseCard(expense: Expense, onDelete: (() -> Unit)? = null) {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    val categoryColor = when (expense.category.name.lowercase()) {
        "food" -> FoodColor
        "entertainment" -> EntertainmentColor
        "travel" -> TravelColor
        "bills" -> BillsColor
        "shopping" -> ShoppingColor
        else -> OtherColor
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = categoryColor,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    expense.category.displayName(),
                    color = OnPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fmt.format(expense.amount), fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(expense.expenseDate)
                        expense.note?.let { append(" — $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
            }
            onDelete?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.Delete, "Delete", tint = Error)
                }
            }
        }
    }
}
