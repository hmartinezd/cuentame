package com.miara.cuentame.feature.activity.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miara.cuentame.R
import com.miara.cuentame.core.model.inventory.InventoryActivityCategory
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceInfo

@Composable
fun InventoryActivityCategory.toDisplayText(): String = when (this) {
    InventoryActivityCategory.PURCHASE -> stringResource(R.string.inventory_activity_purchase)
    InventoryActivityCategory.WASTE -> stringResource(R.string.inventory_activity_waste)
    InventoryActivityCategory.STOCK_COUNT -> stringResource(R.string.inventory_activity_stock_count)
    InventoryActivityCategory.PRODUCTION_CONSUMPTION -> stringResource(R.string.inventory_activity_production_consumption)
    InventoryActivityCategory.PRODUCTION_OUTPUT -> stringResource(R.string.inventory_activity_production_output)
    InventoryActivityCategory.REVERSAL -> stringResource(R.string.inventory_activity_reversal)
    InventoryActivityCategory.OTHER -> stringResource(R.string.inventory_activity_other)
}

@Composable
fun InventoryActivitySourceInfo.toDisplayTitle(): String = when (this) {
    is InventoryActivitySourceInfo.Purchase -> {
        supplierName?.let { stringResource(R.string.inventory_activity_source_purchase_from, it) }
            ?: stringResource(R.string.inventory_activity_purchase)
    }
    is InventoryActivitySourceInfo.Waste -> {
        reason?.let { stringResource(R.string.inventory_activity_source_waste_reason, it) }
            ?: stringResource(R.string.inventory_activity_waste)
    }
    is InventoryActivitySourceInfo.StockCount -> {
        countName ?: stringResource(R.string.inventory_activity_source_stock_count_adj)
    }
    is InventoryActivitySourceInfo.Production -> {
        recipeName?.let { stringResource(R.string.inventory_activity_source_production_recipe, it) }
            ?: stringResource(R.string.inventory_activity_source_production_batch)
    }
    is InventoryActivitySourceInfo.Other -> stringResource(R.string.inventory_activity_source_unavailable)
}

@Composable
fun InventoryActivitySourceInfo.toDisplaySubtitle(): String? = when (this) {
    is InventoryActivitySourceInfo.Purchase -> invoiceNumber?.let { stringResource(R.string.inventory_activity_source_invoice, it) }
    is InventoryActivitySourceInfo.Waste -> sourceAreaName
    is InventoryActivitySourceInfo.StockCount -> null
    is InventoryActivitySourceInfo.Production -> status // Need to localize status if it's an enum, but it's passed as String? for now.
    is InventoryActivitySourceInfo.Other -> null
}
