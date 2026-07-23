package com.miara.cuentame.feature.counts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.domain.repository.StockCountSummary
import com.miara.cuentame.core.domain.validation.toUserMessageRes
import com.miara.cuentame.core.model.inventory.StockCountStatus
import com.miara.cuentame.feature.counts.viewmodel.StockCountListUiState
import com.miara.cuentame.feature.counts.viewmodel.StockCountListViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StockCountListRoute(
    onStartCount: () -> Unit,
    onCountClick: (StockCountId, StockCountStatus) -> Unit,
    viewModel: StockCountListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    StockCountListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onStartCount = onStartCount,
        onCountClick = onCountClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCountListScreen(
    uiState: StockCountListUiState,
    snackbarHostState: SnackbarHostState,
    onStartCount: () -> Unit,
    onCountClick: (StockCountId, StockCountStatus) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.count_title)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onStartCount, modifier = Modifier.testTag("start_count_fab")) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_start_count))
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.counts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.state_empty_desc))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(uiState.counts) { summary ->
                    StockCountSummaryItem(
                        summary = summary,
                        onClick = { onCountClick(summary.count.id, summary.count.status) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun StockCountSummaryItem(
    summary: StockCountSummary,
    onClick: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(summary.count.name, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Column {
                Text(dateFormatter.format(summary.count.effectiveAt))
                if (summary.count.status == StockCountStatus.DRAFT) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { summary.progress },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(summary.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        trailingContent = {
            StatusChip(status = summary.count.status)
        }
    )
}
