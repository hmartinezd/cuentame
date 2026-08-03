package com.miara.cuentame.feature.production.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.ProductionBatchSummary
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchListUiState
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchListViewModel
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchScreenState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProductionBatchListRoute(
    onBackClick: () -> Unit,
    onCreateBatch: () -> Unit,
    onBatchClick: (ProductionBatchId, DocumentStatus) -> Unit,
    viewModel: ProductionBatchListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProductionBatchListScreen(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onStatusFilterChanged = viewModel::onStatusFilterChanged,
        onRetry = viewModel::onRetry,
        onBackClick = onBackClick,
        onCreateBatch = onCreateBatch,
        onBatchClick = onBatchClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionBatchListScreen(
    uiState: ProductionBatchListUiState,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterChanged: (DocumentStatus?) -> Unit,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    onCreateBatch: () -> Unit,
    onBatchClick: (ProductionBatchId, DocumentStatus) -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("production_batch_list_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.production_batches)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateBatch,
                modifier = Modifier.testTag("add_production_batch_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_production_batch))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BatchListFilters(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChanged,
                selectedStatus = uiState.selectedStatus,
                onStatusChange = onStatusFilterChanged
            )

            when (val screenState = uiState.screenState) {
                ProductionBatchScreenState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ProductionBatchScreenState.LoadError -> {
                    Box(modifier = Modifier.fillMaxSize().testTag("production_batch_error"), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(R.string.state_error_desc))
                            Button(onClick = onRetry, modifier = Modifier.testTag("production_batch_retry")) {
                                Text(stringResource(R.string.action_retry_desc))
                            }
                        }
                    }
                }
                ProductionBatchScreenState.Ready -> {
                    if (uiState.batches.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().testTag(if (uiState.searchQuery.isNotBlank() || uiState.selectedStatus != null) "production_batch_filtered_empty" else "production_batch_empty"), contentAlignment = Alignment.Center) {
                            val emptyText = if (uiState.searchQuery.isNotBlank() || uiState.selectedStatus != null)
                                stringResource(R.string.no_batches_match_filters)
                            else
                                stringResource(R.string.no_production_batches)
                            Text(text = emptyText, style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("production_batch_list"),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(uiState.batches, key = { it.id.value }) { batch ->
                                ProductionBatchItem(
                                    batch = batch,
                                    onClick = { onBatchClick(batch.id, batch.status) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun BatchListFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedStatus: DocumentStatus?,
    onStatusChange: (DocumentStatus?) -> Unit
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("production_batch_search"),
        placeholder = { Text(stringResource(R.string.action_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            Box {
                IconButton(onClick = { statusMenuExpanded = true }, modifier = Modifier.testTag("production_batch_status_filter")) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.status_label),
                        tint = if (selectedStatus != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all)) },
                        onClick = {
                            onStatusChange(null)
                            statusMenuExpanded = false
                        }
                    )
                    DocumentStatus.entries.forEach { status ->
                        val labelRes = when (status) {
                            DocumentStatus.DRAFT -> R.string.status_draft
                            DocumentStatus.POSTED -> R.string.status_posted
                            DocumentStatus.VOIDED -> R.string.status_voided
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(labelRes)) },
                            onClick = {
                                onStatusChange(status)
                                statusMenuExpanded = false
                            }
                        )
                    }
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        singleLine = true
    )
}

@Composable
private fun ProductionBatchItem(
    batch: ProductionBatchSummary,
    onClick: () -> Unit
) {
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
    }

    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("production_batch_item_${batch.id.value}"),
        headlineContent = {
            Text(text = batch.recipeName, fontWeight = FontWeight.Bold)
        },
        supportingContent = {
            Column {
                Text(
                    text = stringResource(R.string.output_ingredient) + ": ${batch.outputIngredientName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.component_count_format, batch.componentCount),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = dateTimeFormatter.format(batch.effectiveAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                ProductionStatusBadge(status = batch.status)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Formatters.formatQuantity(batch.actualOutputQuantityEntered, batch.outputUnitLabel ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
                if (batch.totalComponentCost != null) {
                    Text(
                        text = Formatters.formatCurrency(batch.totalComponentCost, ""), // Currency code handling?
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}
