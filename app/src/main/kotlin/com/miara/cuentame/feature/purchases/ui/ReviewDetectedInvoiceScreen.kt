package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.designsystem.component.adaptiveContentWidth
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
import com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationFailure
import com.miara.cuentame.core.ocr.parser.*
import com.miara.cuentame.feature.purchases.viewmodel.ReviewDetectedInvoiceUiState
import com.miara.cuentame.feature.purchases.viewmodel.ReviewDetectedInvoiceViewModel
import com.miara.cuentame.feature.purchases.viewmodel.MatchSummary
import com.miara.cuentame.core.domain.repository.MappingConflict
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
    onAddIngredient: (String) -> Unit,
    viewModel: ReviewDetectedInvoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReviewDetectedInvoiceScreen(
        uiState = uiState,
        onBack = onBack,
        onUpdateHeader = viewModel::onUpdateHeaderCorrections,
        onUpdateLine = viewModel::onUpdateLineCorrection,
        onAddMissingLine = viewModel::onAddMissingLine,
        onToggleIgnoreLine = viewModel::onToggleIgnoreLine,
        onResetHeader = viewModel::onResetHeader,
        onResetLine = viewModel::onResetLine,
        onViewDocument = { onViewDocument(receiptId) },
        onViewRawOcr = { onViewRawOcr(receiptId) },
        onSelectSupplier = viewModel::onSelectSupplier,
        onConfirmMatch = viewModel::onConfirmMatch,
        onConfirmConflict = viewModel::onConfirmConflict,
        onSelectIngredientForMatch = viewModel::onSelectIngredientForMatch,
        onStartCreateIngredient = { lineIndex, name -> 
            viewModel.onStartCreateIngredient(lineIndex)
            onAddIngredient(name) 
        },
        onStartMatch = { viewModel.onStartMatch(it) },
        onApplyToDraft = viewModel::onApplyToDraft,
        onContinueDuplicate = viewModel::onContinueDuplicate,
        onClearMaterializationFailure = viewModel::clearMaterializationFailure
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetectedInvoiceScreen(
    uiState: ReviewDetectedInvoiceUiState,
    onBack: () -> Unit,
    onUpdateHeader: (PurchaseInvoiceCorrections) -> Unit,
    onUpdateLine: (Int, Boolean, ParsedInvoiceLineCorrection?) -> Unit,
    onAddMissingLine: (ParsedInvoiceLineCorrection) -> Unit,
    onToggleIgnoreLine: (Int) -> Unit,
    onResetHeader: () -> Unit,
    onResetLine: (Int) -> Unit,
    onViewDocument: () -> Unit,
    onViewRawOcr: () -> Unit,
    onSelectSupplier: (SupplierId) -> Unit,
    onConfirmMatch: (Int, IngredientId, IngredientUnitOptionId?, InventoryAreaId?) -> Unit,
    onConfirmConflict: (Boolean) -> Unit,
    onSelectIngredientForMatch: (IngredientId) -> Unit,
    onStartCreateIngredient: (Int, String) -> Unit,
    onStartMatch: (Int?) -> Unit,
    onApplyToDraft: () -> Unit,
    onContinueDuplicate: () -> Unit = {},
    onClearMaterializationFailure: () -> Unit
) {
    val result = uiState.result ?: return
    val details = uiState.purchaseDetails ?: return

    var selectingSupplier by remember { mutableStateOf(false) }
    var matchingLineIndex by remember { mutableStateOf<Int?>(uiState.matchingLineIndex) }
    var editingHeaderField by remember { mutableStateOf<HeaderField?>(null) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }
    var addingMissingLine by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.matchingLineIndex) {
        matchingLineIndex = uiState.matchingLineIndex
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_action_review)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onViewDocument) {
                        Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.purchase_view_document))
                    }
                    IconButton(onClick = onViewRawOcr) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.ocr_action_view_text))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().adaptiveContentWidth(maxWidth = 960.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    SupplierSelectionHeader(
                        currentSupplierId = details.receipt.supplierId,
                        suggestedName = result.supplierNameCandidate.effectiveValue(result.corrections?.supplierName),
                        suppliers = uiState.allSuppliers,
                        onSelectSupplier = onSelectSupplier,
                        onSearchSupplier = { selectingSupplier = true }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.ocr_header_corrections_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    ParsedFieldRow(
                        label = stringResource(R.string.ocr_invoice_number_label),
                        field = result.invoiceNumber,
                        correction = result.corrections?.invoiceNumber,
                        formatter = { it ?: "" },
                        onClick = { editingHeaderField = HeaderField.InvoiceNumber }
                    )
                    ParsedFieldRow(
                        label = stringResource(R.string.ocr_date_label),
                        field = result.invoiceDate,
                        correction = result.corrections?.invoiceDate,
                        formatter = { it?.toString() ?: "" },
                        onClick = { editingHeaderField = HeaderField.Date }
                    )
                    ParsedFieldRow(
                        label = stringResource(R.string.ocr_total_label),
                        field = result.total,
                        correction = result.corrections?.total,
                        formatter = { Formatters.formatCurrency(it ?: BigDecimal.ZERO, uiState.currencyCode) },
                        onClick = { editingHeaderField = HeaderField.Total }
                    )
                    ParsedFieldRow(
                        label = stringResource(R.string.ocr_tax_label),
                        field = result.tax,
                        correction = result.corrections?.tax,
                        formatter = { Formatters.formatCurrency(it ?: BigDecimal.ZERO, uiState.currencyCode) },
                        onClick = { editingHeaderField = HeaderField.Tax }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    MatchSummaryHeader(summary = uiState.matchSummary)
                }

                uiState.proposal?.let { proposal ->
                    item {
                        MaterializationImpactCard(
                            proposal = proposal,
                            currencyCode = uiState.currencyCode,
                            isMaterializing = uiState.isMaterializing,
                            onApply = onApplyToDraft
                        )
                    }
                }

                items(result.lines) { line ->
                    val match = uiState.matches.find { it.lineIndex == line.index }
                    val lineProposal = uiState.proposal?.lines?.find { it.lineIndex == line.index }
                    ParsedInvoiceLineItem(
                        line = line,
                        match = match,
                        blockingReason = lineProposal?.blockingReason,
                        currencyCode = uiState.currencyCode,
                        onEdit = { editingLineIndex = line.index },
                        onMatch = { onStartMatch(line.index) },
                        onToggleIgnore = { onToggleIgnoreLine(line.index) },
                        onReset = { onResetLine(line.index) }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { addingMissingLine = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text(stringResource(R.string.ocr_add_missing_item)) }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // Dialogs...
    if (selectingSupplier) {
        SupplierSelectionDialog(
            currentSupplierId = details.receipt.supplierId,
            suppliers = uiState.allSuppliers,
            onDismiss = { selectingSupplier = false },
            onSelectSupplier = {
                onSelectSupplier(it)
                selectingSupplier = false
            }
        )
    }

    if (matchingLineIndex != null) {
        val line = result.lines.find { it.index == matchingLineIndex }
        if (line != null) {
            val currentMatch = uiState.matches.find { it.lineIndex == matchingLineIndex }
            MatchProductDialog(
                line = line,
                currentMatch = currentMatch,
                preselectedIngredientId = uiState.preselectedIngredientId,
                allIngredients = uiState.allIngredients,
                allAreas = uiState.allAreas,
                ingredientUnitOptions = uiState.ingredientUnitOptions,
                isConfirming = uiState.isConfirmingMatch,
                error = uiState.confirmMatchError,
                onDismiss = { onStartMatch(null) },
                onConfirmMatch = { ingredientId, unitOptionId, areaId ->
                    onConfirmMatch(line.index, ingredientId, unitOptionId, areaId)
                },
                onSelectIngredient = onSelectIngredientForMatch,
                onAddIngredient = { name -> onStartCreateIngredient(line.index, name) }
            )
        }
    }

    if (uiState.activeMappingConflict != null) {
        MatchingConflictDialog(
            conflict = uiState.activeMappingConflict!!,
            onConfirm = onConfirmConflict
        )
    }

    if (editingHeaderField != null) {
        EditHeaderDialog(
            field = editingHeaderField!!,
            result = result,
            onDismiss = { editingHeaderField = null },
            onUpdate = {
                onUpdateHeader(it)
                editingHeaderField = null
            }
        )
    }

    if (editingLineIndex != null) {
        val line = result.lines.find { it.index == editingLineIndex }
        if (line != null) {
            EditLineDialog(
                line = line,
                onDismiss = { editingLineIndex = null },
                onUpdate = {
                    onUpdateLine(line.index, line.isIgnored, it)
                    editingLineIndex = null
                }
            )
        }
    }

    if (addingMissingLine) {
        ManualLineDialog(
            onDismiss = { addingMissingLine = false },
            onAdd = {
                onAddMissingLine(it)
                addingMissingLine = false
            }
        )
    }

    if (uiState.materializationFailure != null) {
        MaterializationFailureDialog(
            failure = uiState.materializationFailure!!,
            onDismiss = onClearMaterializationFailure,
            onContinueDuplicate = onContinueDuplicate
        )
    }

    if (uiState.isMaterialized) {
        AlertDialog(
            onDismissRequest = onBack,
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_ok)) }
            },
            title = { Text(stringResource(R.string.ocr_materialization_success_title)) },
            text = { Text(stringResource(R.string.ocr_materialization_success)) }
        )
    }
}

