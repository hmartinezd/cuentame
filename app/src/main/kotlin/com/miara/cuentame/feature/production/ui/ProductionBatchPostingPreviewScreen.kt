package com.miara.cuentame.feature.production.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.domain.repository.ProductionBatchComponentPostingPreview
import com.miara.cuentame.core.presentation.ui.toDisplayText
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchPreviewEvent
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchPreviewUiState
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchPostingPreviewViewModel
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchScreenState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProductionBatchPostingPreviewRoute(
    onBack: () -> Unit,
    onPosted: (ProductionBatchId) -> Unit,
    viewModel: ProductionBatchPostingPreviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is ProductionBatchPreviewEvent.Posted -> onPosted(event.batchId)
            }
        }
    }

    ProductionBatchPostingPreviewScreen(
        uiState = uiState,
        onBackClick = onBack,
        onPostClick = viewModel::onPost,
        onRetry = viewModel::onRetry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionBatchPostingPreviewScreen(
    uiState: ProductionBatchPreviewUiState,
    onBackClick: () -> Unit,
    onPostClick: () -> Unit,
    onRetry: () -> Unit
) {
    var showPostConfirm by remember { mutableStateOf(false) }
    var showNegativeConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("production_batch_preview_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.posting_preview)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("production_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val screenState = uiState.screenState) {
            ProductionBatchScreenState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ProductionBatchScreenState.LoadError -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = stringResource(R.string.state_error_desc))
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.action_retry_desc))
                        }
                    }
                }
            }
            ProductionBatchScreenState.Ready -> {
                val preview = uiState.preview ?: return@Scaffold
                
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (uiState.blockers.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth().testTag("production_preview_blockers")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = stringResource(R.string.reports_operational_alerts), style = MaterialTheme.typography.titleSmall)
                                uiState.blockers.forEach { message ->
                                    Text(text = message.toDisplayText(), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (uiState.hasNegativeBalances) {
                        Surface(
                            color = MaterialTheme.colorScheme.warningContainer,
                            modifier = Modifier.fillMaxWidth().testTag("production_negative_balance_warning")
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.negative_inventory_warning), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            PreviewHeader(
                                preview = preview,
                                batch = uiState.batch,
                                currencyCode = uiState.currencyCode
                            )
                        }

                        item {
                            Text(stringResource(R.string.production_components), style = MaterialTheme.typography.titleMedium)
                        }

                        items(preview.components, key = { it.componentId.value }) { component ->
                            PreviewComponentItem(component, uiState.currencyCode)
                            HorizontalDivider()
                        }
                    }

                    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                        Button(
                            onClick = { showPostConfirm = true },
                            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("production_batch_post"),
                            enabled = uiState.blockers.isEmpty() && !uiState.isPosting
                        ) {
                            if (uiState.isPosting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            else Text(stringResource(R.string.post_production_batch))
                        }
                    }
                }
            }
            else -> {}
        }
    }

    if (showPostConfirm) {
        val preview = uiState.preview
        AlertDialog(
            onDismissRequest = { showPostConfirm = false },
            title = { Text(stringResource(R.string.post_production_batch)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.actual_output) + ": ${Formatters.formatQuantity(preview?.actualOutputQuantityBase ?: java.math.BigDecimal.ZERO)}")
                    Text(stringResource(R.string.total_component_cost) + ": ${preview?.totalComponentCost?.let { Formatters.formatCurrency(it, uiState.currencyCode) } ?: stringResource(R.string.not_applicable)}")
                    Text(stringResource(R.string.posting_warning))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showPostConfirm = false
                        if (uiState.hasNegativeBalances) {
                            showNegativeConfirm = true
                        } else {
                            onPostClick()
                        }
                    },
                    modifier = Modifier.testTag("production_post_confirmation")
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostConfirm = false }) {
                    Text(stringResource(R.string.action_back))
                }
            }
        )
    }

    if (showNegativeConfirm) {
        AlertDialog(
            onDismissRequest = { showNegativeConfirm = false },
            title = { Text(stringResource(R.string.negative_balance_warning)) },
            text = {
                Text(
                    text = "One or more component balances will become negative. Inventory will still be posted. Continue?",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onPostClick(); showNegativeConfirm = false },
                    modifier = Modifier.testTag("production_negative_balance_continue")
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNegativeConfirm = false }) {
                    Text(stringResource(R.string.action_back))
                }
            }
        )
    }
}

