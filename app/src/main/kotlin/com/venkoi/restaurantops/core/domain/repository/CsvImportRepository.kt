package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.RestaurantId

interface CsvImportRepository {
    suspend fun commitImport(
        restaurantId: RestaurantId,
        document: CsvIngredientImportDocument
    ): ImportResult
}

sealed class ImportResult {
    data class Success(
        val ingredientsCreated: Int,
        val categoriesCreated: Int,
        val suppliersCreated: Int,
        val mappingsCreated: Int,
        val rowsSkipped: Int
    ) : ImportResult()
    
    data class Failure(val failure: ImportFailure) : ImportResult()
}

sealed interface ImportFailure {
    data object InvalidPlan : ImportFailure
    data object StateChanged : ImportFailure
    data object RestaurantUnavailable : ImportFailure
    data object PersistenceFailure : ImportFailure
    data object FileReadFailure : ImportFailure
    data object Unexpected : ImportFailure
}
