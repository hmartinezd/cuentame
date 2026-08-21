package com.venkoi.restaurantops.feature.production.ui

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
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.common.ids.ProductionBatchComponentId
import com.venkoi.restaurantops.core.common.ids.ProductionBatchId
import com.venkoi.restaurantops.core.designsystem.util.Formatters
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import com.venkoi.restaurantops.core.model.inventory.ProductionBatch
import com.venkoi.restaurantops.core.model.inventory.ProductionBatchComponent
import com.venkoi.restaurantops.core.presentation.ui.toDisplayText
import com.venkoi.restaurantops.feature.production.viewmodel.ProductionBatchDetailEvent
import com.venkoi.restaurantops.feature.production.viewmodel.ProductionBatchDetailUiState
import com.venkoi.restaurantops.feature.production.viewmodel.ProductionBatchDetailViewModel
import com.venkoi.restaurantops.feature.production.viewmodel.ProductionBatchScreenState
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
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("production_batch_detail_back")) {
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
                                ingredientName = uiState.componentNames[component.id] ?: stringResource(R.string.not_available),
                                unitLabel = uiState.componentUnitLabels[component.id] ?: stringResource(R.string.not_available),
                                recipeUnitLabel = uiState.componentRecipeUnitLabels[component.id] ?: "",
                                areaName = uiState.componentAreaNames[component.id] ?: stringResource(R.string.not_available),
                                currencyCode = uiState.currencyCode
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
            ProductionBatchScreenState.InvalidRoute -> {
                 Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.error_generic))
                }
            }
            ProductionBatchScreenState.BatchNotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.error_batch_not_found))
                }
            }
            ProductionBatchScreenState.ComponentNotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.error_component_not_found))
                }
            }
            ProductionBatchScreenState.ParentNotEditable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.error_recipe_not_editable))
                }
            }
        }
    }

    if (showVoidConfirm) {
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            title = { Text(stringResource(R.string.void_production_batch)) },
            text = { Text(stringResource(R.string.voiding_warning)) },
            confirmButton = {
                TextButton(
                    onClick = { onVoidClick(); showVoidConfirm = false },
                    modifier = Modifier.testTag("production_void_confirm")
                ) {
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
            
            DetailRow(
                stringResource(R.string.standard_yield), 
                stringResource(R.string.production_quantity_with_base, Formatters.formatQuantity(batch.recipeStandardYieldQuantitySnapshot), Formatters.formatQuantity(batch.recipeStandardYieldBaseSnapshot))
            )

            DetailRow(
                stringResource(R.string.expected_output), 
                stringResource(R.string.production_quantity_with_base, Formatters.formatQuantity(batch.expectedOutputQuantityEntered, uiState.outputUnitLabel), Formatters.formatQuantity(batch.expectedOutputQuantityBase))
            )

            DetailRow(
                stringResource(R.string.actual_output), 
                stringResource(R.string.production_quantity_with_base, Formatters.formatQuantity(batch.actualOutputQuantityEntered, uiState.outputUnitLabel), Formatters.formatQuantity(batch.actualOutputQuantityBase))
            )
            
            if (batch.hasManualOutputQuantityOverride) {
                Text(text = stringResource(R.string.manually_overridden), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }

            DetailRow(
                stringResource(R.string.total_component_cost),
                batch.totalComponentCostSnapshot?.let { Formatters.formatCurrency(it, uiState.currencyCode) } ?: stringResource(R.string.not_available)
            )
            
            DetailRow(
                stringResource(R.string.output_unit_cost),
                batch.outputUnitCostBaseSnapshot?.let { stringResource(R.string.production_currency_per_base, Formatters.formatCurrency(it, uiState.currencyCode)) } ?: stringResource(R.string.not_available)
            )

            if (batch.notes != null) {
                Text(text = stringResource(R.string.notes) + ": ${batch.notes}", style = MaterialTheme.typography.bodySmall)
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
    areaName: String,
    currencyCode: String
) {
    ListItem(
        headlineContent = { Text(ingredientName) },
        supportingContent = {
            Column {
                Text(text = stringResource(R.string.area_label) + ": $areaName")
                Text(text = stringResource(R.string.production_recipe_snapshot) + ": " + stringResource(R.string.production_quantity_with_base, Formatters.formatQuantity(component.recipeQuantityEnteredSnapshot, recipeUnitLabel), Formatters.formatQuantity(component.recipeQuantityBaseSnapshot)))
                Text(text = stringResource(R.string.expected_quantity) + ": " + stringResource(R.string.production_quantity_with_base, Formatters.formatQuantity(component.expectedQuantityEntered, unitLabel), Formatters.formatQuantity(component.expectedQuantityBase)))
                Text(text = stringResource(R.string.actual_output) + ": " + stringResource(R.string.production_quantity_with_base, Formatters.formatQuantity(component.actualQuantityEntered, unitLabel), Formatters.formatQuantity(component.actualQuantityBase)))
                
                if (component.hasManualQuantityOverride) {
                    Text(text = stringResource(R.string.manually_overridden), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }

                if (component.notes != null) {
                    Text(text = stringResource(R.string.notes) + ": ${component.notes}", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (component.totalCostSnapshot != null) {
                    Text(Formatters.formatCurrency(component.totalCostSnapshot, currencyCode), fontWeight = FontWeight.Bold)
                } else {
                    Text(stringResource(R.string.not_available), style = MaterialTheme.typography.labelSmall)
                }
                if (component.unitCostBaseSnapshot != null) {
                    Text(stringResource(R.string.production_currency_per_base, Formatters.formatCurrency(component.unitCostBaseSnapshot, currencyCode)), style = MaterialTheme.typography.labelSmall)
                } else {
                    Text(stringResource(R.string.production_unit_cost_unavailable), style = MaterialTheme.typography.labelSmall)
                }
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
        Text(text = stringResource(R.string.audit_updated, dateTimeFormatter.format(batch.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
