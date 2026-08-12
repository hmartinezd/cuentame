package com.miara.cuentame.feature.reorder

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.domain.service.ReorderConfigurationStatus

private fun java.math.BigDecimal?.shown() = this?.stripTrailingZeros()?.toPlainString().orEmpty()

@Composable
fun ReorderRoute(onBack: () -> Unit, viewModel: ReorderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReorderScreen(state, viewModel::setFilter, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderScreen(state: ReorderUiState, onFilter: (ReorderFilter) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val noSupplier = stringResource(R.string.no_supplier_assigned)
    fun share(text: String, mime: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_TEXT, text)
        }, context.getString(R.string.reorder_share)))
    }
    Scaffold(
        modifier = Modifier.testTag("reorder_screen"),
        topBar = { TopAppBar(
            title = { Text(stringResource(R.string.reorder_assistance)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            actions = {
                IconButton(onClick = { share(ReorderExport.shoppingList(state.items, noSupplier), "text/plain") }, enabled = state.items.any { it.needsReorder }) { Icon(Icons.Default.Share, stringResource(R.string.reorder_share)) }
                TextButton(onClick = { share(ReorderExport.csv(state.items), "text/csv") }, enabled = state.items.any { it.needsReorder }) { Text(stringResource(R.string.export_csv)) }
            }
        ) }
    ) { padding -> Column(Modifier.fillMaxSize().padding(padding)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
            ReorderFilter.entries.forEachIndexed { index, filter ->
                SegmentedButton(selected = state.filter == filter, onClick = { onFilter(filter) }, shape = SegmentedButtonDefaults.itemShape(index, ReorderFilter.entries.size)) {
                    Text(stringResource(when (filter) { ReorderFilter.NEEDS_REORDER -> R.string.needs_reorder; ReorderFilter.ALL_CONFIGURED -> R.string.all_configured; ReorderFilter.MISSING_SETUP -> R.string.missing_setup }))
                }
            }
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(stringResource(R.string.error_generic)) }
            state.visibleItems.isEmpty() -> Box(Modifier.fillMaxSize().testTag("reorder_empty"), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(stringResource(if (state.filter == ReorderFilter.NEEDS_REORDER) R.string.nothing_to_reorder else R.string.no_ingredients)) }
            else -> LazyColumn(Modifier.testTag("reorder_list")) {
                state.visibleItems.groupBy { it.supplierName ?: noSupplier }.toSortedMap().forEach { (supplier, group) ->
                    item(key = "supplier:$supplier") { Text(supplier, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)) }
                    items(group, key = { it.ingredientId.value }) { item -> ReorderCard(item) }
                }
            }
        }
    }
}
}

@Composable private fun ReorderCard(item: ReorderItem) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).testTag("reorder_item_${item.ingredientId.value}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.ingredientName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.reorder_current_par, item.currentBase.shown(), item.baseUnit, item.parBase.shown(), item.baseUnit))
            item.neededBase?.let { Text(stringResource(R.string.reorder_needed, it.shown(), item.baseUnit), style = MaterialTheme.typography.titleSmall) }
            item.purchaseUnits?.let { Text(stringResource(R.string.reorder_suggested, it.shown(), item.purchaseUnit.orEmpty(), item.purchaseCoverageBase.shown(), item.baseUnit)) }
            item.configurationIssues.sortedBy { it.ordinal }.forEach { issue ->
                val warning = when (issue) {
                    ReorderConfigurationStatus.MISSING_PAR -> R.string.par_not_configured
                    ReorderConfigurationStatus.MISSING_PURCHASE_UNIT -> R.string.purchase_unit_not_configured
                    ReorderConfigurationStatus.MISSING_SUPPLIER -> R.string.no_supplier_assigned
                    ReorderConfigurationStatus.AMBIGUOUS_SUPPLIER -> R.string.ambiguous_supplier
                    ReorderConfigurationStatus.READY -> null
                }
                warning?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
            item.supplierSku?.let { Text(stringResource(R.string.supplier_sku, it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
