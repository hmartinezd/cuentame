package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        onToggleIgnoreLine = viewModel::onToggleIgnoreLine,
        onViewDocument = { onViewDocument(receiptId) },
        onViewRawOcr = { onViewRawOcr(receiptId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetectedInvoiceScreen(
    uiState: ReviewDetectedInvoiceUiState,
    onBack: () -> Unit,
    onToggleIgnoreLine: (Int) -> Unit,
    onViewDocument: () -> Unit,
    onViewRawOcr: () -> Unit
) {
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(stringResource(R.string.purchases), style = MaterialTheme.typography.titleMedium)
                    ParsedFieldRow(label = stringResource(R.string.supplier_name), field = result.supplierNameCandidate)
                    ParsedFieldRow(label = stringResource(R.string.invoice_number), field = result.invoiceNumber)
                    ParsedFieldRow(label = stringResource(R.string.purchase_date), field = result.invoiceDate, formatValue = { it?.toString() ?: "" })
                }

                item {
                    HorizontalDivider()
                    Text(stringResource(R.string.receipt_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                    ParsedFieldRow(label = stringResource(R.string.line_total), field = result.total, formatValue = { Formatters.formatCurrency(it ?: BigDecimal.ZERO, result.currency.normalizedValue ?: "USD") })
                    ParsedFieldRow(label = stringResource(R.string.template_cat_other), field = result.tax, formatValue = { Formatters.formatCurrency(it ?: BigDecimal.ZERO, result.currency.normalizedValue ?: "USD") })
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
                        onToggleIgnore = { onToggleIgnoreLine(line.index) }
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
    }
}

@Composable
fun <T> ParsedFieldRow(
    label: String,
    field: ParsedField<T>,
    formatValue: (T?) -> String = { it?.toString() ?: "" }
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.labelMedium) },
        supportingContent = { 
            Text(
                text = formatValue(field.normalizedValue), 
                style = MaterialTheme.typography.bodyLarge,
                color = if (field.confidenceBand == ConfidenceBand.Low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            ) 
        },
        trailingContent = {
            if (field.confidenceBand == ConfidenceBand.Low) {
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
    onToggleIgnore: () -> Unit
) {
    val opacity = if (line.isIgnored) 0.5f else 1.0f
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = if (line.isIgnored) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                 else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.description.normalizedValue ?: stringResource(R.string.uncategorized),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (line.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         Text(
                             text = "${Formatters.formatQuantity(line.quantity.normalizedValue ?: BigDecimal.ZERO, line.packageText.normalizedValue)}",
                             style = MaterialTheme.typography.bodySmall
                         )
                         Text(
                             text = "@ ${Formatters.formatCurrency(line.unitPrice.normalizedValue ?: BigDecimal.ZERO, currency)}",
                             style = MaterialTheme.typography.bodySmall
                         )
                    }
                }
                
                Text(
                    text = Formatters.formatCurrency(line.lineTotal.normalizedValue ?: BigDecimal.ZERO, currency),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onToggleIgnore) {
                    Icon(
                        imageVector = if (line.isIgnored) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (line.isIgnored) "Include line" else "Ignore line"
                    )
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
