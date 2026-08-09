package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.supplier.Supplier
import com.miara.cuentame.core.ocr.parser.*
import com.miara.cuentame.feature.purchases.viewmodel.ReviewDetectedInvoiceUiState
import com.miara.cuentame.feature.purchases.viewmodel.ReviewDetectedInvoiceViewModel
import java.math.BigDecimal
import java.time.LocalDate

enum class HeaderField {
    Supplier, InvoiceNumber, Date, Total, Tax
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetectedInvoiceRoute(
    receiptId: PurchaseReceiptId,
    onBack: () -> Unit,
    onViewDocument: (PurchaseReceiptId) -> Unit,
    onViewRawOcr: (PurchaseReceiptId) -> Unit,
    viewModel: ReviewDetectedInvoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReviewDetectedInvoiceScreen(
        uiState = uiState,
        onBack = onBack,
        onUpdateHeader = viewModel::onUpdateHeaderCorrections,
        onUpdateLine = viewModel::onUpdateLineCorrection,
        onToggleIgnoreLine = viewModel::onToggleIgnoreLine,
        onResetHeader = viewModel::onResetHeader,
        onResetLine = viewModel::onResetLine,
        onViewDocument = { onViewDocument(receiptId) },
        onViewRawOcr = { onViewRawOcr(receiptId) },
        onSelectSupplier = viewModel::onSelectSupplier,
        onConfirmMatch = viewModel::onConfirmMatch,
        onConfirmConflict = viewModel::onConfirmConflict,
        onSelectIngredientForMatch = viewModel::onSelectIngredientForMatch,
        onCreateQuickIngredient = viewModel::onCreateQuickIngredient
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetectedInvoiceScreen(
    uiState: ReviewDetectedInvoiceUiState,
    onBack: () -> Unit,
    onUpdateHeader: (PurchaseInvoiceCorrections) -> Unit,
    onUpdateLine: (Int, Boolean, ParsedInvoiceLineCorrection?) -> Unit,
    onToggleIgnoreLine: (Int) -> Unit,
    onResetHeader: () -> Unit,
    onResetLine: (Int) -> Unit,
    onViewDocument: () -> Unit,
    onViewRawOcr: () -> Unit,
    onSelectSupplier: (SupplierId) -> Unit,
    onConfirmMatch: (Int, IngredientId, IngredientUnitOptionId?, InventoryAreaId?) -> Unit,
    onConfirmConflict: (Boolean) -> Unit,
    onSelectIngredientForMatch: (IngredientId) -> Unit,
    onCreateQuickIngredient: (String, (IngredientId) -> Unit) -> Unit
) {
    var editingHeaderField by remember { mutableStateOf<HeaderField?>(null) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }
    var matchingLineIndex by remember { mutableStateOf<Int?>(null) }
    var showingSupplierSelection by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_action_review)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onViewDocument) {
                        Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.purchase_view_document))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.result == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.state_empty_desc))
            }
        } else {
            val result = uiState.result
            val corrections = result.corrections ?: PurchaseInvoiceCorrections()
            
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SupplierSelectionHeader(
                        selectedSupplierId = uiState.purchaseDetails?.receipt?.supplierId,
                        suggestedSuppliers = uiState.suggestedSuppliers,
                        onSelect = onSelectSupplier,
                        onChangeSupplier = { showingSupplierSelection = true }
                    )
                }

                item {
                    Text(stringResource(R.string.purchases), style = MaterialTheme.typography.titleMedium)
                    ParsedFieldRow(
                        label = stringResource(R.string.supplier_name), 
                        field = result.supplierNameCandidate,
                        correction = corrections.supplierName,
                        onClick = { editingHeaderField = HeaderField.Supplier }
                    )
                    ParsedFieldRow(
                        label = stringResource(R.string.invoice_number), 
                        field = result.invoiceNumber,
                        correction = corrections.invoiceNumber,
                        onClick = { editingHeaderField = HeaderField.InvoiceNumber }
                    )
                    ParsedFieldRow(
                        label = stringResource(R.string.purchase_date), 
                        field = result.invoiceDate,
                        correction = corrections.invoiceDate,
                        formatValue = { it?.toString() ?: "" },
                        onClick = { editingHeaderField = HeaderField.Date }
                    )
                }

                item {
                    HorizontalDivider()
                    Text(stringResource(R.string.receipt_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                    ParsedFieldRow(
                        label = stringResource(R.string.receipt_total), 
                        field = result.total,
                        correction = corrections.total,
                        formatValue = { Formatters.formatCurrency(it ?: BigDecimal.ZERO, result.currency.normalizedValue ?: "USD") },
                        onClick = { editingHeaderField = HeaderField.Total }
                    )
                    ParsedFieldRow(
                        label = stringResource(R.string.template_cat_other), 
                        field = result.tax,
                        correction = corrections.tax,
                        formatValue = { Formatters.formatCurrency(it ?: BigDecimal.ZERO, result.currency.normalizedValue ?: "USD") },
                        onClick = { editingHeaderField = HeaderField.Tax }
                    )
                }

                item {
                    HorizontalDivider()
                    MatchSummaryHeader(summary = uiState.matchSummary)
                }

                items(result.lines) { line ->
                    val match = uiState.matches.find { it.lineIndex == line.index }
                    ParsedInvoiceLineItem(
                        line = line,
                        match = match,
                        currency = result.currency.normalizedValue ?: "USD",
                        onToggleIgnore = { onToggleIgnoreLine(line.index) },
                        onEdit = { editingLineIndex = line.index },
                        onReset = { onResetLine(line.index) },
                        onMatchProduct = { matchingLineIndex = line.index }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onViewRawOcr,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.ocr_action_view_text))
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        
        if (editingHeaderField != null) {
            val result = uiState.result!!
            EditHeaderDialog(
                field = editingHeaderField!!,
                result = result,
                onDismiss = { editingHeaderField = null },
                onSave = { updatedCorrections ->
                    onUpdateHeader(updatedCorrections)
                    editingHeaderField = null
                }
            )
        }

        if (editingLineIndex != null) {
            val result = uiState.result!!
            val line = result.lines.find { it.index == editingLineIndex }
            if (line != null) {
                EditLineDialog(
                    line = line,
                    onDismiss = { editingLineIndex = null },
                    onSave = { correction ->
                        onUpdateLine(line.index, line.isIgnored, correction)
                        editingLineIndex = null
                    }
                )
            }
        }

        if (matchingLineIndex != null) {
            val line = uiState.result?.lines?.find { it.index == matchingLineIndex }
            if (line != null) {
                MatchProductDialog(
                    line = line,
                    currentMatch = uiState.matches.find { it.lineIndex == matchingLineIndex },
                    ingredients = uiState.allIngredients,
                    areas = uiState.allAreas,
                    unitOptions = uiState.ingredientUnitOptions,
                    onDismiss = { matchingLineIndex = null },
                    onConfirmMatch = { ingId, optId, areaId ->
                        onConfirmMatch(line.index, ingId, optId, areaId)
                        matchingLineIndex = null
                    },
                    onSelectIngredient = onSelectIngredientForMatch,
                    onCreateNewIngredient = { name, onCreated ->
                        onCreateQuickIngredient(name, onCreated)
                    }
                )
            }
        }

        if (showingSupplierSelection) {
            SupplierSelectionDialog(
                currentSupplierId = uiState.purchaseDetails?.receipt?.supplierId,
                suppliers = uiState.allSuppliers,
                onDismiss = { showingSupplierSelection = false },
                onSelect = {
                    onSelectSupplier(it)
                    showingSupplierSelection = false
                }
            )
        }

        uiState.activeMappingConflict?.let { conflict ->
            MatchingConflictDialog(
                conflict = conflict,
                onConfirm = { replace -> onConfirmConflict(replace) }
            )
        }
    }
}

