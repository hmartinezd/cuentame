package com.miara.cuentame.core.domain.validation

sealed class ValidationError(override val message: String) : Exception(message) {
    data object InvalidName : ValidationError("Name cannot be empty")
    data object DuplicateActiveName : ValidationError("An active record with this name already exists")
    data object InvalidDecimal : ValidationError("Invalid decimal value")
    data object InvalidUnitFactor : ValidationError("Unit factor must be greater than zero")
    data object IncompatibleUnitDimensions : ValidationError("Units must have the same dimension")
    data object MissingBaseUnitOption : ValidationError("Ingredient must have a base unit option")
    data object MultipleBaseUnitOptions : ValidationError("Ingredient cannot have more than one base unit option")
    data object InvalidBaseUnitFactor : ValidationError("Base unit option must have a factor of 1")
    data object InvalidDefaultUnitOption : ValidationError("Default option must belong to the correct ingredient")
    data object IngredientHasInventoryHistory : ValidationError("Cannot perform this action on an ingredient with movement history")
    data object ArchivedReference : ValidationError("Cannot reference an archived record")
    data object InvalidCurrencyCode : ValidationError("Invalid ISO currency code")
}
