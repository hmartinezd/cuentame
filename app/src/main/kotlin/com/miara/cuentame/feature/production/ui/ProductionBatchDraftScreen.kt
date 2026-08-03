package com.miara.cuentame.feature.production.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.ProductionBatchComponentId
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.inventory.ProductionBatchComponent
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchDraftEvent
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchDraftUiState
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchDraftViewModel
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchScreenState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProductionBatchDraftRoute(
    onBack: () -> Unit,
    onNavigateToDetail: (ProductionBatchId) -> Unit,
    onDeleted: () -> Unit,
    onEditComponent: (ProductionBatchId, ProductionBatchComponentId) -> Unit,
    onReview: (ProductionBatchId) -> Unit,
    viewModel: ProductionBatchDraftViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is ProductionBatchDraftEvent.NavigateToDetail -> onNavigateToDetail(event.batchId)
                is ProductionBatchDraftEvent.NavigateToPreview -> onReview(event.batchId)
                ProductionBatchDraftEvent.Deleted -> onDeleted()
            }
        }
    }

    ProductionBatchDraftScreen(
        uiState = uiState,
        onBackClick = onBack,
        onSaveClick = viewModel::onSave,
        onDeleteClick = viewModel::onDelete,
        onReviewClick = { uiState.batch?.let { onReview(it.id) } },
        onMultiplierChanged = viewModel::onMultiplierChanged,
        onAreaSelected = viewModel::onAreaSelected,
        onUnitOptionSelected = viewModel::onUnitOptionSelected,
        onActualOutputChanged = viewModel::onActualOutputChanged,
        onEffectiveAtChanged = viewModel::onEffectiveAtChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onOverrideOutput = viewModel::onOverrideOutput,
        onComponentClick = { compId -> uiState.batch?.let { onEditComponent(it.id, compId) } },
        onRetry = viewModel::onRetry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionBatchDraftScreen(
    uiState: ProductionBatchDraftUiState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReviewClick: () -> Unit,
    onMultiplierChanged: (String) -> Unit,
    onAreaSelected: (com.miara.cuentame.core.common.ids.InventoryAreaId) -> Unit,
    onUnitOptionSelected: (com.miara.cuentame.core.common.ids.IngredientUnitOptionId) -> Unit,
    onActualOutputChanged: (String) -> Unit,
    onEffectiveAtChanged: (java.time.Instant) -> Unit,
    onNotesChanged: (String) -> Unit,
    onOverrideOutput: () -> Unit,
    onComponentClick: (ProductionBatchComponentId) -> Unit,
    onRetry: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("production_batch_draft_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.production_batch)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("production_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.testTag("production_batch_delete")) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_draft))
                    }
                }
            )
        }
    ) { padding ->
        when (val screenState = uiState.screenState) {
            ProductionBatchScreenState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ProductionBatchScreenState.LoadError -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = stringResource(R.string.state_error_desc))
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.action_retry_desc))
                        }
                    }
                }
            }
            ProductionBatchScreenState.Ready -> {
                val batch = uiState.batch ?: return@Scaffold
                
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            DraftHeader(
                                uiState = uiState,
                                onMultiplierChanged = onMultiplierChanged,
                                onAreaSelected = onAreaSelected,
                                onUnitOptionSelected = onUnitOptionSelected,
                                onActualOutputChanged = onActualOutputChanged,
                                onEffectiveAtChanged = onEffectiveAtChanged,
                                onNotesChanged = onNotesChanged,
                                onOverrideOutput = onOverrideOutput
                            )
                        }

                        item {
                            Text(stringResource(R.string.production_components), style = MaterialTheme.typography.titleMedium)
                        }

                        items(batch.components.sortedBy { it.sortOrder }, key = { it.id.value }) { component ->
                            ComponentItem(
                                component = component,
                                ingredientName = uiState.componentNames[component.id] ?: component.componentIngredientId.value,
                                unitLabel = uiState.componentUnitLabels[component.id] ?: component.unitOptionId.value,
                                recipeUnitLabel = uiState.componentRecipeUnitLabels[component.id] ?: "",
                                areaName = uiState.componentAreaNames[component.id],
                                onClick = { onComponentClick(component.id) }
                            )
                            HorizontalDivider()
                        }
                    }

                    ActionButtons(
                        onSave = onSaveClick,
                        onReview = onReviewClick,
                        isSaving = uiState.isSaving,
                        hasUnsavedChanges = uiState.hasUnsavedChanges
                    )
                }
            }
            ProductionBatchScreenState.InvalidRoute -> {
                 Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.error_generic))
                }
            }
            ProductionBatchScreenState.BatchNotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.error_batch_not_found))
                }
            }
            ProductionBatchScreenState.ComponentNotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.error_component_not_found))
                }
            }
            ProductionBatchScreenState.ParentNotEditable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.error_recipe_not_editable))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_draft)) },
            text = { Text(stringResource(R.string.delete_draft_confirm)) },
            confirmButton = {
                TextButton(onClick = { onDeleteClick(); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_back))
                }
            }
        )
    }
}

