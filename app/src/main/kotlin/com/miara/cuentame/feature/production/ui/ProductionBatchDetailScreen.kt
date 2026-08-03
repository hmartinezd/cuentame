package com.miara.cuentame.feature.production.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import com.miara.cuentame.core.common.ids.ProductionBatchComponentId
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.inventory.ProductionBatchComponent
import com.miara.cuentame.core.presentation.ui.toDisplayText
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchDetailEvent
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchDetailUiState
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchDetailViewModel
import com.miara.cuentame.feature.production.viewmodel.ProductionBatchScreenState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProductionBatchDetailRoute(
    onBack: () -> Unit,
    onNavigateToDraft: (ProductionBatchId) -> Unit,
    viewModel: ProductionBatchDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is ProductionBatchDetailEvent.NavigateToDraft -> onNavigateToDraft(event.batchId)
            }
        }
    }

    ProductionBatchDetailScreen(
        uiState = uiState,
        onBackClick = onBack,
        onVoidClick = viewModel::onVoid,
        onRetry = viewModel::onRetry,
        onClearError = viewModel::clearInlineError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionBatchDetailScreen(
    uiState: ProductionBatchDetailUiState,
    onBackClick: () -> Unit,
    onVoidClick: () -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit
) {
    var showVoidConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("production_batch_detail_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.production_batch)) },
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
                val batch = uiState.batch ?: return@Scaffold
                
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    uiState.inlineError?.let { message ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth().testTag("production_detail_inline_error")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = message.toDisplayText(),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = onClearError, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            DetailHeader(uiState)
                        }

                        item {
                            Text(stringResource(R.string.production_components), style = MaterialTheme.typography.titleMedium)
                        }

                        items(batch.components.sortedBy { it.sortOrder }, key = { it.id.value }) { component ->
                            ComponentDetailItem(
                                component = component,
                                ingredientName = uiState.componentNames[component.id] ?: component.componentIngredientId.value,
                                unitLabel = uiState.componentUnitLabels[component.id] ?: component.unitOptionId.value,
                                recipeUnitLabel = uiState.componentRecipeUnitLabels[component.id] ?: "",
                                areaName = uiState.componentAreaNames[component.id] ?: component.sourceAreaId?.value ?: ""
                            )
                            HorizontalDivider()
                        }

                        item {
                            AuditInfo(batch)
                        }
                    }

                    if (batch.status == DocumentStatus.POSTED) {
                        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                            Button(
                                onClick = { showVoidConfirm = true },
                                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("production_batch_void"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                enabled = !uiState.isOperating
                            ) {
                                if (uiState.isOperating) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                else Text(stringResource(R.string.void_production_batch))
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }

    if (showVoidConfirm) {
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            title = { Text(stringResource(R.string.void_production_batch)) },
            text = { Text(stringResource(R.string.voiding_warning)) },
            confirmButton = {
                TextButton(onClick = { onVoidClick(); showVoidConfirm = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidConfirm = false }) {
                    Text(stringResource(R.string.action_back))
                }
            }
        )
    }
}

@Composable
private fun DetailHeader(uiState: ProductionBatchDetailUiState) {
    val batch = uiState.batch ?: return
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = batch.recipeNameSnapshot, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                ProductionStatusBadge(status = batch.status)
            }
            
            Text(text = stringResource(R.string.output_ingredient) + ": ${uiState.outputIngredientName}", style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.effective_time) + ": ${dateTimeFormatter.format(batch.effectiveAt)}", style = MaterialTheme.typography.bodySmall)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DetailRow(stringResource(R.string.batch_multiplier), batch.batchMultiplier.toPlainString())
            DetailRow(stringResource(R.string.output_area), uiState.outputAreaName)
            DetailRow(stringResource(R.string.actual_output), Formatters.formatQuantity(batch.actualOutputQuantityEntered, uiState.outputUnitLabel))
            
            if (batch.totalComponentCostSnapshot != null) {
                DetailRow(stringResource(R.string.total_component_cost), Formatters.formatCurrency(batch.totalComponentCostSnapshot, ""))
            }
        }
    }
}

@Composable
private fun ComponentDetailItem(
    component: ProductionBatchComponent,
    ingredientName: String,
    unitLabel: String,
    recipeUnitLabel: String,
    areaName: String
) {
    ListItem(
        headlineContent = { Text(ingredientName) },
        supportingContent = {
            Column {
                Text(text = stringResource(R.string.area_label) + ": $areaName")
                Text(text = stringResource(R.string.expected_quantity) + ": ${Formatters.formatQuantity(component.expectedQuantityEntered, recipeUnitLabel)}")
                Text(text = stringResource(R.string.actual_output) + ": ${Formatters.formatQuantity(component.actualQuantityEntered, unitLabel)}")
            }
        },
        trailingContent = {
            if (component.totalCostSnapshot != null) {
                Text(Formatters.formatCurrency(component.totalCostSnapshot, ""), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun AuditInfo(batch: ProductionBatch) {
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = stringResource(R.string.audit_created, dateTimeFormatter.format(batch.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        batch.postedAt?.let {
            Text(text = stringResource(R.string.posted_at, dateTimeFormatter.format(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        batch.voidedAt?.let {
            Text(text = stringResource(R.string.voided_at, dateTimeFormatter.format(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
