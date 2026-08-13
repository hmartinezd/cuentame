package com.miara.cuentame.core.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miara.cuentame.R
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.StockCountStatus

@Composable
fun StatusChip(status: DocumentStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        DocumentStatus.DRAFT -> MaterialTheme.colorScheme.secondary
        DocumentStatus.POSTED -> MaterialTheme.colorScheme.primary
        DocumentStatus.VOIDED -> MaterialTheme.colorScheme.error
        DocumentStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    val text = when (status) {
        DocumentStatus.DRAFT -> stringResource(R.string.status_draft)
        DocumentStatus.POSTED -> stringResource(R.string.status_posted)
        DocumentStatus.VOIDED -> stringResource(R.string.status_voided)
        DocumentStatus.UNKNOWN -> stringResource(R.string.status_unavailable)
    }
    
    StatusChipContent(text = text, color = color, modifier = modifier)
}

@Composable
fun StatusChip(status: StockCountStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        StockCountStatus.DRAFT -> MaterialTheme.colorScheme.secondary
        StockCountStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        StockCountStatus.VOIDED -> MaterialTheme.colorScheme.error
        StockCountStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    val text = when (status) {
        StockCountStatus.DRAFT -> stringResource(R.string.status_draft)
        StockCountStatus.COMPLETED -> stringResource(R.string.status_completed)
        StockCountStatus.VOIDED -> stringResource(R.string.status_voided)
        StockCountStatus.UNKNOWN -> stringResource(R.string.status_unavailable)
    }
    
    StatusChipContent(text = text, color = color, modifier = modifier)
}

@Composable
fun AreaStatusChip(status: CountAreaStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        CountAreaStatus.NOT_STARTED -> MaterialTheme.colorScheme.outline
        CountAreaStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
        CountAreaStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        CountAreaStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    val text = when (status) {
        CountAreaStatus.NOT_STARTED -> stringResource(R.string.not_started)
        CountAreaStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
        CountAreaStatus.COMPLETED -> stringResource(R.string.area_completed)
        CountAreaStatus.UNKNOWN -> stringResource(R.string.status_unavailable)
    }
    
    StatusChipContent(text = text, color = color, modifier = modifier)
}

@Composable
private fun StatusChipContent(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(color.copy(alpha = 0.1f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("status_chip_$text")
            .semantics(mergeDescendants = true) {}
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
