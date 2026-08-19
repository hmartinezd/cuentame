package com.miara.cuentame.feature.waste.ui

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.miara.cuentame.R
import com.miara.cuentame.core.designsystem.component.adaptiveContentWidth
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.presentation.validation.toUserMessageRes
import com.miara.cuentame.core.presentation.ui.toLabelRes
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.feature.waste.viewmodel.WasteFormEvent
import com.miara.cuentame.feature.waste.viewmodel.WasteFormScreenState
import com.miara.cuentame.feature.waste.viewmodel.WasteFormUiState
import com.miara.cuentame.feature.waste.viewmodel.WasteFormViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WasteFormRoute(
    onBack: () -> Unit,
    onSuccess: (WasteEventId) -> Unit,
    viewModel: WasteFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WasteFormEvent.Success -> onSuccess(event.wasteEventId)
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    WasteFormScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onIngredientSelected = viewModel::onIngredientSelected,
        onAreaSelected = viewModel::onAreaSelected,
        onUnitOptionSelected = viewModel::onUnitOptionSelected,
        onQuantityChanged = viewModel::onQuantityChanged,
        onReasonSelected = viewModel::onReasonSelected,
        onDateChanged = viewModel::onEffectiveAtChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onAttachmentChanged = { uri ->
            viewModel.onAttachmentChanged(uri?.toString())
        },
        onSave = viewModel::onSave
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteFormScreen(
    uiState: WasteFormUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onIngredientSelected: (IngredientId) -> Unit,
    onAreaSelected: (InventoryAreaId) -> Unit,
    onUnitOptionSelected: (com.miara.cuentame.core.common.ids.IngredientUnitOptionId) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onReasonSelected: (WasteReason) -> Unit,
    onDateChanged: (Instant) -> Unit,
    onNotesChanged: (String) -> Unit,
    onAttachmentChanged: (Uri?) -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showDatePicker by remember { mutableStateOf(false) }
    
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        onAttachmentChanged(uri)
    }

    Scaffold(
        modifier = Modifier.testTag("waste_form_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (uiState.wasteEventId == null) stringResource(R.string.log_waste)
                        else stringResource(R.string.edit_line)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("waste_form_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState.screenState) {
            is WasteFormScreenState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is WasteFormScreenState.SetupRequired -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_no_restaurant))
                }
            }
            is WasteFormScreenState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_waste_not_found))
                }
            }
            is WasteFormScreenState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_generic))
                }
            }
            is WasteFormScreenState.OwnershipMismatch -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_waste_ownership))
                }
            }
            is WasteFormScreenState.Immutable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_waste_immutable))
                }
            }
            is WasteFormScreenState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(state.throwable.toUserMessageRes()), modifier = Modifier.testTag("form_error_text"))
                }
            }
            is WasteFormScreenState.Ready -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                        .testTag("waste_form_scroll_page"),
                    contentAlignment = Alignment.TopCenter
                ) {
                  Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveContentWidth(maxWidth = 720.dp)
                        .padding(16.dp)
                        .testTag("waste_form_content"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                  ) {
                    // Ingredient Selector
                    var ingredientExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = ingredientExpanded,
                        onExpandedChange = { if (!uiState.isSaving) ingredientExpanded = !ingredientExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selectedIngredient = uiState.ingredients.find { it.id == uiState.selectedIngredientId }
                        val label = selectedIngredient?.let {
                            if (it.isActive) it.label else "${it.label} (${stringResource(R.string.archived_label)})"
                        } ?: ""
                        OutlinedTextField(
                            value = label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.ingredient_name)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ingredientExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("ingredient_selector"),
                            enabled = !uiState.isSaving
                        )
                        ExposedDropdownMenu(
                            expanded = ingredientExpanded,
                            onDismissRequest = { ingredientExpanded = false }
                        ) {
                            uiState.ingredients.forEach { ingredient ->
                                val label = if (ingredient.isActive) ingredient.label else "${ingredient.label} (${stringResource(R.string.archived_label)})"
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { onIngredientSelected(ingredient.id); ingredientExpanded = false },
                                    modifier = Modifier.testTag("ingredient_item_${ingredient.label}")
                                )
                            }
                        }
                    }

                    // Area Selector
                    var areaExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = areaExpanded,
                        onExpandedChange = { if (!uiState.isSaving) areaExpanded = !areaExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selectedArea = uiState.areas.find { it.id == uiState.selectedAreaId }
                        val label = selectedArea?.let {
                            if (it.isActive) it.label else "${it.label} (${stringResource(R.string.archived_label)})"
                        } ?: ""
                        OutlinedTextField(
                            value = label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.receiving_area)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = areaExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("area_selector"),
                            enabled = !uiState.isSaving
                        )
                        ExposedDropdownMenu(
                            expanded = areaExpanded,
                            onDismissRequest = { areaExpanded = false }
                        ) {
                            uiState.areas.forEach { area ->
                                val label = if (area.isActive) area.label else "${area.label} (${stringResource(R.string.archived_label)})"
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { onAreaSelected(area.id); areaExpanded = false },
                                    modifier = Modifier.testTag("area_item_${area.label}")
                                )
                            }
                        }
                    }

                    // Quantity and Unit Row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.quantityText,
                            onValueChange = onQuantityChanged,
                            label = { Text(stringResource(R.string.quantity)) },
                            modifier = Modifier.weight(1f).testTag("quantity_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !uiState.isSaving
                        )

                        var unitExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = unitExpanded,
                            onExpandedChange = { if (uiState.selectedIngredientId != null && !uiState.isSaving) unitExpanded = !unitExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val selectedUnit = uiState.unitOptions.find { it.id == uiState.selectedUnitOptionId }
                            val label = selectedUnit?.let {
                                if (it.isActive) it.label else "${it.label} (${stringResource(R.string.archived_label)})"
                            } ?: ""
                            OutlinedTextField(
                                value = label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.field_unit)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("unit_selector"),
                                enabled = uiState.selectedIngredientId != null && !uiState.isSaving
                            )
                            ExposedDropdownMenu(
                                expanded = unitExpanded,
                                onDismissRequest = { unitExpanded = false }
                            ) {
                                uiState.unitOptions.forEach { option ->
                                    val label = if (option.isActive) option.label else "${option.label} (${stringResource(R.string.archived_label)})"
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { onUnitOptionSelected(option.id); unitExpanded = false },
                                        enabled = option.isSelectable,
                                        modifier = Modifier.testTag("unit_item_${option.label}")
                                    )
                                }
                            }
                        }
                    }

                    // Reason Selector
                    var reasonExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = reasonExpanded,
                        onExpandedChange = { if (!uiState.isSaving) reasonExpanded = !reasonExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedReason?.let { stringResource(it.toLabelRes()) } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.waste_reason)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("reason_selector"),
                            enabled = !uiState.isSaving
                        )
                        ExposedDropdownMenu(
                            expanded = reasonExpanded,
                            onDismissRequest = { reasonExpanded = false }
                        ) {
                            WasteReason.entries.forEach { reason ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(reason.toLabelRes())) },
                                    onClick = { onReasonSelected(reason); reasonExpanded = false },
                                    modifier = Modifier.testTag("reason_item_${reason.name}")
                                )
                            }
                        }
                    }

                    // Date and Time Row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = dateFormatter.format(uiState.effectiveAt),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.effective_date)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isSaving,
                                trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable(enabled = !uiState.isSaving) { showDatePicker = true })
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = timeFormatter.format(uiState.effectiveAt),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.effective_time)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isSaving,
                                trailingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable(enabled = !uiState.isSaving) {
                                val dt = LocalDateTime.ofInstant(uiState.effectiveAt, ZoneId.systemDefault())
                                TimePickerDialog(context, { _, hour, minute ->
                                    val newDt = dt.withHour(hour).withMinute(minute)
                                    onDateChanged(newDt.atZone(ZoneId.systemDefault()).toInstant())
                                }, dt.hour, dt.minute, true).show()
                            })
                        }
                    }

                    // Previews
                    if (uiState.preview != null) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.unit_conversion_format, uiState.quantityText, uiState.preview.quantityBase.toPlainString(), uiState.preview.baseUnitSymbol ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("conversion_preview")
                            )
                            Text(
                                text = "${stringResource(R.string.current_balance)}: ${Formatters.formatQuantity(uiState.preview.currentAreaQuantityBase, uiState.preview.baseUnitSymbol)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("current_balance_preview")
                            )
                            Text(
                                text = "${stringResource(R.string.remaining_balance)}: ${Formatters.formatQuantity(uiState.preview.remainingAreaQuantityBase, uiState.preview.baseUnitSymbol)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.preview.createsNegativeBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.testTag("remaining_balance_preview")
                            )
                            if (uiState.preview.estimatedWasteValue != null) {
                                Text(
                                    text = "${stringResource(R.string.estimated_waste_value)}: ${Formatters.formatCurrency(uiState.preview.estimatedWasteValue, uiState.currencyCode)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("estimated_value_preview")
                                )
                            }
                            if (uiState.preview.createsNegativeBalance) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.testTag("negative_warning_row")
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Text(text = stringResource(R.string.negative_inventory_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else if (uiState.isLoadingPreview) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                    }

                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = onNotesChanged,
                        label = { Text(stringResource(R.string.notes)) },
                        modifier = Modifier.fillMaxWidth().testTag("notes_input"),
                        minLines = 2,
                        enabled = !uiState.isSaving
                    )

                    // Photo Attachment
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.add_photo), style = MaterialTheme.typography.titleMedium)
                        
                        if (uiState.attachmentUri != null) {
                            Box(modifier = Modifier.fillMaxWidth().size(200.dp)) {
                                AsyncImage(
                                    model = uiState.attachmentUri,
                                    contentDescription = stringResource(R.string.add_photo),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onError = {
                                        // Handle image unavailable if needed
                                    }
                                )
                                IconButton(
                                    onClick = { onAttachmentChanged(null) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_photo), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Button(onClick = { launcher.launch(arrayOf("image/*")) }, enabled = !uiState.isSaving) {
                                Text(stringResource(R.string.replace_photo))
                            }
                        } else {
                            IconButton(
                                onClick = { launcher.launch(arrayOf("image/*")) },
                                modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
                                enabled = !uiState.isSaving
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = stringResource(R.string.add_photo), modifier = Modifier.size(48.dp))
                            }
                        }
                    }

                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("waste_save_button"),
                        enabled = uiState.canSave
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.effectiveAt.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        val currentDt = LocalDateTime.ofInstant(uiState.effectiveAt, ZoneId.systemDefault())
                        val newDt = LocalDateTime.of(selectedDate, currentDt.toLocalTime())
                        onDateChanged(newDt.atZone(ZoneId.systemDefault()).toInstant())
                    }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
