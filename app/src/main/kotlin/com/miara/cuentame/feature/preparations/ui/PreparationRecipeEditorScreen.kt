package com.miara.cuentame.feature.preparations.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.model.ingredient.PreparationRecipeComponent
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeEditorEvent
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeEditorUiState
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeEditorViewModel

@Composable
fun PreparationRecipeEditorRoute(
    onBack: () -> Unit,
    onRecipeCreated: (PreparationRecipeId) -> Unit,
    onSaveSuccess: () -> Unit,
    onAddComponent: (PreparationRecipeId) -> Unit,
    onEditComponent: (PreparationRecipeId, PreparationRecipeComponentId) -> Unit,
    onNavigateToDetail: (PreparationRecipeId) -> Unit,
    viewModel: PreparationRecipeEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is PreparationRecipeEditorEvent.Created -> onRecipeCreated(event.recipeId)
                is PreparationRecipeEditorEvent.Saved -> onSaveSuccess()
                is PreparationRecipeEditorEvent.DeletedOrArchived -> onBack()
            }
        }
    }

    // Requirement 8: If the recipe changes to ACTIVE or ARCHIVED, navigate to its detail screen
    LaunchedEffect(uiState.recipe?.status) {
        val status = uiState.recipe?.status
        if (status != null && status != PreparationRecipeStatus.DRAFT) {
            onNavigateToDetail(uiState.recipe!!.id)
        }
    }

    PreparationRecipeEditorScreen(
        uiState = uiState,
        onBackClick = onBack,
        onSaveClick = viewModel::onSave,
        onRecipeNameChanged = viewModel::onRecipeNameChanged,
        onOutputIngredientSelected = viewModel::onOutputIngredientSelected,
        onYieldQuantityChanged = viewModel::onYieldQuantityChanged,
        onYieldUnitOptionSelected = viewModel::onYieldUnitOptionSelected,
        onNotesChanged = viewModel::onNotesChanged,
        onAddComponent = { uiState.recipe?.let { onAddComponent(it.id) } },
        onEditComponent = { compId -> uiState.recipe?.let { onEditComponent(it.id, compId) } },
        onRemoveComponent = viewModel::onRemoveComponent,
        onMoveComponentUp = viewModel::onMoveComponentUp,
        onMoveComponentDown = viewModel::onMoveComponentDown
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationRecipeEditorScreen(
    uiState: PreparationRecipeEditorUiState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onRecipeNameChanged: (String) -> Unit,
    onOutputIngredientSelected: (com.miara.cuentame.core.model.ingredient.Ingredient) -> Unit,
    onYieldQuantityChanged: (String) -> Unit,
    onYieldUnitOptionSelected: (com.miara.cuentame.core.model.ingredient.IngredientUnitOption) -> Unit,
    onNotesChanged: (String) -> Unit,
    onAddComponent: () -> Unit,
    onEditComponent: (PreparationRecipeComponentId) -> Unit,
    onRemoveComponent: (PreparationRecipeComponentId) -> Unit,
    onMoveComponentUp: (PreparationRecipeComponentId) -> Unit,
    onMoveComponentDown: (PreparationRecipeComponentId) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val isEditing = uiState.recipe != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.edit_ingredient) else stringResource(R.string.new_preparation_recipe)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            onSaveClick()
                        },
                        enabled = !uiState.isSaving && uiState.selectedOutputIngredient != null
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    IngredientSelector(
                        label = stringResource(R.string.output_ingredient),
                        selectedIngredient = uiState.selectedOutputIngredient,
                        ingredients = uiState.availableIngredients,
                        onIngredientSelected = onOutputIngredientSelected,
                        enabled = !isEditing && !uiState.isSaving,
                        isError = false
                    )
                }

                item {
                    OutlinedTextField(
                        value = uiState.recipeName,
                        onValueChange = onRecipeNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.recipe_name)) },
                        enabled = !uiState.isSaving,
                        singleLine = true
                    )
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigDecimalField(
                            value = uiState.yieldQuantity,
                            onValueChange = onYieldQuantityChanged,
                            label = stringResource(R.string.standard_yield),
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving,
                            isError = uiState.yieldQuantityError
                        )
                        UnitOptionSelector(
                            label = stringResource(R.string.yield_unit),
                            selectedOptionId = uiState.selectedYieldUnitOptionId,
                            options = uiState.availableUnitOptions,
                            onOptionSelected = onYieldUnitOptionSelected,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving && uiState.selectedOutputIngredient != null
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = onNotesChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.notes)) },
                        enabled = !uiState.isSaving,
                        minLines = 3
                    )
                }

                if (uiState.inlineError != null) {
                    item {
                        InlineValidationMessage(message = uiState.inlineError)
                    }
                }

                if (isEditing) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.components), style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = onAddComponent,
                                modifier = Modifier.testTag("add_recipe_component")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.add_component))
                            }
                        }
                    }

                    val components = uiState.recipe?.components?.sortedBy { it.sortOrder } ?: emptyList()
                    if (components.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.missing_components),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(components, key = { it.id.value }) { component ->
                            // TODO: Fetch these from UI state or repository
                            val ingredientName = uiState.componentNames[component.id] ?: component.componentIngredientId.value
                            val unitLabel = uiState.componentUnitLabels[component.id] ?: component.unitOptionId.value
                            
                            RecipeComponentRow(
                                component = component,
                                ingredientName = ingredientName,
                                unitLabel = unitLabel,
                                onEdit = { onEditComponent(component.id) },
                                onRemove = { onRemoveComponent(component.id) },
                                onMoveUp = { onMoveComponentUp(component.id) },
                                onMoveDown = { onMoveComponentDown(component.id) },
                                isFirst = component == components.first(),
                                isLast = component == components.last(),
                                enabled = !uiState.isSaving && !uiState.isReordering
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeComponentRow(
    component: PreparationRecipeComponent,
    ingredientName: String,
    unitLabel: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier
            .testTag("recipe_component_item_${component.id.value}")
            .clickable(enabled = enabled, onClick = onEdit),
        headlineContent = { Text(ingredientName, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text("${com.miara.cuentame.core.designsystem.util.Formatters.formatQuantity(component.quantityEntered)} $unitLabel")
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = enabled && !isFirst, modifier = Modifier.testTag("move_recipe_component_up_${component.id.value}")) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.move_up, ingredientName))
                }
                IconButton(onClick = onMoveDown, enabled = enabled && !isLast, modifier = Modifier.testTag("move_recipe_component_down_${component.id.value}")) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.move_down, ingredientName))
                }
                IconButton(onClick = { showDeleteConfirm = true }, enabled = enabled, modifier = Modifier.testTag("delete_recipe_component_${component.id.value}")) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove_item, ingredientName))
                }
            }
        }
    )

    if (showDeleteConfirm) {
        LifecycleConfirmationDialog(
            title = stringResource(R.string.action_remove),
            message = stringResource(R.string.action_remove_item, ingredientName),
            onConfirm = {
                onRemove()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
