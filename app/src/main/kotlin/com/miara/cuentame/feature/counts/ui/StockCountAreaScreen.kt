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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.miara.cuentame.core.model.inventory.CountAreaStatus
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

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    StockCountAreaScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onAddIngredient = viewModel::onAddIngredient,
        onQuantityChanged = viewModel::onQuantityChanged,
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
    onCompleteArea: () -> Unit,
    onReopenArea: () -> Unit
) {
    var showMissingConfirm by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.details?.areaName ?: stringResource(R.string.count_by_area)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.details == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_purchase_not_found))
            }
        } else {
            val details = uiState.details
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth().testTag("ingredient_search"),
                    label = { Text(stringResource(R.string.action_search)) },
                    placeholder = { Text(stringResource(R.string.search_ingredients)) },
                    enabled = details.area.status != CountAreaStatus.COMPLETED
                )

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (uiState.searchQuery.length >= 2 && uiState.searchResults.isNotEmpty()) {
                        item {
                            Text(text = "Search Results", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
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

                    items(uiState.lineEntries, key = { it.ingredientId }) { entry ->
                        StockCountLineItem(
                            entry = entry,
                            onQuantityChanged = { qty -> onQuantityChanged(entry.ingredientId, qty) },
                            enabled = details.area.status != CountAreaStatus.COMPLETED
                        )
                        HorizontalDivider()
                    }
                }

                if (details.area.status == CountAreaStatus.COMPLETED) {
                    Button(
                        onClick = onReopenArea,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(stringResource(R.string.reopen_area))
                    }
                } else {
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

    if (showMissingConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.missing_items),
            message = stringResource(R.string.complete_area_desc),
            isSaving = uiState.isCompleting,
            onDismiss = { if (!uiState.isCompleting) showMissingConfirm = false },
            onConfirm = {
                showMissingConfirm = false
                onCompleteArea()
            }
        )
    }
}

@Composable
fun StockCountLineItem(
    entry: StockCountLineEntry,
    onQuantityChanged: (String) -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.ingredientName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                if (entry.categoryName != null) {
                    Text(text = entry.categoryName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                OutlinedTextField(
                    value = entry.quantityText,
                    onValueChange = onQuantityChanged,
                    modifier = Modifier.size(width = 120.dp, height = 56.dp).testTag("count_quantity_${entry.ingredientId}"),
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text(entry.unitName) }
                )
                
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    if (entry.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp).testTag("save_indicator_saving_${entry.ingredientId}"), strokeWidth = 2.dp)
                    } else if (entry.isSaved) {
                        Icon(Icons.Default.Check, contentDescription = "Saved", modifier = Modifier.size(16.dp).testTag("save_indicator_saved_${entry.ingredientId}"), tint = MaterialTheme.colorScheme.primary)
                    } else if (entry.error != null) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", modifier = Modifier.size(16.dp).testTag("save_indicator_error_${entry.ingredientId}"), tint = MaterialTheme.colorScheme.error)
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
                        "${stringResource(R.string.expected_quantity)}: ${entry.preview.expectedQuantityBase ?: 0}",
                    style = MaterialTheme.typography.labelSmall
                )
                
                val adjustment = entry.preview.provisionalAdjustmentBase
                val color = when {
                    adjustment > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
                    adjustment < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
                Text(
                    text = "${stringResource(R.string.adjustment)}: $adjustment",
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
        }
    }
}
