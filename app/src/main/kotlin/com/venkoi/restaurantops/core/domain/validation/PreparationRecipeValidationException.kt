package com.venkoi.restaurantops.core.domain.validation

class PreparationRecipeValidationException(
    val failures: List<PreparationRecipeValidationFailure>
) : Exception(failures.joinToString())
