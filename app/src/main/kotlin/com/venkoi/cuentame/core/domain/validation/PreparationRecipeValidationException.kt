package com.venkoi.cuentame.core.domain.validation

class PreparationRecipeValidationException(
    val failures: List<PreparationRecipeValidationFailure>
) : Exception(failures.joinToString())
