package com.miara.cuentame.feature.reports.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.dashboard.InventoryDetailItem
import com.miara.cuentame.core.model.dashboard.InventoryDetailReport
import com.miara.cuentame.feature.reports.viewmodel.DetailReportScreenState
import com.miara.cuentame.feature.reports.viewmodel.InventoryDetailViewModel
import java.util.*

@Composable
fun InventoryDetailRoute(
    modifier: Modifier = Modifier,
    viewModel: InventoryDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    InventoryDetailScreen(
        uiState = uiState,
        onRetry = viewModel::onRetry,
        modifier = modifier
    )
}

@Composable
fun InventoryDetailScreen(
    uiState: DetailReportScreenState<InventoryDetailReport>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().testTag("inventory_report_screen")) {
        when (uiState) {
            is DetailReportScreenState.Loading -> DetailReportLoading("inventory_report_loading")
            is DetailReportScreenState.SetupRequired -> DetailReportSetupRequired("inventory_report_setup_required")
            is DetailReportScreenState.Error -> DetailReportError("inventory_report_error", onRetry)
            is DetailReportScreenState.Ready -> {
                InventoryDetailContent(state = uiState)
            }
        }
    }
}

@Composable
private fun InventoryDetailContent(
    state: DetailReportScreenState.Ready<InventoryDetailReport>
) {
    val locale = remember(state.localeTag) { Locale.forLanguageTag(state.localeTag) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("inventory_report_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DetailReportHeader(
                restaurantName = state.restaurantName,
                reportTitle = stringResource(R.string.inventory_detail_title),
                testTag = "inventory_report_header"
            )
        }

        item {
            InventorySummaryCard(state.report, state.currencyCode, locale)
        }

        if (state.report.rows.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("inventory_report_empty")) {
                    Text(
                        stringResource(R.string.no_stocked_ingredients),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(state.report.rows, key = { it.ingredientId.value }) { item ->
                InventoryDetailRow(item, state.currencyCode, locale)
            }
        }
    }
}

@Composable
private fun InventorySummaryCard(
    report: InventoryDetailReport,
    currencyCode: String,
    locale: Locale
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("inventory_report_summary")) {
        Column(modifier = Modifier.padding(16.dp)) {
            SummaryMetric(
                label = stringResource(R.string.inventory_value_label),
                value = Formatters.formatCurrency(report.totalValue, currencyCode, locale),
                testTag = "inventory_report_total_value"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.valuation_coverage_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        stringResource(R.string.coverage_format, report.valuedIngredientCount, report.stockedIngredientCount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.missing_costs_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        report.missingCostCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (report.missingCostCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.negative_balances_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        report.negativeBalanceCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (report.negativeBalanceCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryDetailRow(
    item: InventoryDetailItem,
    currencyCode: String,
    locale: Locale
) {
    val formattedQuantity = Formatters.formatQuantity(item.totalQuantityBase, item.baseUnitSymbol)
    val formattedCost = item.currentAverageCost?.let { Formatters.formatCurrency(it, currencyCode, locale) } ?: stringResource(R.string.not_applicable)
    val formattedValue = item.currentInventoryValue?.let { Formatters.formatCurrency(it, currencyCode, locale) } ?: stringResource(R.string.not_applicable)
    
    val semanticsDesc = "${item.ingredientName}. Quantity: $formattedQuantity. Cost: $formattedCost. Value: $formattedValue" +
            (if (item.isMissingCost) ". " + stringResource(R.string.missing_current_cost_label) else "") +
            (if (item.negativeAreaBalanceCount > 0) ". " + stringResource(R.string.negative_area_balances_label) else "")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_report_item_${item.ingredientId.value}")
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDesc
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.ingredientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formattedQuantity, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.current_avg_cost_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(formattedCost, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(stringResource(R.string.current_inventory_value_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(formattedValue, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
            
            if (item.isMissingCost || item.negativeAreaBalanceCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.isMissingCost) {
                        WarningLabel(stringResource(R.string.missing_current_cost_label), Icons.Default.Warning, MaterialTheme.colorScheme.secondary)
                    }
                    if (item.negativeAreaBalanceCount > 0) {
                        WarningLabel(stringResource(R.string.negative_area_balances_label), Icons.Default.Warning)
                    }
                }
            }
        }
    }
}
