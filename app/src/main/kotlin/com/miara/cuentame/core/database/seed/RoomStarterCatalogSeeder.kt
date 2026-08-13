package com.miara.cuentame.core.database.seed

import androidx.room.withTransaction
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientCategoryEntity
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.domain.service.StarterCatalogSeedFailure
import com.miara.cuentame.core.domain.service.StarterCatalogSeedResult
import com.miara.cuentame.core.domain.service.StarterCatalogSeeder
import com.miara.cuentame.core.model.catalog.StarterCatalogDefinition
import java.util.UUID
import javax.inject.Inject

class RoomStarterCatalogSeeder @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val timeProvider: TimeProvider
) : StarterCatalogSeeder {

    override suspend fun seedNewRestaurant(
        restaurantId: String,
        catalog: StarterCatalogDefinition
    ): StarterCatalogSeedResult {
        return try {
            android.util.Log.i("CRITICAL_SEED", "Starting seed for restaurant $restaurantId")
            database.withTransaction {
                var categoriesInserted = 0
                var categoriesReused = 0
                var ingredientsInserted = 0
                var ingredientsSkipped = 0
                var unitOptionsInserted = 0

                val now = timeProvider.now().toEpochMilli()
                val categoryMap = mutableMapOf<String, String>() // sourceName -> id

                // 1. Process Categories
                catalog.categories.forEach { catDef ->
                    val normalized = catDef.sourceName.normalizeName()
                    val existing = database.ingredientCategoryDao().findByNormalizedName(restaurantId, normalized)
                    
                    val id = if (existing != null) {
                        categoriesReused++
                        existing.id
                    } else {
                        val newId = generateDeterministicId(
                            catalog, restaurantId, "category", normalized
                        )
                        database.ingredientCategoryDao().upsert(
                            IngredientCategoryEntity(
                                id = newId,
                                restaurantId = restaurantId,
                                name = catDef.sourceName,
                                normalizedName = normalized,
                                sortOrder = catDef.sortOrder,
                                isActive = true,
                                createdAt = now,
                                updatedAt = now,
                                deletedAt = null
                            )
                        )
                        categoriesInserted++
                        newId
                    }
                    categoryMap[catDef.sourceName] = id
                }

                // 2. Process Ingredients
                catalog.items.forEach { itemDef ->
                    val normalized = itemDef.name.normalizeName()
                    val existing = database.ingredientDao().findByNormalizedName(restaurantId, normalized)
                    
                    if (existing != null) {
                        ingredientsSkipped++
                    } else {
                        val ingredientId = generateDeterministicId(
                            catalog, restaurantId, "ingredient", normalized
                        )
                        val categoryId = categoryMap[itemDef.sourceCategoryName]
                        
                        database.ingredientDao().insert(
                            IngredientEntity(
                                id = ingredientId,
                                restaurantId = restaurantId,
                                name = itemDef.name,
                                normalizedName = normalized,
                                categoryId = categoryId,
                                baseUnitId = itemDef.baseUnitId,
                                defaultAreaId = null,
                                sku = null,
                                notes = null,
                                reorderPointBase = null,
                                isActive = true,
                                createdAt = now,
                                updatedAt = now,
                                deletedAt = null
                            )
                        )
                        ingredientsInserted++

                        // 3. Process Unit Options
                        val hasPackageOptions = itemDef.additionalUnitOptions.any { it.isDefaultPurchase }
                        val baseOptionId = generateDeterministicId(
                            catalog, restaurantId, "base-unit-option", normalized
                        )
                        database.ingredientUnitOptionDao().insert(
                            IngredientUnitOptionEntity(
                                id = baseOptionId,
                                ingredientId = ingredientId,
                                displayName = itemDef.baseOptionLabel,
                                shortLabel = itemDef.baseOptionShortLabel,
                                standardUnitId = itemDef.baseUnitId,
                                factorToBase = java.math.BigDecimal.ONE,
                                isBase = true,
                                isDefaultCount = true,
                                isDefaultPurchase = !hasPackageOptions,
                                isActive = true,
                                createdAt = now,
                                updatedAt = now,
                                deletedAt = null
                            )
                        )
                        unitOptionsInserted++

                        itemDef.additionalUnitOptions.forEach { optDef ->
                            val optNormalized = optDef.displayName.normalizeName()
                            val optId = generateDeterministicId(
                                catalog, restaurantId, "package-unit-option", "$normalized:$optNormalized"
                            )
                            database.ingredientUnitOptionDao().insert(
                                IngredientUnitOptionEntity(
                                    id = optId,
                                    ingredientId = ingredientId,
                                    displayName = optDef.displayName,
                                    shortLabel = optDef.shortLabel,
                                    standardUnitId = null,
                                    factorToBase = optDef.factorToBase,
                                    isBase = false,
                                    isDefaultCount = optDef.isDefaultCount,
                                    isDefaultPurchase = optDef.isDefaultPurchase,
                                    isActive = true,
                                    createdAt = now,
                                    updatedAt = now,
                                    deletedAt = null
                                )
                            )
                            unitOptionsInserted++
                        }
                    }
                }

                android.util.Log.i("CRITICAL_SEED", "Seed completed: $ingredientsInserted ingredients")
                StarterCatalogSeedResult.Success(
                    categoriesInserted = categoriesInserted,
                    categoriesReused = categoriesReused,
                    ingredientsInserted = ingredientsInserted,
                    ingredientsSkipped = ingredientsSkipped,
                    unitOptionsInserted = unitOptionsInserted
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("CRITICAL_SEED", "Seeding failed", e)
            StarterCatalogSeedResult.Failure(StarterCatalogSeedFailure.DatabaseError(e))
        }
    }

    private fun generateDeterministicId(
        catalog: StarterCatalogDefinition,
        restaurantId: String,
        namespace: String,
        normalizedName: String
    ): String {
        val seed = "starter-catalog:${catalog.key}:v${catalog.version}:$restaurantId:$namespace:$normalizedName"
        return UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
    }
}
