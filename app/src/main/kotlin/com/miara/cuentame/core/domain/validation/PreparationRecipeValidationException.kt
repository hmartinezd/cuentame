package com.miara.cuentame.core.domain.validation

class PreparationRecipeValidationException(
    val failures: List<PreparationRecipeValidationFailure>
) : Exception(failures.joinToString())
