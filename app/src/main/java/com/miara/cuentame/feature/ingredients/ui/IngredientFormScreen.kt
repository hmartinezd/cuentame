package com.miara.cuentame.feature.ingredients.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.text.DecimalParser
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import com.miara.cuentame.feature.ingredients.model.IngredientFormUiState
import com.miara.cuentame.feature.ingredients.viewmodel.IngredientFormEvent
import com.miara.cuentame.feature.ingredients.viewmodel.IngredientFormViewModel
import java.math.BigDecimal

@Composable
fun IngredientFormRoute(
    ingredientId: IngredientId? = null,
    onBack: () -> Unit,
    viewModel: IngredientFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val compatibleUnits by viewModel.compatibleUnits.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is IngredientFormEvent.Success -> onBack()
            }
        }
    }

    IngredientFormScreen(
        uiState = uiState,
        categories = categories,
        compatibleUnits = compatibleUnits,
        onNameChanged = viewModel::onNameChanged,
        onCategorySelected = viewModel::onCategorySelected,
        onDimensionSelected = viewModel::onDimensionSelected,
        onBaseUnitSelected = viewModel::onBaseUnitSelected,
        onAddStandardOption = viewModel::onAddStandardOption,
        onAddPackageOption = viewModel::onAddPackageOption,
        onRemoveOption = viewModel::onRemoveOption,
        onSetDefaultCount = viewModel::onSetDefaultCount,
        onSetDefaultPurchase = viewModel::onSetDefaultPurchase,
        onSave = viewModel::onSave,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientFormScreen(
    uiState: IngredientFormUiState,
    categories: List<IngredientCategory>,
    compatibleUnits: List<UnitOfMeasure>,
    onNameChanged: (String) -> Unit,
    onCategorySelected: (IngredientCategoryId?) -> Unit,
    onDimensionSelected: (UnitDimension) -> Unit,
    onBaseUnitSelected: (UnitOfMeasure) -> Unit,
    onAddStandardOption: (UnitOfMeasure) -> Unit,
    onAddPackageOption: (String, BigDecimal) -> Unit,
    onRemoveOption: (String) -> Unit,
    onSetDefaultCount: (String) -> Unit,
    onSetDefaultPurchase: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    var showStandardDialog by remember { mutableStateOf(false) }
    var showPackageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (uiState.isEditMode) stringResource(R.string.edit_ingredient) 
                        else stringResource(R.string.add_ingredient)
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
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(R.string.ingredient_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.fieldErrors.containsKey("name"),
                    supportingText = uiState.fieldErrors["name"]?.let { { Text(stringResource(it)) } }
                )

                CategorySelector(
                    categories = categories,
                    selected = uiState.selectedCategoryId,
                    onSelected = onCategorySelected
                )

                if (!uiState.isEditMode) {
                    DimensionSelector(
                        selected = uiState.selectedDimension,
                        onSelected = onDimensionSelected
                    )

                    if (uiState.selectedDimension != null) {
                        BaseUnitSelector(
                            units = compatibleUnits,
                            selectedId = uiState.selectedBaseUnitId,
                            onSelected = onBaseUnitSelected
                        )
                    }
                } else {
                    Text("${stringResource(R.string.base_unit)}: ${uiState.selectedBaseUnitId?.value}")
                }

                if (uiState.selectedBaseUnitId != null) {
                    UnitOptionsSection(
                        options = uiState.unitOptions,
                        onAddStandard = { showStandardDialog = true },
                        onAddPackage = { showPackageDialog = true },
                        onRemove = onRemoveOption,
                        onSetDefaultCount = onSetDefaultCount,
                        onSetDefaultPurchase = onSetDefaultPurchase
                    )
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    enabled = !uiState.isSubmitting && uiState.name.isNotBlank() && uiState.selectedBaseUnitId != null
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp))
                    }
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }

    if (showStandardDialog) {
        StandardUnitDialog(
            units = compatibleUnits,
            onDismiss = { showStandardDialog = false },
            onSelect = { 
                onAddStandardOption(it)
                showStandardDialog = false
            }
        )
    }

    if (showPackageDialog) {
        AddPackageDialog(
            onDismiss = { showPackageDialog = false },
            onConfirm = { name, qty ->
                onAddPackageOption(name, qty)
                showPackageDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    categories: List<IngredientCategory>,
    selected: IngredientCategoryId?,
    onSelected: (IngredientCategoryId?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selected }?.name ?: stringResource(R.string.uncategorized)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uncategorized)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelected(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DimensionSelector(
    selected: UnitDimension?,
    onSelected: (UnitDimension) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dimensions = UnitDimension.entries

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.let { 
                when(it) {
                    UnitDimension.MASS -> stringResource(R.string.dim_mass)
                    UnitDimension.VOLUME -> stringResource(R.string.dim_volume)
                    UnitDimension.COUNT -> stringResource(R.string.dim_count)
                }
            } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.measurement_dimension)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            dimensions.forEach { dim ->
                DropdownMenuItem(
                    text = { 
                        Text(when(dim) {
                            UnitDimension.MASS -> stringResource(R.string.dim_mass)
                            UnitDimension.VOLUME -> stringResource(R.string.dim_volume)
                            UnitDimension.COUNT -> stringResource(R.string.dim_count)
                        })
                    },
                    onClick = {
                        onSelected(dim)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseUnitSelector(
    units: List<UnitOfMeasure>,
    selectedId: com.miara.cuentame.core.common.ids.UnitId?,
    onSelected: (UnitOfMeasure) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedUnit = units.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedUnit?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.base_unit)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text("${unit.name} (${unit.symbol})") },
                    onClick = {
                        onSelected(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun UnitOptionsSection(
    options: List<com.miara.cuentame.feature.ingredients.model.EditableUnitOptionUiModel>,
    onAddStandard: () -> Unit,
    onAddPackage: () -> Unit,
    onRemove: (String) -> Unit,
    onSetDefaultCount: (String) -> Unit,
    onSetDefaultPurchase: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.unit_options), style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = onAddStandard) { Text(stringResource(R.string.standard_unit)) }
                TextButton(onClick = onAddPackage) { Text(stringResource(R.string.package_option)) }
            }
        }

        options.forEach { option ->
            ListItem(
                headlineContent = { Text(option.name) },
                supportingContent = { Text("Factor: ${option.factorToBase}") },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onSetDefaultCount(option.id) }) {
                            Icon(
                                Icons.Default.Add, // Use appropriate icon
                                contentDescription = "Set Count Default",
                                tint = if (option.isDefaultCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onSetDefaultPurchase(option.id) }) {
                            Icon(
                                Icons.Default.Add, // Use appropriate icon
                                contentDescription = "Set Purchase Default",
                                tint = if (option.isDefaultPurchase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!option.isBase) {
                            IconButton(onClick = { onRemove(option.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun StandardUnitDialog(
    units: List<UnitOfMeasure>,
    onDismiss: () -> Unit,
    onSelect: (UnitOfMeasure) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.standard_unit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                units.forEach { unit ->
                    ListItem(
                        headlineContent = { Text(unit.name) },
                        trailingContent = { Text(unit.symbol) },
                        modifier = Modifier.clickable { onSelect(unit) }
                    )
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun AddPackageDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, BigDecimal) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.package_option)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.package_name)) }
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text(stringResource(R.string.contains_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    DecimalParser.parse(qtyText)?.let { onConfirm(name, it) }
                },
                enabled = name.isNotBlank() && DecimalParser.parse(qtyText)?.let { it > BigDecimal.ZERO } == true
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
