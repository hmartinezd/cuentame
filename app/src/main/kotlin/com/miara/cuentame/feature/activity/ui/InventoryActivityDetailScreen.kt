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
import com.miara.cuentame.core.model.inventory.toInventoryActivityCategory
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityDetailScreenState
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityDetailViewModel
import com.miara.cuentame.feature.reports.ui.DetailReportError
import com.miara.cuentame.feature.reports.ui.DetailReportLoading
import java.math.BigDecimal
import java.time.Instant
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
                Box(modifier = Modifier.fillMaxSize().padding(padding).testTag("inventory_activity_detail_invalid_route"), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.inventory_activity_detail_invalid_route))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackClick, modifier = Modifier.testTag("inventory_activity_detail_back")) {
                            Text(stringResource(R.string.inventory_activity_detail_back))
                        }
                    }
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.inventory_activity_not_found))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackClick, modifier = Modifier.testTag("inventory_activity_detail_back")) {
                            Text(stringResource(R.string.inventory_activity_detail_back))
                        }
                    }
                }
            }
            is InventoryActivityDetailScreenState.LoadError -> DetailReportError("inventory_activity_detail_error", onRetry)
            is InventoryActivityDetailScreenState.Ready -> {
                InventoryActivityDetailContent(
                    item = uiState.item,
                    sourceTarget = uiState.sourceTarget,
                    currencyCode = uiState.currencyCode,
                    localeTag = uiState.localeTag,
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
    localeTag: String,
    onOpenSource: (InventoryActivitySourceTarget) -> Unit,
    onOpenMovement: (InventoryMovementId) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = remember(localeTag) { java.util.Locale.forLanguageTag(localeTag) }
    val dateTimeFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).withLocale(locale) }
    
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
            Text("${item.movement.movementType.toInventoryActivityCategory().toDisplayText()} • ${item.areaName}", style = MaterialTheme.typography.titleMedium)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow(stringResource(R.string.quantity), formatSignedQuantity(item.movement.quantityBaseSigned, item.baseUnitSymbol))
                
                DetailRow(
                    label = stringResource(R.string.inventory_activity_detail_unit_cost),
                    value = item.movement.unitCostBaseSnapshot?.let { Formatters.formatCurrency(it, currencyCode, locale) } ?: stringResource(R.string.not_available)
                )

                DetailRow(
                    label = stringResource(R.string.inventory_activity_detail_total_value),
                    value = item.movement.totalValueSnapshot?.let { Formatters.formatCurrency(it.abs(), currencyCode, locale) } ?: stringResource(R.string.not_available),
                    valueColor = item.movement.totalValueSnapshot?.let { 
                        if (it > BigDecimal.ZERO) MaterialTheme.colorScheme.primary 
                        else if (it < BigDecimal.ZERO) MaterialTheme.colorScheme.error 
                        else MaterialTheme.colorScheme.onSurface 
                    } ?: MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider()

                DetailRow(stringResource(R.string.effective_time), dateTimeFormatter.format(item.movement.effectiveAt))
                DetailRow(stringResource(R.string.audit_created).substringBefore(":"), dateTimeFormatter.format(item.movement.createdAt))
            }
        }

        // Source Document
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.inventory_activity_source_document), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text(item.sourceInfo.toDisplayTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                item.sourceInfo.toDisplaySubtitle()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

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
        if (item.movement.movementType == com.miara.cuentame.core.model.inventory.InventoryMovementType.REVERSAL) {
            RelatedMovementSection(
                label = stringResource(R.string.original_movement),
                display = item.reversalOfDisplay,
                onOpen = onOpenMovement,
                unavailableText = stringResource(R.string.inventory_activity_original_unavailable),
                actionText = stringResource(R.string.inventory_activity_view_original),
                testTag = "inventory_activity_open_original",
                locale = locale
            )
        } else if (item.reversedByMovementId != null) {
            RelatedMovementSection(
                label = stringResource(R.string.reversal_movement),
                display = item.reversedByDisplay,
                onOpen = onOpenMovement,
                unavailableText = stringResource(R.string.inventory_activity_reversal_unavailable),
                actionText = stringResource(R.string.inventory_activity_view_reversal),
                testTag = "inventory_activity_open_reversal",
                locale = locale
            )
        }
    }
}

@Composable
private fun RelatedMovementSection(
    label: String,
    display: com.miara.cuentame.core.model.inventory.InventoryActivityRelatedMovementDisplay?,
    onOpen: (InventoryMovementId) -> Unit,
    unavailableText: String,
    actionText: String,
    testTag: String,
    locale: java.util.Locale
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            if (display != null) {
                RelatedMovementCard(
                    title = actionText,
                    related = display,
                    onClick = { onOpen(display.movementId) },
                    testTag = testTag,
                    locale = locale
                )
            } else {
                Text(unavailableText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RelatedMovementCard(
    title: String,
    related: com.miara.cuentame.core.model.inventory.InventoryActivityRelatedMovementDisplay,
    onClick: () -> Unit,
    testTag: String,
    locale: java.util.Locale
) {
    val dateTimeFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).withLocale(locale) }
    
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
