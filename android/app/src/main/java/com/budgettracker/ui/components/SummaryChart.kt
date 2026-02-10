package com.budgettracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.budgettracker.domain.model.CategorySummary
import com.budgettracker.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SummaryChart(categories: List<CategorySummary>) {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    val maxTotal = categories.maxOfOrNull { it.total } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { cat ->
            val color = when (cat.category.lowercase()) {
                "food" -> FoodColor
                "entertainment" -> EntertainmentColor
                "travel" -> TravelColor
                "bills" -> BillsColor
                "shopping" -> ShoppingColor
                else -> OtherColor
            }
            val fraction = (cat.total / maxTotal).toFloat().coerceIn(0.05f, 1f)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cat.category.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.width(100.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
                Text(
                    fmt.format(cat.total),
                    modifier = Modifier.width(80.dp).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
