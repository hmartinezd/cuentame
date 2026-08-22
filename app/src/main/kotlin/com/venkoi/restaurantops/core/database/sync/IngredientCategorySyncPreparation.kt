package com.venkoi.restaurantops.core.database.sync

import androidx.room.withTransaction
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import javax.inject.Inject

class IngredientCategorySyncPreparation @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val outboxWriter: IngredientCategorySyncOutboxWriter
) {
    suspend fun prepareUnsyncedIngredientCategories(restaurantId: String) {
        database.withTransaction {
            database.ingredientCategoryDao().getAllCategoriesForRestaurant(restaurantId)
                .forEach { category ->
                    val hasMetadata = database.syncEntityMetadataDao()
                        .get(INGREDIENT_CATEGORY_ENTITY_TYPE, category.id) != null
                    val hasPending = database.syncOutboxDao()
                        .hasPending(INGREDIENT_CATEGORY_ENTITY_TYPE, category.id)
                    if (!hasMetadata && !hasPending) outboxWriter.record(category)
                }
        }
    }
}
