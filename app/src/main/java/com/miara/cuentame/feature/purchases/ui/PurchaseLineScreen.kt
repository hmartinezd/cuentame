package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.domain.validation.toUserMessageRes
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseLineEvent
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseLineScreenState
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
                    val titleRes = when(uiState.screenState) {
                        is PurchaseLineScreenState.Ready -> if (uiState.lineId == null) R.string.add_line else R.string.edit_line
                        else -> R.string.purchases
                    }
                    Text(stringResource(titleRes))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (uiState.screenState) {
            is PurchaseLineScreenState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PurchaseLineScreenState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_purchase_line_not_found))
                }
            }
            is PurchaseLineScreenState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_purchase_not_found))
                }
            }
            is PurchaseLineScreenState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_generic))
                }
            }
            is PurchaseLineScreenState.Ready -> {
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
                        onExpandedChange = { if (!uiState.isSaving) ingredientExpanded = !ingredientExpanded }
                    ) {
                        val selectedIngredient = uiState.ingredients.find { it.id == uiState.selectedIngredientId }
                        OutlinedTextField(
                            value = selectedIngredient?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.ingredient_name)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ingredientExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            isError = uiState.fieldErrors.containsKey("ingredient"),
                            supportingText = uiState.fieldErrors["ingredient"]?.let { { Text(stringResource(it)) } },
                            enabled = !uiState.isSaving
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
                        onExpandedChange = { if (!uiState.isSaving) areaExpanded = !areaExpanded }
                    ) {
                        val selectedArea = uiState.areas.find { it.id == uiState.selectedAreaId }
                        OutlinedTextField(
                            value = selectedArea?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.receiving_area)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = areaExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            isError = uiState.fieldErrors.containsKey("area"),
                            supportingText = uiState.fieldErrors["area"]?.let { { Text(stringResource(it)) } },
                            enabled = !uiState.isSaving
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
                        onExpandedChange = { if (uiState.selectedIngredientId != null && !uiState.isSaving) unitExpanded = !unitExpanded }
                    ) {
                        val selectedOption = uiState.unitOptions.find { it.id == uiState.selectedUnitOptionId }
                        OutlinedTextField(
                            value = selectedOption?.displayName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.purchase_unit)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = uiState.selectedIngredientId != null && !uiState.isSaving,
                            isError = uiState.fieldErrors.containsKey("unit"),
                            supportingText = uiState.fieldErrors["unit"]?.let { { Text(stringResource(it)) } }
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.fieldErrors.containsKey("quantity"),
                        supportingText = uiState.fieldErrors["quantity"]?.let { { Text(stringResource(it)) } },
                        enabled = !uiState.isSaving
                    )

                    OutlinedTextField(
                        value = uiState.totalText,
                        onValueChange = onTotalChanged,
                        label = { Text(stringResource(R.string.line_total)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.fieldErrors.containsKey("total"),
                        supportingText = uiState.fieldErrors["total"]?.let { { Text(stringResource(it)) } },
                        enabled = !uiState.isSaving
                    )

                    if (uiState.baseQuantityPreview != null) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(
                                text = "= ${Formatters.formatQuantity(uiState.baseQuantityPreview, uiState.baseUnitSymbol)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (uiState.unitCostPreview != null) {
                                Text(
                                    text = "${stringResource(R.string.cost_per_base_unit, uiState.baseUnitSymbol)}: ${uiState.unitCostPreview.stripTrailingZeros().toPlainString()}",
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
                        minLines = 2,
                        enabled = !uiState.isSaving
                    )

                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                        }
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
