package com.miara.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class IngredientUnitConverterTest {
    private val converter = IngredientUnitConverter()

    @Test
    fun `toBase converts case to lb`() {
        val option = createOption(BigDecimal("40")) // Case of 40lb
        val result = converter.toBase(BigDecimal("2"), option)
        assertThat(result.compareTo(BigDecimal("80"))).isEqualTo(0)
    }

    @Test
    fun `fromBase converts lb to case`() {
        val option = createOption(BigDecimal("40"))
        val result = converter.fromBase(BigDecimal("80"), option)
        assertThat(result.compareTo(BigDecimal("2"))).isEqualTo(0)
    }

    private fun createOption(factor: BigDecimal) = IngredientUnitOption(
        id = IngredientUnitOptionId("1"),
        ingredientId = IngredientId("1"),
        displayName = "Case",
        shortLabel = "cs",
        standardUnitId = null,
        factorToBase = factor,
        isBase = false,
        isDefaultCount = false,
        isDefaultPurchase = false,
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
