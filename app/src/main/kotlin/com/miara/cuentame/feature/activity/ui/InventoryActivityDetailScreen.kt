package com.miara.cuentame.feature.activity.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.inventory.InventoryActivityCategory
import com.miara.cuentame.core.model.inventory.InventoryActivityItem
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceTarget
import com.miara.cuentame.core.presentation.ui.toDisplayText
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityDetailScreenState
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityDetailViewModel
import com.miara.cuentame.feature.reports.ui.DetailReportError
import com.miara.cuentame.feature.reports.ui.DetailReportLoading
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InventoryActivityDetailRoute(
    onBack: () -> Unit,
    onOpenSource: (InventoryActivitySourceTarget) -> Unit,
    onOpenMovement: (InventoryMovementId) -> Unit,
    viewModel: InventoryActivityDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    InventoryActivityDetailScreen(
        uiState = uiState,
        onBackClick = onBack,
        onOpenSource = onOpenSource,
        onOpenMovement = onOpenMovement,
        onRetry = viewModel::onRetry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryActivityDetailScreen(
    uiState: InventoryActivityDetailScreenState,
    onBackClick: () -> Unit,
    onOpenSource: (InventoryActivitySourceTarget) -> Unit,
    onOpenMovement: (InventoryMovementId) -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("inventory_activity_detail_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_activity_view_details)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            InventoryActivityDetailScreenState.Loading -> DetailReportLoading("inventory_activity_detail_loading")
            InventoryActivityDetailScreenState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.inventory_activity_invalid_route))
                }
            }
            InventoryActivityDetailScreenState.MovementNotFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .testTag("inventory_activity_detail_not_found"), 
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.inventory_activity_not_found))
                }
            }
            is InventoryActivityDetailScreenState.LoadError -> DetailReportError("inventory_activity_detail_error", onRetry)
            is InventoryActivityDetailScreenState.Ready -> {
                InventoryActivityDetailContent(
                    item = uiState.item,
                    sourceTarget = uiState.sourceTarget,
                    currencyCode = uiState.currencyCode,
                    onOpenSource = onOpenSource,
                    onOpenMovement = onOpenMovement,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun InventoryActivityDetailContent(
    item: InventoryActivityItem,
    sourceTarget: InventoryActivitySourceTarget,
    currencyCode: String,
    onOpenSource: (InventoryActivitySourceTarget) -> Unit,
    onOpenMovement: (InventoryMovementId) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateTimeFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(item.ingredientName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${item.movement.movementType.toActivityCategory().toDisplayText()} • ${item.areaName}", style = MaterialTheme.typography.titleMedium)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow(stringResource(R.string.quantity), formatSignedQuantity(item.movement.quantityBaseSigned, item.baseUnitSymbol))
                
                item.movement.unitCostBaseSnapshot?.let { cost ->
                    DetailRow(
                        label = stringResource(R.string.cost_per_base_unit, item.baseUnitSymbol),
                        value = Formatters.formatCurrency(cost, currencyCode)
                    )
                }

                item.movement.totalValueSnapshot?.let { value ->
                    DetailRow(
                        label = if (value >= BigDecimal.ZERO) stringResource(R.string.inventory_activity_summary_value_added) else stringResource(R.string.inventory_activity_summary_value_removed),
                        value = Formatters.formatCurrency(value.abs(), currencyCode),
                        valueColor = if (value > BigDecimal.ZERO) MaterialTheme.colorScheme.primary else if (value < BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider()

                DetailRow(stringResource(R.string.effective_date), dateTimeFormatter.format(item.movement.effectiveAt))
                DetailRow(stringResource(R.string.audit_created, ""), dateTimeFormatter.format(item.movement.createdAt))
            }
        }

        // Source Document
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.original_movement), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text(item.sourceDisplay.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                item.sourceDisplay.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                item.sourceDisplay.status?.let { 
                    SuggestionChip(onClick = {}, label = { Text(it) })
                }

                if (sourceTarget !is InventoryActivitySourceTarget.Unavailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onOpenSource(sourceTarget) },
                        modifier = Modifier.fillMaxWidth().testTag("inventory_activity_open_source")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.inventory_activity_open_source))
                    }
                } else {
                    Text(
                        stringResource(R.string.inventory_activity_source_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Related Movements (Reversal / Original)
        item.reversalOfDisplay?.let { related ->
            RelatedMovementCard(
                title = stringResource(R.string.inventory_activity_view_original),
                related = related,
                onClick = { onOpenMovement(related.movementId) },
                testTag = "inventory_activity_open_original"
            )
        }

        item.reversedByDisplay?.let { related ->
            RelatedMovementCard(
                title = stringResource(R.string.inventory_activity_view_reversal),
                related = related,
                onClick = { onOpenMovement(related.movementId) },
                testTag = "inventory_activity_open_reversal"
            )
        }
    }
}

@Composable
private fun RelatedMovementCard(
    title: String,
    related: com.miara.cuentame.core.model.inventory.InventoryActivityRelatedMovementDisplay,
    onClick: () -> Unit,
    testTag: String
) {
    val dateTimeFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { 
                Text("${related.category.toDisplayText()} • ${dateTimeFormatter.format(related.effectiveAt)}")
            },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

private fun formatSignedQuantity(quantity: BigDecimal, unitSymbol: String): String {
    val prefix = if (quantity > BigDecimal.ZERO) "+" else if (quantity < BigDecimal.ZERO) "\u2212" else ""
    return "$prefix${Formatters.formatQuantity(quantity.abs(), unitSymbol)}"
}

@Composable
private fun InventoryActivityCategory.toDisplayText(): String = when (this) {
    InventoryActivityCategory.PURCHASE -> stringResource(R.string.inventory_activity_purchase)
    InventoryActivityCategory.WASTE -> stringResource(R.string.inventory_activity_waste)
    InventoryActivityCategory.STOCK_COUNT -> stringResource(R.string.inventory_activity_stock_count)
    InventoryActivityCategory.PRODUCTION_CONSUMPTION -> stringResource(R.string.inventory_activity_production_consumption)
    InventoryActivityCategory.PRODUCTION_OUTPUT -> stringResource(R.string.inventory_activity_production_output)
    InventoryActivityCategory.REVERSAL -> stringResource(R.string.inventory_activity_reversal)
    InventoryActivityCategory.OTHER -> stringResource(R.string.inventory_activity_other)
}

private fun com.miara.cuentame.core.model.inventory.InventoryMovementType.toActivityCategory(): InventoryActivityCategory = when (this) {
    com.miara.cuentame.core.model.inventory.InventoryMovementType.PURCHASE -> InventoryActivityCategory.PURCHASE
    com.miara.cuentame.core.model.inventory.InventoryMovementType.WASTE -> InventoryActivityCategory.WASTE
    com.miara.cuentame.core.model.inventory.InventoryMovementType.COUNT_ADJUSTMENT -> InventoryActivityCategory.STOCK_COUNT
    com.miara.cuentame.core.model.inventory.InventoryMovementType.MANUAL_ADJUSTMENT -> InventoryActivityCategory.OTHER
    com.miara.cuentame.core.model.inventory.InventoryMovementType.OPENING_BALANCE -> InventoryActivityCategory.OTHER
    com.miara.cuentame.core.model.inventory.InventoryMovementType.REVERSAL -> InventoryActivityCategory.REVERSAL
    com.miara.cuentame.core.model.inventory.InventoryMovementType.PRODUCTION_CONSUMPTION -> InventoryActivityCategory.PRODUCTION_CONSUMPTION
    com.miara.cuentame.core.model.inventory.InventoryMovementType.PRODUCTION_OUTPUT -> InventoryActivityCategory.PRODUCTION_OUTPUT
}
