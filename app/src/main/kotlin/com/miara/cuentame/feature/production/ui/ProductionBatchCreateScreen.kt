package com.miara.cuentame.feature.production.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.ingredient.PreparationRecipeSummary
import com.miara.cuentame.core.presentation.ui.toDisplayText
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchCreateEvent
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchCreateUiState
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchCreateViewModel
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchScreenState
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun ProductionBatchCreateRoute(
    onBack: () -> Unit,
    onBatchCreated: (ProductionBatchId) -> Unit,
    viewModel: ProductionBatchCreateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is ProductionBatchCreateEvent.Created -> onBatchCreated(event.batchId)
            }
        }
    }

    ProductionBatchCreateScreen(
        uiState = uiState,
        onRecipeSelected = viewModel::onRecipeSelected,
        onMultiplierChanged = viewModel::onMultiplierChanged,
        onAreaSelected = viewModel::onAreaSelected,
        onUnitOptionSelected = viewModel::onUnitOptionSelected,
        onActualOutputChanged = viewModel::onActualOutputChanged,
        onEffectiveAtChanged = viewModel::onEffectiveAtChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onCreateClick = viewModel::onCreate,
        onBackClick = onBack,
        onRetry = viewModel::onRetry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionBatchCreateScreen(
    uiState: ProductionBatchCreateUiState,
    onRecipeSelected: (PreparationRecipeSummary) -> Unit,
    onMultiplierChanged: (String) -> Unit,
    onAreaSelected: (com.miara.cuentame.core.common.ids.InventoryAreaId) -> Unit,
    onUnitOptionSelected: (com.miara.cuentame.core.common.ids.IngredientUnitOptionId) -> Unit,
    onActualOutputChanged: (String) -> Unit,
    onEffectiveAtChanged: (java.time.Instant) -> Unit,
    onNotesChanged: (String) -> Unit,
    onCreateClick: () -> Unit,
    onBackClick: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("production_batch_create_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_production_batch)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RecipeSelector(
                        selected = uiState.selectedRecipeSummary,
                        recipes = uiState.availableRecipes,
                        onSelected = onRecipeSelected
                    )

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
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = uiState.actualOutputQuantity,
                        onValueChange = onActualOutputChanged,
                        label = { Text(stringResource(R.string.actual_output) + " (${stringResource(R.string.optional)})") },
                        modifier = Modifier.fillMaxWidth().testTag("production_actual_output_field"),
                        isError = uiState.actualOutputError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

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
                        onClick = onCreateClick,
                        modifier = Modifier.fillMaxWidth().testTag("production_batch_create"),
                        enabled = !uiState.isCreating
                    ) {
                        if (uiState.isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text(stringResource(R.string.action_add))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeSelector(
    selected: PreparationRecipeSummary?,
    recipes: List<PreparationRecipeSummary>,
    onSelected: (PreparationRecipeSummary) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().testTag("production_recipe_selector")
    ) {
        OutlinedTextField(
            value = selected?.recipeName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.production_recipe_selector)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            recipes.forEach { recipe ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(recipe.recipeName)
                            Text(
                                stringResource(R.string.output_ingredient) + ": ${recipe.outputIngredientName}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    onClick = {
                        onSelected(recipe)
                        expanded = false
                    }
                )
            }
        }
    }
}
