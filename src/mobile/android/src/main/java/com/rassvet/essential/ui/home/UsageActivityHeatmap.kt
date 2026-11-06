package com.rassvet.essential.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R

private const val HEATMAP_ROWS = 3
private const val HEATMAP_COLUMNS = 10
private val HeatmapCellSize = 12.dp
private val HeatmapCellGap = 6.dp
private val HeatmapGridWidth =
    HeatmapCellSize * HEATMAP_COLUMNS + HeatmapCellGap * (HEATMAP_COLUMNS - 1)

@Composable
fun UsageActivityHeatmap(
    dailyTotals: List<Int>,
    modifier: Modifier = Modifier,
) {
    val cells =
        dailyTotals
            .take(HEATMAP_ROWS * HEATMAP_COLUMNS)
            .let { values ->
                if (values.size >= HEATMAP_ROWS * HEATMAP_COLUMNS) {
                    values
                } else {
                    values + List(HEATMAP_ROWS * HEATMAP_COLUMNS - values.size) { 0 }
                }
            }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val cellColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.width(HeatmapGridWidth),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.empty_usage_label),
                color = labelColor,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = stringResource(R.string.empty_usage_period),
                color = labelColor,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(HeatmapCellGap)) {
            repeat(HEATMAP_ROWS) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(HeatmapCellGap)) {
                    repeat(HEATMAP_COLUMNS) { column ->
                        val count = cells[row * HEATMAP_COLUMNS + column]
                        UsageHeatmapCell(
                            count = count,
                            color = cellColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageHeatmapCell(
    count: Int,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = intensityAlpha(count)),
        modifier = Modifier.size(HeatmapCellSize),
    ) {}
}

private fun intensityAlpha(count: Int): Float =
    when {
        count <= 0 -> 0.10f
        count == 1 -> 0.28f
        count <= 3 -> 0.48f
        count <= 6 -> 0.68f
        else -> 0.92f
    }
