package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.domain.validation.toUserMessageRes
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseLineEvent
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseLineUiState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseLineViewModel

@Composable
fun PurchaseLineRoute(
    onBack: () -> Unit,
    viewModel: PurchaseLineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseLineEvent.Success -> onBack()
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    PurchaseLineScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onIngredientSelected = viewModel::onIngredientSelected,
        onAreaSelected = viewModel::onAreaSelected,
        onUnitOptionSelected = viewModel::onUnitOptionSelected,
        onQuantityChanged = viewModel::onQuantityChanged,
        onTotalChanged = viewModel::onTotalChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onSave = viewModel::onSave
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseLineScreen(
    uiState: PurchaseLineUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onIngredientSelected: (IngredientId) -> Unit,
    onAreaSelected: (InventoryAreaId) -> Unit,
    onUnitOptionSelected: (com.miara.cuentame.core.common.ids.IngredientUnitOptionId) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onTotalChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (uiState.lineId == null) stringResource(R.string.add_line)
                        else stringResource(R.string.edit_line)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ingredient Selector
            var ingredientExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = ingredientExpanded,
                onExpandedChange = { ingredientExpanded = !ingredientExpanded }
            ) {
                val selectedIngredient = uiState.ingredients.find { it.id == uiState.selectedIngredientId }
                OutlinedTextField(
                    value = selectedIngredient?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.ingredient_name)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ingredientExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = ingredientExpanded,
                    onDismissRequest = { ingredientExpanded = false }
                ) {
                    uiState.ingredients.forEach { ingredient ->
                        DropdownMenuItem(
                            text = { Text(ingredient.name) },
                            onClick = { onIngredientSelected(ingredient.id); ingredientExpanded = false }
                        )
                    }
                }
            }

            // Receiving Area Selector
            var areaExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = areaExpanded,
                onExpandedChange = { areaExpanded = !areaExpanded }
            ) {
                val selectedArea = uiState.areas.find { it.id == uiState.selectedAreaId }
                OutlinedTextField(
                    value = selectedArea?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.receiving_area)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = areaExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = areaExpanded,
                    onDismissRequest = { areaExpanded = false }
                ) {
                    uiState.areas.forEach { area ->
                        DropdownMenuItem(
                            text = { Text(area.name) },
                            onClick = { onAreaSelected(area.id); areaExpanded = false }
                        )
                    }
                }
            }

            // Unit Selector
            var unitExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { if (uiState.selectedIngredientId != null) unitExpanded = !unitExpanded }
            ) {
                val selectedOption = uiState.unitOptions.find { it.id == uiState.selectedUnitOptionId }
                OutlinedTextField(
                    value = selectedOption?.displayName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.purchase_unit)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = uiState.selectedIngredientId != null
                )
                ExposedDropdownMenu(
                    expanded = unitExpanded,
                    onDismissRequest = { unitExpanded = false }
                ) {
                    uiState.unitOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = { onUnitOptionSelected(option.id); unitExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.quantityText,
                onValueChange = onQuantityChanged,
                label = { Text(stringResource(R.string.quantity)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = uiState.totalText,
                onValueChange = onTotalChanged,
                label = { Text(stringResource(R.string.line_total)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            if (uiState.baseQuantityPreview != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = "= ${uiState.baseQuantityPreview.toPlainString()} ${uiState.baseUnitSymbol}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (uiState.unitCostPreview != null) {
                        Text(
                            text = "${stringResource(R.string.cost_per_base_unit, uiState.baseUnitSymbol)}: ${uiState.unitCostPreview.toPlainString()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChanged,
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = !uiState.isSaving && uiState.selectedUnitOptionId != null && uiState.quantityText.isNotBlank() && uiState.totalText.isNotBlank()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp))
                }
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
