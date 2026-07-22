package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.domain.repository.PurchaseSummary
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseListUiState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseListViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PurchaseListRoute(
    onBack: () -> Unit,
    onAddPurchase: () -> Unit,
    onPurchaseClick: (PurchaseReceiptId) -> Unit,
    viewModel: PurchaseListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PurchaseListScreen(
        uiState = uiState,
        onBack = onBack,
        onAddPurchase = onAddPurchase,
        onPurchaseClick = onPurchaseClick,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onStatusFilterChanged = viewModel::onStatusFilterChanged,
        onSupplierFilterChanged = viewModel::onSupplierFilterChanged
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseListScreen(
    uiState: PurchaseListUiState,
    onBack: () -> Unit,
    onAddPurchase: () -> Unit,
    onPurchaseClick: (PurchaseReceiptId) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterChanged: (DocumentStatus?) -> Unit,
    onSupplierFilterChanged: (com.miara.cuentame.core.common.ids.SupplierId?) -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.purchases)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter_by_category))
                    }
                    Box {
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_draft)) },
                                onClick = { onStatusFilterChanged(DocumentStatus.DRAFT); showFilterMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_posted)) },
                                onClick = { onStatusFilterChanged(DocumentStatus.POSTED); showFilterMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_voided)) },
                                onClick = { onStatusFilterChanged(DocumentStatus.VOIDED); showFilterMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.all)) },
                                onClick = { onStatusFilterChanged(null); showFilterMenu = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPurchase) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_purchase))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text(stringResource(R.string.action_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.purchases) { summary ->
                        PurchaseItem(
                            summary = summary,
                            onClick = { onPurchaseClick(summary.receipt.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun PurchaseItem(
    summary: PurchaseSummary,
    onClick: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault()) }
    
    ListItem(
        headlineContent = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = summary.supplierName ?: stringResource(R.string.no_supplier),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary.totalAmount.toPlainString(), // Format as currency later
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        supportingContent = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dateFormatter.format(summary.receipt.purchaseDate))
                    Text(
                        text = when (summary.receipt.status) {
                            DocumentStatus.DRAFT -> stringResource(R.string.status_draft)
                            DocumentStatus.POSTED -> stringResource(R.string.status_posted)
                            DocumentStatus.VOIDED -> stringResource(R.string.status_voided)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (summary.receipt.status) {
                            DocumentStatus.DRAFT -> MaterialTheme.colorScheme.secondary
                            DocumentStatus.POSTED -> MaterialTheme.colorScheme.primary
                            DocumentStatus.VOIDED -> MaterialTheme.colorScheme.error
                        }
                    )
                }
                if (!summary.receipt.invoiceNumber.isNullOrBlank()) {
                    Text(
                        text = "${stringResource(R.string.invoice_number)}: ${summary.receipt.invoiceNumber}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "${summary.lineCount} items",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}
