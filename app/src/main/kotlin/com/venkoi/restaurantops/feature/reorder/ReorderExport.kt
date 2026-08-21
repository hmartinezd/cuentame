package com.venkoi.restaurantops.feature.reorder

import com.venkoi.restaurantops.core.domain.service.ReorderConfigurationStatus

object ReorderExport {
    private fun csv(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value
    private fun n(value: java.math.BigDecimal?) = value?.stripTrailingZeros()?.toPlainString().orEmpty()

    fun csv(items: List<ReorderItem>): String = buildString {
        appendLine("Supplier,Supplier Item / SKU,Ingredient,Current Quantity Base,Base Unit,Par Quantity Base,Reorder Point Base,Quantity Needed Base,Purchase Unit,Purchase Unit Factor To Base,Purchase Units Suggested,Suggested Purchase Quantity Base,Configuration Status")
        items.filter { it.needsReorder }.sortedWith(compareBy({ it.supplierName ?: "" }, { it.ingredientName }, { it.ingredientId.value })).forEach { i ->
            val configuration = i.configurationIssues.sortedBy { it.ordinal }.joinToString("|") { it.name }.ifEmpty { ReorderConfigurationStatus.READY.name }
            appendLine(listOf(i.supplierName.orEmpty(), listOfNotNull(i.supplierItem, i.supplierSku).joinToString(" / "), i.ingredientName, n(i.currentBase), i.baseUnit, n(i.parBase), n(i.reorderPointBase), n(i.neededBase), i.purchaseUnit.orEmpty(), n(i.purchaseFactorBase), n(i.purchaseUnits), n(i.purchaseCoverageBase), configuration).joinToString(",", transform = ::csv))
        }
    }

    fun shoppingList(items: List<ReorderItem>, noSupplier: String): String = items.filter { it.needsReorder }
        .groupBy { it.supplierName ?: noSupplier }.toSortedMap()
        .entries.joinToString("\n\n") { (supplier, group) ->
            supplier.uppercase() + "\n\n" + group.sortedBy { it.ingredientName }.joinToString("\n") { i ->
                val amount = i.purchaseUnits?.let { "${n(it)} ${i.purchaseUnit}" } ?: "${n(i.neededBase)} ${i.baseUnit}"
                "${i.ingredientName}\n$amount"
            }
        }
}
