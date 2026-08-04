package com.miara.cuentame.feature.activity.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.presentation.ui.toDisplayText
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityListScreenState
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityListViewModel
import com.miara.cuentame.feature.reports.ui.DetailReportError
import com.miara.cuentame.feature.reports.ui.DetailReportLoading
import com.miara.cuentame.feature.reports.ui.DetailReportSetupRequired
import com.miara.cuentame.feature.reports.ui.SummaryMetric
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InventoryActivityListRoute(
    onBack: () -> Unit,
    onActivityDetail: (InventoryMovementId) -> Unit,
    viewModel: InventoryActivityListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    InventoryActivityListScreen(
        uiState = uiState,
        searchQuery = searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onBackClick = onBack,
        onActivityClick = onActivityDetail,
        onRetry = viewModel::onRetry,
        onResetFilters = { viewModel.onFilterChange(InventoryActivityFilters()) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryActivityListScreen(
    uiState: InventoryActivityListScreenState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (InventoryActivityFilters) -> Unit,
    onBackClick: () -> Unit,
    onActivityClick: (InventoryMovementId) -> Unit,
    onRetry: () -> Unit,
    onResetFilters: () -> Unit
) {
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("inventory_activity_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_activity_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = true }, modifier = Modifier.testTag("inventory_activity_filters")) {
                        BadgedBox(
                            badge = {
                                if (uiState is InventoryActivityListScreenState.Ready && uiState.activeFilterCount > 0) {
                                    Badge { Text(uiState.activeFilterCount.toString(), modifier = Modifier.testTag("inventory_activity_filter_count")) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.inventory_activity_filter_title))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("inventory_activity_search"),
                placeholder = { Text(stringResource(R.string.inventory_activity_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            when (uiState) {
                InventoryActivityListScreenState.Loading -> DetailReportLoading("inventory_activity_loading")
                InventoryActivityListScreenState.SetupRequired -> DetailReportSetupRequired("inventory_activity_setup_required")
                is InventoryActivityListScreenState.LoadError -> DetailReportError("inventory_activity_error", onRetry)
                InventoryActivityListScreenState.Empty -> {
                    EmptyActivityState(
                        onRetry = onRetry,
                        modifier = Modifier.testTag("inventory_activity_empty")
                    )
                }
                is InventoryActivityListScreenState.Ready -> {
                    if (uiState.items.isEmpty()) {
                        FilteredEmptyActivityState(
                            onResetFilters = onResetFilters,
                            modifier = Modifier.testTag("inventory_activity_filtered_empty")
                        )
                    } else {
                        InventoryActivityContent(
                            items = uiState.items,
                            summary = uiState.summary,
                            currencyCode = uiState.currencyCode,
                            onActivityClick = onActivityClick
                        )
                    }
                }
            }
        }
    }

    if (showFilters && uiState is InventoryActivityListScreenState.Ready) {
        InventoryActivityFilterSheet(
            filters = uiState.filters,
            availableIngredients = uiState.availableIngredients,
            availableAreas = uiState.availableAreas,
            onDismiss = { showFilters = false },
            onApply = { 
                onFilterChange(it)
                showFilters = false 
            },
            onReset = {
                onResetFilters()
                showFilters = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InventoryActivityFilterSheet(
    filters: InventoryActivityFilters,
    availableIngredients: List<com.miara.cuentame.feature.activity.viewmodel.IngredientFilterOption>,
    availableAreas: List<com.miara.cuentame.feature.activity.viewmodel.AreaFilterOption>,
    onDismiss: () -> Unit,
    onApply: (InventoryActivityFilters) -> Unit,
    onReset: () -> Unit
) {
    var currentFilters by remember { mutableStateOf(filters) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("inventory_activity_filter_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.inventory_activity_filter_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onReset, modifier = Modifier.testTag("inventory_activity_filter_reset")) {
                    Text(stringResource(R.string.inventory_activity_reset_filters))
                }
            }

            // Date Range
            Text(stringResource(R.string.field_time), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    InventoryActivityDateRange.Last7Days to stringResource(R.string.range_7_days_label),
                    InventoryActivityDateRange.Last30Days to stringResource(R.string.range_30_days_label),
                    InventoryActivityDateRange.Last90Days to stringResource(R.string.range_90_days_label)
                ).forEach { (range, label) ->
                    FilterChip(
                        selected = currentFilters.dateRange == range,
                        onClick = { currentFilters = currentFilters.copy(dateRange = range) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("inventory_activity_filter_date_${range.javaClass.simpleName}")
                    )
                }
            }

            // Ingredient
            Text(stringResource(R.string.nav_inventory), style = MaterialTheme.typography.titleSmall)
            var ingredientExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = ingredientExpanded,
                onExpandedChange = { ingredientExpanded = it }
            ) {
                OutlinedTextField(
                    value = availableIngredients.find { it.id == currentFilters.ingredientId }?.name ?: stringResource(R.string.all),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ingredientExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("inventory_activity_filter_ingredient")
                )
                ExposedDropdownMenu(
                    expanded = ingredientExpanded,
                    onDismissRequest = { ingredientExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all)) },
                        onClick = { currentFilters = currentFilters.copy(ingredientId = null); ingredientExpanded = false }
                    )
                    availableIngredients.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.name) },
                            onClick = { currentFilters = currentFilters.copy(ingredientId = opt.id); ingredientExpanded = false }
                        )
                    }
                }
            }

            // Area
            Text(stringResource(R.string.area_label), style = MaterialTheme.typography.titleSmall)
            var areaExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = areaExpanded,
                onExpandedChange = { areaExpanded = it }
            ) {
                OutlinedTextField(
                    value = availableAreas.find { it.id == currentFilters.areaId }?.name ?: stringResource(R.string.all),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = areaExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("inventory_activity_filter_area")
                )
                ExposedDropdownMenu(
                    expanded = areaExpanded,
                    onDismissRequest = { areaExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all)) },
                        onClick = { currentFilters = currentFilters.copy(areaId = null); areaExpanded = false }
                    )
                    availableAreas.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.name) },
                            onClick = { currentFilters = currentFilters.copy(areaId = opt.id); areaExpanded = false }
                        )
                    }
                }
            }

            // Direction
            Text(stringResource(R.string.status_label), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InventoryActivityDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = currentFilters.direction == dir,
                        onClick = { currentFilters = currentFilters.copy(direction = dir) },
                        label = { 
                            Text(when(dir) {
                                InventoryActivityDirection.ALL -> stringResource(R.string.all)
                                InventoryActivityDirection.IN -> stringResource(R.string.inventory_activity_summary_incoming)
                                InventoryActivityDirection.OUT -> stringResource(R.string.inventory_activity_summary_outgoing)
                            })
                        },
                        modifier = Modifier.testTag("inventory_activity_filter_direction_${dir.name}")
                    )
                }
            }

            // Categories
            Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InventoryActivityCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = currentFilters.categories.contains(cat),
                        onClick = {
                            val newCats = if (currentFilters.categories.contains(cat)) {
                                currentFilters.categories - cat
                            } else {
                                currentFilters.categories + cat
                            }
                            currentFilters = currentFilters.copy(categories = newCats)
                        },
                        label = { Text(cat.toDisplayText()) },
                        modifier = Modifier.testTag("inventory_activity_filter_category_${cat.name}")
                    )
                }
            }

            // Reversals
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inventory_activity_reversal), modifier = Modifier.weight(1f))
                Switch(
                    checked = currentFilters.includeReversals,
                    onCheckedChange = { currentFilters = currentFilters.copy(includeReversals = it) },
                    modifier = Modifier.testTag("inventory_activity_filter_reversals")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onApply(currentFilters) },
                modifier = Modifier.fillMaxWidth().testTag("inventory_activity_filter_apply")
            ) {
                Text(stringResource(R.string.action_save))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InventoryActivityContent(
    items: List<InventoryActivityItem>,
    summary: InventoryActivitySummary,
    currencyCode: String,
    onActivityClick: (InventoryMovementId) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("inventory_activity_list"),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            SummarySection(summary, currencyCode)
        }
        items(items, key = { it.movement.id.value }) { item ->
            ActivityRow(item, currencyCode, onClick = { onActivityClick(item.movement.id) })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun SummarySection(
    summary: InventoryActivitySummary,
    currencyCode: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("inventory_activity_summary"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.inventory_activity_summary_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryMetric(stringResource(R.string.inventory_activity_summary_movements), summary.movementCount.toString(), testTag = "inventory_activity_movement_count", modifier = Modifier.weight(1f))
                SummaryMetric(stringResource(R.string.inventory_activity_summary_incoming), summary.incomingMovementCount.toString(), testTag = "inventory_activity_incoming_count", modifier = Modifier.weight(1f))
                SummaryMetric(stringResource(R.string.inventory_activity_summary_outgoing), summary.outgoingMovementCount.toString(), testTag = "inventory_activity_outgoing_count", modifier = Modifier.weight(1f))
                SummaryMetric(stringResource(R.string.inventory_activity_summary_reversals), summary.reversalCount.toString(), testTag = "inventory_activity_reversal_count", modifier = Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryMetric(
                    stringResource(R.string.inventory_activity_summary_value_added), 
                    summary.valueAdded?.let { Formatters.formatCurrency(it, currencyCode) } ?: "-", 
                    testTag = "inventory_activity_value_added",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    stringResource(R.string.inventory_activity_summary_value_removed), 
                    summary.valueRemoved?.let { Formatters.formatCurrency(it, currencyCode) } ?: "-", 
                    testTag = "inventory_activity_value_removed",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }

            if (summary.valueAdded == null || summary.valueRemoved == null) {
                Text(
                    stringResource(R.string.inventory_activity_value_incomplete),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.testTag("inventory_activity_value_incomplete")
                )
            }

            summary.quantitySummary?.let { q ->
                HorizontalDivider()
                Text("${stringResource(R.string.inventory_activity_summary_title)}: ${q.ingredientName}", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryMetric(stringResource(R.string.inventory_activity_summary_quantity_in), Formatters.formatQuantity(q.quantityIn, q.baseUnitSymbol), testTag = "inventory_activity_quantity_in", modifier = Modifier.weight(1f))
                    SummaryMetric(stringResource(R.string.inventory_activity_summary_quantity_out), Formatters.formatQuantity(q.quantityOut, q.baseUnitSymbol), testTag = "inventory_activity_quantity_out", modifier = Modifier.weight(1f))
                    SummaryMetric(stringResource(R.string.inventory_activity_summary_quantity_net), formatSignedQuantity(q.netQuantity, q.baseUnitSymbol), testTag = "inventory_activity_quantity_net", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    item: InventoryActivityItem,
    currencyCode: String,
    onClick: () -> Unit
) {
    val dateTimeFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
    
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("inventory_activity_row_${item.movement.id.value}"),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.ingredientName, fontWeight = FontWeight.Bold)
                if (item.reversedByMovementId != null) {
                    SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.inventory_activity_reversed)) }, modifier = Modifier.height(24.dp))
                }
                if (item.movement.movementType == com.miara.cuentame.core.model.inventory.InventoryMovementType.REVERSAL) {
                    SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.inventory_activity_reversal)) }, modifier = Modifier.height(24.dp))
                }
            }
        },
        supportingContent = {
            Column {
                Text("${item.movement.movementType.toActivityCategory().toDisplayText()} • ${item.areaName}")
                Text(item.sourceDisplay.title, style = MaterialTheme.typography.bodySmall)
                Text(dateTimeFormatter.format(item.movement.effectiveAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val quantity = item.movement.quantityBaseSigned
                val color = if (quantity > BigDecimal.ZERO) MaterialTheme.colorScheme.primary else if (quantity < BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                
                Text(
                    text = formatSignedQuantity(quantity, item.baseUnitSymbol),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                item.movement.totalValueSnapshot?.let { value ->
                    Text(Formatters.formatCurrency(value.abs(), currencyCode), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    )
}

@Composable
private fun EmptyActivityState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.inventory_activity_empty_state), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.inventory_activity_empty_desc), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun FilteredEmptyActivityState(onResetFilters: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.inventory_activity_filtered_empty_state), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onResetFilters) {
                Text(stringResource(R.string.inventory_activity_reset_filters))
            }
        }
    }
}

