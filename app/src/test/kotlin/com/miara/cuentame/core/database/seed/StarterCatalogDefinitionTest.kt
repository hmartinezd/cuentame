package com.miara.cuentame.core.database.seed

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.catalog.CubanFoodiesStarterCatalog
import com.miara.cuentame.core.common.text.normalizeName

import org.junit.Test

class StarterCatalogDefinitionTest {

    @Test
    fun `Cuban Foodies definition satisfies all requirements`() {
        val catalog = CubanFoodiesStarterCatalog.definition

        // 1. Categories
        assertThat(catalog.categories).hasSize(9)
        val categoryNames = catalog.categories.map { it.sourceName }
        assertThat(categoryNames).containsExactly(
            "Produce", "Meat & Seafood", "Dairy", "Dry Goods", 
            "Bread & Bakery", "Beverages", "Packaging", "Cleaning", "Miscellaneous"
        ).inOrder()

        // 2. Ingredients
        assertThat(catalog.items).hasSize(89)
        
        // 3. Fuel Charge exclusion
        val ingredientNames = catalog.items.map { it.name }
        assertThat(ingredientNames).doesNotContain("Fuel Charge")

        // 4. Normalized names uniqueness
        val normalizedCategories = catalog.categories.map { it.sourceName.normalizeName() }
        assertThat(normalizedCategories).containsNoDuplicates()

        val normalizedIngredients = catalog.items.map { it.name.normalizeName() }
        assertThat(normalizedIngredients).containsNoDuplicates()

        // 5. Category references
        catalog.items.forEach { item ->
            assertThat(categoryNames).contains(item.sourceCategoryName)
        }

        // 6. System unit references
        val validUnits = listOf("mass_lb", "mass_oz", "volume_gallon_us", "count_each")
        catalog.items.forEach { item ->
            assertThat(validUnits).contains(item.baseUnitId)
        }

        // 7. Additional package options
        val ingredientsWithAdditionalOptions = catalog.items.filter { it.additionalUnitOptions.isNotEmpty() }
        assertThat(ingredientsWithAdditionalOptions).hasSize(7)

        val totalAdditionalOptions = catalog.items.sumOf { it.additionalUnitOptions.size }
        assertThat(totalAdditionalOptions).isEqualTo(8) // 6 items have 1, Eggs has 2
    }

    @Test
    fun `Eggs XL has Dozen and 15 dozen case options`() {
        val eggs = CubanFoodiesStarterCatalog.definition.items.find { it.name == "Eggs XL (15 Dozen)" }!!
        assertThat(eggs.baseUnitId).isEqualTo("count_each")
        assertThat(eggs.additionalUnitOptions).hasSize(2)
        
        val dozen = eggs.additionalUnitOptions.find { it.displayName == "Dozen" }!!
        assertThat(dozen.factorToBase).isEqualTo(java.math.BigDecimal(12))
        assertThat(dozen.isDefaultPurchase).isFalse()

        val fifteenDozen = eggs.additionalUnitOptions.find { it.displayName == "15 dozen case" }!!
        assertThat(fifteenDozen.factorToBase).isEqualTo(java.math.BigDecimal(180))
        assertThat(fifteenDozen.isDefaultPurchase).isTrue()
    }
}
