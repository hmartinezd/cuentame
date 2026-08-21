package com.venkoi.restaurantops.feature.priceintelligence.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.common.ids.PurchaseLineId
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.designsystem.util.Formatters
import com.venkoi.restaurantops.core.domain.repository.*
import com.venkoi.restaurantops.feature.priceintelligence.viewmodel.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun PriceHistoryRoute(onBack: () -> Unit, onSource: (PurchaseReceiptId, PurchaseLineId) -> Unit,
    viewModel: PriceHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PriceHistoryScreen(state, onBack, onSource, viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PriceHistoryScreen(state: PriceHistoryState, onBack: () -> Unit,
    onSource: (PurchaseReceiptId, PurchaseLineId) -> Unit, onRetry: () -> Unit) {
    Scaffold(modifier = Modifier.testTag("price_history_screen"), topBar = { TopAppBar(
        title = { Text(stringResource(R.string.price_history_title)) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
    ) }) { padding -> when (state) {
        PriceHistoryState.Loading -> Center(padding) { CircularProgressIndicator() }
        PriceHistoryState.Error -> Center(padding) { Button(onClick = onRetry) { Text(stringResource(R.string.action_retry_desc)) } }
        is PriceHistoryState.Ready -> HistoryContent(state.history, padding, onSource)
    } }
}

@Composable private fun HistoryContent(history: IngredientPriceHistory, padding: PaddingValues,
    onSource: (PurchaseReceiptId, PurchaseLineId) -> Unit) {
    if (history.observations.isEmpty()) { Center(padding) { Text(stringResource(R.string.price_history_empty)) }; return }
    val c = history.comparison
    val latest = requireNotNull(c.latest)
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.price_summary), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.latest_normalized_cost, money(latest.unitCostBase, latest.currencyCode), latest.baseUnitSymbol)) }
        item { Text(c.previous?.let { stringResource(R.string.previous_normalized_cost, money(it.unitCostBase, it.currencyCode), it.baseUnitSymbol) } ?: stringResource(R.string.previous_price_missing)) }
        c.percentChange?.let { item { Text(stringResource(R.string.price_change_format, Formatters.formatPercent(it), money(c.absoluteChange!!, latest.currencyCode))) } }
        item { Text(stringResource(R.string.latest_supplier_date, latest.supplierName ?: stringResource(R.string.no_supplier), date(latest.purchaseDate))) }
        if (c.percentChange != null && c.percentChange >= java.math.BigDecimal.TEN) item { Text(stringResource(R.string.large_price_increase), color = MaterialTheme.colorScheme.error) }
        if (c.coverage.isNotEmpty()) item { Column { Text(stringResource(R.string.price_coverage_warning), color = MaterialTheme.colorScheme.secondary); c.coverage.forEach { Text(coverageText(it), color = MaterialTheme.colorScheme.secondary) } } }
        item { Text(stringResource(R.string.recent_supplier_prices), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        items(history.recentSupplierPrices) { p -> Column { Text("${p.observation.supplierName ?: stringResource(R.string.no_supplier)}: ${money(p.observation.unitCostBase, p.observation.currencyCode)}/${p.observation.baseUnitSymbol} • ${date(p.observation.purchaseDate)}${if (p.isLowest) " • ${stringResource(R.string.lowest_price)}" else ""}"); p.observation.coverage.forEach { Text(coverageText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) } } }
        item { Text(stringResource(R.string.price_history_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        items(history.observations, key = { it.purchaseLineId.value }) { PriceRow(it) { onSource(it.purchaseReceiptId, it.purchaseLineId) }; HorizontalDivider() }
    }
}

@Composable private fun PriceRow(o: VendorPriceObservation, onClick: () -> Unit) {
    val date = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault()).format(o.purchaseDate)
    ListItem(modifier = Modifier.clickable(onClick = onClick).testTag("price_history_row_${o.purchaseLineId.value}"),
        headlineContent = { Text("$date • ${o.supplierName ?: stringResource(R.string.no_supplier)}") },
        supportingContent = { Column {
            Text(stringResource(R.string.entered_unit_price, o.enteredUnitPrice?.let { money(it, o.currencyCode) } ?: "—", o.purchaseUnitLabel ?: stringResource(R.string.unit_unknown)))
            Text(stringResource(R.string.normalized_cost, money(o.unitCostBase, o.currencyCode), o.baseUnitSymbol))
            Text(stringResource(R.string.vendor_item, o.vendorItemCode ?: stringResource(R.string.vendor_item_unknown)))
        } })
}

@Composable fun PriceAlertsRoute(onBack: () -> Unit, onSource: (PurchaseReceiptId, PurchaseLineId) -> Unit,
    viewModel: PriceAlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PriceAlertsScreen(state, onBack, onSource, viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PriceAlertsScreen(state: PriceAlertsState, onBack: () -> Unit,
    onSource: (PurchaseReceiptId, PurchaseLineId) -> Unit, onRetry: () -> Unit) {
    Scaffold(modifier = Modifier.testTag("price_alerts_screen"), topBar = { TopAppBar(title = { Text(stringResource(R.string.price_increases_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }) }) { padding ->
        when (state) {
            PriceAlertsState.Loading -> Center(padding) { CircularProgressIndicator() }
            PriceAlertsState.Error -> Center(padding) { Button(onClick = onRetry) { Text(stringResource(R.string.action_retry_desc)) } }
            is PriceAlertsState.Ready -> if (state.alerts.isEmpty()) Center(padding) { Text(stringResource(R.string.price_increases_empty)) } else LazyColumn(Modifier.padding(padding)) {
                items(state.alerts) { a -> ListItem(modifier = Modifier.clickable { onSource(a.purchaseReceiptId, a.purchaseLineId) }, headlineContent = { Text(a.ingredientName) }, supportingContent = { Text("${a.supplierName ?: stringResource(R.string.no_supplier)} • ${money(a.previousCost, a.currencyCode)} → ${money(a.latestCost, a.currencyCode)} • ${date(a.purchaseDate)}") }, trailingContent = { Text("+${Formatters.formatPercent(a.percentIncrease)}", color = MaterialTheme.colorScheme.error) }); HorizontalDivider() }
            }
        }
    }
}

@Composable private fun Center(padding: PaddingValues, content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { content() }
private fun money(value: java.math.BigDecimal, currencyCode: String) = Formatters.formatCurrency(value, currencyCode)
private fun date(value: java.time.Instant) = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault()).format(value)

@Composable private fun coverageText(value: PriceDataCoverage): String = stringResource(when (value) {
    PriceDataCoverage.VENDOR_ITEM_UNKNOWN -> R.string.coverage_vendor_unknown
    PriceDataCoverage.PACKAGE_LABEL_UNKNOWN -> R.string.coverage_package_unknown
    PriceDataCoverage.SOURCE_PROVENANCE_DIVERGED -> R.string.coverage_provenance_diverged
    PriceDataCoverage.PREVIOUS_PRICE_MISSING -> R.string.previous_price_missing
    PriceDataCoverage.PREVIOUS_PRICE_ZERO -> R.string.coverage_previous_zero
    PriceDataCoverage.CONTRADICTORY_VENDOR_ITEMS -> R.string.coverage_contradictory_vendor
    PriceDataCoverage.INVALID_HISTORICAL_QUANTITY -> R.string.coverage_invalid_quantity
    else -> R.string.price_coverage_warning
})
