package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class ChickenFixtureTest {
    
    @Test
    fun `Chicken Breast fixture domain validation`() {
        val now = Instant.now()
        val restId = RestaurantId("rest_1")
        val ingId = IngredientId("chicken_1")
        val baseUnitId = UnitId("mass_lb")

        val chicken = Ingredient(
            id = ingId,
            restaurantId = restId,
            name = "Chicken Breast",
            normalizedName = "chicken breast",
            categoryId = IngredientCategoryId("cat_meat"),
            baseUnitId = baseUnitId,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        val lbOption = IngredientUnitOption(
            id = IngredientUnitOptionId("opt_lb"),
            ingredientId = ingId,
            displayName = "Pound",
            shortLabel = "lb",
            standardUnitId = baseUnitId,
            factorToBase = BigDecimal.ONE,
            isBase = true,
            isDefaultCount = true,
            isDefaultPurchase = false,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        val ozOption = IngredientUnitOption(
            id = IngredientUnitOptionId("opt_oz"),
            ingredientId = ingId,
            displayName = "Ounce",
            shortLabel = "oz",
            standardUnitId = UnitId("mass_oz"),
            factorToBase = BigDecimal("0.0625"),
            isBase = false,
            isDefaultCount = false,
            isDefaultPurchase = false,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        val caseOption = IngredientUnitOption(
            id = IngredientUnitOptionId("opt_case"),
            ingredientId = ingId,
            displayName = "Case",
            shortLabel = "case",
            standardUnitId = null,
            factorToBase = BigDecimal("40"),
            isBase = false,
            isDefaultCount = false,
            isDefaultPurchase = true,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        assertThat(lbOption.factorToBase).isEqualTo(BigDecimal.ONE)
        assertThat(ozOption.factorToBase).isEqualTo(BigDecimal("0.0625"))
        assertThat(caseOption.factorToBase).isEqualTo(BigDecimal("40"))
        
        // Conversion check
        val twoCasesInBase = BigDecimal("2").multiply(caseOption.factorToBase)
        assertThat(twoCasesInBase).isEqualTo(BigDecimal("80"))
    }
}
