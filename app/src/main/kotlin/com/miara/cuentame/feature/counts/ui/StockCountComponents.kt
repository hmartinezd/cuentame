package com.miara.cuentame.feature.counts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miara.cuentame.R
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import com.miara.cuentame.core.model.inventory.StockCountStatus

@Composable
fun StatusChip(status: StockCountStatus) {
    val color = when (status) {
        StockCountStatus.DRAFT -> MaterialTheme.colorScheme.secondary
        StockCountStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        StockCountStatus.VOIDED -> MaterialTheme.colorScheme.error
    }
    val text = when (status) {
        StockCountStatus.DRAFT -> stringResource(R.string.status_draft)
        StockCountStatus.COMPLETED -> stringResource(R.string.status_completed)
        StockCountStatus.VOIDED -> stringResource(R.string.status_voided)
    }
    
    StatusChipContent(text = text, color = color)
}

@Composable
fun AreaStatusChip(status: CountAreaStatus) {
    val color = when (status) {
        CountAreaStatus.NOT_STARTED -> MaterialTheme.colorScheme.outline
        CountAreaStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
        CountAreaStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    }
    val text = when (status) {
        CountAreaStatus.NOT_STARTED -> stringResource(R.string.not_started)
        CountAreaStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
        CountAreaStatus.COMPLETED -> stringResource(R.string.area_completed)
    }
    
    StatusChipContent(text = text, color = color)
}

@Composable
private fun StatusChipContent(text: String, color: Color) {
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.1f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
