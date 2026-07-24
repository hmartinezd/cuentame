package com.miara.cuentame.feature.counts.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.domain.validation.toUserMessageRes
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.feature.counts.viewmodel.StockCountAreaEvent
import com.miara.cuentame.feature.counts.viewmodel.StockCountAreaScreenState
import com.miara.cuentame.feature.counts.viewmodel.StockCountAreaUiState
import com.miara.cuentame.feature.counts.viewmodel.StockCountAreaViewModel
import com.miara.cuentame.feature.counts.viewmodel.StockCountLineEntry
import com.miara.cuentame.feature.ingredients.ui.ArchiveConfirmDialog
import java.math.BigDecimal

@Composable
fun StockCountAreaRoute(
    onBack: () -> Unit,
    viewModel: StockCountAreaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StockCountAreaEvent.AreaCompleted -> onBack()
                is StockCountAreaEvent.NavigateBack -> onBack()
                is StockCountAreaEvent.ShowError -> {
                    snackbarHostState.showSnackbar(context.getString(event.error.toUserMessageRes()))
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
        snackbarHostState = snackbarHostState,
        onBack = viewModel::onBackRequested,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onAddIngredient = viewModel::onAddIngredient,
        onQuantityChanged = viewModel::onQuantityChanged,
        onUnitChanged = viewModel::onUnitChanged,
        onDeleteLine = viewModel::onDeleteLine,
        onCompleteArea = viewModel::onCompleteArea,
        onReopenArea = viewModel::onReopenArea
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCountAreaScreen(
    uiState: StockCountAreaUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onAddIngredient: (Ingredient) -> Unit,
    onQuantityChanged: (String, String) -> Unit,
    onUnitChanged: (String, String) -> Unit,
    onDeleteLine: (String) -> Unit,
    onCompleteArea: () -> Unit,
    onReopenArea: () -> Unit
) {
    var showMissingConfirm by remember { mutableStateOf(false) }
    var lineToDelete by remember { mutableStateOf<StockCountLineEntry?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    val title = when (uiState.screenState) {
                        is StockCountAreaScreenState.Ready -> uiState.details?.areaName ?: ""
                        is StockCountAreaScreenState.Loading -> stringResource(R.string.state_loading_desc)
                        is StockCountAreaScreenState.NotFound -> stringResource(R.string.state_empty_desc) // TODO: Specific
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
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
                        .fillMaxSize()
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
                    }

                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (uiState.searchQuery.length >= 2 && uiState.searchResults.isNotEmpty()) {
                            item {
                                Text(text = stringResource(R.string.search_ingredients), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                            }
                            items(uiState.searchResults) { ingredient ->
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

                        if (uiState.archivedWarnings.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.archived_nonzero_warning),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                            items(uiState.archivedWarnings) { warning ->
                                ListItem(
                                    headlineContent = { Text(warning.name, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                                    supportingContent = {
                                        Text(text = stringResource(R.string.expected_quantity_format, warning.expectedBalanceBase.toPlainString(), ""))
                                    }
                                )
                                HorizontalDivider()
                            }
                        }

                        items(uiState.lineEntries, key = { it.ingredientId }) { entry ->
                            StockCountLineItem(
                                entry = entry,
                                onQuantityChanged = { qty -> onQuantityChanged(entry.ingredientId, qty) },
                                onUnitChanged = { uid -> onUnitChanged(entry.ingredientId, uid) },
                                onDelete = { lineToDelete = entry },
                                enabled = uiState.canEdit
                            )
                            HorizontalDivider()
                        }
                    }

                    if (uiState.canReopen) {
                        Button(
                            onClick = onReopenArea,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
        ArchiveConfirmDialog(
            title = stringResource(R.string.action_remove),
            message = stringResource(R.string.action_remove_item, lineToDelete!!.ingredientName),
            isSaving = false,
            onDismiss = { lineToDelete = null },
            onConfirm = {
                onDeleteLine(lineToDelete!!.ingredientId)
                lineToDelete = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCountLineItem(
    entry: StockCountLineEntry,
    onQuantityChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                    modifier = Modifier.testTag("line_ingredient_${entry.ingredientId}")
                )
                if (entry.categoryName != null) {
                    Text(text = entry.categoryName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            
            if (enabled) {
                IconButton(onClick = onDelete) {
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
                modifier = Modifier.weight(1f).testTag("count_quantity_${entry.ingredientId}"),
                enabled = enabled,
                label = { Text(stringResource(R.string.quantity)) },
                isError = entry.error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                supportingText = entry.error?.let { { Text(stringResource(it.toUserMessageRes())) } }
            )

            var unitExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = unitExpanded && enabled,
                onExpandedChange = { if (enabled) unitExpanded = !unitExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = entry.unitName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_unit)) },
                    trailingIcon = { if (enabled) ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    enabled = enabled
                )
                ExposedDropdownMenu(
                    expanded = unitExpanded,
                    onDismissRequest = { unitExpanded = false }
                ) {
                    entry.unitOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                onUnitChanged(option.id.value)
                                unitExpanded = false
                            }
                        )
                    }
                }
            }
            
            if (enabled) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (entry.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp).testTag("save_indicator_saving_${entry.ingredientId}"), strokeWidth = 2.dp)
                    } else if (entry.isSaved) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.saved), modifier = Modifier.size(16.dp).testTag("save_indicator_saved_${entry.ingredientId}"), tint = MaterialTheme.colorScheme.primary)
                    } else if (entry.error != null) {
                        Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.state_error_desc), modifier = Modifier.size(16.dp).testTag("save_indicator_error_${entry.ingredientId}"), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        
        if (entry.preview != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (entry.preview.willCreateOpeningBalance) 
                        stringResource(R.string.opening_balance) 
                    else 
                        stringResource(R.string.expected_quantity_format, entry.preview.expectedQuantityBase ?: BigDecimal.ZERO, entry.baseUnitName),
                    style = MaterialTheme.typography.labelSmall
                )
                
                val adjustment = entry.preview.provisionalAdjustmentBase
                val color = when {
                    adjustment > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
                    adjustment < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
                Text(
                    text = stringResource(R.string.adjustment_format, (if (adjustment > BigDecimal.ZERO) "+" else "") + adjustment.toPlainString(), entry.baseUnitName),
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
        }
    }
}