private fun formatSignedQuantity(quantity: BigDecimal, unitSymbol: String): String {
    val prefix = if (quantity > BigDecimal.ZERO) "+" else if (quantity < BigDecimal.ZERO) "\u2212" else ""
    return "$prefix${Formatters.formatQuantity(quantity.abs(), unitSymbol)}"
}

@Composable
private fun InventoryActivityCategory.toDisplayText(): String = when (this) {
    InventoryActivityCategory.PURCHASE -> stringResource(R.string.inventory_activity_purchase)
    InventoryActivityCategory.WASTE -> stringResource(R.string.inventory_activity_waste)
    InventoryActivityCategory.STOCK_COUNT -> stringResource(R.string.inventory_activity_stock_count)
    InventoryActivityCategory.PRODUCTION_CONSUMPTION -> stringResource(R.string.inventory_activity_production_consumption)
    InventoryActivityCategory.PRODUCTION_OUTPUT -> stringResource(R.string.inventory_activity_production_output)
    InventoryActivityCategory.REVERSAL -> stringResource(R.string.inventory_activity_reversal)
    InventoryActivityCategory.OTHER -> stringResource(R.string.inventory_activity_other)
}

private fun com.miara.cuentame.core.model.inventory.InventoryMovementType.toActivityCategory(): InventoryActivityCategory = when (this) {
    com.miara.cuentame.core.model.inventory.InventoryMovementType.PURCHASE -> InventoryActivityCategory.PURCHASE
    com.miara.cuentame.core.model.inventory.InventoryMovementType.WASTE -> InventoryActivityCategory.WASTE
    com.miara.cuentame.core.model.inventory.InventoryMovementType.COUNT_ADJUSTMENT -> InventoryActivityCategory.STOCK_COUNT
    com.miara.cuentame.core.model.inventory.InventoryMovementType.MANUAL_ADJUSTMENT -> InventoryActivityCategory.OTHER
    com.miara.cuentame.core.model.inventory.InventoryMovementType.OPENING_BALANCE -> InventoryActivityCategory.OTHER
    com.miara.cuentame.core.model.inventory.InventoryMovementType.REVERSAL -> InventoryActivityCategory.REVERSAL
    com.miara.cuentame.core.model.inventory.InventoryMovementType.PRODUCTION_CONSUMPTION -> InventoryActivityCategory.PRODUCTION_CONSUMPTION
    com.miara.cuentame.core.model.inventory.InventoryMovementType.PRODUCTION_OUTPUT -> InventoryActivityCategory.PRODUCTION_OUTPUT
}
