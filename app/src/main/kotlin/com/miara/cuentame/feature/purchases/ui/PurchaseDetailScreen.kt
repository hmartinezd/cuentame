package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.PurchaseLineWithDetails
import com.miara.cuentame.core.presentation.validation.toUserMessageRes
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.presentation.ui.ArchiveConfirmDialog
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailEvent
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailUiState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PurchaseDetailRoute(
    onBack: () -> Unit,
    onNavigateToDocument: (PurchaseReceiptId) -> Unit,
    viewModel: PurchaseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseDetailEvent.Voided -> {
                    // Reactive update will happen via StateFlow
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

    PurchaseDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onViewDocument = { (uiState.state as? PurchaseDetailState.Ready)?.details?.receipt?.id?.let { onNavigateToDocument(it) } },
        onVoid = viewModel::onVoid
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(
    uiState: PurchaseDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onViewDocument: () -> Unit,
    onVoid: () -> Unit
) {
    var showVoidConfirm by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }

    Scaffold(
        modifier = Modifier.testTag("purchase_detail_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.purchases)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("purchase_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState.state) {
            is PurchaseDetailState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PurchaseDetailState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_purchase_not_found))
                }
            }
            is PurchaseDetailState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_generic))
                }
            }
            is PurchaseDetailState.Ready -> {
                val details = state.details
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .testTag("purchase_detail_list")
                ) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(
                                    text = details.supplierName ?: stringResource(R.string.no_supplier),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = dateFormatter.format(details.receipt.purchaseDate),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (!details.receipt.invoiceNumber.isNullOrBlank()) {
                                    Text(
                                        text = "${stringResource(R.string.invoice_number)}: ${details.receipt.invoiceNumber}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.testTag("purchase_invoice_number")
                                    )
                                }
                            }
                            StatusChip(status = details.receipt.status)
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    }

                    item {
                        ReadOnlyPurchaseDocumentSection(
                            uiState = uiState,
                            onView = onViewDocument
                        )
                    }

                    items(details.lines) { line ->
                        ReadOnlyPurchaseLineItem(line, uiState.currencyCode)
                        HorizontalDivider()
                    }

                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            val total = details.lines.fold(java.math.BigDecimal.ZERO) { acc, l -> acc.add(l.line.lineTotal) }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.receipt_total), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = Formatters.formatCurrency(total, uiState.currencyCode),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("purchase_receipt_total")
                                )
                            }

                            if (details.receipt.status == DocumentStatus.POSTED) {
                                details.receipt.postedAt?.let {
                                    Text(
                                        text = stringResource(R.string.posted_at, timeFormatter.format(it)),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp).testTag("purchase_posted_at")
                                    )
                                }
                                Button(
                                    onClick = { showVoidConfirm = true },
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("purchase_void_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    enabled = !uiState.isVoiding
                                ) {
                                    if (uiState.isVoiding) {
                                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                                    }
                                    Text(stringResource(R.string.void_purchase))
                                }
                            } else {
                                details.receipt.postedAt?.let {
                                    Text(
                                        text = stringResource(R.string.posted_at, timeFormatter.format(it)),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp).testTag("purchase_posted_at")
                                    )
                                }
                                if (details.receipt.status == DocumentStatus.VOIDED) {
                                    details.receipt.voidedAt?.let {
                                        Text(
                                            text = stringResource(R.string.voided_at, timeFormatter.format(it)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 8.dp).testTag("purchase_voided_at")
                                        )
                                    }
                                } else if (details.receipt.status == DocumentStatus.UNKNOWN) {
                                    details.receipt.voidedAt?.let {
                                        Text(
                                            text = stringResource(R.string.voided_at, timeFormatter.format(it)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 8.dp).testTag("purchase_voided_at")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVoidConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.void_purchase),
            message = stringResource(R.string.voiding_warning),
            isSaving = uiState.isVoiding,
            modifier = Modifier.testTag("purchase_void_confirm_dialog"),
            onDismiss = { if (!uiState.isVoiding) showVoidConfirm = false },
            onConfirm = {
                onVoid()
            }
        )
    }
    
    // Close dialog on success
    LaunchedEffect(uiState.state) {
        if (uiState.state is PurchaseDetailState.Ready && uiState.state.details.receipt.status == DocumentStatus.VOIDED) {
            showVoidConfirm = false
        }
    }
}

