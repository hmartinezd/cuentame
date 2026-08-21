package com.venkoi.cuentame.feature.reorder

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.domain.service.ReorderConfigurationStatus
import java.math.BigDecimal

enum class ReorderFilter { NEEDS_REORDER, ALL_CONFIGURED, MISSING_SETUP }

data class ReorderItem(
    val ingredientId: IngredientId,
    val ingredientName: String,
    val baseUnit: String,
    val currentBase: BigDecimal,
    val parBase: BigDecimal?,
    val reorderPointBase: BigDecimal?,
    val neededBase: BigDecimal?,
    val purchaseUnit: String?,
    val purchaseFactorBase: BigDecimal?,
    val purchaseUnits: BigDecimal?,
    val purchaseCoverageBase: BigDecimal?,
    val supplierName: String?,
    val supplierItem: String?,
    val supplierSku: String?,
    val needsReorder: Boolean,
    val status: ReorderConfigurationStatus,
    val configurationIssues: Set<ReorderConfigurationStatus> =
        if (status == ReorderConfigurationStatus.READY) emptySet() else setOf(status)
)

data class ReorderUiState(
    val isLoading: Boolean = true,
    val items: List<ReorderItem> = emptyList(),
    val filter: ReorderFilter = ReorderFilter.NEEDS_REORDER,
    val error: Throwable? = null
) {
    val visibleItems: List<ReorderItem> get() = when (filter) {
        ReorderFilter.NEEDS_REORDER -> items.filter { it.needsReorder }
        ReorderFilter.ALL_CONFIGURED -> items.filter { it.parBase != null }
        ReorderFilter.MISSING_SETUP -> items.filter { it.configurationIssues.isNotEmpty() }
    }
}
