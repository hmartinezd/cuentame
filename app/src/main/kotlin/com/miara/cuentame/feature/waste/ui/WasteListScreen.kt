package com.miara.cuentame.feature.waste.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.domain.repository.WasteSummary
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.feature.counts.ui.StatusChip
import com.miara.cuentame.feature.waste.viewmodel.WasteListUiState
import com.miara.cuentame.feature.waste.viewmodel.WasteListViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WasteListRoute(
    onBack: () -> Unit,
    onAddWaste: () -> Unit,
    onWasteClick: (WasteEventId, DocumentStatus) -> Unit,
    viewModel: WasteListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WasteListScreen(
        uiState = uiState,
        onBack = onBack,
        onAddWaste = onAddWaste,
        onWasteClick = onWasteClick,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onStatusFilterChanged = viewModel::onStatusFilterChanged
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteListScreen(
    uiState: WasteListUiState,
    onBack: () -> Unit,
    onAddWaste: () -> Unit,
    onWasteClick: (WasteEventId, DocumentStatus) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterChanged: (DocumentStatus?) -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.waste_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter_by_category))
                    }
                    Box {
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_draft)) },
                                onClick = { onStatusFilterChanged(DocumentStatus.DRAFT); showFilterMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_posted)) },
                                onClick = { onStatusFilterChanged(DocumentStatus.POSTED); showFilterMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_voided)) },
                                onClick = { onStatusFilterChanged(DocumentStatus.VOIDED); showFilterMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.all)) },
                                onClick = { onStatusFilterChanged(null); showFilterMenu = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddWaste,
                modifier = Modifier.testTag("add_waste_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.log_waste))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("waste_search"),
                placeholder = { Text(stringResource(R.string.action_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.wasteEvents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.state_empty_desc))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag("waste_list")) {
                    items(uiState.wasteEvents) { summary ->
                        WasteItem(
                            summary = summary,
                            currencyCode = uiState.currencyCode,
                            onClick = { onWasteClick(summary.event.id, summary.event.status) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun WasteItem(
    summary: WasteSummary,
    currencyCode: String,
    onClick: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }
    
    ListItem(
        modifier = Modifier.clickable { onClick() }.testTag("waste_item_${summary.event.id.value}"),
        headlineContent = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = summary.ingredientName ?: stringResource(R.string.error_ingredient_not_found),
                    fontWeight = FontWeight.Bold
                )
                if (summary.estimatedValue != null) {
                    Text(
                        text = Formatters.formatCurrency(summary.estimatedValue, currencyCode),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        supportingContent = {
            Column {
                Text(text = "${summary.event.quantityEntered} ${summary.unitLabel ?: stringResource(R.string.unknown_unit)}")
                Text(text = summary.areaName ?: stringResource(R.string.unknown_area))
                Text(text = dateFormatter.format(summary.event.effectiveAt), style = MaterialTheme.typography.labelSmall)
            }
        },
        trailingContent = {
            StatusChip(status = summary.event.status)
        }
    )
}