@Composable
private fun PreviewHeader(
    preview: com.miara.cuentame.core.domain.repository.ProductionBatchPostingPreview,
    batch: com.miara.cuentame.core.model.inventory.ProductionBatch?,
    currencyCode: String
) {
    val zoneId = java.time.ZoneId.systemDefault()
    val formatter = remember { java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM) }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = batch?.recipeNameSnapshot ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            Text(
                text = stringResource(R.string.production_effective_time) + ": ${preview.effectiveAt.atZone(zoneId).format(formatter)}",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (batch != null) {
                DetailRow(stringResource(R.string.expected_output), "${Formatters.formatQuantity(batch.expectedOutputQuantityEntered)} entered (${Formatters.formatQuantity(batch.expectedOutputQuantityBase)} base)")
                DetailRow(stringResource(R.string.actual_output), "${Formatters.formatQuantity(batch.actualOutputQuantityEntered)} entered (${Formatters.formatQuantity(preview.actualOutputQuantityBase)} base)")
            } else {
                DetailRow(stringResource(R.string.actual_output), "${Formatters.formatQuantity(preview.actualOutputQuantityBase)} base")
            }

            DetailRow(stringResource(R.string.total_component_cost), preview.totalComponentCost?.let { Formatters.formatCurrency(it, currencyCode) } ?: stringResource(R.string.not_applicable))
            DetailRow(stringResource(R.string.output_unit_cost), preview.outputUnitCostBase?.let { Formatters.formatCurrency(it, currencyCode) } ?: stringResource(R.string.not_applicable))
            
            if (preview.yieldVariancePercent != null) {
                DetailRow(stringResource(R.string.yield_variance), Formatters.formatPercent(preview.yieldVariancePercent, java.util.Locale.getDefault()))
            }
        }
    }
}

@Composable
private fun PreviewComponentItem(
    component: ProductionBatchComponentPostingPreview,
    currencyCode: String
) {
    ListItem(
        modifier = Modifier.testTag("production_preview_component_${component.componentId.value}"),
        headlineContent = { Text(component.ingredientName) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.area_label) + ": ${component.sourceAreaName}")
                Text(stringResource(R.string.quantity) + ": ${Formatters.formatQuantity(component.actualQuantityEntered, component.unitOptionLabel)} (${Formatters.formatQuantity(component.actualQuantityBase)} base)")
                Text(stringResource(R.string.current_balance) + ": ${Formatters.formatQuantity(component.currentAreaBalanceBase)}")
                Text(stringResource(R.string.remaining_balance) + ": ${Formatters.formatQuantity(component.remainingAreaBalanceBase)}")
                
                if (component.createsNegativeBalance) {
                    Text(stringResource(R.string.negative_balance_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                if (component.costUnavailable) {
                    Text(stringResource(R.string.component_cost_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                } else {
                    Text(text = component.totalCost?.let { Formatters.formatCurrency(it, currencyCode) } ?: stringResource(R.string.not_applicable), fontWeight = FontWeight.Bold)
                    Text(text = component.averageUnitCostBase?.let { Formatters.formatCurrency(it, currencyCode) + " base" } ?: "", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// Add warningContainer to MaterialTheme if not present or use local
private val ColorScheme.warningContainer: androidx.compose.ui.graphics.Color
    @Composable
    get() = androidx.compose.ui.graphics.Color(0xFFFFF4E5) // Generic warning color