@Composable
fun SupplierSelectionHeader(
    selectedSupplierId: SupplierId?,
    suggestedSuppliers: List<Supplier>,
    onSelect: (SupplierId) -> Unit,
    onChangeSupplier: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.supplier_name), style = MaterialTheme.typography.titleMedium)
        if (selectedSupplierId == null) {
            if (suggestedSuppliers.isNotEmpty()) {
                Text(
                    text = "Suggested based on invoice:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                suggestedSuppliers.forEach { supplier ->
                    AssistChip(
                        onClick = { onSelect(supplier.id) },
                        label = { Text(supplier.name) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            } else {
                Text(
                    text = "No supplier selected. Select one to enable matching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Button(
                onClick = onChangeSupplier,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Select Supplier")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Supplier confirmed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onChangeSupplier) {
                    Text("Change")
                }
            }
        }
    }
}

@Composable
fun MatchSummaryHeader(summary: com.miara.cuentame.feature.purchases.viewmodel.MatchSummary) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Matching Status", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusBadge(count = summary.matched, label = "Matched", color = Color(0xFF4CAF50))
            StatusBadge(count = summary.review, label = "Review", color = Color(0xFFFFA500))
            if (summary.unmatched > 0) {
                StatusBadge(count = summary.unmatched, label = "Unmatched", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun StatusBadge(count: Int, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$count", fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun MatchProductDialog(
    line: ParsedInvoiceLineCandidate,
    currentMatch: PurchaseInvoiceLineMatch?,
    ingredients: List<Ingredient>,
    areas: List<InventoryArea>,
    unitOptions: Map<IngredientId, List<IngredientUnitOption>>,
    onDismiss: () -> Unit,
    onConfirmMatch: (IngredientId, IngredientUnitOptionId?, InventoryAreaId?) -> Unit,
    onSelectIngredient: (IngredientId) -> Unit,
    onCreateNewIngredient: (String, (IngredientId) -> Unit) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var selectedUnitOptionId by remember { mutableStateOf<IngredientUnitOptionId?>(null) }
    var selectedAreaId by remember { mutableStateOf<InventoryAreaId?>(null) }

    val filteredIngredients = remember(searchQuery, ingredients) {
        if (searchQuery.isBlank()) emptyList()
        else ingredients.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(10)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(text = "Invoice: ${line.description.effectiveValue(line.correction?.description)}", style = MaterialTheme.typography.labelSmall)
                
                if (currentMatch != null && selectedIngredient == null) {
                    val ing = ingredients.find { it.id == currentMatch.ingredientId }
                    if (ing != null) {
                        Text(text = "Current match: ${ing.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (selectedIngredient == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Ingredient") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (filteredIngredients.isEmpty() && searchQuery.isNotBlank()) {
                        TextButton(
                            onClick = { 
                                onCreateNewIngredient(searchQuery) { newId ->
                                    onConfirmMatch(newId, null, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create new ingredient '$searchQuery'")
                        }
                    }

                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(filteredIngredients) { ing ->
                            ListItem(
                                headlineContent = { Text(ing.name) },
                                modifier = Modifier.clickable {
                                    selectedIngredient = ing
                                    selectedAreaId = ing.defaultAreaId
                                    onSelectIngredient(ing.id)
                                }
                            )
                        }
                    }
                } else {
                    ListItem(
                        headlineContent = { Text(selectedIngredient!!.name) },
                        trailingContent = { IconButton(onClick = { selectedIngredient = null }) { Icon(Icons.Default.Close, contentDescription = "Deselect") } },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                    )

                    val options = unitOptions[selectedIngredient!!.id] ?: emptyList()
                    if (options.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(options.find { it.id == selectedUnitOptionId }?.displayName ?: "Select Unit Option")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                options.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt.displayName) },
                                        onClick = {
                                            selectedUnitOptionId = opt.id
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    var areaExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { areaExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(areas.find { it.id == selectedAreaId }?.name ?: "Select Area")
                        }
                        DropdownMenu(expanded = areaExpanded, onDismissRequest = { areaExpanded = false }) {
                            areas.forEach { area ->
                                DropdownMenuItem(
                                    text = { Text(area.name) },
                                    onClick = {
                                        selectedAreaId = area.id
                                        areaExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIngredient != null) {
                        onConfirmMatch(selectedIngredient!!.id, selectedUnitOptionId, selectedAreaId)
                    }
                },
                enabled = selectedIngredient != null
            ) {
                Text("Confirm Match")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun SupplierSelectionDialog(
    currentSupplierId: SupplierId?,
    suppliers: List<Supplier>,
    onDismiss: () -> Unit,
    onSelect: (SupplierId) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredSuppliers = remember(searchQuery, suppliers) {
        if (searchQuery.isBlank()) suppliers
        else suppliers.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Supplier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Suppliers") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(filteredSuppliers) { supplier ->
                        val isSelected = supplier.id == currentSupplierId
                        ListItem(
                            headlineContent = { Text(supplier.name) },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable { onSelect(supplier.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun MatchingConflictDialog(
    conflict: com.miara.cuentame.core.domain.repository.MappingConflict,
    onConfirm: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onConfirm(false) },
        title = { Text("Mapping Conflict") },
        text = {
            Text("This supplier item is already mapped to an ingredient. Update the saved mapping to use your new selection for future invoices?")
        },
        confirmButton = {
            Button(onClick = { onConfirm(true) }) {
                Text("Update Mapping")
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(false) }) {
                Text("Keep Current")
            }
        }
    )
}

@Composable
fun EditHeaderDialog(
    field: HeaderField,
    result: PurchaseInvoiceParseResult,
    onDismiss: () -> Unit,
    onSave: (PurchaseInvoiceCorrections) -> Unit
) {
    val corrections = result.corrections ?: PurchaseInvoiceCorrections()
    var textValue by remember {
        mutableStateOf(
            when (field) {
                HeaderField.Supplier -> result.supplierNameCandidate.effectiveValue(corrections.supplierName) ?: ""
                HeaderField.InvoiceNumber -> result.invoiceNumber.effectiveValue(corrections.invoiceNumber) ?: ""
                HeaderField.Date -> result.invoiceDate.effectiveValue(corrections.invoiceDate)?.toString() ?: ""
                HeaderField.Total -> result.total.effectiveValue(corrections.total)?.toPlainString() ?: ""
                HeaderField.Tax -> result.tax.effectiveValue(corrections.tax)?.toPlainString() ?: ""
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${field.name}") },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text(field.name) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val updated = when (field) {
                    HeaderField.Supplier -> corrections.copy(supplierName = Correction(textValue))
                    HeaderField.InvoiceNumber -> corrections.copy(invoiceNumber = Correction(textValue))
                    HeaderField.Date -> {
                        val date = try { LocalDate.parse(textValue) } catch (e: Exception) { null }
                        if (date != null) corrections.copy(invoiceDate = Correction(date)) else corrections
                    }
                    HeaderField.Total -> {
                        val amount = try { BigDecimal(textValue) } catch (e: Exception) { null }
                        if (amount != null) corrections.copy(total = Correction(amount)) else corrections
                    }
                    HeaderField.Tax -> {
                        val amount = try { BigDecimal(textValue) } catch (e: Exception) { null }
                        if (amount != null) corrections.copy(tax = Correction(amount)) else corrections
                    }
                }
                onSave(updated)
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun EditLineDialog(
    line: ParsedInvoiceLineCandidate,
    onDismiss: () -> Unit,
    onSave: (ParsedInvoiceLineCorrection) -> Unit
) {
    val correction = line.correction ?: ParsedInvoiceLineCorrection()
    var description by remember { mutableStateOf(line.description.effectiveValue(correction.description) ?: "") }
    var quantity by remember { mutableStateOf(line.quantity.effectiveValue(correction.quantity)?.toPlainString() ?: "") }
    var unitPrice by remember { mutableStateOf(line.unitPrice.effectiveValue(correction.unitPrice)?.toPlainString() ?: "") }
    var lineTotal by remember { mutableStateOf(line.lineTotal.effectiveValue(correction.lineTotal)?.toPlainString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ocr_action_edit_line)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.product_description)) })
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text(stringResource(R.string.product_quantity)) })
                OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = it }, label = { Text(stringResource(R.string.product_price)) })
                OutlinedTextField(value = lineTotal, onValueChange = { lineTotal = it }, label = { Text(stringResource(R.string.line_total)) })
            }
        },
        confirmButton = {
            Button(onClick = {
                val amountQty = try { BigDecimal(quantity) } catch (e: Exception) { null }
                val amountPrice = try { BigDecimal(unitPrice) } catch (e: Exception) { null }
                val amountTotal = try { BigDecimal(lineTotal) } catch (e: Exception) { null }
                
                onSave(
                    correction.copy(
                        description = Correction(description),
                        quantity = Correction(amountQty),
                        unitPrice = Correction(amountPrice),
                        lineTotal = Correction(amountTotal)
                    )
                )
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun <T> ParsedFieldRow(
    label: String,
    field: ParsedField<T>,
    correction: Correction<T>?,
    formatValue: (T?) -> String = { it?.toString() ?: "" },
    onClick: () -> Unit
) {
    val effectiveValue = field.effectiveValue(correction)
    val isEdited = field.isEdited(correction)

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label, style = MaterialTheme.typography.labelMedium) },
        supportingContent = {
            Column {
                Text(
                    text = formatValue(effectiveValue),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (field.confidenceBand == ConfidenceBand.Low && !isEdited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (isEdited) {
                    Text(
                        text = "Edited (Original: ${formatValue(field.normalizedValue)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        trailingContent = {
            if (isEdited) {
                Icon(Icons.Default.Edit, contentDescription = "Edited", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            } else if (field.confidenceBand == ConfidenceBand.Low) {
                Icon(Icons.Default.Info, contentDescription = "Low confidence", tint = MaterialTheme.colorScheme.error)
            } else if (field.confidenceBand == ConfidenceBand.Medium) {
                Icon(Icons.Default.Info, contentDescription = "Medium confidence", tint = Color(0xFFFFA500)) // Orange
            }
        }
    )
}

@Composable
fun ParsedInvoiceLineItem(
    line: ParsedInvoiceLineCandidate,
    match: PurchaseInvoiceLineMatch?,
    currency: String,
    onToggleIgnore: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onMatchProduct: () -> Unit
) {
    val isEdited = line.correction != null
    val effectiveDesc = line.description.effectiveValue(line.correction?.description)
    val effectiveQty = line.quantity.effectiveValue(line.correction?.quantity)
    val effectivePrice = line.unitPrice.effectiveValue(line.correction?.unitPrice)
    val effectiveTotal = line.lineTotal.effectiveValue(line.correction?.lineTotal)
    val effectivePackage = line.packageText.effectiveValue(line.correction?.packageText)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onEdit),
        colors = if (line.isIgnored) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = effectiveDesc ?: stringResource(R.string.uncategorized),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (line.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${Formatters.formatQuantity(effectiveQty ?: BigDecimal.ZERO, effectivePackage)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "@ ${Formatters.formatCurrency(effectivePrice ?: BigDecimal.ZERO, currency)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text = Formatters.formatCurrency(effectiveTotal ?: BigDecimal.ZERO, currency),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    if (isEdited) {
                        IconButton(onClick = onReset) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset correction")
                        }
                    }
                    IconButton(onClick = onToggleIgnore) {
                        Icon(
                            imageVector = if (line.isIgnored) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (line.isIgnored) "Include line" else "Ignore line"
                        )
                    }
                }
            }

            if (!line.isIgnored) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        val isKnownMapping = match?.matchMethod == "KnownSupplierItem"
                        val statusColor = when {
                            match?.status == InvoiceLineMatchStatus.CONFIRMED -> Color(0xFF4CAF50)
                            match?.status == InvoiceLineMatchStatus.SUGGESTED && isKnownMapping -> MaterialTheme.colorScheme.primary
                            match?.status == InvoiceLineMatchStatus.SUGGESTED -> Color(0xFFFFA500)
                            else -> MaterialTheme.colorScheme.error
                        }
                        Icon(
                            imageVector = when {
                                match?.status == InvoiceLineMatchStatus.CONFIRMED -> Icons.Default.CheckCircle
                                isKnownMapping -> Icons.Default.CheckCircle
                                else -> Icons.Default.Inventory
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                match?.status == InvoiceLineMatchStatus.CONFIRMED -> "Matched"
                                match?.status == InvoiceLineMatchStatus.SUGGESTED && isKnownMapping -> "Known supplier item"
                                match?.status == InvoiceLineMatchStatus.SUGGESTED -> "Suggested"
                                else -> "Needs match"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                    TextButton(onClick = onMatchProduct) {
                        Text(if (match?.status == InvoiceLineMatchStatus.CONFIRMED) "Change" else "Match Product")
                    }
                }
            }

            if (line.warnings.isNotEmpty()) {
                Text(
                    text = line.warnings.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
