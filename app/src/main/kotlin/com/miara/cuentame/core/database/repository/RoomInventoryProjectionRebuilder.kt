package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.parsePersistedEnum
import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientCostProjectionDao
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.InventoryProjectionDao
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostCalculationResult
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostFailure
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostCalculator
import com.miara.cuentame.core.domain.service.HistoricalInventoryMovement
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Rebuilds inventory projections (balance and cost) from historical movements.
 */
class RoomInventoryProjectionRebuilder @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val ingredientDao: IngredientDao,
    private val movementDao: InventoryMovementDao,
    private val projectionDao: InventoryProjectionDao,
    private val costProjectionDao: IngredientCostProjectionDao,
    private val costCalculator: HistoricalInventoryCostCalculator,
    private val historyValidator: InventoryMovementHistoryValidator,
    private val timeProvider: TimeProvider
) {
    suspend fun rebuildForIngredient(ingredientId: IngredientId) {
        database.withTransaction {
            val ingredient = ingredientDao.getById(ingredientId.value) ?: return@withTransaction
            val restaurantId = RestaurantId(ingredient.restaurantId)
            
            val allMovements = movementDao.getByIngredient(ingredientId.value)
            
            // Validate the complete movement/reversal graph
            historyValidator.validateCompleteHistory(allMovements)
            
            val historicalMoves = allMovements.map { move ->
                HistoricalInventoryMovement(
                    id = move.id,
                    movementType = parsePersistedEnum(move.movementType, InventoryMovementType.UNKNOWN),
                    quantityBaseSigned = BigDecimal(move.quantityBaseSigned),
                    unitCostBaseSnapshot = move.unitCostBaseSnapshot?.let { BigDecimal(it) },
                    sourceDocumentType = parsePersistedEnum(move.sourceDocumentType, SourceDocumentType.UNKNOWN),
                    sourceDocumentId = move.sourceDocumentId,
                    effectiveAt = move.effectiveAt,
                    createdAt = move.createdAt,
                    reversalOfMovementId = move.reversalOfMovementId
                )
            }

            val calculationResult = costCalculator.calculate(historicalMoves)

            val costResult = when (calculationResult) {
                is HistoricalInventoryCostCalculationResult.Success -> calculationResult.value
                is HistoricalInventoryCostCalculationResult.Failure -> {
                    throw ValidationError.MalformedInventoryMovementHistory
                }
            }

            val effectiveMovements = allMovements.filter { it.id in costResult.effectiveMovementIds }

            // Rebuild balance by area
            projectionDao.deleteForIngredient(ingredientId.value)
            val areaBalances = mutableMapOf<String, BigDecimal>()
            
            effectiveMovements.forEach { movement ->
                val areaId = movement.areaId
                val currentAreaBalance = areaBalances.getOrDefault(areaId, BigDecimal.ZERO)
                areaBalances[areaId] = currentAreaBalance.add(BigDecimal(movement.quantityBaseSigned))
            }
            
            val updatedAt = timeProvider.now().toEpochMilli()

            // Persist balances
            areaBalances.forEach { (areaId, quantity) ->
                projectionDao.upsert(
                    InventoryBalanceProjectionEntity(
                        restaurantId = restaurantId.value,
                        ingredientId = ingredientId.value,
                        areaId = areaId,
                        quantityBase = quantity.toPlainString(),
                        updatedAt = updatedAt
                    )
                )
            }
            
            // Persist cost - Delete if no established cost
            costProjectionDao.deleteForIngredient(ingredientId.value)
            if (costResult.hasEstablishedCost) {
                costProjectionDao.upsert(
                    IngredientCostProjectionEntity(
                        restaurantId = restaurantId.value,
                        ingredientId = ingredientId.value,
                        averageUnitCostBase = costResult.averageUnitCostBase?.toPlainString(),
                        updatedAt = updatedAt
                    )
                )
            }
        }
    }
}
