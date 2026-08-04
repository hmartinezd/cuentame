package com.miara.cuentame.feature.activity.logic

import android.content.Context
import com.miara.cuentame.R
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryActivityCategory
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceInfo
import com.miara.cuentame.core.model.inventory.WasteReason
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidInventoryActivityTextResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : InventoryActivityTextResolver {

    override fun categoryText(category: InventoryActivityCategory): String = when (category) {
        InventoryActivityCategory.PURCHASE -> context.getString(R.string.inventory_activity_purchase)
        InventoryActivityCategory.WASTE -> context.getString(R.string.inventory_activity_waste)
        InventoryActivityCategory.STOCK_COUNT -> context.getString(R.string.inventory_activity_stock_count)
        InventoryActivityCategory.PRODUCTION_CONSUMPTION -> context.getString(R.string.inventory_activity_production_consumption)
        InventoryActivityCategory.PRODUCTION_OUTPUT -> context.getString(R.string.inventory_activity_production_output)
        InventoryActivityCategory.REVERSAL -> context.getString(R.string.inventory_activity_reversal)
        InventoryActivityCategory.OTHER -> context.getString(R.string.inventory_activity_other)
    }

    override fun sourceTitle(info: InventoryActivitySourceInfo): String = when (info) {
        is InventoryActivitySourceInfo.Purchase -> context.getString(
            R.string.inventory_activity_source_purchase_from,
            info.supplierName ?: context.getString(R.string.no_supplier)
        )
        is InventoryActivitySourceInfo.Waste -> context.getString(
            R.string.inventory_activity_source_waste_reason,
            info.reason?.let { wasteReasonText(it) } ?: context.getString(R.string.reason_other)
        )
        is InventoryActivitySourceInfo.StockCount -> info.countName
            ?: context.getString(R.string.inventory_activity_source_stock_count_adj)
        is InventoryActivitySourceInfo.Production -> context.getString(
            R.string.inventory_activity_source_production_recipe,
            info.recipeName ?: context.getString(R.string.not_available)
        )
        is InventoryActivitySourceInfo.Other -> context.getString(R.string.inventory_activity_other)
    }

    override fun sourceSubtitle(info: InventoryActivitySourceInfo): String? = when (info) {
        is InventoryActivitySourceInfo.Purchase -> info.invoiceNumber?.let {
            context.getString(R.string.inventory_activity_source_invoice, it)
        }
        is InventoryActivitySourceInfo.Waste -> info.sourceAreaName
        is InventoryActivitySourceInfo.StockCount -> null
        is InventoryActivitySourceInfo.Production -> info.status?.let { productionStatusText(it) }
        is InventoryActivitySourceInfo.Other -> null
    }

    override fun wasteReasonText(reason: WasteReason): String = when (reason) {
        WasteReason.EXPIRED -> context.getString(R.string.reason_expired)
        WasteReason.SPOILED -> context.getString(R.string.reason_spoiled)
        WasteReason.PREPARATION_ERROR -> context.getString(R.string.reason_preparation_error)
        WasteReason.OVERPRODUCTION -> context.getString(R.string.reason_overproduction)
        WasteReason.DROPPED_OR_DAMAGED -> context.getString(R.string.reason_dropped_or_damaged)
        WasteReason.CUSTOMER_RETURN -> context.getString(R.string.reason_customer_return)
        WasteReason.QUALITY_REJECTION -> context.getString(R.string.reason_quality_rejection)
        WasteReason.OTHER -> context.getString(R.string.reason_other)
    }

    override fun productionStatusText(status: DocumentStatus): String = when (status) {
        DocumentStatus.DRAFT -> context.getString(R.string.status_draft)
        DocumentStatus.POSTED -> context.getString(R.string.status_posted)
        DocumentStatus.VOIDED -> context.getString(R.string.status_voided)
    }
}
