package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import java.math.BigDecimal

class IngredientValidator {
    fun validateIngredient(ingredient: Ingredient) {
        if (ingredient.name.normalizeName().isBlank()) {
            throw ValidationError.InvalidName
        }
    }

    fun validateUnitOption(option: IngredientUnitOption, baseUnitId: com.miara.cuentame.core.common.ids.UnitId) {
        if (option.displayName.normalizeName().isBlank()) {
            throw ValidationError.InvalidName
        }
        if (option.factorToBase <= BigDecimal.ZERO) {
            throw ValidationError.InvalidUnitFactor
        }
        if (option.isBase) {
            if (option.factorToBase.compareTo(BigDecimal.ONE) != 0) {
                throw ValidationError.InvalidBaseUnitFactor
            }
            if (option.standardUnitId != baseUnitId) {
                throw ValidationError.InvalidBaseUnitFactor
            }
        }
    }
}
