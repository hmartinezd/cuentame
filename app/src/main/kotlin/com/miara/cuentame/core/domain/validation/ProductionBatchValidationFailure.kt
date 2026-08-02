package com.miara.cuentame.core.domain.validation

sealed interface ProductionBatchValidationFailure {
    data object RestaurantMismatch : ProductionBatchValidationFailure
    data object RecipeNotFound : ProductionBatchValidationFailure
    data object RecipeNotActive : ProductionBatchValidationFailure
    data object RecipeHasNoYield : ProductionBatchValidationFailure
    data object RecipeHasNoComponents : ProductionBatchValidationFailure
    data object MultiplierMustBePositive : ProductionBatchValidationFailure
    data object EffectiveTimeInFuture : ProductionBatchValidationFailure
    data object OutputIngredientNotFound : ProductionBatchValidationFailure
    data object OutputIngredientInactive : ProductionBatchValidationFailure
    data object OutputAreaNotFound : ProductionBatchValidationFailure
    data object OutputAreaInactive : ProductionBatchValidationFailure
    data object OutputUnitOptionNotFound : ProductionBatchValidationFailure
    data object OutputUnitOptionInactive : ProductionBatchValidationFailure
    data object ActualOutputMustBePositive : ProductionBatchValidationFailure
    data object BatchNotFound : ProductionBatchValidationFailure
    data object BatchNotDraft : ProductionBatchValidationFailure
    data object ComponentNotFound : ProductionBatchValidationFailure
    data object ComponentIngredientNotFound : ProductionBatchValidationFailure
    data object ComponentIngredientInactive : ProductionBatchValidationFailure
    data object ComponentIngredientRestaurantMismatch : ProductionBatchValidationFailure
    data object ComponentQuantityMustBePositive : ProductionBatchValidationFailure
    data object InvalidUnitFactor : ProductionBatchValidationFailure
    data object SourceAreaNotFound : ProductionBatchValidationFailure
    data object SourceAreaInactive : ProductionBatchValidationFailure
    data object SourceAreaRestaurantMismatch : ProductionBatchValidationFailure
    data object ComponentUnitOptionNotFound : ProductionBatchValidationFailure
    data object ComponentUnitOptionInactive : ProductionBatchValidationFailure
    data object ComponentUnitOptionMismatch : ProductionBatchValidationFailure
    data object ComponentCostUnavailable : ProductionBatchValidationFailure
    data object MovementHistoryConflict : ProductionBatchValidationFailure
    data object RestrictedByArchive : ProductionBatchValidationFailure
}

class ProductionBatchValidationException(val failures: List<ProductionBatchValidationFailure>) : Exception(failures.joinToString())
