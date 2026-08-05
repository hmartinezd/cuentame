package com.miara.cuentame.feature.activity.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.model.inventory.toInventoryActivityCategory
import com.miara.cuentame.feature.activity.logic.InventoryActivityDateUtils
import com.miara.cuentame.feature.activity.logic.LocalInventoryActivityTextResolver
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityListScreenState
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityListViewModel
import com.miara.cuentame.core.presentation.ui.DetailReportError
import com.miara.cuentame.core.presentation.ui.DetailReportLoading
import com.miara.cuentame.core.presentation.ui.DetailReportSetupRequired
import com.miara.cuentame.core.presentation.ui.SummaryMetric
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
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
        onResetFilters = viewModel::resetFilters
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
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("inventory_activity_back")
                    ) {
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

            if (uiState is InventoryActivityListScreenState.Ready) {
                ActiveFiltersRow(
                    filters = uiState.filters,
                    availableIngredients = uiState.availableIngredients,
                    availableAreas = uiState.availableAreas,
                    searchQuery = searchQuery,
                    onFilterChange = onFilterChange,
                    onSearchQueryChange = onSearchQueryChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

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
                    InventoryActivityContent(
                        items = uiState.items,
                        summary = uiState.summary,
                        currencyCode = uiState.currencyCode,
                        localeTag = uiState.localeTag,
                        onActivityClick = onActivityClick,
                        onResetFilters = onResetFilters
                    )
                }
            }
        }
    }

    if (showFilters && uiState is InventoryActivityListScreenState.Ready) {
        InventoryActivityFilterSheet(
            filters = uiState.filters,
            availableIngredients = uiState.availableIngredients,
            availableAreas = uiState.availableAreas,
            today = uiState.today,
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
    today: LocalDate,
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
            Text(stringResource(R.string.inventory_activity_filter_date_range), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    InventoryActivityDateRange.Last7Days to stringResource(R.string.range_7_days_label),
                    InventoryActivityDateRange.Last30Days to stringResource(R.string.range_30_days_label),
                    InventoryActivityDateRange.Last90Days to stringResource(R.string.range_90_days_label),
                    InventoryActivityDateRange.Custom(today, today) to stringResource(R.string.inventory_activity_filter_date_custom)
                ).forEach { (range, label) ->
                    val isCustom = range is InventoryActivityDateRange.Custom
                    val isSelected = if (isCustom) currentFilters.dateRange is InventoryActivityDateRange.Custom else currentFilters.dateRange == range
                    
                    var showCustomPicker by remember { mutableStateOf(false) }

                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            if (isCustom) {
                                showCustomPicker = true
                            } else {
                                currentFilters = currentFilters.copy(dateRange = range)
                            }
                        },
                        label = { Text(label) },
                        modifier = Modifier.testTag("inventory_activity_filter_date_${if (isCustom) "Custom" else range.javaClass.simpleName}")
                    )

                    if (showCustomPicker) {
                        val initialRange = currentFilters.dateRange as? InventoryActivityDateRange.Custom
                        CustomDateRangeDialog(
                            initialStartDate = initialRange?.startDate ?: today,
                            initialEndDate = initialRange?.endDateInclusive ?: today,
                            today = today,
                            onDismiss = { showCustomPicker = false },
                            onConfirm = { start, end ->
                                currentFilters = currentFilters.copy(dateRange = InventoryActivityDateRange.Custom(start, end))
                                showCustomPicker = false
                            }
                        )
                    }
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
            Text(stringResource(R.string.inventory_activity_filter_direction), style = MaterialTheme.typography.titleSmall)
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
            Text(stringResource(R.string.inventory_activity_filter_categories), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val resolver = LocalInventoryActivityTextResolver.current
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
                        label = { Text(resolver.categoryText(cat)) },
                        modifier = Modifier.testTag("inventory_activity_filter_category_${cat.name}")
                    )
                }
            }

            // Reversals
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inventory_activity_filter_include_reversals), modifier = Modifier.weight(1f))
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
                Text(stringResource(R.string.inventory_activity_filter_apply))
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
    localeTag: String,
    onActivityClick: (InventoryMovementId) -> Unit,
    onResetFilters: () -> Unit
) {
    val locale = remember(localeTag) { java.util.Locale.forLanguageTag(localeTag) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("inventory_activity_list"),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            SummarySection(summary, currencyCode, locale)
        }
        
        if (items.isEmpty()) {
            item {
                FilteredEmptyActivityState(
                    onResetFilters = onResetFilters,
                    modifier = Modifier.testTag("inventory_activity_filtered_empty").fillParentMaxHeight(0.7f)
                )
            }
        } else {
            items(items, key = { it.movement.id.value }) { item ->
                ActivityRow(item, currencyCode, locale, onClick = { onActivityClick(item.movement.id) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun SummarySection(
    summary: InventoryActivitySummary,
    currencyCode: String,
    locale: java.util.Locale
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
                val valueAddedText = when (summary.valueCoverage) {
                    InventoryActivityValueCoverage.UNAVAILABLE -> stringResource(R.string.not_available)
                    else -> Formatters.formatCurrency(summary.valueAdded, currencyCode, locale)
                }
                val valueRemovedText = when (summary.valueCoverage) {
                    InventoryActivityValueCoverage.UNAVAILABLE -> stringResource(R.string.not_available)
                    else -> Formatters.formatCurrency(summary.valueRemoved, currencyCode, locale)
                }

                SummaryMetric(
                    stringResource(R.string.inventory_activity_summary_value_added), 
                    valueAddedText, 
                    testTag = "inventory_activity_value_added",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    stringResource(R.string.inventory_activity_summary_value_removed), 
                    valueRemovedText, 
                    testTag = "inventory_activity_value_removed",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            if (summary.valueCoverage == InventoryActivityValueCoverage.PARTIAL || summary.valueCoverage == InventoryActivityValueCoverage.UNAVAILABLE) {
                Text(
                    stringResource(R.string.inventory_activity_value_incomplete),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.testTag("inventory_activity_value_incomplete")
                )
            }

            if (summary.quantityCoverage == InventoryActivityValueCoverage.PARTIAL || summary.quantityCoverage == InventoryActivityValueCoverage.UNAVAILABLE) {
                Text(
                    stringResource(R.string.inventory_activity_quantity_incomplete),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.testTag("inventory_activity_quantity_incomplete")
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
    locale: java.util.Locale,
    onClick: () -> Unit
) {
    val resolver = LocalInventoryActivityTextResolver.current
    val dateTimeFormatter = remember(locale) { 
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .withLocale(locale) 
    }
    
    val categoryText = resolver.categoryText(item.movement.movementType.toInventoryActivityCategory())
    val quantityText = formatSignedQuantity(item.movement.quantityBaseSigned, item.baseUnitSymbol)
    val dateText = dateTimeFormatter.format(item.movement.effectiveAt)
    val sourceTitle = resolver.sourceTitle(item.sourceInfo)
    val sourceSubtitle = resolver.sourceSubtitle(item.sourceInfo)
    
    val reversalLabel = if (item.reversedByMovementId != null) {
        stringResource(R.string.inventory_activity_reversed)
    } else if (item.movement.movementType == com.miara.cuentame.core.model.inventory.InventoryMovementType.REVERSAL) {
        stringResource(R.string.inventory_activity_reversal)
    } else null

    val valueText = item.movement.totalValueSnapshot?.let { 
        Formatters.formatCurrency(it.abs(), currencyCode, locale) 
    } ?: ""

    val semanticContentDescription = stringResource(
        R.string.inventory_activity_row_content_description,
        categoryText,
        item.ingredientName,
        item.areaName,
        quantityText,
        dateText,
        sourceTitle,
        sourceSubtitle ?: ""
    ) + (if (reversalLabel != null) ". $reversalLabel" else "") + (if (valueText.isNotEmpty()) ". $valueText" else "")

    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("inventory_activity_row_${item.movement.id.value}")
            .semantics(mergeDescendants = true) {
                contentDescription = semanticContentDescription
            },
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(item.ingredientName, fontWeight = FontWeight.Bold)
                if (reversalLabel != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.height(20.dp)
                    ) {
                        Text(
                            text = reversalLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = {
            Column {
                Text("${categoryText} \u2022 ${item.areaName}")
                Text(sourceTitle, style = MaterialTheme.typography.bodySmall)
                sourceSubtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val quantity = item.movement.quantityBaseSigned
                val color = if (quantity > BigDecimal.ZERO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                
                Text(
                    text = quantityText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (valueText.isNotEmpty()) {
                    Text(valueText, style = MaterialTheme.typography.labelSmall)
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
            Button(
                onClick = onResetFilters,
                modifier = Modifier.testTag("inventory_activity_filtered_empty_reset")
            ) {
                Text(stringResource(R.string.inventory_activity_filtered_empty_reset))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDateRangeDialog(
    initialStartDate: LocalDate,
    initialEndDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = InventoryActivityDateUtils.localDateToDatePickerMillis(initialStartDate),
        initialSelectedEndDateMillis = InventoryActivityDateUtils.localDateToDatePickerMillis(initialEndDate),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return InventoryActivityDateUtils.datePickerMillisToLocalDate(utcTimeMillis)
                    .isAfter(today)
                    .not()
            }
        }
    )

    val errorMessage = remember(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis) {
        val startMillis = dateRangePickerState.selectedStartDateMillis
        val endMillis = dateRangePickerState.selectedEndDateMillis
        if (startMillis != null && endMillis != null) {
            val start = InventoryActivityDateUtils.datePickerMillisToLocalDate(startMillis)
            val end = InventoryActivityDateUtils.datePickerMillisToLocalDate(endMillis)
            if (start.isAfter(end)) {
                return@remember R.string.inventory_activity_custom_date_error
            }
        }
        null
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMillis = dateRangePickerState.selectedStartDateMillis
                    val endMillis = dateRangePickerState.selectedEndDateMillis
                    if (startMillis != null && endMillis != null) {
                        val start = InventoryActivityDateUtils.datePickerMillisToLocalDate(startMillis)
                        val end = InventoryActivityDateUtils.datePickerMillisToLocalDate(endMillis)
                        if (start.isAfter(end)) {
                            return@TextButton
                        }
                        onConfirm(start, end)
                    }
                },
                enabled = errorMessage == null && dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null,
                modifier = Modifier.testTag("inventory_activity_custom_date_confirm")
            ) {
                Text(stringResource(R.string.inventory_activity_custom_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_dismiss))
            }
        },
        modifier = Modifier.testTag("inventory_activity_custom_date_dialog")
    ) {
        Column {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text(stringResource(R.string.inventory_activity_custom_date_dialog), modifier = Modifier.padding(16.dp)) },
                modifier = Modifier.weight(1f)
            )
            errorMessage?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(16.dp).testTag("inventory_activity_custom_date_error")
                )
            }
        }
    }
}

private fun formatSignedQuantity(quantity: BigDecimal, unitSymbol: String): String {
    val prefix = if (quantity > BigDecimal.ZERO) "+" else if (quantity < BigDecimal.ZERO) "\u2212" else ""
    return "$prefix${Formatters.formatQuantity(quantity.abs(), unitSymbol)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFiltersRow(
    filters: InventoryActivityFilters,
    availableIngredients: List<com.miara.cuentame.feature.activity.viewmodel.IngredientFilterOption>,
    availableAreas: List<com.miara.cuentame.feature.activity.viewmodel.AreaFilterOption>,
    searchQuery: String,
    onFilterChange: (InventoryActivityFilters) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.testTag("inventory_activity_active_filters"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (searchQuery.isNotBlank()) {
            ActiveFilterChip(
                label = stringResource(R.string.inventory_activity_active_search, searchQuery),
                onClear = { onSearchQueryChange("") },
                testTag = "inventory_activity_active_search",
                removeTestTag = "inventory_activity_active_search_remove"
            )
        }

        if (filters.ingredientId != null) {
            val name = availableIngredients.find { it.id == filters.ingredientId }?.name 
                ?: stringResource(R.string.inventory_activity_filter_ingredient_unavailable)
            ActiveFilterChip(
                label = stringResource(R.string.inventory_activity_active_ingredient_filter, name),
                onClear = { onFilterChange(filters.copy(ingredientId = null)) },
                testTag = "inventory_activity_active_ingredient_filter",
                removeTestTag = "inventory_activity_active_ingredient_filter_remove"
            )
        }

        if (filters.areaId != null) {
            val name = availableAreas.find { it.id == filters.areaId }?.name 
                ?: stringResource(R.string.inventory_activity_filter_area_unavailable)
            ActiveFilterChip(
                label = stringResource(R.string.inventory_activity_active_area_filter, name),
                onClear = { onFilterChange(filters.copy(areaId = null)) },
                testTag = "inventory_activity_active_area_filter",
                removeTestTag = "inventory_activity_active_area_filter_remove"
            )
        }

        if (filters.dateRange != InventoryActivityDateRange.Last30Days) {
            ActiveFilterChip(
                label = stringResource(R.string.inventory_activity_active_date_filter, formatDateRangeLabel(filters.dateRange)),
                onClear = { onFilterChange(filters.copy(dateRange = InventoryActivityDateRange.Last30Days)) },
                testTag = "inventory_activity_active_date_filter",
                removeTestTag = "inventory_activity_active_date_filter_remove"
            )
        }
    }
}

@Composable
private fun ActiveFilterChip(
    label: String,
    onClear: () -> Unit,
    testTag: String,
    removeTestTag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(16.dp)
                    .testTag(removeTestTag)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun formatDateRangeLabel(range: InventoryActivityDateRange): String = when (range) {
    InventoryActivityDateRange.Last7Days -> stringResource(R.string.range_7_days_label)
    InventoryActivityDateRange.Last30Days -> stringResource(R.string.range_30_days_label)
    InventoryActivityDateRange.Last90Days -> stringResource(R.string.range_90_days_label)
    is InventoryActivityDateRange.Custom -> {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        "${formatter.format(range.startDate)} \u2212 ${formatter.format(range.endDateInclusive)}"
    }
}
