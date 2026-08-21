package com.venkoi.cuentame.feature.preparations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.presentation.ui.UiMessage
import com.venkoi.cuentame.core.common.ids.PreparationRecipeId
import com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentEvent
import com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentUiState
import com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentViewModel

@Composable
fun PreparationRecipeComponentRoute(
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onNavigateToDetail: (PreparationRecipeId) -> Unit,
    viewModel: PreparationRecipeComponentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

    val backHandler = {
        viewModel.onBackAction(onBack)
    }

    BackHandler(onBack = backHandler)

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is PreparationRecipeComponentEvent.Saved -> onSaveSuccess()
                is PreparationRecipeComponentEvent.NavigateToDetail -> onNavigateToDetail(event.recipeId)
            }
        }
    }

    PreparationRecipeComponentScreen(
        uiState = uiState,
        onBackClick = backHandler,
        onSaveClick = viewModel::onSave,
        onIngredientSelected = viewModel::onIngredientSelected,
        onQuantityChanged = viewModel::onQuantityChanged,
        onUnitOptionSelected = viewModel::onUnitOptionSelected,
        onNotesChanged = viewModel::onNotesChanged,
        onRetry = viewModel::onRetry,
        onDismissDiscardConfirmation = viewModel::dismissDiscardConfirmation,
        onDiscardChanges = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationRecipeComponentScreen(
    uiState: PreparationRecipeComponentUiState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onIngredientSelected: (com.venkoi.cuentame.core.model.ingredient.Ingredient) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onUnitOptionSelected: (com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption) -> Unit,
    onNotesChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissDiscardConfirmation: () -> Unit,
    onDiscardChanges: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val loadState = uiState.loadState
    val mode = uiState.mode
    val isEditing = mode is com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentMode.Edit

    Scaffold(
        modifier = Modifier.testTag("preparation_recipe_component_screen"),
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.edit_component) else stringResource(R.string.add_component)) },
                modifier = Modifier.testTag("recipe_component_app_bar"),
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("preparation_back_button")) {
                        Icon(
                            imageVector = if (isEditing) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                            contentDescription = stringResource(if (isEditing) R.string.action_back else android.R.string.cancel)
                        )
                    }
                },
                actions = {
                    if (loadState is com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.CreateReady || 
                        loadState is com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.EditReady) {
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                onSaveClick()
                            },
                            enabled = !uiState.isSaving,
                            modifier = Modifier.testTag("recipe_component_save")
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (loadState) {
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.error_generic),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_component_invalid_route")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.RecipeNotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.error_recipe_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_component_recipe_not_found")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.ComponentNotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.error_recipe_component_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_component_not_found")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.ParentNotEditable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.error_recipe_not_editable),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_component_parent_not_editable")
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
                            modifier = Modifier.testTag("recipe_component_load_error")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRetry, modifier = Modifier.testTag("recipe_component_retry")) {
                            Text(stringResource(R.string.action_retry_desc))
                        }
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
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
                            label = stringResource(R.string.ingredients_title),
                            selectedIngredient = uiState.selectedIngredient,
                            ingredients = uiState.availableIngredients,
                            onIngredientSelected = onIngredientSelected,
                            enabled = !uiState.isSaving && !isEditing,
                            isError = false,
                            testTag = "recipe_component_ingredient_selector"
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BigDecimalField(
                                value = uiState.quantity,
                                onValueChange = onQuantityChanged,
                                label = stringResource(R.string.quantity),
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isSaving,
                                isError = uiState.quantityError,
                                errorText = uiState.quantityErrorText?.toRecipeDisplayText(),
                                testTag = "recipe_component_quantity_field"
                            )
                            UnitOptionSelector(
                                label = stringResource(R.string.field_unit),
                                selectedOptionId = uiState.selectedUnitOptionId,
                                options = uiState.availableUnitOptions,
                                onOptionSelected = onUnitOptionSelected,
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isSaving && uiState.selectedIngredient != null,
                                testTag = "recipe_component_unit_selector"
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = onNotesChanged,
                            modifier = Modifier.fillMaxWidth().testTag("recipe_component_notes_field"),
                            label = { Text(stringResource(R.string.notes)) },
                            enabled = !uiState.isSaving,
                            minLines = 3
                        )
                    }

                    uiState.inlineError?.let { message ->
                        item {
                            InlineValidationMessage(message = message.toRecipeDisplayText())
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
}

@Composable
private fun UiMessage.toRecipeDisplayText(): String =
    when (this) {
        is UiMessage.Resource ->
            stringResource(id, *args.toTypedArray())

        is UiMessage.PlainTextInternalOnly ->
            stringResource(R.string.error_generic)
    }
