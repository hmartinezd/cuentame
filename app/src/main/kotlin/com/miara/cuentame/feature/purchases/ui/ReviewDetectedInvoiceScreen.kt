package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
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
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.designsystem.util.Formatters
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
        onViewRawOcr = { onViewRawOcr(receiptId) }
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
    onViewRawOcr: () -> Unit
) {
    var editingHeaderField by remember { mutableStateOf<HeaderField?>(null) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }

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
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.purchase_lines), style = MaterialTheme.typography.titleMedium)
                    }
                }

                items(result.lines) { line ->
                    ParsedInvoiceLineItem(
                        line = line,
                        currency = result.currency.normalizedValue ?: "USD",
                        onToggleIgnore = { onToggleIgnoreLine(line.index) },
                        onEdit = { editingLineIndex = line.index },
                        onReset = { onResetLine(line.index) }
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
    }
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
    currency: String,
    onToggleIgnore: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit
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
                    if (isEdited) {
                         Text(text = "Edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
