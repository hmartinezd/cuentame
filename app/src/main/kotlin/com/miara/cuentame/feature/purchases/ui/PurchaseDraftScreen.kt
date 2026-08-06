package com.miara.cuentame.feature.purchases.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.domain.repository.PurchaseLineWithDetails
import com.miara.cuentame.core.presentation.validation.toUserMessageRes
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.presentation.ui.ArchiveConfirmDialog
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDraftEvent
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDraftUiState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDraftViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PurchaseDraftRoute(
    purchaseId: PurchaseReceiptId?,
    onBack: () -> Unit,
    onNavigateToDraft: (PurchaseReceiptId) -> Unit,
    onNavigateToDocument: (PurchaseReceiptId) -> Unit,
    onAddLine: (PurchaseReceiptId) -> Unit,
    onEditLine: (PurchaseReceiptId, PurchaseLineId) -> Unit,
    onPostSuccess: (PurchaseReceiptId) -> Unit,
    viewModel: PurchaseDraftViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var lastDeletedLineId by remember { mutableStateOf<PurchaseLineId?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseDraftEvent.Created -> onNavigateToDraft(event.receiptId)
                is PurchaseDraftEvent.Posted -> onPostSuccess(purchaseId!!)
                is PurchaseDraftEvent.Deleted -> onBack()
                is PurchaseDraftEvent.LineDeleted -> {
                    lastDeletedLineId = event.lineId
                }
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.onAttachDocument(it) }
    }

    PurchaseDraftScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        lastDeletedLineId = lastDeletedLineId,
        onBack = onBack,
        onSaveHeader = viewModel::onSaveHeader,
        onImportDocument = { pickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
        onRemoveDocument = viewModel::onRemoveDocument,
        onViewDocument = { uiState.receiptId?.let { onNavigateToDocument(it) } },
        onAddLine = { purchaseId?.let { onAddLine(it) } },
        onEditLine = { lineId -> purchaseId?.let { onEditLine(it, lineId) } },
        onDeleteLine = viewModel::onDeleteLine,
        onPost = viewModel::onPost,
        onDeleteDraft = viewModel::onDeleteDraft,
        onResetLastDeletedLineId = { lastDeletedLineId = null }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDraftScreen(
    uiState: PurchaseDraftUiState,
    snackbarHostState: SnackbarHostState,
    lastDeletedLineId: PurchaseLineId?,
    onBack: () -> Unit,
    onSaveHeader: (SupplierId?, String?, Instant, String?) -> Unit,
    onImportDocument: () -> Unit,
    onRemoveDocument: () -> Unit,
    onViewDocument: () -> Unit,
    onAddLine: () -> Unit,
    onEditLine: (PurchaseLineId) -> Unit,
    onDeleteLine: (PurchaseLineId) -> Unit,
    onPost: () -> Unit,
    onDeleteDraft: () -> Unit,
    onResetLastDeletedLineId: () -> Unit
) {
    var showDeleteDraftConfirm by remember { mutableStateOf(false) }
    var showPostConfirm by remember { mutableStateOf(false) }
    var lineToDelete by remember { mutableStateOf<PurchaseLineWithDetails?>(null) }

    Scaffold(
        modifier = Modifier.testTag("purchase_draft_screen"),
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.testTag("purchase_error_snackbar")) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.testTag("purchase_error_snackbar_content")
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (uiState.receiptId == null) stringResource(R.string.new_purchase)
                        else stringResource(R.string.draft_purchase)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("purchase_draft_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.receiptId != null) {
                        IconButton(onClick = { showDeleteDraftConfirm = true }, enabled = !uiState.isDeletingDraft && !uiState.isPosting, modifier = Modifier.testTag("purchase_delete_button")) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_draft))
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .testTag("purchase_draft_list")
            ) {
                item {
                    PurchaseHeaderSection(
                        uiState = uiState,
                        onSave = onSaveHeader
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                }

                item {
                    PurchaseDocumentSection(
                        uiState = uiState,
                        onImport = onImportDocument,
                        onRemove = onRemoveDocument,
                        onView = onViewDocument
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                }

                if (uiState.receiptId != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(R.string.purchase_lines), style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = onAddLine, enabled = !uiState.isPosting && !uiState.isDeletingDraft) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_line))
                            }
                        }
                    }

                    val lines = uiState.details?.lines ?: emptyList()
                    if (lines.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.state_empty_desc))
                            }
                        }
                    } else {
                        items(lines) { lineWithDetails ->
                            PurchaseLineItem(
                                lineWithDetails = lineWithDetails,
                                currencyCode = uiState.currencyCode,
                                onEdit = { onEditLine(lineWithDetails.line.id) },
                                onDelete = { lineToDelete = lineWithDetails },
                                enabled = !uiState.isPosting && !uiState.isDeletingDraft && uiState.deletingLineId == null
                            )
                            HorizontalDivider()
                        }
                    }

                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${stringResource(R.string.receipt_total)}:",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    val total = uiState.details?.lines?.fold(java.math.BigDecimal.ZERO) { acc, l -> acc.add(l.line.lineTotal) } ?: java.math.BigDecimal.ZERO
                                    Text(
                                        text = Formatters.formatCurrency(total, uiState.currencyCode),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.purchase_items_count, lines.size),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Button(
                                    onClick = { 
                                        showPostConfirm = true 
                                    },
                                    modifier = Modifier.testTag("purchase_post_button"),
                                    enabled = !uiState.isPosting && !uiState.isDeletingDraft && lines.isNotEmpty()
                                ) {
                                    if (uiState.isPosting) {
                                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                                    }
                                    Text(stringResource(R.string.post_purchase))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDraftConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.delete_draft),
            message = stringResource(R.string.delete_draft_confirm),
            isSaving = uiState.isDeletingDraft,
            modifier = Modifier.testTag("purchase_delete_draft_confirm_dialog"),
            onDismiss = { if (!uiState.isDeletingDraft) showDeleteDraftConfirm = false },
            onConfirm = {
                onDeleteDraft()
            }
        )
    }

    if (showPostConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.post_purchase),
            message = stringResource(R.string.posting_warning),
            confirmText = stringResource(R.string.action_confirm),
            isSaving = uiState.isPosting,
            modifier = Modifier.testTag("purchase_post_confirm_dialog"),
            onDismiss = { if (!uiState.isPosting) showPostConfirm = false },
            onConfirm = {
                onPost()
            }
        )
    }

    lineToDelete?.let { line ->
        ArchiveConfirmDialog(
            title = stringResource(R.string.delete_line),
            message = stringResource(R.string.delete_line_desc, line.ingredientName ?: ""),
            isSaving = uiState.deletingLineId == line.line.id,
            onDismiss = { if (uiState.deletingLineId == null) lineToDelete = null },
            onConfirm = {
                onDeleteLine(line.line.id)
            }
        )
    }
    
    // Close dialogs on success
    LaunchedEffect(uiState.details?.receipt?.status) {
        if (uiState.details?.receipt?.status == DocumentStatus.POSTED) {
            showPostConfirm = false
        }
    }
    
    LaunchedEffect(lastDeletedLineId) {
        if (lastDeletedLineId != null && lineToDelete?.line?.id == lastDeletedLineId) {
            lineToDelete = null
            onResetLastDeletedLineId()
        }
    }
}

