package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.domain.repository.PurchaseLineWithDetails
import com.miara.cuentame.core.domain.validation.toUserMessageRes
import com.miara.cuentame.core.model.supplier.Supplier
import com.miara.cuentame.feature.ingredients.ui.ArchiveConfirmDialog
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
    onAddLine: (PurchaseReceiptId) -> Unit,
    onEditLine: (PurchaseReceiptId, PurchaseLineId) -> Unit,
    onPostSuccess: (PurchaseReceiptId) -> Unit,
    viewModel: PurchaseDraftViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseDraftEvent.Created -> onNavigateToDraft(event.receiptId)
                is PurchaseDraftEvent.Posted -> onPostSuccess(purchaseId!!)
                is PurchaseDraftEvent.Deleted -> onBack()
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    PurchaseDraftScreen(
        uiState = uiState,
        onBack = onBack,
        onSaveHeader = viewModel::onSaveHeader,
        onAddLine = { purchaseId?.let { onAddLine(it) } },
        onEditLine = { lineId -> purchaseId?.let { onEditLine(it, lineId) } },
        onDeleteLine = viewModel::onDeleteLine,
        onPost = viewModel::onPost,
        onDeleteDraft = viewModel::onDeleteDraft
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDraftScreen(
    uiState: PurchaseDraftUiState,
    onBack: () -> Unit,
    onSaveHeader: (SupplierId?, String?, Instant, String?) -> Unit,
    onAddLine: () -> Unit,
    onEditLine: (PurchaseLineId) -> Unit,
    onDeleteLine: (PurchaseLineId) -> Unit,
    onPost: () -> Unit,
    onDeleteDraft: () -> Unit
) {
    var showDeleteDraftConfirm by remember { mutableStateOf(false) }
    var showPostConfirm by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_purchase)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.receiptId != null) {
                        IconButton(onClick = { showDeleteDraftConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_draft))
                        }
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
            ) {
                PurchaseHeaderSection(
                    uiState = uiState,
                    onSave = onSaveHeader
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                if (uiState.receiptId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.unit_options), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onAddLine) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_line))
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.details?.lines ?: emptyList()) { lineWithDetails ->
                            PurchaseLineItem(
                                lineWithDetails = lineWithDetails,
                                onEdit = { onEditLine(lineWithDetails.line.id) },
                                onDelete = { onDeleteLine(lineWithDetails.line.id) }
                            )
                            HorizontalDivider()
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${stringResource(R.string.receipt_total)}:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = uiState.details?.lines?.fold(java.math.BigDecimal.ZERO) { acc, l -> acc.add(l.line.lineTotal) }?.toPlainString() ?: "0.00",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = { showPostConfirm = true },
                            enabled = !uiState.isPosting && (uiState.details?.lines?.isNotEmpty() == true)
                        ) {
                            if (uiState.isPosting) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp))
                            }
                            Text(stringResource(R.string.post_purchase))
                        }
                    }
                } else {
                    // Only show save button for new draft
                    Button(
                        onClick = { 
                            // Header save is handled inside PurchaseHeaderSection or here?
                            // Let's make PurchaseHeaderSection handle its own local state and expose onSave
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }

    if (showDeleteDraftConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.delete_draft),
            message = stringResource(R.string.delete_draft_confirm),
            isSaving = uiState.isSaving,
            onDismiss = { showDeleteDraftConfirm = false },
            onConfirm = {
                onDeleteDraft()
                showDeleteDraftConfirm = false
            }
        )
    }

    if (showPostConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.post_purchase),
            message = stringResource(R.string.posting_warning),
            isSaving = uiState.isPosting,
            onDismiss = { showPostConfirm = false },
            onConfirm = {
                onPost()
                showPostConfirm = false
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
        // Supplier Selector
        var supplierExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = supplierExpanded,
            onExpandedChange = { supplierExpanded = !supplierExpanded }
        ) {
            val selectedSupplierName = uiState.suppliers.find { it.id == supplierId }?.name ?: stringResource(R.string.no_supplier)
            OutlinedTextField(
                value = selectedSupplierName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.suppliers)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
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
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = dateFormatter.format(purchaseDate),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.purchase_date)) },
            modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
            enabled = false, // To prevent keyboard but keep it clickable through modifier?
            // Actually better to use a non-editable field or a Button
        )
        // Correct way for date picker trigger
        Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
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
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onSave(supplierId, invoiceNumber, purchaseDate, notes) },
            modifier = Modifier.align(Alignment.End),
            enabled = !uiState.isSaving
        ) {
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun PurchaseLineItem(
    lineWithDetails: PurchaseLineWithDetails,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(lineWithDetails.ingredientName, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Column {
                Text("${lineWithDetails.line.quantityEntered} ${lineWithDetails.unitOptionName}")
                Text(
                    text = stringResource(R.string.unit_conversion_format, lineWithDetails.line.quantityBase.toPlainString(), lineWithDetails.baseUnitSymbol, ""), // Format correctly
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = {
            Row {
                Text(
                    text = lineWithDetails.line.lineTotal.toPlainString(),
                    modifier = Modifier.padding(end = 16.dp).align(Alignment.CenterVertically)
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.purchase_line_desc, lineWithDetails.ingredientName))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_line_desc, lineWithDetails.ingredientName))
                }
            }
        }
    )
}
