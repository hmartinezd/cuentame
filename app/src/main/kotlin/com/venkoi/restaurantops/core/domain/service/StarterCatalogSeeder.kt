package com.venkoi.restaurantops.core.domain.service

import com.venkoi.restaurantops.core.model.catalog.StarterCatalogDefinition

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