@Composable
fun PurchaseDocumentSection(
    uiState: PurchaseDraftUiState,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    onView: () -> Unit
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().testTag("purchase_document_section")) {
        Text(
            text = stringResource(R.string.purchase_invoice_document),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (uiState.receiptId == null) {
            Text(
                text = stringResource(R.string.purchase_save_header_first),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (uiState.documentMetadata == null) {
            Button(
                onClick = onImport,
                enabled = !uiState.isImportingDocument && !uiState.isPosting && !uiState.isDeletingDraft,
                modifier = Modifier.testTag("purchase_document_import")
            ) {
                if (uiState.isImportingDocument) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(R.string.purchase_import_document))
            }
        } else {
            ListItem(
                modifier = Modifier.testTag("purchase_document_metadata"),
                headlineContent = { 
                    Text(
                        text = uiState.documentMetadata.displayName,
                        modifier = Modifier.testTag("purchase_document_name")
                    ) 
                },
                supportingContent = {
                    Text(
                        text = "${uiState.documentMetadata.mimeType} • ${Formatters.formatFileSize(uiState.documentMetadata.sizeBytes)}",
                        modifier = Modifier.testTag("purchase_document_info")
                    )
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = onView, modifier = Modifier.testTag("purchase_document_view")) {
                            Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.purchase_view_document))
                        }
                        IconButton(
                            onClick = onImport, 
                            enabled = !uiState.isImportingDocument && !uiState.isPosting && !uiState.isDeletingDraft,
                            modifier = Modifier.testTag("purchase_document_replace")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.purchase_replace_document))
                        }
                        IconButton(
                            onClick = { showRemoveConfirm = true }, 
                            enabled = !uiState.isRemovingDocument && !uiState.isPosting && !uiState.isDeletingDraft,
                            modifier = Modifier.testTag("purchase_document_remove")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.purchase_remove_document))
                        }
                    }
                }
            )
        }
    }

    if (showRemoveConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.purchase_remove_document),
            message = stringResource(R.string.purchase_remove_document_confirmation),
            isSaving = uiState.isRemovingDocument,
            modifier = Modifier.testTag("purchase_document_remove_confirm_dialog"),
            onDismiss = { if (!uiState.isRemovingDocument) showRemoveConfirm = false },
            onConfirm = {
                onRemove()
                showRemoveConfirm = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseHeaderSection(
    uiState: PurchaseDraftUiState,
    onSave: (SupplierId?, String?, Instant, String?) -> Unit
) {
    var supplierId by remember(uiState.details) { mutableStateOf(uiState.details?.receipt?.supplierId) }
    var invoiceNumber by remember(uiState.details) { mutableStateOf(uiState.details?.receipt?.invoiceNumber ?: "") }
    var purchaseDate by remember(uiState.details) { mutableStateOf(uiState.details?.receipt?.purchaseDate ?: Instant.now()) }
    var notes by remember(uiState.details) { mutableStateOf(uiState.details?.receipt?.notes ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            StatusChip(status = uiState.details?.receipt?.status ?: DocumentStatus.DRAFT)
        }
        
        // Supplier Selector
        var supplierExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = supplierExpanded,
            onExpandedChange = { if (!uiState.isSaving && !uiState.isPosting) supplierExpanded = !supplierExpanded }
        ) {
            val selectedSupplierName = uiState.suppliers.find { it.id == supplierId }?.name ?: stringResource(R.string.no_supplier)
            OutlinedTextField(
                value = selectedSupplierName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.suppliers)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                enabled = !uiState.isSaving && !uiState.isPosting
            )
            ExposedDropdownMenu(
                expanded = supplierExpanded,
                onDismissRequest = { supplierExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.no_supplier)) },
                    onClick = { supplierId = null; supplierExpanded = false }
                )
                uiState.suppliers.forEach { supplier ->
                    DropdownMenuItem(
                        text = { Text(supplier.name) },
                        onClick = { supplierId = supplier.id; supplierExpanded = false }
                    )
                }
            }
        }

        OutlinedTextField(
            value = invoiceNumber,
            onValueChange = { invoiceNumber = it },
            label = { Text(stringResource(R.string.invoice_number)) },
            modifier = Modifier.fillMaxWidth().testTag("purchase_invoice_input"),
            enabled = !uiState.isSaving && !uiState.isPosting
        )

        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isSaving && !uiState.isPosting) { showDatePicker = true }) {
             OutlinedTextField(
                value = dateFormatter.format(purchaseDate),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.purchase_date)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.notes)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving && !uiState.isPosting
        )

        Button(
            onClick = { onSave(supplierId, invoiceNumber, purchaseDate, notes) },
            modifier = Modifier.align(Alignment.End).testTag("purchase_header_save"),
            enabled = !uiState.isSaving && !uiState.isPosting
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
            }
            Text(stringResource(R.string.action_save))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = purchaseDate.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { purchaseDate = Instant.ofEpochMilli(it) }
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

@Composable
fun PurchaseLineItem(
    lineWithDetails: PurchaseLineWithDetails,
    currencyCode: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    ListItem(
        headlineContent = { Text(lineWithDetails.ingredientName ?: stringResource(R.string.uncategorized), fontWeight = FontWeight.Bold) },
        supportingContent = {
            Column {
                Text(Formatters.formatQuantity(lineWithDetails.line.quantityEntered, lineWithDetails.unitOptionName))
                Text(
                    text = Formatters.formatQuantity(lineWithDetails.line.quantityBase, lineWithDetails.baseUnitSymbol),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.receiving_area)}: ${lineWithDetails.areaName ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = {
            Row {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 16.dp)) {
                    Text(
                        text = Formatters.formatCurrency(lineWithDetails.line.lineTotal, currencyCode),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${Formatters.formatCurrency(lineWithDetails.line.unitCostBase, currencyCode)} per ${lineWithDetails.baseUnitSymbol ?: ""}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                IconButton(onClick = onEdit, enabled = enabled) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.purchase_line_desc, lineWithDetails.ingredientName ?: ""))
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_line_desc, lineWithDetails.ingredientName ?: ""))
                }
            }
        }
    )
}
