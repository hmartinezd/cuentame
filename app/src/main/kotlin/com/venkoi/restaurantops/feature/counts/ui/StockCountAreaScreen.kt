package com.venkoi.restaurantops.feature.counts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.designsystem.component.adaptiveContentWidth
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.presentation.validation.toUserMessageRes
import com.venkoi.restaurantops.core.model.ingredient.Ingredient
import com.venkoi.restaurantops.feature.counts.viewmodel.CountUnitOptionUi
import com.venkoi.restaurantops.feature.counts.viewmodel.StockCountAreaEvent
import com.venkoi.restaurantops.feature.counts.viewmodel.StockCountAreaScreenState
import com.venkoi.restaurantops.feature.counts.viewmodel.StockCountAreaUiState
import com.venkoi.restaurantops.feature.counts.viewmodel.StockCountAreaViewModel
import com.venkoi.restaurantops.feature.counts.viewmodel.StockCountLineEntry
import com.venkoi.restaurantops.feature.counts.viewmodel.StockCountItemFilter
import com.venkoi.restaurantops.core.presentation.ui.ArchiveConfirmDialog
import com.venkoi.restaurantops.core.domain.service.hasLargeCountVariance
import java.math.BigDecimal
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

internal fun countRowAbsoluteIndex(
    rowIndex: Int,
    searchResultCount: Int,
    hasSearchSection: Boolean,
    archivedWarningCount: Int
): Int =
    (if (hasSearchSection) 1 + searchResultCount else 0) +
        1 +
        (if (archivedWarningCount > 0) 1 + archivedWarningCount else 0) +
        rowIndex

