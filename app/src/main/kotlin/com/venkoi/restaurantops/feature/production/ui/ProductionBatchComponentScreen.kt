package com.venkoi.restaurantops.feature.production.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.venkoi.restaurantops.core.designsystem.component.adaptiveContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.designsystem.util.Formatters
import com.venkoi.restaurantops.core.presentation.ui.toDisplayText
import com.venkoi.restaurantops.feature.production.viewmodel.ProductionBatchComponentEvent
import com.venkoi.restaurantops.feature.production.viewmodel.ProductionBatchComponentUiState
import com.venkoi.restaurantops.feature.production.viewmodel.ProductionBatchComponentViewModel
import com.venkoi.restaurantops.feature.production.viewmodel.ProductionBatchScreenState

@Composable
fun ProductionBatchComponentRoute(
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: ProductionBatchComponentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                ProductionBatchComponentEvent.Saved -> onSaveSuccess()
            }
        }
    }

    ProductionBatchComponentScreen(
        uiState = uiState,
        onBackClick = onBack,
        onSaveClick = viewModel::onSave,
        onAreaSelected = viewModel::onAreaSelected,
        onUnitOptionSelected = viewModel::onUnitOptionSelected,
        onQuantityChanged = viewModel::onQuantityChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onOverrideQuantity = viewModel::onOverrideQuantity,
        onResetToRecipe = viewModel::onResetToRecipe,
        onRetry = viewModel::onRetry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionBatchComponentScreen(
    uiState: ProductionBatchComponentUiState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onAreaSelected: (com.venkoi.restaurantops.core.common.ids.InventoryAreaId) -> Unit,
    onUnitOptionSelected: (com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onOverrideQuantity: () -> Unit,
    onResetToRecipe: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("production_batch_component_screen"),
        topBar = {
            TopAppBar(
                title = { Text(uiState.ingredientName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("production_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                val component = uiState.component ?: return@Scaffold
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .adaptiveContentWidth(760.dp)
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.expected_quantity) + ": ${Formatters.formatQuantity(component.expectedQuantityEntered, uiState.recipeUnitLabel)}",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    AreaSelector(
                        selectedId = uiState.selectedAreaId,
                        areas = uiState.availableAreas,
                        onSelected = onAreaSelected,
                        label = stringResource(R.string.production_component_area_selector),
                        modifier = Modifier.testTag("production_component_area_selector")
                    )

                    if (uiState.hasManualOverride) {
                        UnitSelector(
                            selectedId = uiState.selectedUnitOptionId,
                            options = uiState.availableUnitOptions,
                            onSelected = onUnitOptionSelected,
                            label = stringResource(R.string.production_component_unit_selector),
                            modifier = Modifier.testTag("production_component_unit_selector")
                        )

                        OutlinedTextField(
                            value = uiState.actualQuantity,
                            onValueChange = onQuantityChanged,
                            label = { Text(stringResource(R.string.production_component_quantity_field)) },
                            modifier = Modifier.fillMaxWidth().testTag("production_component_quantity_field"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = uiState.quantityError,
                            supportingText = {
                                val error = uiState.quantityErrorMessage?.toDisplayText()
                                if (error != null) {
                                    Text(error)
                                } else {
                                    Text(stringResource(R.string.manually_overridden))
                                }
                            }
                        )

                        TextButton(onClick = onResetToRecipe, modifier = Modifier.testTag("production_component_reset")) {
                            Text(stringResource(R.string.reset_to_recipe_quantity))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.actual_output) + ": ${Formatters.formatQuantity(component.actualQuantityEntered, uiState.recipeUnitLabel)}",
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onOverrideQuantity) {
                                Text(stringResource(R.string.override_quantity))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = onNotesChanged,
                        label = { Text(stringResource(R.string.notes)) },
                        modifier = Modifier.fillMaxWidth().testTag("production_notes_field"),
                        minLines = 3
                    )

                    if (uiState.inlineError != null) {
                        Text(
                            text = uiState.inlineError.toDisplayText(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.fillMaxWidth().testTag("production_batch_save"),
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text(stringResource(R.string.action_save))
                        }
                    }
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
}