@Composable
private fun DraftHeader(
    uiState: ProductionBatchDraftUiState,
    onMultiplierChanged: (String) -> Unit,
    onAreaSelected: (com.miara.cuentame.core.common.ids.InventoryAreaId) -> Unit,
    onUnitOptionSelected: (com.miara.cuentame.core.common.ids.IngredientUnitOptionId) -> Unit,
    onActualOutputChanged: (String) -> Unit,
    onEffectiveAtChanged: (java.time.Instant) -> Unit,
    onNotesChanged: (String) -> Unit,
    onOverrideOutput: () -> Unit
) {
    val batch = uiState.batch ?: return
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = batch.recipeNameSnapshot, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = stringResource(R.string.output_ingredient) + ": ${uiState.outputIngredientName}", style = MaterialTheme.typography.bodyMedium)
            
            OutlinedTextField(
                value = uiState.multiplier,
                onValueChange = onMultiplierChanged,
                label = { Text(stringResource(R.string.batch_multiplier)) },
                modifier = Modifier.fillMaxWidth().testTag("production_multiplier_field"),
                isError = uiState.multiplierError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            AreaSelector(
                selectedId = uiState.selectedAreaId,
                areas = uiState.availableAreas,
                onSelected = onAreaSelected,
                label = stringResource(R.string.production_output_area_selector)
            )
            UnitSelector(
                selectedId = uiState.selectedUnitOptionId,
                options = uiState.availableUnitOptions,
                onSelected = onUnitOptionSelected,
                label = stringResource(R.string.production_output_unit_selector)
            )

            ProductionEffectiveTimeEditor(
                effectiveAt = uiState.effectiveAt,
                onEffectiveAtChanged = onEffectiveAtChanged
            )

            if (uiState.expectedOutputEntered != null) {
                Text(
                    text = stringResource(R.string.expected_output) + ": ${Formatters.formatQuantity(uiState.expectedOutputEntered)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (uiState.hasManualOutputOverride) {
                OutlinedTextField(
                    value = uiState.actualOutputQuantity,
                    onValueChange = onActualOutputChanged,
                    label = { Text(stringResource(R.string.actual_output)) },
                    modifier = Modifier.fillMaxWidth().testTag("production_actual_output_field"),
                    isError = uiState.actualOutputError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text(stringResource(R.string.manually_overridden)) }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.actual_output) + ": ${Formatters.formatQuantity(batch.actualOutputQuantityEntered)}",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOverrideOutput) {
                        Text(stringResource(R.string.override_actual_output))
                    }
                }
            }

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChanged,
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth().testTag("production_notes_field")
            )
        }
    }
}

@Composable
private fun ComponentItem(
    component: ProductionBatchComponent,
    ingredientName: String,
    unitLabel: String,
    recipeUnitLabel: String,
    areaName: String?,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("production_component_item_${component.id.value}"),
        headlineContent = { Text(ingredientName) },
        supportingContent = {
            Column {
                Row {
                    Text(text = stringResource(R.string.expected_quantity) + ": ${Formatters.formatQuantity(component.expectedQuantityEntered, recipeUnitLabel)}")
                }
                Text(text = stringResource(R.string.actual_output) + ": ${Formatters.formatQuantity(component.actualQuantityEntered, unitLabel)}")
                if (areaName != null) {
                    Text(text = stringResource(R.string.area_label) + ": $areaName")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.error_missing_component_area), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        trailingContent = {
            if (component.hasManualQuantityOverride) {
                Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.manually_overridden), modifier = Modifier.testTag("production_component_override_${component.id.value}"))
            }
        }
    )
}

@Composable
private fun ActionButtons(
    onSave: () -> Unit,
    onReview: () -> Unit,
    isSaving: Boolean,
    hasUnsavedChanges: Boolean
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column {
            if (hasUnsavedChanges) {
                Text(
                    text = stringResource(R.string.save_before_review),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f).testTag("production_batch_save"), enabled = !isSaving) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Text(stringResource(R.string.action_save))
                }
                Button(
                    onClick = onReview,
                    modifier = Modifier.weight(1f).testTag("production_batch_review"),
                    enabled = !isSaving && !hasUnsavedChanges
                ) {
                    Text(stringResource(R.string.review_and_post))
                }
            }
        }
    }
}