@Composable
fun SupplierSelectionHeader(
    currentSupplierId: SupplierId?,
    suggestedName: String?,
    suppliers: List<Supplier>,
    onSelectSupplier: (SupplierId) -> Unit,
    onSearchSupplier: () -> Unit
) {
    val currentSupplier = currentSupplierId?.let { id -> suppliers.find { it.id == id } }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ocr_supplier_section_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (currentSupplier != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0.1f, 0.6f, 0.1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = currentSupplier.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onSearchSupplier) {
                        Text(stringResource(R.string.ocr_matching_change))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestedName ?: stringResource(R.string.ocr_matching_not_detected),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (suggestedName == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ocr_matching_no_supplier_selected),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onSearchSupplier) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_search))
                    }
                }
            }
        }
    }
}

@Composable
private fun matchReasonLabel(method: String?): String? = when (method) {
    "ConfirmedSupplierSku" -> stringResource(R.string.ocr_match_reason_supplier_sku)
    "ConfirmedSupplierDescriptionPackage" -> stringResource(R.string.ocr_match_reason_supplier_description_package)
    "ExactIngredientName" -> stringResource(R.string.ocr_match_reason_exact_name)
    "SimilarDescription", "DescriptionAndPackageMatch" -> stringResource(R.string.ocr_match_reason_similar_description)
    null -> null
    else -> null
}

