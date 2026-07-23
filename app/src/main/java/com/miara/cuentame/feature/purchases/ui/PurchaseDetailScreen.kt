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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.validation.toUserMessageRes
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.feature.ingredients.ui.ArchiveConfirmDialog
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailEvent
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailUiState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDetailViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PurchaseDetailRoute(
    onBack: () -> Unit,
    viewModel: PurchaseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseDetailEvent.Voided -> {
                    // Stay on screen, it should update to VOIDED via Flow
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
        onVoid = viewModel::onVoid
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(
    uiState: PurchaseDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onVoid: () -> Unit
) {
    var showVoidConfirm by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.purchases)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
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
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        StatusChip(status = details.receipt.status)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(details.lines) { line ->
                            ReadOnlyPurchaseLineItem(line, uiState.currencyCode)
                            HorizontalDivider()
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        val total = details.lines.fold(java.math.BigDecimal.ZERO) { acc, l -> acc.add(l.line.lineTotal) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.receipt_total), style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = total.toPlainString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (details.receipt.status == DocumentStatus.POSTED) {
                            details.receipt.postedAt?.let {
                                Text(
                                    text = stringResource(R.string.posted_at, timeFormatter.format(it)),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            Button(
                                onClick = { showVoidConfirm = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                enabled = !uiState.isVoiding
                            ) {
                                if (uiState.isVoiding) {
                                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                                }
                                Text(stringResource(R.string.void_purchase))
                            }
                        } else if (details.receipt.status == DocumentStatus.VOIDED) {
                            details.receipt.voidedAt?.let {
                                Text(
                                    text = stringResource(R.string.voided_at, timeFormatter.format(it)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
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
            onDismiss = { showVoidConfirm = false },
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
fun StatusChip(status: DocumentStatus) {
    val color = when (status) {
        DocumentStatus.DRAFT -> MaterialTheme.colorScheme.secondary
        DocumentStatus.POSTED -> MaterialTheme.colorScheme.primary
        DocumentStatus.VOIDED -> MaterialTheme.colorScheme.error
    }
    val text = when (status) {
        DocumentStatus.DRAFT -> stringResource(R.string.status_draft)
        DocumentStatus.POSTED -> stringResource(R.string.status_posted)
        DocumentStatus.VOIDED -> stringResource(R.string.status_voided)
    }
    
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.1f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ReadOnlyPurchaseLineItem(
    line: com.miara.cuentame.core.domain.repository.PurchaseLineWithDetails,
    currencyCode: String
) {
    ListItem(
        headlineContent = { Text(line.ingredientName ?: stringResource(R.string.uncategorized)) },
        supportingContent = {
            Column {
                Text("${line.line.quantityEntered} ${line.unitOptionName ?: ""} (${line.line.quantityBase} ${line.baseUnitSymbol ?: ""})")
                Text(
                    text = "${stringResource(R.string.receiving_area)}: ${line.areaName ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currencyCode ${line.line.lineTotal.toPlainString()}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.cost_per_base_unit, line.baseUnitSymbol ?: ""),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = line.line.unitCostBase.toPlainString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    )
}
