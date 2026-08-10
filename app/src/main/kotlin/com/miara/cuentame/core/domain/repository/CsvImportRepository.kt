package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.feature.ingredient.import.domain.CsvIngredientImportDocument

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
    
    data class Failure(val message: String) : ImportResult()
    object StaleData : ImportResult()
}
