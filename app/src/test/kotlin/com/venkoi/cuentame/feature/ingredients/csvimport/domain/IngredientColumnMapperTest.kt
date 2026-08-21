package com.venkoi.cuentame.feature.ingredients.csvimport.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IngredientColumnMapperTest {
    @Test fun `canonical and common aliases map deterministically`() {
        val table = table("Item", "Department", "UOM", "Pack Qty", "Item #", "Price")
        val mapping = IngredientColumnMapper.suggest(table)
        assertThat(mapping.sourceToTarget.values).containsExactly(
            IngredientImportField.INGREDIENT_NAME,
            IngredientImportField.CATEGORY,
            IngredientImportField.BASE_UNIT,
            IngredientImportField.PACKAGE_CONVERSION_FACTOR,
            IngredientImportField.VENDOR_ITEM_CODE,
            null
        ).inOrder()
        assertThat(mapping.isValid).isTrue()
    }

    @Test fun `required mappings are reported and duplicate target is cleared by override`() {
        val table = table("Mystery", "Unit")
        val suggested = IngredientColumnMapper.suggest(table)
        assertThat(suggested.missingRequiredFields).containsExactly(IngredientImportField.INGREDIENT_NAME)
        val overridden = IngredientColumnMapper.update(suggested, 0, IngredientImportField.BASE_UNIT)
        assertThat(overridden.sourceToTarget[1]).isNull()
        assertThat(overridden.hasDuplicateTargets).isFalse()
    }

    @Test fun `canonical rows use positions and blank optional fields`() {
        val table = CsvSourceTable(
            listOf(CsvSourceColumn(0, "UOM"), CsvSourceColumn(1, "Item")),
            listOf(listOf("lbs", "Chicken Breast"))
        )
        val rows = IngredientColumnMapper.toCanonicalRows(table, IngredientColumnMapper.suggest(table))
        assertThat(rows.single()["ingredient_name"]).isEqualTo("Chicken Breast")
        assertThat(rows.single()["base_unit"]).isEqualTo("lbs")
        assertThat(rows.single()["supplier"]).isEmpty()
    }

    private fun table(vararg headers: String) = CsvSourceTable(
        headers.mapIndexed { index, header -> CsvSourceColumn(index, header) }, emptyList()
    )
}
