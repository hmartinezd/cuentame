package com.venkoi.restaurantops.core.database.repository

import androidx.room.withTransaction
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.common.text.normalizeName
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.dao.IngredientCategoryDao
import com.venkoi.restaurantops.core.database.dao.InventoryAreaDao
import com.venkoi.restaurantops.core.database.dao.RestaurantDao
import com.venkoi.restaurantops.core.database.entity.IngredientCategoryEntity
import com.venkoi.restaurantops.core.database.entity.InventoryAreaEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.domain.repository.CompleteLocalSetupCommand
import com.venkoi.restaurantops.core.domain.repository.LocalSetupRepository
import com.venkoi.restaurantops.core.domain.repository.LocalSetupResult
import com.venkoi.restaurantops.core.domain.usecase.LocalSetupValidator
import com.venkoi.restaurantops.core.domain.validation.ValidationError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class RoomLocalSetupRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val restaurantDao: RestaurantDao,
    private val areaDao: InventoryAreaDao,
    private val categoryDao: IngredientCategoryDao,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val validator: LocalSetupValidator
) : LocalSetupRepository {

    override suspend fun isSetupComplete(): Boolean {
        val restaurant = restaurantDao.getRestaurant()
        if (restaurant == null) return false
        
        return areaDao.getActiveCount(restaurant.id) > 0
    }

    override fun observeIsSetupComplete(): Flow<Boolean> {
        return restaurantDao.observeRestaurant().flatMapLatest { restaurant ->
            if (restaurant == null) {
                android.util.Log.d("LocalSetupRepository", "observeIsSetupComplete: No restaurant found")
                flowOf(false)
            } else {
                areaDao.observeActiveAreas(restaurant.id).map { areas ->
                    val isComplete = areas.isNotEmpty()
                    android.util.Log.d("LocalSetupRepository", "observeIsSetupComplete: Restaurant ${restaurant.id} found, areas empty? ${areas.isEmpty()}, complete? $isComplete")
                    isComplete
                }
            }
        }.onStart { android.util.Log.d("LocalSetupRepository", "observeIsSetupComplete: Started") }
    }

    override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult {
        return try {
            validator.validate(command)

            database.withTransaction {
                val existing = restaurantDao.getRestaurant()
                val now = timeProvider.now().toEpochMilli()

                if (existing != null &&
                    command.restaurantId != null &&
                    existing.id != command.restaurantId.value
                ) {
                    throw LocalRestaurantIdentityMismatchException()
                }
                
                val restaurantId = if (existing != null) {
                    // Check if setup is already complete
                    if (areaDao.getActiveCount(existing.id) > 0) {
                        return@withTransaction LocalSetupResult.AlreadyCompleted
                    }
                    // Recovery: update existing restaurant
                    restaurantDao.update(
                        existing.copy(
                            name = command.restaurantName,
                            currencyCode = command.currencyCode,
                            localeTag = command.localeTag,
                            updatedAt = now
                        )
                    )
                    existing.id
                } else {
                    val newId = command.restaurantId?.value ?: idGenerator.newId()
                    android.util.Log.i("LocalSetupRepository", "Creating new restaurant: $newId - ${command.restaurantName}")
                    restaurantDao.insert(
                        RestaurantEntity(
                            id = newId,
                            name = command.restaurantName,
                            currencyCode = command.currencyCode,
                            localeTag = command.localeTag,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null
                        )
                    )
                    newId
                }

                command.areas.forEach { areaInput ->
                    areaDao.upsert(
                        InventoryAreaEntity(
                            id = idGenerator.newId(),
                            restaurantId = restaurantId,
                            name = areaInput.name,
                            normalizedName = areaInput.name.normalizeName(),
                            sortOrder = areaInput.sortOrder,
                            isActive = true,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null
                        )
                    )
                }

                command.categories.forEach { categoryInput ->
                    categoryDao.upsert(
                        IngredientCategoryEntity(
                            id = idGenerator.newId(),
                            restaurantId = restaurantId,
                            name = categoryInput.name,
                            normalizedName = categoryInput.name.normalizeName(),
                            sortOrder = categoryInput.sortOrder,
                            isActive = true,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null
                        )
                    )
                }
                LocalSetupResult.Success
            }
        } catch (e: Exception) {
            LocalSetupResult.Failure(e)
        }
    }
}

class LocalRestaurantIdentityMismatchException : IllegalStateException(
    "The authoritative restaurant identity does not match the existing local restaurant"
)