@Composable
fun StockCountAreaRoute(
    onBack: () -> Unit,
    onConfigureIngredients: () -> Unit,
    viewModel: StockCountAreaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var lineToDelete by remember { mutableStateOf<StockCountLineEntry?>(null) }
    var focusTarget by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StockCountAreaEvent.AreaCompleted -> onBack()
                is StockCountAreaEvent.NavigateBack -> onBack()
                is StockCountAreaEvent.LineDeleted -> {
                    lineToDelete = null
                }
                is StockCountAreaEvent.ShowError -> {
                    snackbarHostState.showSnackbar(context.getString(event.error.toUserMessageRes()))
                }
                is StockCountAreaEvent.FocusQuantity -> focusTarget = event.ingredientId
                StockCountAreaEvent.ImeDone -> {
                    focusTarget = null
                    focusManager.clearFocus()
                }
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    StockCountAreaScreen(
        uiState = uiState,
        lineToDelete = lineToDelete,
        onShowDeleteConfirm = { lineToDelete = it },
        snackbarHostState = snackbarHostState,
        onBack = viewModel::onBackRequested,
        onConfigureIngredients = onConfigureIngredients,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onItemFilterChanged = viewModel::onItemFilterChanged,
        onToggleOrderEditing = viewModel::onToggleOrderEditing,
        onMoveItem = viewModel::onMoveItem,
        onAddIngredient = viewModel::onAddIngredient,
        onQuantityChanged = viewModel::onQuantityChanged,
        onUnitChanged = viewModel::onUnitChanged,
        onImeAction = viewModel::onImeAction,
        focusTarget = focusTarget,
        onFocusHandled = { focusTarget = null },
        onConfirmDelete = viewModel::onConfirmDelete,
        onCompleteArea = viewModel::onCompleteArea,
        onReopenArea = viewModel::onReopenArea
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCountAreaScreen(
    uiState: StockCountAreaUiState,
    lineToDelete: StockCountLineEntry?,
    onShowDeleteConfirm: (StockCountLineEntry?) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onConfigureIngredients: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onItemFilterChanged: (StockCountItemFilter) -> Unit,
    onToggleOrderEditing: () -> Unit,
    onMoveItem: (String, Int) -> Unit,
    onAddIngredient: (Ingredient) -> Unit,
    onQuantityChanged: (String, String) -> Unit,
    onUnitChanged: (String, String) -> Unit,
    onImeAction: (String) -> Unit,
    focusTarget: String?,
    onFocusHandled: () -> Unit,
    onConfirmDelete: (String) -> Unit,
    onCompleteArea: () -> Unit,
    onReopenArea: () -> Unit
) {
    var showMissingConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(focusTarget, uiState.lineEntries) {
        val target = focusTarget ?: return@LaunchedEffect
        val rowIndex = uiState.lineEntries.indexOfFirst { it.ingredientId == target }
        if (rowIndex < 0) return@LaunchedEffect
        val absoluteIndex = countRowAbsoluteIndex(
            rowIndex = rowIndex,
            searchResultCount = uiState.searchResults.size,
            hasSearchSection = uiState.searchQuery.length >= 2 && uiState.searchResults.isNotEmpty(),
            archivedWarningCount = uiState.archivedWarnings.size
        )
        val rowKey = "count-row-$target"
        listState.scrollToItem(absoluteIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == rowKey } }
            .filter { it }
            .first()
        focusRequesters.getOrPut(target) { FocusRequester() }.requestFocus()
        onFocusHandled()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    val title = when (uiState.screenState) {
                        is StockCountAreaScreenState.Ready -> uiState.details?.areaName ?: ""
                        is StockCountAreaScreenState.Loading -> stringResource(R.string.state_loading_desc)
                        is StockCountAreaScreenState.NotFound -> stringResource(R.string.error_count_area_not_found)
                        is StockCountAreaScreenState.InvalidRoute -> stringResource(R.string.error_invalid_count_route)
                        else -> stringResource(R.string.count_by_area)
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("count_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState.screenState) {
            is StockCountAreaScreenState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is StockCountAreaScreenState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_count_area_not_found))
                }
            }
            is StockCountAreaScreenState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding).testTag("stock_count_invalid_route"), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_invalid_count_route))
                }
            }
            is StockCountAreaScreenState.OwnershipMismatch -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_area_ownership_mismatch))
                }
            }
            is StockCountAreaScreenState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(state.throwable.toUserMessageRes()))
                }
            }
            is StockCountAreaScreenState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .adaptiveContentWidth(maxWidth = 960.dp)
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    if (uiState.canEdit) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            modifier = Modifier.fillMaxWidth().testTag("ingredient_search"),
                            label = { Text(stringResource(R.string.action_search)) },
                            placeholder = { Text(stringResource(R.string.search_ingredients)) }
                        )
                        Text(
                            text = stringResource(R.string.count_progress_format, uiState.countedItemCount, uiState.totalCountableItemCount),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp).testTag("count_item_progress")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StockCountItemFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = uiState.itemFilter == filter,
                                    onClick = { onItemFilterChanged(filter) },
                                    label = { Text(stringResource(when (filter) {
                                        StockCountItemFilter.ALL -> R.string.count_filter_all
                                        StockCountItemFilter.UNCOUNTED -> R.string.count_filter_uncounted
                                        StockCountItemFilter.COUNTED -> R.string.count_filter_counted
                                    })) }
                                )
                            }
                        }
                        TextButton(onClick = onToggleOrderEditing) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text(stringResource(R.string.edit_count_order), modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (uiState.searchQuery.length >= 2 && uiState.searchResults.isNotEmpty()) {
                            item {
                                Text(text = stringResource(R.string.search_ingredients), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                            }
                            items(uiState.searchResults, key = { "search-${it.id.value}" }) { ingredient ->
                                ListItem(
                                    headlineContent = { Text(ingredient.name) },
                                    trailingContent = {
                                        IconButton(onClick = { onAddIngredient(ingredient) }) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier.clickable { onAddIngredient(ingredient) }
                                )
                                HorizontalDivider()
                            }
                        }

                        item {
                            Text(text = stringResource(R.string.count_by_area), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        }

                        if (uiState.lineEntries.isEmpty()) {
                            item {
                                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(if (uiState.activeIngredientCount == 0) R.string.count_empty_no_ingredients else R.string.count_empty_area_assignment), style = MaterialTheme.typography.titleMedium)
                                    if (uiState.unassignedIngredientCount > 0) Text(stringResource(R.string.setup_unassigned_count, uiState.unassignedIngredientCount), modifier = Modifier.padding(top = 8.dp))
                                    Button(onClick = onConfigureIngredients, modifier = Modifier.padding(top = 12.dp).testTag("count_configure_ingredients")) { Text(stringResource(R.string.action_fix)) }
                                }
                            }
                        }

                        if (uiState.archivedWarnings.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.archived_nonzero_warning),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                            items(uiState.archivedWarnings, key = { "archived-${it.ingredientId}" }) { warning ->
                                ListItem(
                                    headlineContent = { Text(warning.name, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                                    supportingContent = {
                                        Text(
                                            text = stringResource(R.string.expected_quantity_format, warning.expectedBalanceBase.toPlainString(), ""),
                                            modifier = Modifier.testTag("archived_expected_${warning.ingredientId}")
                                        )
                                    }
                                )
                                HorizontalDivider()
                            }
                        }

                        items(uiState.lineEntries, key = { "count-row-${it.ingredientId}" }) { entry ->
                            StockCountLineItem(
                                entry = entry,
                                areaId = uiState.details?.area?.id?.value ?: "",
                                onQuantityChanged = { qty -> onQuantityChanged(entry.ingredientId, qty) },
                                onUnitChanged = { uid -> onUnitChanged(entry.ingredientId, uid) },
                                onImeAction = { onImeAction(entry.ingredientId) },
                                focusRequester = focusRequesters.getOrPut(entry.ingredientId) { FocusRequester() },
                                isLastVisible = entry == uiState.lineEntries.lastOrNull(),
                                isEditingOrder = uiState.isEditingOrder,
                                onMoveUp = { onMoveItem(entry.ingredientId, -1) },
                                onMoveDown = { onMoveItem(entry.ingredientId, 1) },
                                onDelete = { onShowDeleteConfirm(entry) },
                                enabled = uiState.canEdit
                            )
                            HorizontalDivider()
                        }
                    }

                    if (uiState.canReopen) {
                        Button(
                            onClick = onReopenArea,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("reopen_area_button"),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(stringResource(R.string.reopen_area))
                        }
                    } else if (uiState.canEdit) {
                        Button(
                            onClick = {
                                if (uiState.missingCount > 0) {
                                    showMissingConfirm = true
                                } else {
                                    onCompleteArea()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("complete_area_button"),
                            enabled = !uiState.isCompleting
                        ) {
                            if (uiState.isCompleting) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.complete_area))
                        }
                    }
                }
            }
        }
    }

    if (showMissingConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.missing_items),
            message = stringResource(R.string.complete_area_desc),
            isSaving = uiState.isCompleting,
            onDismiss = { if (!uiState.isCompleting) showMissingConfirm = false },
            onConfirm = onCompleteArea
        )
    }

    if (lineToDelete != null) {
        val isDeleting = uiState.deletingIngredientId == lineToDelete.ingredientId
        ArchiveConfirmDialog(
            title = stringResource(R.string.action_remove),
            message = stringResource(R.string.action_remove_item, lineToDelete.ingredientName),
            confirmText = stringResource(R.string.action_remove),
            isSaving = isDeleting,
            onDismiss = { if (!isDeleting) onShowDeleteConfirm(null) },
            onConfirm = { onConfirmDelete(lineToDelete.ingredientId) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCountLineItem(
    entry: StockCountLineEntry,
    areaId: String,
    onQuantityChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onImeAction: () -> Unit,
    focusRequester: FocusRequester,
    isLastVisible: Boolean,
    isEditingOrder: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().testTag("line_item_${areaId}_${entry.ingredientId}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.ingredientName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("line_ingredient_${areaId}_${entry.ingredientId}")
                )
                if (entry.categoryName != null) {
                    Text(text = entry.categoryName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            
            if (isEditingOrder) {
                IconButton(onClick = onMoveUp) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.count_move_up)) }
                IconButton(onClick = onMoveDown) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.count_move_down)) }
            } else if (enabled) {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_line_${areaId}_${entry.ingredientId}")) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = entry.quantityText,
                onValueChange = onQuantityChanged,
                modifier = Modifier.weight(1f).testTag("quantity_${areaId}_${entry.ingredientId}").focusRequester(focusRequester),
                enabled = enabled && !entry.isDeleting,
                label = { Text(stringResource(R.string.quantity)) },
                isError = entry.error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = if (isLastVisible) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() }
                ),
                singleLine = true,
                supportingText = entry.error?.let { { Text(stringResource(it.toUserMessageRes())) } }
            )

            var unitExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = unitExpanded && enabled && !entry.isDeleting,
                onExpandedChange = { if (enabled && !entry.isDeleting) unitExpanded = !unitExpanded },
                modifier = Modifier.weight(1f).testTag("unit_selector_${areaId}_${entry.ingredientId}")
            ) {
                OutlinedTextField(
                    value = entry.unitName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_unit)) }, 
                    trailingIcon = { if (enabled && !entry.isDeleting) ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    enabled = enabled && !entry.isDeleting
                )
                ExposedDropdownMenu(
                    expanded = unitExpanded,
                    onDismissRequest = { unitExpanded = false }
                ) {
                    entry.unitOptions.forEach { optionUi ->
                        DropdownMenuItem(
                            text = { Text(optionUi.label) },
                            onClick = {
                                if (optionUi.isSelectable) {
                                    onUnitChanged(optionUi.id.value)
                                    unitExpanded = false
                                }
                            },
                            enabled = optionUi.isSelectable
                        )
                    }
                }
            }
            
            if (enabled) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (entry.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp).testTag("save_indicator_saving_${areaId}_${entry.ingredientId}"), strokeWidth = 2.dp)
                    } else if (entry.isSaved) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.saved), modifier = Modifier.size(16.dp).testTag("save_indicator_saved_${areaId}_${entry.ingredientId}"), tint = MaterialTheme.colorScheme.primary)
                    } else if (entry.error != null) {
                        Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.state_error_desc), modifier = Modifier.size(16.dp).testTag("save_indicator_error_${areaId}_${entry.ingredientId}"), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Text(
            text = stringResource(if (entry.isCounted) R.string.count_filter_counted else R.string.count_not_counted),
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.isCounted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        val preview = entry.preview
        if (preview != null && hasLargeCountVariance(preview.expectedQuantityBase, preview.countedQuantityBase)) {
            Text(
                text = stringResource(R.string.count_large_variance_message),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("large_variance_${entry.ingredientId}")
            )
        }
        
        if (preview != null) {
            val unitLabel = if (entry.baseUnitName.isNullOrBlank()) stringResource(R.string.unknown_unit) else entry.baseUnitName
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (preview.willCreateOpeningBalance)
                        stringResource(R.string.opening_balance)
                    else
                        stringResource(R.string.expected_quantity_format, preview.expectedQuantityBase?.toPlainString() ?: "0", unitLabel),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("historical_expected_${areaId}_${entry.ingredientId}")
                )
                
                val adjustment = preview.provisionalAdjustmentBase
                val color = when {
                    adjustment > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
                    adjustment < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
                Text(
                    text = stringResource(R.string.adjustment_format, (if (adjustment > BigDecimal.ZERO) "+" else "") + adjustment.toPlainString(), unitLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.testTag("historical_adjustment_${areaId}_${entry.ingredientId}")
                )
            }
        }
    }
}
