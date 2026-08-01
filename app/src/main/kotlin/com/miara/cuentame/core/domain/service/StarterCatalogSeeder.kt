package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.model.catalog.StarterCatalogDefinition

sealed interface StarterCatalogSeedResult {
    data class Success(
        val categoriesInserted: Int,
        val categoriesReused: Int,
        val ingredientsInserted: Int,
        val ingredientsSkipped: Int,
        val unitOptionsInserted: Int
    ) : StarterCatalogSeedResult

    data class Failure(val reason: StarterCatalogSeedFailure) : StarterCatalogSeedResult
}

sealed interface StarterCatalogSeedFailure {
    data class DatabaseError(val cause: Throwable) : StarterCatalogSeedFailure
}

interface StarterCatalogSeeder {
    suspend fun seedNewRestaurant(
        restaurantId: String,
        catalog: StarterCatalogDefinition
    ): StarterCatalogSeedResult
}
