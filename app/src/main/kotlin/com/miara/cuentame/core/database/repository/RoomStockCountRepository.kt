package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.StockCountLineId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.StockCountDao
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.SaveStockCountLineCommand
import com.miara.cuentame.core.domain.repository.StartStockCountCommand
import com.miara.cuentame.core.domain.repository.StockCountAreaDetails
import com.miara.cuentame.core.domain.repository.StockCountDetails
import com.miara.cuentame.core.domain.repository.StockCountFilter
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.repository.StockCountSummary
import com.miara.cuentame.core.domain.repository.UpdateStockCountDraftCommand
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.count.StockCount
import com.miara.cuentame.core.model.count.StockCountArea
import com.miara.cuentame.core.model.count.StockCountLine
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.inventory.StockCountStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class RoomStockCountRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val countDao: StockCountDao,
    private val movementDao: InventoryMovementDao,
    private val ingredientDao: IngredientDao,
    private val areaDao: InventoryAreaDao,
    private val unitOptionDao: IngredientUnitOptionDao,
    private val restaurantDao: RestaurantDao,
    private val snapshotService: InventorySnapshotService,
    private val historyValidator: StockCountMovementHistoryValidator,
    private val projectionRebuilder: RoomInventoryProjectionRebuilder,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : StockCountRepository {

    private suspend fun requireActiveRestaurant(): RestaurantEntity {
        return restaurantDao.getRestaurant() ?: throw ValidationError.RecordNotFound
    }

    override fun observeCounts(filter: StockCountFilter): Flow<List<StockCountSummary>> {
        return countDao.observeFilteredCounts(
            restaurantId = filter.restaurantId.value,
            status = filter.status?.name,
            query = filter.query?.trim()?.ifBlank { null }
        ).flatMapLatest { counts ->
            val summaryFlows = counts.map { entity ->
                countDao.observeAreasForCount(entity.id).map { areas ->
                    val completedCount = areas.count { it.status == CountAreaStatus.COMPLETED.name }
                    val progress = if (areas.isEmpty()) 0f else completedCount.toFloat() / areas.size
                    StockCountSummary(
                        count = entity.toDomain(),
                        areaCount = areas.size,
                        progress = progress
                    )
                }
            }
            if (summaryFlows.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                combine(summaryFlows) { it.toList() }
            }
        }
    }

    override fun observeCount(id: StockCountId): Flow<StockCountDetails?> {
        return countDao.observeCountById(id.value).flatMapLatest { countEntity ->
            if (countEntity == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(null)
            
            countDao.observeAreasForCount(id.value).flatMapLatest { areaEntities ->
                val areaDetailFlows = areaEntities.map { areaEntity ->
                    val areaInfoFlow = areaDao.observeById(areaEntity.areaId)
                    val linesFlow = countDao.observeLinesForArea(areaEntity.id)
                    combine(areaInfoFlow, linesFlow) { areaInfo, lineEntities ->
                        StockCountAreaDetails(
                            area = areaEntity.toDomain(),
                            areaName = areaInfo?.name ?: "Unknown archived area",
                            restaurantId = RestaurantId(countEntity.restaurantId),
                            effectiveAt = Instant.ofEpochMilli(countEntity.effectiveAt),
                            lines = lineEntities.map { it.toDomain() }
                        )
                    }
                }
                
                if (areaDetailFlows.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(StockCountDetails(countEntity.toDomain(), emptyList()))
                } else {
                    combine(areaDetailFlows) { areaDetails ->
                        StockCountDetails(
                            count = countEntity.toDomain(),
                            areas = areaDetails.toList()
                        )
                    }
                }
            }
        }
    }

    override fun observeCountArea(id: StockCountAreaId): Flow<StockCountAreaDetails?> {
        return countDao.observeAreaById(id.value).flatMapLatest { areaEntity ->
            if (areaEntity == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(null)
            
            countDao.observeCountById(areaEntity.stockCountId).filterNotNull().flatMapLatest { countEntity ->
                val areaInfoFlow = areaDao.observeById(areaEntity.areaId)
                val linesFlow = countDao.observeLinesForArea(id.value)
                
                combine(areaInfoFlow, linesFlow) { areaInfo, lineEntities ->
                    StockCountAreaDetails(
                        area = areaEntity.toDomain(),
                        areaName = areaInfo?.name ?: "Unknown archived area",
                        restaurantId = RestaurantId(countEntity.restaurantId),
                        effectiveAt = Instant.ofEpochMilli(countEntity.effectiveAt),
                        lines = lineEntities.map { it.toDomain() }
                    )
                }
            }
        }
    }

    override suspend fun getCountedIngredientIds(countId: StockCountId, areaId: InventoryAreaId): Set<IngredientId> {
        val areas = countDao.getAreasForCount(countId.value)
        val countArea = areas.find { it.areaId == areaId.value } ?: return emptySet()
        return countDao.getLinesForArea(countArea.id).map { IngredientId(it.ingredientId) }.toSet()
    }

    override suspend fun getDraftAreaIds(restaurantId: RestaurantId): Set<InventoryAreaId> {
        val draftCounts = countDao.getCountsByStatus(restaurantId.value, StockCountStatus.DRAFT.name)
        val areaIds = mutableSetOf<InventoryAreaId>()
        draftCounts.forEach { count ->
            val areas = countDao.getAreasForCount(count.id)
            areaIds.addAll(areas.map { InventoryAreaId(it.areaId) })
        }
        return areaIds
    }

    override suspend fun start(command: StartStockCountCommand): StockCountId {
        return database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            if (activeRestaurant.id != command.restaurantId.value) throw ValidationError.StockCountOwnershipMismatch

            if (command.name.isBlank()) throw ValidationError.InvalidName
            
            if (command.effectiveAt > timeProvider.now()) throw ValidationError.InvalidCountEffectiveTime

            if (command.areaIds.isEmpty()) throw ValidationError.StockCountHasNoAreas
            if (command.areaIds.distinct().size != command.areaIds.size) throw ValidationError.InvalidCountArea

            for (areaId in command.areaIds) {
                val area = areaDao.getById(areaId.value) ?: throw ValidationError.InvalidCountArea
                if (area.restaurantId != activeRestaurant.id) throw ValidationError.InvalidCountArea
                if (!area.isActive || area.deletedAt != null) throw ValidationError.ArchivedReference
                
                if (countDao.isAreaInAnyDraftCount(areaId.value)) throw ValidationError.StockCountAreaAlreadyInDraft
            }

            val now = timeProvider.now()
            val countId = StockCountId(idGenerator.newId())
            val count = StockCount(
                id = countId,
                restaurantId = command.restaurantId,
                name = command.name.trim(),
                startedAt = now,
                effectiveAt = command.effectiveAt,
                status = StockCountStatus.DRAFT,
                notes = command.notes?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now
            )

            countDao.insertCount(count.toEntity())

            val areaEntities = command.areaIds.mapIndexed { index, areaId ->
                StockCountArea(
                    id = StockCountAreaId(idGenerator.newId()),
                    stockCountId = countId,
                    areaId = areaId,
                    status = CountAreaStatus.NOT_STARTED,
                    sortOrder = index
                ).toEntity()
            }
            countDao.insertCountAreas(areaEntities)

            countId
        }
    }

    override suspend fun updateDraft(command: UpdateStockCountDraftCommand) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val existing = countDao.getCountById(command.countId.value) ?: throw ValidationError.StockCountNotFound
            if (existing.restaurantId != activeRestaurant.id) throw ValidationError.StockCountOwnershipMismatch
            if (existing.status != StockCountStatus.DRAFT.name) throw ValidationError.StockCountNotDraft

            if (command.name.isBlank()) throw ValidationError.InvalidName
            if (command.effectiveAt > timeProvider.now()) throw ValidationError.InvalidCountEffectiveTime

            if (existing.effectiveAt != command.effectiveAt.toEpochMilli()) {
                val areas = countDao.getAreasForCount(command.countId.value)
                val anyStarted = areas.any { it.status != CountAreaStatus.NOT_STARTED.name }
                val lines = countDao.getAllLinesForCount(command.countId.value)
                if (anyStarted || lines.isNotEmpty()) {
                     throw ValidationError.InvalidCountEffectiveTime 
                }
            }

            val updated = existing.copy(
                name = command.name.trim(),
                effectiveAt = command.effectiveAt.toEpochMilli(),
                notes = command.notes?.trim()?.ifBlank { null },
                updatedAt = timeProvider.now().toEpochMilli()
            )
            val affected = countDao.updateCount(updated)
            if (affected != 1) throw ValidationError.RecordNotFound
        }
    }

    override suspend fun saveLine(command: SaveStockCountLineCommand): StockCountLineId {
        return database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val count = countDao.getCountById(command.countId.value) ?: throw ValidationError.StockCountNotFound
            if (count.restaurantId != activeRestaurant.id) throw ValidationError.StockCountOwnershipMismatch
            if (count.status != StockCountStatus.DRAFT.name) throw ValidationError.StockCountNotDraft

            val areaEntity = countDao.getAreaById(command.countAreaId.value) ?: throw ValidationError.StockCountAreaNotFound
            if (areaEntity.stockCountId != count.id) throw ValidationError.StockCountAreaOwnershipMismatch
            if (areaEntity.status == CountAreaStatus.COMPLETED.name) throw ValidationError.StockCountAlreadyCompleted

            val ingredient = ingredientDao.getById(command.ingredientId.value) ?: throw ValidationError.IngredientNotFound
            if (ingredient.restaurantId != activeRestaurant.id) throw ValidationError.IngredientOwnershipMismatch

            val option = unitOptionDao.getById(command.ingredientUnitOptionId.value) ?: throw ValidationError.UnitOptionNotFound
            if (option.ingredientId != ingredient.id) throw ValidationError.InvalidCountUnitOption
            if (option.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor

            if (command.quantityEntered < BigDecimal.ZERO) throw ValidationError.InvalidCountQuantity

            val qtyBase = command.quantityEntered.multiply(option.factorToBase, MathContext.DECIMAL128)
            val now = timeProvider.now()

            if (command.lineId == null) {
                if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference
                if (!option.isActive || option.deletedAt != null) throw ValidationError.ArchivedReference

                // Check for existing ingredient in area
                val existingLines = countDao.getLinesForArea(command.countAreaId.value)
                if (existingLines.any { it.ingredientId == command.ingredientId.value }) {
                    throw ValidationError.DuplicateIngredientInCountArea
                }

                val newLineId = StockCountLineId(idGenerator.newId())
                val line = StockCountLine(
                    id = newLineId,
                    stockCountAreaId = command.countAreaId,
                    ingredientId = command.ingredientId,
                    ingredientUnitOptionId = command.ingredientUnitOptionId,
                    quantityEntered = command.quantityEntered,
                    quantityBase = qtyBase,
                    notes = command.notes?.trim()?.ifBlank { null },
                    createdAt = now,
                    updatedAt = now
                )
                countDao.insertCountLine(line.toEntity())

                if (areaEntity.status == CountAreaStatus.NOT_STARTED.name) {
                    countDao.updateCountArea(areaEntity.copy(
                        status = CountAreaStatus.IN_PROGRESS.name,
                        startedAt = now.toEpochMilli()
                    ))
                }

                newLineId
            } else {
                val existingLine = countDao.getLineById(command.lineId.value) ?: throw ValidationError.StockCountLineNotFound
                if (existingLine.stockCountAreaId != command.countAreaId.value) throw ValidationError.StockCountLineOwnershipMismatch

                if (existingLine.ingredientId != command.ingredientId.value || existingLine.ingredientUnitOptionId != command.ingredientUnitOptionId.value) {
                     if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference
                     if (!option.isActive || option.deletedAt != null) throw ValidationError.ArchivedReference
                }

                val updatedLine = existingLine.copy(
                    ingredientId = command.ingredientId.value,
                    ingredientUnitOptionId = command.ingredientUnitOptionId.value,
                    quantityEntered = command.quantityEntered.toPlainString(),
                    quantityBase = qtyBase.toPlainString(),
                    expectedQuantityBaseSnapshot = null,
                    adjustmentQuantityBase = null,
                    notes = command.notes?.trim()?.ifBlank { null },
                    updatedAt = now.toEpochMilli()
                )
                val affected = countDao.updateCountLine(updatedLine)
                if (affected != 1) throw ValidationError.StockCountLineNotFound
                command.lineId
            }
        }
    }

    override suspend fun deleteLine(countId: StockCountId, countAreaId: StockCountAreaId, lineId: StockCountLineId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val count = countDao.getCountById(countId.value) ?: throw ValidationError.StockCountNotFound
            if (count.restaurantId != activeRestaurant.id) throw ValidationError.StockCountOwnershipMismatch
            if (count.status != StockCountStatus.DRAFT.name) throw ValidationError.StockCountNotDraft

            val area = countDao.getAreaById(countAreaId.value) ?: throw ValidationError.StockCountAreaNotFound
            if (area.stockCountId != count.id) throw ValidationError.StockCountAreaOwnershipMismatch
            if (area.status == CountAreaStatus.COMPLETED.name) throw ValidationError.StockCountAlreadyCompleted

            val line = countDao.getLineById(lineId.value) ?: throw ValidationError.StockCountLineNotFound
            if (line.stockCountAreaId != countAreaId.value) throw ValidationError.StockCountLineOwnershipMismatch

            val affected = countDao.deleteLine(lineId.value)
            if (affected != 1) throw ValidationError.StockCountLineNotFound
        }
    }

    override suspend fun completeArea(countId: StockCountId, countAreaId: StockCountAreaId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val count = countDao.getCountById(countId.value) ?: throw ValidationError.StockCountNotFound
            if (count.restaurantId != activeRestaurant.id) throw ValidationError.StockCountOwnershipMismatch
            if (count.status != StockCountStatus.DRAFT.name) throw ValidationError.StockCountNotDraft

            val area = countDao.getAreaById(countAreaId.value) ?: throw ValidationError.StockCountAreaNotFound
            if (area.stockCountId != count.id) throw ValidationError.StockCountAreaOwnershipMismatch
            
            if (area.status == CountAreaStatus.COMPLETED.name) return@withTransaction

            val affected = countDao.updateCountArea(area.copy(
                status = CountAreaStatus.COMPLETED.name,
                completedAt = timeProvider.now().toEpochMilli()
            ))
            if (affected != 1) throw ValidationError.StockCountAreaNotFound
        }
    }

    override suspend fun reopenArea(countId: StockCountId, countAreaId: StockCountAreaId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val count = countDao.getCountById(countId.value) ?: throw ValidationError.StockCountNotFound
            if (count.restaurantId != activeRestaurant.id) throw ValidationError.StockCountOwnershipMismatch
            if (count.status != StockCountStatus.DRAFT.name) throw ValidationError.StockCountNotDraft

            val area = countDao.getAreaById(countAreaId.value) ?: throw ValidationError.StockCountAreaNotFound
            if (area.stockCountId != count.id) throw ValidationError.StockCountAreaOwnershipMismatch
            
            if (area.status != CountAreaStatus.COMPLETED.name) return@withTransaction

            val affected = countDao.updateCountArea(area.copy(
                status = CountAreaStatus.IN_PROGRESS.name,
                completedAt = null
            ))
            if (affected != 1) throw ValidationError.StockCountAreaNotFound
        }
    }

    override suspend fun deleteDraft(countId: StockCountId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val count = countDao.getCountById(countId.value) ?: throw ValidationError.StockCountNotFound
            if (count.restaurantId != activeRestaurant.id) throw ValidationError.StockCountOwnershipMismatch
            if (count.status != StockCountStatus.DRAFT.name) throw ValidationError.StockCountNotDraft

            val movements = movementDao.getBySourceDocument(SourceDocumentType.STOCK_COUNT.name, countId.value)
            if (movements.isNotEmpty()) throw ValidationError.MalformedStockCountMovementHistory

            val affected = countDao.deleteDraftCount(countId.value)
            if (affected != 1) throw ValidationError.StockCountNotFound
            
            val areas = countDao.getAreasForCount(countId.value)
            areas.forEach { countDao.deleteLinesForArea(it.id) }
            countDao.deleteAreasForCount(countId.value)
        }
    }

    override suspend fun completeCount(countId: StockCountId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val count = countDao.getCountById(countId.value) ?: throw ValidationError.StockCountNotFound
            if (count.restaurantId != activeRestaurant.id) throw ValidationError.StockCountOwnershipMismatch
            
            val areas = countDao.getAreasForCount(countId.value)
            val lines = countDao.getAllLinesForCount(countId.value)
            val movements = movementDao.getBySourceDocument(SourceDocumentType.STOCK_COUNT.name, countId.value)
            val graph = StockCountValidationGraph(count, areas, lines, movements)

            if (count.status == StockCountStatus.COMPLETED.name) {
                historyValidator.validateCompletedHistory(graph)
                return@withTransaction
            }

            if (count.status != StockCountStatus.DRAFT.name) throw ValidationError.StockCountAlreadyVoided

            if (areas.isEmpty()) throw ValidationError.StockCountHasNoAreas
            if (areas.any { it.status != CountAreaStatus.COMPLETED.name }) throw ValidationError.StockCountAreasIncomplete
            if (lines.isEmpty()) throw ValidationError.StockCountHasNoLines

            historyValidator.validateDraftHistory(count, movements)

            val now = timeProvider.now()
            val effectiveAt = Instant.ofEpochMilli(count.effectiveAt)
            
            val seenIngredientsInArea = mutableSetOf<Pair<String, String>>()

            val movementsToInsert = lines.map { lineEntity ->
                val areaEntity = areas.find { it.id == lineEntity.stockCountAreaId } ?: throw ValidationError.StockCountAreaOwnershipMismatch
                
                if (!seenIngredientsInArea.add(lineEntity.ingredientId to areaEntity.id)) {
                    throw ValidationError.DuplicateIngredientInCountArea
                }

                val ingredient = ingredientDao.getById(lineEntity.ingredientId) ?: throw ValidationError.InvalidCountIngredient
                if (ingredient.restaurantId != activeRestaurant.id) throw ValidationError.IngredientOwnershipMismatch

                val option = unitOptionDao.getById(lineEntity.ingredientUnitOptionId) ?: throw ValidationError.InvalidCountUnitOption
                if (option.ingredientId != ingredient.id) throw ValidationError.InvalidCountUnitOption
                if (option.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor

                val qtyEntered = BigDecimal(lineEntity.quantityEntered)
                if (qtyEntered < BigDecimal.ZERO) throw ValidationError.InvalidCountQuantity
                
                val canonicalQtyBase = qtyEntered.multiply(option.factorToBase, MathContext.DECIMAL128)

                val snapshot = snapshotService.calculateAt(
                    restaurantId = RestaurantId(activeRestaurant.id),
                    ingredientId = IngredientId(lineEntity.ingredientId),
                    areaId = InventoryAreaId(areaEntity.areaId),
                    effectiveAt = effectiveAt
                )

                val expectedQtyBase = if (snapshot.hasEffectiveHistory) snapshot.areaQuantityBase else null
                val adjustmentQtyBase = if (expectedQtyBase == null) canonicalQtyBase else canonicalQtyBase.subtract(expectedQtyBase)

                val updatedLine = lineEntity.copy(
                    quantityBase = canonicalQtyBase.toPlainString(),
                    expectedQuantityBaseSnapshot = expectedQtyBase?.toPlainString(),
                    adjustmentQuantityBase = adjustmentQtyBase.toPlainString(),
                    updatedAt = now.toEpochMilli()
                )
                val affected = countDao.updateCountLine(updatedLine)
                if (affected != 1) throw ValidationError.StockCountLineNotFound

                val movementType = if (expectedQtyBase == null) InventoryMovementType.OPENING_BALANCE else InventoryMovementType.COUNT_ADJUSTMENT
                
                val totalValueSnapshot = if (snapshot.ingredientAverageCostBase != null) {
                    adjustmentQtyBase.multiply(snapshot.ingredientAverageCostBase, MathContext.DECIMAL128).toPlainString()
                } else null

                InventoryMovementEntity(
                    id = idGenerator.newId(),
                    restaurantId = activeRestaurant.id,
                    ingredientId = lineEntity.ingredientId,
                    areaId = areaEntity.areaId,
                    movementType = movementType.name,
                    quantityBaseSigned = adjustmentQtyBase.toPlainString(),
                    unitCostBaseSnapshot = snapshot.ingredientAverageCostBase?.toPlainString(),
                    totalValueSnapshot = totalValueSnapshot,
                    effectiveAt = count.effectiveAt,
                    sourceDocumentType = SourceDocumentType.STOCK_COUNT.name,
                    sourceDocumentId = count.id,
                    sourceLineId = lineEntity.id,
                    sourceOperationId = "stock-count-complete:${count.id}:${lineEntity.id}",
                    reversalOfMovementId = null,
                    createdAt = now.toEpochMilli()
                )
            }

            movementDao.insertAll(movementsToInsert)

            val affectedIngredients = lines.map { it.ingredientId }.distinct()
            affectedIngredients.forEach { ingredientId ->
                projectionRebuilder.rebuildForIngredient(IngredientId(ingredientId))
            }

            val affected = countDao.updateCount(count.copy(
                status = StockCountStatus.COMPLETED.name,
                completedAt = now.toEpochMilli(),
                updatedAt = now.toEpochMilli()
            ))
            if (affected != 1) throw ValidationError.StockCountNotFound
        }
    }

    override suspend fun voidCount(countId: StockCountId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val count = countDao.getCountById(countId.value) ?: throw ValidationError.StockCountNotFound
            if (count.restaurantId != activeRestaurant.id) throw ValidationError.StockCountOwnershipMismatch

            val areas = countDao.getAreasForCount(countId.value)
            val lines = countDao.getAllLinesForCount(countId.value)
            val allMovements = movementDao.getBySourceDocument(SourceDocumentType.STOCK_COUNT.name, countId.value)
            val graph = StockCountValidationGraph(count, areas, lines, allMovements)

            if (count.status == StockCountStatus.VOIDED.name) {
                historyValidator.validateVoidedHistory(graph)
                return@withTransaction
            }

            if (count.status != StockCountStatus.COMPLETED.name) throw ValidationError.StockCountNotDraft

            historyValidator.validateCompletedHistory(graph)

            val now = timeProvider.now()
            val originalMovements = allMovements.filter { it.movementType != InventoryMovementType.REVERSAL.name }
            
            val reversals = originalMovements.map { original ->
                val originalTotal = original.totalValueSnapshot?.let { BigDecimal(it) }
                val reversalTotal = originalTotal?.negate()?.toPlainString()

                InventoryMovementEntity(
                    id = idGenerator.newId(),
                    restaurantId = original.restaurantId,
                    ingredientId = original.ingredientId,
                    areaId = original.areaId,
                    movementType = InventoryMovementType.REVERSAL.name,
                    quantityBaseSigned = BigDecimal(original.quantityBaseSigned).negate().toPlainString(),
                    unitCostBaseSnapshot = original.unitCostBaseSnapshot,
                    totalValueSnapshot = reversalTotal,
                    effectiveAt = now.toEpochMilli(),
                    sourceDocumentType = SourceDocumentType.STOCK_COUNT.name,
                    sourceDocumentId = count.id,
                    sourceLineId = original.sourceLineId,
                    sourceOperationId = "reversal:${original.id}",
                    reversalOfMovementId = original.id,
                    createdAt = now.toEpochMilli()
                )
            }

            movementDao.insertAll(reversals)

            val affectedIngredients = originalMovements.map { it.ingredientId }.distinct()
            affectedIngredients.forEach { ingredientId ->
                projectionRebuilder.rebuildForIngredient(IngredientId(ingredientId))
            }

            val affected = countDao.updateCount(count.copy(
                status = StockCountStatus.VOIDED.name,
                voidedAt = now.toEpochMilli(),
                updatedAt = now.toEpochMilli()
            ))
            if (affected != 1) throw ValidationError.StockCountNotFound
        }
    }
}