@Composable
fun MatchSummaryHeader(summary: MatchSummary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatusBadge(count = summary.matched, label = stringResource(R.string.ocr_matching_matched), color = Color(0.1f, 0.6f, 0.1f))
        StatusBadge(count = summary.review, label = stringResource(R.string.ocr_matching_review), color = Color(0.8f, 0.5f, 0.0f))
        StatusBadge(count = summary.unmatched, label = stringResource(R.string.ocr_matching_unmatched), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun StatusBadge(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = color,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchProductDialog(
    line: ParsedInvoiceLineCandidate,
    currentMatch: PurchaseInvoiceLineMatch?,
    preselectedIngredientId: IngredientId?,
    allIngredients: List<Ingredient>,
    allAreas: List<InventoryArea>,
    ingredientUnitOptions: Map<IngredientId, List<IngredientUnitOption>>,
    isConfirming: Boolean,
    error: com.miara.cuentame.feature.purchases.viewmodel.MatchConfirmationError?,
    onDismiss: () -> Unit,
    onConfirmMatch: (IngredientId, IngredientUnitOptionId?, InventoryAreaId?) -> Unit,
    onSelectIngredient: (IngredientId) -> Unit,
    onAddIngredient: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val initialIngredientId = preselectedIngredientId ?: currentMatch?.ingredientId
    var selectedIngredientId by remember { mutableStateOf<IngredientId?>(initialIngredientId) }
    var selectedUnitOptionId by remember { 
        mutableStateOf<IngredientUnitOptionId?>(
            if (selectedIngredientId != null && selectedIngredientId == currentMatch?.ingredientId) currentMatch?.unitOptionId else null
        ) 
    }
    var selectedAreaId by remember { 
        mutableStateOf<InventoryAreaId?>(
            if (selectedIngredientId != null && selectedIngredientId == currentMatch?.ingredientId) {
                currentMatch?.inventoryAreaId
            } else {
                allIngredients.find { it.id == selectedIngredientId }?.defaultAreaId
            }
        ) 
    }

    val filteredIngredients = remember(searchQuery, allIngredients) {
        if (searchQuery.isBlank()) allIngredients.take(10)
        else allIngredients.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(20)
    }

    LaunchedEffect(selectedIngredientId) {
        selectedIngredientId?.let { onSelectIngredient(it) }
    }

    AlertDialog(
        onDismissRequest = if (isConfirming) ({}) else onDismiss,
        title = { Text(stringResource(R.string.ocr_matching_match_product)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = line.description.effectiveValue(line.correction?.description) ?: stringResource(R.string.ocr_matching_unknown_product),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.ocr_matching_invoice_package, line.packageText.effectiveValue(line.correction?.packageText) ?: stringResource(R.string.not_applicable)),
                    style = MaterialTheme.typography.bodySmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ocr_matching_search_ingredient)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    enabled = !isConfirming
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (selectedIngredientId == null) {
                    filteredIngredients.forEach { ing ->
                        ListItem(
                            headlineContent = { Text(ing.name) },
                            modifier = Modifier.clickable(enabled = !isConfirming) { 
                                selectedIngredientId = ing.id
                                selectedUnitOptionId = null
                                selectedAreaId = ing.defaultAreaId
                            }
                        )
                    }
                    
                    if (searchQuery.isNotBlank() && filteredIngredients.none { it.name.equals(searchQuery, true) }) {
                        TextButton(
                            onClick = { onAddIngredient(searchQuery) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isConfirming
                        ) {
                            Icon(Icons.Default.Inventory, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ocr_matching_create_new_ingredient, searchQuery))
                        }
                    }
                } else {
                    val selectedIng = allIngredients.find { it.id == selectedIngredientId }
                    ListItem(
                        headlineContent = { Text(selectedIng?.name ?: "") },
                        trailingContent = {
                            IconButton(onClick = { 
                                selectedIngredientId = null 
                                selectedUnitOptionId = null
                                selectedAreaId = null
                            }, enabled = !isConfirming) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(stringResource(R.string.ocr_matching_select_unit_option), style = MaterialTheme.typography.labelMedium)
                    val options = ingredientUnitOptions[selectedIngredientId] ?: emptyList()
                    options.forEach { opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !isConfirming) { selectedUnitOptionId = opt.id }
                        ) {
                            RadioButton(selected = selectedUnitOptionId == opt.id, onClick = { selectedUnitOptionId = opt.id }, enabled = !isConfirming)
                            Text(opt.displayName)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(stringResource(R.string.ocr_matching_select_area), style = MaterialTheme.typography.labelMedium)
                    allAreas.forEach { area ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !isConfirming) { selectedAreaId = area.id }
                        ) {
                            RadioButton(selected = selectedAreaId == area.id, onClick = { selectedAreaId = area.id }, enabled = !isConfirming)
                            Text(area.name)
                        }
                    }
                }

                error?.let { e ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (e) {
                            com.miara.cuentame.feature.purchases.viewmodel.MatchConfirmationError.SourceChanged -> stringResource(R.string.ocr_materialization_parse_changed)
                            com.miara.cuentame.feature.purchases.viewmodel.MatchConfirmationError.SourceLocked -> stringResource(R.string.ocr_materialization_error_source_locked)
                            com.miara.cuentame.feature.purchases.viewmodel.MatchConfirmationError.InvalidSelection -> stringResource(R.string.ocr_materialization_invalid_confirmed_match)
                            com.miara.cuentame.feature.purchases.viewmodel.MatchConfirmationError.Generic -> stringResource(R.string.error_generic)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            val canConfirm = remember(selectedIngredientId, selectedUnitOptionId, selectedAreaId, ingredientUnitOptions) {
                ReviewDetectedInvoiceViewModel.isMatchSelectionValid(
                    selectedIngredientId,
                    selectedUnitOptionId,
                    selectedAreaId,
                    ingredientUnitOptions[selectedIngredientId]
                )
            }

            Button(
                onClick = { 
                    if (canConfirm) {
                        onConfirmMatch(selectedIngredientId!!, selectedUnitOptionId, selectedAreaId) 
                    }
                },
                enabled = canConfirm && !isConfirming
            ) {
                if (isConfirming) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConfirming) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun SupplierSelectionDialog(
    currentSupplierId: SupplierId?,
    suppliers: List<Supplier>,
    onDismiss: () -> Unit,
    onSelectSupplier: (SupplierId) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = suppliers.filter { it.name.contains(query, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ocr_matching_select_supplier)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.ocr_matching_search_suppliers)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(filtered) { supplier ->
                        ListItem(
                            headlineContent = { Text(supplier.name) },
                            trailingContent = {
                                if (supplier.id == currentSupplierId) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable { onSelectSupplier(supplier.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_confirm)) }
        }
    )
}

@Composable
fun MatchingConflictDialog(
    conflict: MappingConflict,
    onConfirm: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onConfirm(false) },
        title = { Text(stringResource(R.string.ocr_matching_mapping_conflict)) },
        text = {
            Text(stringResource(R.string.ocr_matching_mapping_conflict, conflict.existingMapping.ingredientId.value))
        },
        confirmButton = {
            Button(onClick = { onConfirm(true) }) { Text(stringResource(R.string.ocr_matching_update_mapping)) }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(false) }) { Text(stringResource(R.string.ocr_matching_keep_current)) }
        }
    )
}

@Composable
fun EditHeaderDialog(
    field: HeaderField,
    result: PurchaseInvoiceParseResult,
    onDismiss: () -> Unit,
    onUpdate: (PurchaseInvoiceCorrections) -> Unit
) {
    var value by remember { mutableStateOf(
        when(field) {
            HeaderField.Supplier -> ""
            HeaderField.InvoiceNumber -> result.invoiceNumber.effectiveValue(result.corrections?.invoiceNumber) ?: ""
            HeaderField.Date -> result.invoiceDate.effectiveValue(result.corrections?.invoiceDate)?.toString() ?: ""
            HeaderField.Total -> result.total.effectiveValue(result.corrections?.total)?.toPlainString() ?: ""
            HeaderField.Tax -> result.tax.effectiveValue(result.corrections?.tax)?.toPlainString() ?: ""
        }
    )}

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ocr_header_correction_dialog_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { 
                    Text(
                        when(field) {
                            HeaderField.Supplier -> stringResource(R.string.ocr_supplier_section_title)
                            HeaderField.InvoiceNumber -> stringResource(R.string.ocr_invoice_number_label)
                            HeaderField.Date -> stringResource(R.string.ocr_date_label)
                            HeaderField.Total -> stringResource(R.string.ocr_total_label)
                            HeaderField.Tax -> stringResource(R.string.ocr_tax_label)
                        }
                    )
                }
            )
        },
        confirmButton = {
            Button(onClick = {
                val newCorrections = when(field) {
                    HeaderField.InvoiceNumber -> result.corrections?.copy(invoiceNumber = Correction(value)) ?: PurchaseInvoiceCorrections(invoiceNumber = Correction(value))
                    HeaderField.Date -> {
                        val date = try { LocalDate.parse(value) } catch(e: Exception) { null }
                        result.corrections?.copy(invoiceDate = Correction(date)) ?: PurchaseInvoiceCorrections(invoiceDate = Correction(date))
                    }
                    HeaderField.Total -> {
                        val amount = try { BigDecimal(value) } catch(e: Exception) { null }
                        result.corrections?.copy(total = Correction(amount)) ?: PurchaseInvoiceCorrections(total = Correction(amount))
                    }
                    HeaderField.Tax -> {
                        val amount = try { BigDecimal(value) } catch(e: Exception) { null }
                        result.corrections?.copy(tax = Correction(amount)) ?: PurchaseInvoiceCorrections(tax = Correction(amount))
                    }
                    else -> result.corrections ?: PurchaseInvoiceCorrections()
                }
                onUpdate(newCorrections)
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun EditLineDialog(
    line: ParsedInvoiceLineCandidate,
    onDismiss: () -> Unit,
    onUpdate: (ParsedInvoiceLineCorrection) -> Unit
) {
    var description by remember { mutableStateOf(line.description.effectiveValue(line.correction?.description) ?: "") }
    var vendorCode by remember { mutableStateOf(line.vendorCode.effectiveValue(line.correction?.vendorCode) ?: "") }
    var packageText by remember { mutableStateOf(line.packageText.effectiveValue(line.correction?.packageText) ?: "") }
    var quantity by remember { mutableStateOf(line.quantity.effectiveValue(line.correction?.quantity)?.toPlainString() ?: "") }
    var unitPrice by remember { mutableStateOf(line.unitPrice.effectiveValue(line.correction?.unitPrice)?.toPlainString() ?: "") }
    var total by remember { mutableStateOf(line.lineTotal.effectiveValue(line.correction?.lineTotal)?.toPlainString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ocr_line_correction_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.ocr_product_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vendorCode, onValueChange = { vendorCode = it }, label = { Text(stringResource(R.string.ocr_vendor_code)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = packageText, onValueChange = { packageText = it }, label = { Text(stringResource(R.string.ocr_package_description)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text(stringResource(R.string.product_quantity)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = it }, label = { Text(stringResource(R.string.product_price)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = total, onValueChange = { total = it }, label = { Text(stringResource(R.string.ocr_total_label)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val correction = ParsedInvoiceLineCorrection(
                    description = Correction(description.trim().ifBlank { null }),
                    vendorCode = Correction(vendorCode.trim().ifBlank { null }),
                    packageText = Correction(packageText.trim().ifBlank { null }),
                    quantity = Correction(try { BigDecimal(quantity) } catch(e: Exception) { null }),
                    unitPrice = Correction(try { BigDecimal(unitPrice) } catch(e: Exception) { null }),
                    lineTotal = Correction(try { BigDecimal(total) } catch(e: Exception) { null })
                )
                onUpdate(correction)
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun ManualLineDialog(
    onDismiss: () -> Unit,
    onAdd: (ParsedInvoiceLineCorrection) -> Unit
) {
    val blank = ParsedInvoiceLineCandidate.manual(0)
    EditLineDialog(line = blank, onDismiss = onDismiss, onUpdate = onAdd)
}

@Composable
fun MaterializationImpactCard(
    proposal: PurchaseInvoiceDraftProposal,
    currencyCode: String,
    isMaterializing: Boolean,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.status_draft),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            val readyCount = proposal.lines.count { it.blockingReason == null }
            
            Text(
                text = stringResource(R.string.ocr_materialization_lines_ready, readyCount),
                style = MaterialTheme.typography.bodyMedium
            )

            if (proposal.blockingIssues.isNotEmpty()) {
                proposal.blockingIssues.forEach { issue ->
                    Text(
                        text = when(issue) {
                            MaterializationBlockingIssue.UnresolvedLines -> stringResource(R.string.ocr_materialization_unresolved_lines_warning)
                            MaterializationBlockingIssue.MissingSupplier -> stringResource(R.string.ocr_matching_no_supplier_selected)
                            MaterializationBlockingIssue.PurchaseAlreadyPosted -> stringResource(R.string.ocr_materialization_error_already_posted)
                            MaterializationBlockingIssue.DocumentChanged -> stringResource(R.string.ocr_error_document_changed)
                            MaterializationBlockingIssue.ParseChanged -> stringResource(R.string.ocr_materialization_parse_changed)
                            MaterializationBlockingIssue.UnresolvedMatch -> stringResource(R.string.ocr_materialization_unresolved_match)
                            MaterializationBlockingIssue.InvalidConfirmedMatch -> stringResource(R.string.ocr_materialization_invalid_confirmed_match)
                            MaterializationBlockingIssue.MissingIngredient -> stringResource(R.string.ocr_materialization_missing_ingredient)
                            MaterializationBlockingIssue.MissingUnitOption -> stringResource(R.string.ocr_materialization_missing_unit_option)
                            MaterializationBlockingIssue.InvalidUnitOption -> stringResource(R.string.ocr_materialization_invalid_unit_option)
                            MaterializationBlockingIssue.MissingArea -> stringResource(R.string.ocr_materialization_missing_area)
                            MaterializationBlockingIssue.MissingQuantity -> stringResource(R.string.ocr_materialization_missing_quantity)
                            MaterializationBlockingIssue.InvalidQuantity -> stringResource(R.string.ocr_materialization_invalid_quantity)
                            MaterializationBlockingIssue.MissingLineTotal -> stringResource(R.string.ocr_materialization_missing_line_total)
                            MaterializationBlockingIssue.InvalidLineTotal -> stringResource(R.string.ocr_materialization_invalid_line_total)
                            MaterializationBlockingIssue.InvalidConversion -> stringResource(R.string.ocr_materialization_invalid_conversion)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            val readyLines = proposal.lines.filter { it.blockingReason == null }
            if (readyLines.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = stringResource(R.string.ocr_materialization_conversion_preview),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                readyLines.forEach { line ->
                    LineConversionRow(line, currencyCode)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isMaterializing && proposal.blockingIssues.isEmpty() && readyCount > 0
            ) {
                if (isMaterializing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.ocr_materialization_apply_to_draft))
                }
            }
        }
    }
}

@Composable
private fun LineConversionRow(
    line: com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceLineProposal,
    currencyCode: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.ingredientName ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${Formatters.formatQuantity(line.quantityEntered ?: BigDecimal.ZERO)} ${line.unitOptionName ?: ""} × ${Formatters.formatQuantity(line.factorToBase ?: BigDecimal.ONE)} = ${Formatters.formatQuantity(line.quantityBase ?: BigDecimal.ZERO)} ${line.baseUnitSymbol ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = Formatters.formatCurrency(line.lineTotal ?: BigDecimal.ZERO, currencyCode),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun MaterializationFailureDialog(
    failure: PurchaseInvoiceMaterializationFailure,
    onDismiss: () -> Unit,
    onContinueDuplicate: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (failure is PurchaseInvoiceMaterializationFailure.StrongDuplicate) {
                TextButton(onClick = onContinueDuplicate) { Text(stringResource(R.string.ocr_duplicate_continue)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = if (failure is PurchaseInvoiceMaterializationFailure.StrongDuplicate) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
        } else null,
        title = { Text(stringResource(R.string.ocr_materialization_failure_title)) },
        text = {
            Text(
                text = when (failure) {
                    PurchaseInvoiceMaterializationFailure.PurchaseAlreadyPosted -> stringResource(R.string.ocr_materialization_error_already_posted)
                    PurchaseInvoiceMaterializationFailure.ManualEditConflict -> stringResource(R.string.ocr_materialization_error_conflict)
                    PurchaseInvoiceMaterializationFailure.DocumentChanged -> stringResource(R.string.ocr_error_document_changed)
                    PurchaseInvoiceMaterializationFailure.InvoiceStateChanged -> stringResource(R.string.ocr_materialization_parse_changed)
                    PurchaseInvoiceMaterializationFailure.InvoiceSourceLocked -> stringResource(R.string.ocr_materialization_error_source_locked)
                    PurchaseInvoiceMaterializationFailure.UnresolvedLines -> stringResource(R.string.ocr_materialization_error_blocked_line)
                    is PurchaseInvoiceMaterializationFailure.StrongDuplicate -> when (failure.candidate.type) {
                        com.miara.cuentame.core.model.purchase.DuplicateInvoiceType.SAME_DOCUMENT -> stringResource(R.string.ocr_duplicate_same_document)
                        com.miara.cuentame.core.model.purchase.DuplicateInvoiceType.SAME_SUPPLIER_INVOICE_NUMBER -> stringResource(R.string.ocr_duplicate_same_supplier_number, failure.candidate.normalizedInvoiceNumber.orEmpty())
                    }
                    else -> stringResource(R.string.error_generic)
                }
            )
        }
    )
}

@Composable
fun <T> ParsedFieldRow(
    label: String,
    field: ParsedField<T>,
    correction: Correction<T>?,
    formatter: (T?) -> String,
    onClick: () -> Unit
) {
    val isEdited = field.isEdited(correction)
    val effectiveValue = field.effectiveValue(correction)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = formatter(effectiveValue),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isEdited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isEdited) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (isEdited) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ParsedInvoiceLineItem(
    line: ParsedInvoiceLineCandidate,
    match: PurchaseInvoiceLineMatch?,
    blockingReason: com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue?,
    currencyCode: String,
    onEdit: () -> Unit,
    onMatch: () -> Unit,
    onToggleIgnore: () -> Unit,
    onReset: () -> Unit
) {
    val isIgnored = line.isIgnored
    val opacity = if (isIgnored) 0.5f else 1.0f
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isIgnored) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp).alpha(opacity)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.description.effectiveValue(line.correction?.description) ?: stringResource(R.string.ocr_matching_not_detected),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ocr_edit_description))
                    }
                    Text(
                        text = stringResource(R.string.ocr_matching_vendor_code_label, line.vendorCode.effectiveValue(line.correction?.vendorCode) ?: stringResource(R.string.not_applicable)),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatCurrency(line.lineTotal.effectiveValue(line.correction?.lineTotal) ?: BigDecimal.ZERO, currencyCode),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${Formatters.formatQuantity(line.quantity.effectiveValue(line.correction?.quantity) ?: BigDecimal.ZERO)} @ ${Formatters.formatCurrency(line.unitPrice.effectiveValue(line.correction?.unitPrice) ?: BigDecimal.ZERO, currencyCode)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            if (blockingReason != null && !isIgnored) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when(blockingReason) {
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.UnresolvedMatch -> stringResource(R.string.ocr_materialization_unresolved_match)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.InvalidConfirmedMatch -> stringResource(R.string.ocr_materialization_invalid_confirmed_match)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.MissingIngredient -> stringResource(R.string.ocr_materialization_missing_ingredient)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.MissingUnitOption -> stringResource(R.string.ocr_materialization_missing_unit_option)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.InvalidUnitOption -> stringResource(R.string.ocr_materialization_invalid_unit_option)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.MissingArea -> stringResource(R.string.ocr_materialization_missing_area)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.MissingQuantity -> stringResource(R.string.ocr_materialization_missing_quantity)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.InvalidQuantity -> stringResource(R.string.ocr_materialization_invalid_quantity)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.MissingLineTotal -> stringResource(R.string.ocr_materialization_missing_line_total)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.InvalidLineTotal -> stringResource(R.string.ocr_materialization_invalid_line_total)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.InvalidConversion -> stringResource(R.string.ocr_materialization_invalid_conversion)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.ParseChanged -> stringResource(R.string.ocr_materialization_parse_changed)
                                com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue.DocumentChanged -> stringResource(R.string.ocr_error_document_changed)
                                else -> stringResource(R.string.error_generic)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when (match?.status) {
                        InvoiceLineMatchStatus.CONFIRMED -> Color(0.1f, 0.6f, 0.1f).copy(alpha = 0.1f)
                        InvoiceLineMatchStatus.SUGGESTED -> Color(0.8f, 0.5f, 0.0f).copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.clickable(onClick = onMatch)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (match?.status) {
                                InvoiceLineMatchStatus.CONFIRMED -> Icons.Default.Check
                                InvoiceLineMatchStatus.SUGGESTED -> Icons.Default.Warning
                                else -> Icons.Default.Search
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when (match?.status) {
                                InvoiceLineMatchStatus.CONFIRMED -> Color(0.1f, 0.6f, 0.1f)
                                InvoiceLineMatchStatus.SUGGESTED -> Color(0.8f, 0.5f, 0.0f)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (match?.status) {
                                InvoiceLineMatchStatus.CONFIRMED -> stringResource(R.string.ocr_matching_status_matched)
                                InvoiceLineMatchStatus.SUGGESTED -> stringResource(R.string.ocr_matching_status_review_suggestion)
                                else -> stringResource(R.string.ocr_matching_status_not_matched)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (match?.status) {
                                InvoiceLineMatchStatus.CONFIRMED -> Color(0.1f, 0.6f, 0.1f)
                                InvoiceLineMatchStatus.SUGGESTED -> Color(0.8f, 0.5f, 0.0f)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        matchReasonLabel(match?.matchMethod)?.let { reason ->
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = reason, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit), modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onToggleIgnore) { 
                        Icon(
                            if (isIgnored) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isIgnored) stringResource(R.string.ocr_matching_include_line) else stringResource(R.string.ocr_matching_ignore_line),
                            modifier = Modifier.size(20.dp)
                        ) 
                    }
                    if (line.correction != null) {
                        IconButton(onClick = onReset) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.ocr_matching_reset_correction), modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}