@Composable
fun ReadOnlyPurchaseDocumentSection(
    uiState: PurchaseDetailUiState,
    onView: () -> Unit
) {
    val metadata = uiState.documentMetadata
    if (metadata != null) {
        Column(modifier = Modifier.fillMaxWidth().testTag("purchase_document_section")) {
            Text(
                text = stringResource(R.string.purchase_invoice_document),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ListItem(
                modifier = Modifier.testTag("purchase_document_metadata"),
                headlineContent = { 
                    Text(
                        text = metadata.displayName,
                        modifier = Modifier.testTag("purchase_document_name")
                    ) 
                },
                supportingContent = {
                    Text(
                        text = "${metadata.mimeType} • ${Formatters.formatFileSize(metadata.sizeBytes)}",
                        modifier = Modifier.testTag("purchase_document_info")
                    )
                },
                trailingContent = {
                    IconButton(onClick = onView, modifier = Modifier.testTag("purchase_document_view")) {
                        Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.purchase_view_document))
                    }
                }
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    } else if ((uiState.state as? PurchaseDetailState.Ready)?.details?.receipt?.attachmentPath != null) {
        Column(modifier = Modifier.fillMaxWidth().testTag("purchase_document_section")) {
            Text(
                text = stringResource(R.string.purchase_invoice_document),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.purchase_document_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("purchase_document_unavailable")
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
fun StatusChip(status: DocumentStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        DocumentStatus.DRAFT -> MaterialTheme.colorScheme.secondary
        DocumentStatus.POSTED -> MaterialTheme.colorScheme.primary
        DocumentStatus.VOIDED -> MaterialTheme.colorScheme.error
        DocumentStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    val text = when (status) {
        DocumentStatus.DRAFT -> stringResource(R.string.status_draft)
        DocumentStatus.POSTED -> stringResource(R.string.status_posted)
        DocumentStatus.VOIDED -> stringResource(R.string.status_voided)
        DocumentStatus.UNKNOWN -> stringResource(R.string.status_unavailable)
    }
    
    Box(
        modifier = modifier.background(color.copy(alpha = 0.1f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("purchase_status_chip")
            .semantics(mergeDescendants = true) {}
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ReadOnlyPurchaseLineItem(
    line: PurchaseLineWithDetails,
    currencyCode: String
) {
    ListItem(
        headlineContent = {
            Text(
                text = line.ingredientName ?: stringResource(R.string.uncategorized),
                modifier = Modifier.testTag("purchase_line_ingredient_${line.line.id.value}")
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = "${Formatters.formatQuantity(line.line.quantityEntered, line.unitOptionName)} (${Formatters.formatQuantity(line.line.quantityBase, line.baseUnitSymbol)})",
                    modifier = Modifier.testTag("purchase_line_quantity_${line.line.id.value}")
                )
                val areaDisplay = line.areaName
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.not_available)
                Text(
                    text = "${stringResource(R.string.receiving_area)}: $areaDisplay",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("purchase_line_area_${line.line.id.value}")
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatCurrency(line.line.lineTotal, currencyCode),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("purchase_line_total_${line.line.id.value}")
                )
                val formattedCost = Formatters.formatCurrency(line.line.unitCostBase, currencyCode)
                val unitCostText = line.baseUnitSymbol
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$formattedCost per $it" }
                    ?: formattedCost
                Text(
                    text = unitCostText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("purchase_line_unit_cost_${line.line.id.value}")
                )
            }
        }
    )
}
