package com.miara.cuentame.feature.reorder

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.domain.service.ReorderConfigurationStatus
import org.junit.Test
import java.math.BigDecimal

class ReorderExportTest {
    private fun item(name: String, supplier: String? = null, unit: String? = null) = ReorderItem(
        IngredientId(name), name, "lb", BigDecimal("1.25"), BigDecimal("5.5"), null,
        BigDecimal("4.25"), unit, unit?.let { BigDecimal("2.5") }, unit?.let { BigDecimal("2") },
        unit?.let { BigDecimal("5.0") }, supplier, "Roma tomato", "SKU-1", true,
        if (unit == null) ReorderConfigurationStatus.MISSING_PURCHASE_UNIT else if (supplier == null) ReorderConfigurationStatus.MISSING_SUPPLIER else ReorderConfigurationStatus.READY
    )

    @Test fun csvIsDeterministicEscapedAndCanonical() {
        val csv = ReorderExport.csv(listOf(item("Tomatoes, Roma", "Joe's Produce", "case"), item("Basil")))
        assertThat(csv).contains("\"Tomatoes, Roma\"")
        assertThat(csv).contains("Joe's Produce")
        assertThat(csv).contains("1.25")
        assertThat(csv).doesNotContain("5.0")
        assertThat(csv.lines()[1]).contains("Basil")
    }

    @Test fun shoppingListGroupsSupplierAndKeepsUnassigned() {
        val text = ReorderExport.shoppingList(listOf(item("Tomato", "Joe's Produce", "case"), item("Basil")), "No supplier assigned")
        assertThat(text).contains("JOE'S PRODUCE")
        assertThat(text).contains("NO SUPPLIER ASSIGNED")
        assertThat(text).contains("Basil")
    }

    @Test fun csvExportsAllConfigurationIssuesInDeterministicOrder() {
        val incomplete = item("Basil").copy(
            configurationIssues = setOf(
                ReorderConfigurationStatus.MISSING_SUPPLIER,
                ReorderConfigurationStatus.MISSING_PURCHASE_UNIT
            )
        )

        val row = ReorderExport.csv(listOf(incomplete)).lines()[1]

        assertThat(row).contains("MISSING_PURCHASE_UNIT|MISSING_SUPPLIER")
    }

    @Test fun exportAlwaysUsesActionableScopeRegardlessOfVisibleFilter() {
        val abovePar = item("Lemons", "Supplier", "case").copy(needsReorder = false)

        val csv = ReorderExport.csv(listOf(item("Chicken", "Supplier", "case"), abovePar))
        val shoppingList = ReorderExport.shoppingList(listOf(item("Chicken", "Supplier", "case"), abovePar), "Unassigned")

        assertThat(csv).contains("Chicken")
        assertThat(csv).doesNotContain("Lemons")
        assertThat(shoppingList).contains("Chicken")
        assertThat(shoppingList).doesNotContain("Lemons")
    }
}
