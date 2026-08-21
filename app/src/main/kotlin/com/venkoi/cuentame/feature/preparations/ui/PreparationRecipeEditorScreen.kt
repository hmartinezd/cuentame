package com.venkoi.cuentame.feature.preparations.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.common.ids.PreparationRecipeComponentId
import com.venkoi.cuentame.core.common.ids.PreparationRecipeId
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeComponent
import com.venkoi.cuentame.core.presentation.ui.UiMessage
import com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeEditorEvent
import com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeEditorUiState
import com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeEditorViewModel

@Composable
fun PreparationRecipeEditorRoute(
    onBack: () -> Unit,
    onRecipeCreated: (PreparationRecipeId) -> Unit,
    onAddComponent: (PreparationRecipeId) -> Unit,
    onEditComponent: (PreparationRecipeId, PreparationRecipeComponentId) -> Unit,
    onNavigateToDetail: (PreparationRecipeId) -> Unit,
    viewModel: PreparationRecipeEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.recipe_saved)

    val backHandler = {
        viewModel.onBackAction(onBack)
    }

    BackHandler(onBack = backHandler, enabled = !uiState.isActivating)

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is PreparationRecipeEditorEvent.Created -> onRecipeCreated(event.recipeId)
                PreparationRecipeEditorEvent.DraftSaved -> {
                    snackbarHostState.showSnackbar(savedMessage)
                }
                is PreparationRecipeEditorEvent.NavigateToDetail -> onNavigateToDetail(event.recipeId)
            }
        }
    }

    PreparationRecipeEditorScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = backHandler,
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
        onMoveComponentDown = viewModel::onMoveComponentDown,
        onActivateClick = viewModel::onActivateClick,
        onActivateConfirm = viewModel::onActivateConfirm,
        onDismissActivateConfirmation = viewModel::dismissActivateConfirmation,
        onRetry = viewModel::onRetry,
        onDismissDiscardConfirmation = viewModel::dismissDiscardConfirmation,
        onDiscardChanges = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationRecipeEditorScreen(
    uiState: PreparationRecipeEditorUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onRecipeNameChanged: (String) -> Unit,
    onOutputIngredientSelected: (com.venkoi.cuentame.core.model.ingredient.Ingredient) -> Unit,
    onYieldQuantityChanged: (String) -> Unit,
    onYieldUnitOptionSelected: (com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption) -> Unit,
    onNotesChanged: (String) -> Unit,
    onAddComponent: () -> Unit,
    onEditComponent: (PreparationRecipeComponentId) -> Unit,
    onRemoveComponent: (PreparationRecipeComponentId) -> Unit,
    onMoveComponentUp: (PreparationRecipeComponentId) -> Unit,
    onMoveComponentDown: (PreparationRecipeComponentId) -> Unit,
    onActivateClick: () -> Unit,
    onActivateConfirm: () -> Unit,
    onDismissActivateConfirmation: () -> Unit,
    onRetry: () -> Unit,
    onDismissDiscardConfirmation: () -> Unit,
    onDiscardChanges: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val loadState = uiState.loadState
    val isEditing = loadState is com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.EditReady
    val isBusy = uiState.isOperating

    Scaffold(
        modifier = Modifier.testTag("preparation_recipe_editor_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isEditing) stringResource(R.string.edit_preparation_recipe) 
                        else stringResource(R.string.new_preparation_recipe)
                    ) 
                },
                modifier = Modifier.testTag("recipe_editor_app_bar"),
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick, 
                        modifier = Modifier.testTag("preparation_back_button"),
                        enabled = !uiState.isActivating
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                            contentDescription = stringResource(if (isEditing) R.string.action_back else android.R.string.cancel)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (loadState is com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.CreateReady || 
                loadState is com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.EditReady) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.testTag("recipe_editor_action_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSaveClick()
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recipe_editor_save")
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(stringResource(R.string.action_save))
                            }
                        }

                        if (isEditing) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onActivateClick()
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("recipe_editor_activate")
                            ) {
                                if (uiState.isActivating) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text(stringResource(R.string.activate_recipe))
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when (loadState) {
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.RecipeNotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.error_recipe_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_editor_load_error")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            is com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.LoadError -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val message = loadState.message.toRecipeDisplayText()
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_editor_load_error")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRetry, modifier = Modifier.testTag("recipe_editor_retry")) {
                            Text(stringResource(R.string.action_retry_desc))
                        }
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.error_generic),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_editor_invalid_route")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.ComponentNotFound,
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.ParentNotEditable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_generic))
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.CreateReady,
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.EditReady -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(
                        horizontal = maxOf(16.dp, (LocalConfiguration.current.screenWidthDp.dp - 760.dp) / 2),
                        vertical = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        IngredientSelector(
                            label = stringResource(R.string.output_ingredient),
                            selectedIngredient = uiState.selectedOutputIngredient,
                            ingredients = uiState.availableIngredients,
                            onIngredientSelected = onOutputIngredientSelected,
                            enabled = !isEditing && !isBusy,
                            isError = false,
                            testTag = "recipe_output_ingredient_selector"
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = uiState.recipeName,
                            onValueChange = onRecipeNameChanged,
                            modifier = Modifier.fillMaxWidth().testTag("recipe_name_field"),
                            label = { Text(stringResource(R.string.recipe_name)) },
                            enabled = !isBusy,
                            singleLine = true,
                            placeholder = { Text(uiState.selectedOutputIngredient?.name ?: "") }
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BigDecimalField(
                                value = uiState.yieldQuantity,
                                onValueChange = onYieldQuantityChanged,
                                label = stringResource(R.string.standard_yield),
                                modifier = Modifier.weight(1f),
                                enabled = !isBusy,
                                isError = uiState.yieldQuantityError,
                                errorText = uiState.yieldQuantityErrorText?.toRecipeDisplayText(),
                                testTag = "recipe_yield_quantity_field"
                            )
                            UnitOptionSelector(
                                label = stringResource(R.string.yield_unit),
                                selectedOptionId = uiState.selectedYieldUnitOptionId,
                                options = uiState.availableUnitOptions,
                                onOptionSelected = onYieldUnitOptionSelected,
                                modifier = Modifier.weight(1f),
                                enabled = !isBusy && uiState.selectedOutputIngredient != null,
                                testTag = "recipe_yield_unit_selector"
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = onNotesChanged,
                            modifier = Modifier.fillMaxWidth().testTag("recipe_notes_field"),
                            label = { Text(stringResource(R.string.notes)) },
                            enabled = !isBusy,
                            minLines = 3
                        )
                    }

                    uiState.inlineError?.let { message ->
                        item {
                            InlineValidationMessage(message = message.toRecipeDisplayText())
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
                                    enabled = !isBusy,
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
                                    enabled = !isBusy
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showDiscardConfirmation) {
        LifecycleConfirmationDialog(
            title = stringResource(R.string.discard_changes_title),
            message = stringResource(R.string.discard_changes_message),
            confirmText = stringResource(R.string.action_discard),
            dismissText = stringResource(R.string.action_stay),
            onConfirm = onDiscardChanges,
            onDismiss = onDismissDiscardConfirmation
        )
    }

    if (uiState.showActivateConfirmation) {
        LifecycleConfirmationDialog(
            modifier = Modifier.testTag("recipe_activate_confirm_dialog"),
            title = stringResource(R.string.confirm_activation_title),
            message = stringResource(R.string.confirm_activation_message),
            confirmText = stringResource(R.string.activate_recipe),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = onActivateConfirm,
            onDismiss = onDismissActivateConfirmation
        )
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
            Text("${com.venkoi.cuentame.core.designsystem.util.Formatters.formatQuantity(component.quantityEntered)} $unitLabel")
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
                IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.testTag("edit_recipe_component_${component.id.value}")) {
                    Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.action_edit))
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

@Composable
private fun UiMessage.toRecipeDisplayText(): String =
    when (this) {
        is UiMessage.Resource ->
            stringResource(id, *args.toTypedArray())

        is UiMessage.PlainTextInternalOnly ->
            stringResource(R.string.error_generic)
    }
