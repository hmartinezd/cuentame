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
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentEvent
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentUiState
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeComponentViewModel

@Composable
fun PreparationRecipeComponentRoute(
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: PreparationRecipeComponentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is PreparationRecipeComponentEvent.Saved -> onSaveSuccess()
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
        onNotesChanged = viewModel::onNotesChanged
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
    onNotesChanged: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val isEditing = uiState.selectedIngredient != null // Simplified check

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_component)) },
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
                        enabled = !uiState.isSaving && uiState.selectedIngredient != null && uiState.selectedUnitOptionId != null
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
                        label = stringResource(R.string.ingredients_title),
                        selectedIngredient = uiState.selectedIngredient,
                        ingredients = uiState.availableIngredients,
                        onIngredientSelected = onIngredientSelected,
                        enabled = !uiState.isSaving,
                        isError = false
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
                            isError = uiState.quantityError
                        )
                        UnitOptionSelector(
                            label = stringResource(R.string.field_unit),
                            selectedOptionId = uiState.selectedUnitOptionId,
                            options = uiState.availableUnitOptions,
                            onOptionSelected = onUnitOptionSelected,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving && uiState.selectedIngredient != null
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
            }
        }
    }
}
