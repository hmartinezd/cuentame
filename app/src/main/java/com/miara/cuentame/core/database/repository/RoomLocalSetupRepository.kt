package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientCategoryDao
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.entity.IngredientCategoryEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.usecase.LocalSetupValidator
import com.miara.cuentame.core.domain.validation.ValidationError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
            if (restaurant == null) flowOf(false)
            else areaDao.observeActiveAreas(restaurant.id).map { it.isNotEmpty() }
        }
    }

    override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult {
        return try {
            validator.validate(command)

            database.withTransaction {
                val existing = restaurantDao.getRestaurant()
                val now = timeProvider.now().toEpochMilli()
                
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
                    val newId = idGenerator.newId()
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
