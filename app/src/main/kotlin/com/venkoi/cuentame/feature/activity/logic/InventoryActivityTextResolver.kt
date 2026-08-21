package com.venkoi.cuentame.feature.activity.logic

import androidx.compose.runtime.staticCompositionLocalOf
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.InventoryActivityCategory
import com.venkoi.cuentame.core.model.inventory.InventoryActivitySourceInfo
import com.venkoi.cuentame.core.model.inventory.WasteReason

interface InventoryActivityTextResolver {
    fun categoryText(category: InventoryActivityCategory): String
    fun sourceTitle(info: InventoryActivitySourceInfo): String
    fun sourceSubtitle(info: InventoryActivitySourceInfo): String?
    fun wasteReasonText(reason: WasteReason): String
    fun productionStatusText(status: DocumentStatus): String
}

val LocalInventoryActivityTextResolver = staticCompositionLocalOf<InventoryActivityTextResolver> {
    error("No InventoryActivityTextResolver provided")
}
