package com.miara.cuentame.feature.preparations.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.presentation.ui.UiMessage
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentEvent
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentUiState
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentViewModel

@Composable
fun PreparationRecipeComponentRoute(
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onNavigateToDetail: (PreparationRecipeId) -> Unit,
    viewModel: PreparationRecipeComponentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

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
        onBackClick = onBack,
        onSaveClick = viewModel::onSave,
        onIngredientSelected = viewModel::onIngredientSelected,
        onQuantityChanged = viewModel::onQuantityChanged,
        onUnitOptionSelected = viewModel::onUnitOptionSelected,
        onNotesChanged = viewModel::onNotesChanged,
        onRetry = { /* TODO: Implement in ViewModel */ }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationRecipeComponentScreen(
    uiState: PreparationRecipeComponentUiState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onIngredientSelected: (com.miara.cuentame.core.model.ingredient.Ingredient) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onUnitOptionSelected: (com.miara.cuentame.core.model.ingredient.IngredientUnitOption) -> Unit,
    onNotesChanged: (String) -> Unit,
    onRetry: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val isEditing = uiState.selectedIngredient != null // Simplified but enough for title logic if state is correctly initialized

    Scaffold(
        modifier = Modifier.testTag("preparation_recipe_component_screen"),
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.edit_component) else stringResource(R.string.add_component)) },
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
                        enabled = !uiState.isSaving && uiState.selectedIngredient != null && uiState.selectedUnitOptionId != null,
                        modifier = Modifier.testTag("recipe_component_save")
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
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.error is NoSuchElementException) {
                                if (uiState.error.message?.contains("Component") == true) stringResource(R.string.error_recipe_component_not_found)
                                else stringResource(R.string.error_recipe_not_found)
                            } else stringResource(R.string.error_load_recipe_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_component_load_error")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.error !is NoSuchElementException) {
                            Button(onClick = onRetry, modifier = Modifier.testTag("recipe_component_retry")) {
                                Text(stringResource(R.string.action_retry_desc))
                            }
                        }
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        IngredientSelector(
                            label = stringResource(R.string.ingredients_title),
                            selectedIngredient = uiState.selectedIngredient,
                            ingredients = uiState.availableIngredients,
                            onIngredientSelected = onIngredientSelected,
                            enabled = !uiState.isSaving && !isEditing, // Only allow selection if creating
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
                                errorText = uiState.quantityErrorText?.let { message ->
                                    when (message) {
                                        is UiMessage.Resource -> stringResource(message.id, *message.args.toTypedArray())
                                        is UiMessage.PlainTextInternalOnly -> message.value
                                    }
                                },
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
                            val text = when (message) {
                                is UiMessage.Resource -> stringResource(message.id, *message.args.toTypedArray())
                                is UiMessage.PlainTextInternalOnly -> message.value
                            }
                            InlineValidationMessage(message = text)
                        }
                    }
                }
            }
        }
    }
}
