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
import com.miara.cuentame.core.domain.validation.ValidationError
import javax.inject.Inject

class RoomLocalSetupRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val restaurantDao: RestaurantDao,
    private val areaDao: InventoryAreaDao,
    private val categoryDao: IngredientCategoryDao,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : LocalSetupRepository {

    override suspend fun isSetupComplete(): Boolean {
        val restaurant = restaurantDao.getRestaurant()
        if (restaurant == null) return false
        
        return areaDao.getActiveCount() > 0
    }

    override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult {
        return try {
            val existing = restaurantDao.getRestaurant()
            if (existing != null) return LocalSetupResult.AlreadyCompleted

            validateCommand(command)

            database.withTransaction {
                val now = timeProvider.now().toEpochMilli()
                val restaurantId = idGenerator.newId()
                
                restaurantDao.insert(
                    RestaurantEntity(
                        id = restaurantId,
                        name = command.restaurantName,
                        currencyCode = command.currencyCode,
                        localeTag = command.localeTag,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null
                    )
                )

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
            }
            LocalSetupResult.Success
        } catch (e: Exception) {
            LocalSetupResult.Failure(e)
        }
    }

    private suspend fun validateCommand(command: CompleteLocalSetupCommand) {
        if (command.restaurantName.normalizeName().isBlank()) throw ValidationError.InvalidName
        if (command.areas.isEmpty()) throw ValidationError.NoActiveInventoryArea
        
        val normalizedAreas = command.areas.map { it.name.normalizeName() }
        if (normalizedAreas.size != normalizedAreas.distinct().size) {
            throw ValidationError.DuplicateActiveName
        }
    }
}
