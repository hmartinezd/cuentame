package com.miara.cuentame.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import org.junit.Test
import java.time.Instant

class IngredientValidatorTest {
    private val validator = IngredientValidator()

    @Test(expected = ValidationError.InvalidName::class)
    fun `blank name throws InvalidName`() {
        val ingredient = Ingredient(
            id = IngredientId("id"),
            restaurantId = RestaurantId("r1"),
            name = "   ",
            normalizedName = "",
            categoryId = null,
            baseUnitId = UnitId("lb"),
            isActive = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        validator.validateIngredient(ingredient)
    }

    @Test
    fun `valid name does not throw`() {
        val ingredient = Ingredient(
            id = IngredientId("id"),
            restaurantId = RestaurantId("r1"),
            name = "Chicken",
            normalizedName = "chicken",
            categoryId = null,
            baseUnitId = UnitId("lb"),
            isActive = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        validator.validateIngredient(ingredient)
    }
}
