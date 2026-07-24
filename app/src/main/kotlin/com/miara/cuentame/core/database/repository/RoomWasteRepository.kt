package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.dao.WasteDao
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.entity.WasteEventEntity
import com.miara.cuentame.core.domain.repository.CreateWasteDraftCommand
import com.miara.cuentame.core.domain.repository.UpdateWasteDraftCommand
import com.miara.cuentame.core.domain.repository.WasteDetails
import com.miara.cuentame.core.domain.repository.WasteFilter
import com.miara.cuentame.core.domain.repository.WasteRepository
import com.miara.cuentame.core.domain.repository.WasteSummary
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.waste.WasteEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class RoomWasteRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val wasteDao: WasteDao,
    private val movementDao: InventoryMovementDao,
    private val ingredientDao: IngredientDao,
    private val areaDao: InventoryAreaDao,
    private val unitOptionDao: IngredientUnitOptionDao,
    private val restaurantDao: RestaurantDao,
    private val snapshotService: InventorySnapshotService,
    private val historyValidator: WasteMovementHistoryValidator,
    private val projectionRebuilder: RoomInventoryProjectionRebuilder,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) : WasteRepository {

    private suspend fun requireActiveRestaurant(): RestaurantId {
        val restaurant = restaurantDao.getRestaurant() ?: throw ValidationError.RecordNotFound
        return RestaurantId(restaurant.id)
    }

    private fun parseHistoryDecimal(value: String): BigDecimal {
        return try {
            BigDecimal(value)
        } catch (e: Exception) {
            throw ValidationError.MalformedWasteMovementHistory
        }
    }

    override fun observeWasteEvents(filter: WasteFilter): Flow<List<WasteSummary>> {
        return wasteDao.observeFiltered(
            restaurantId = filter.restaurantId.value,
            status = filter.status?.name
        ).flatMapLatest { entities ->
            if (entities.isEmpty()) return@flatMapLatest flowOf(emptyList<WasteSummary>())
            
            val summaryFlows = entities.map { entity ->
                val ingredientFlow = ingredientDao.observeIngredient(entity.ingredientId)
                val areaFlow = areaDao.observeById(entity.areaId)
                val unitFlow = unitOptionDao.observeById(entity.ingredientUnitOptionId)
                
                combine(ingredientFlow, areaFlow, unitFlow) { ingredient, area, unit ->
                    val snapshot = if (entity.status == DocumentStatus.POSTED.name || entity.status == DocumentStatus.VOIDED.name) {
                        movementDao.getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, entity.id)
                            .find { it.movementType == InventoryMovementType.WASTE.name }
                    } else null

                    val averageCost = snapshot?.unitCostBaseSnapshot?.let { parseHistoryDecimal(it) }
                    val quantityBase = parseHistoryDecimal(entity.quantityBase)
                    val estimatedValue = averageCost?.multiply(quantityBase, MathContext.DECIMAL128)

                    WasteSummary(
                        event = entity.toDomainWaste(),
                        ingredientName = ingredient?.name ?: "Unknown",
                        areaName = area?.name,
                        unitLabel = unit?.shortLabel ?: "units",
                        estimatedValue = estimatedValue
                    )
                }
            }
            combine(summaryFlows) { it.toList() }
        }.map { summaries ->
            if (filter.query.isNullOrBlank()) summaries
            else summaries.filter { 
                it.ingredientName.contains(filter.query, ignoreCase = true) ||
                it.areaName?.contains(filter.query, ignoreCase = true) == true
            }
        }
    }

    override fun observeWasteEvent(id: WasteEventId): Flow<WasteDetails?> {
        return wasteDao.observeById(id.value).flatMapLatest { entity ->
            if (entity == null) return@flatMapLatest flowOf(null)

            val ingredientFlow = ingredientDao.observeIngredient(entity.ingredientId)
            val areaFlow = areaDao.observeById(entity.areaId)
            val unitFlow = unitOptionDao.observeById(entity.ingredientUnitOptionId)

            combine(ingredientFlow, areaFlow, unitFlow) { ingredient, area, unit ->
                val movements = movementDao.getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, entity.id)
                val wasteMovement = movements.find { it.movementType == InventoryMovementType.WASTE.name }
                
                val averageCost = wasteMovement?.unitCostBaseSnapshot?.let { parseHistoryDecimal(it) }
                val quantityBase = parseHistoryDecimal(entity.quantityBase)
                
                val baseUnit = if (ingredient != null) {
                    unitOptionDao.getActiveOptions(ingredient.id).find { it.isBase }
                } else null

                WasteDetails(
                    event = entity.toDomainWaste(),
                    ingredientName = ingredient?.name ?: "Unknown",
                    areaName = area?.name,
                    unitLabel = unit?.shortLabel ?: "units",
                    baseUnitSymbol = baseUnit?.shortLabel,
                    averageCostBase = averageCost,
                    estimatedValue = averageCost?.multiply(quantityBase, MathContext.DECIMAL128)
                )
            }
        }
    }

    override suspend fun getById(id: WasteEventId): WasteEvent? {
        return wasteDao.getById(id.value)?.toDomainWaste()
    }

    override suspend fun createDraft(command: CreateWasteDraftCommand): WasteEventId {
        return database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            if (activeRestaurant != command.restaurantId) throw ValidationError.WasteEventOwnershipMismatch

            val ingredient = ingredientDao.getById(command.ingredientId.value) ?: throw ValidationError.WasteIngredientNotFound
            if (ingredient.restaurantId != activeRestaurant.value) throw ValidationError.WasteIngredientOwnershipMismatch
            if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.WasteIngredientInactive

            val area = areaDao.getById(command.areaId.value) ?: throw ValidationError.WasteAreaNotFound
            if (area.restaurantId != activeRestaurant.value) throw ValidationError.WasteAreaOwnershipMismatch
            if (!area.isActive || area.deletedAt != null) throw ValidationError.WasteAreaInactive

            val option = unitOptionDao.getById(command.ingredientUnitOptionId.value) ?: throw ValidationError.WasteUnitOptionNotFound
            if (option.ingredientId != command.ingredientId.value) throw ValidationError.WasteUnitOptionOwnershipMismatch
            if (!option.isActive || option.deletedAt != null) throw ValidationError.WasteUnitOptionInactive
            if (option.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor

            if (command.quantityEntered <= BigDecimal.ZERO) throw ValidationError.InvalidWasteQuantity
            
            if (command.effectiveAt > timeProvider.now()) throw ValidationError.InvalidWasteEffectiveTime

            val qtyBase = command.quantityEntered.multiply(option.factorToBase, MathContext.DECIMAL128)
            val now = timeProvider.now().toEpochMilli()
            val id = WasteEventId(idGenerator.newId())

            wasteDao.insert(
                WasteEventEntity(
                    id = id.value,
                    restaurantId = activeRestaurant.value,
                    ingredientId = command.ingredientId.value,
                    areaId = command.areaId.value,
                    ingredientUnitOptionId = command.ingredientUnitOptionId.value,
                    quantityEntered = command.quantityEntered.toPlainString(),
                    quantityBase = qtyBase.toPlainString(),
                    reason = command.reason.name,
                    effectiveAt = command.effectiveAt.toEpochMilli(),
                    notes = command.notes?.trim()?.ifBlank { null },
                    attachmentPath = command.attachmentUri?.trim()?.ifBlank { null },
                    status = DocumentStatus.DRAFT.name,
                    createdAt = now,
                    updatedAt = now,
                    postedAt = null,
                    voidedAt = null
                )
            )
            id
        }
    }

    override suspend fun updateDraft(command: UpdateWasteDraftCommand) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val existing = wasteDao.getById(command.wasteEventId.value) ?: throw ValidationError.WasteEventNotFound
            if (existing.restaurantId != activeRestaurant.value) throw ValidationError.WasteEventOwnershipMismatch
            if (existing.status != DocumentStatus.DRAFT.name) throw ValidationError.WasteEventNotDraft

            val ingredient = ingredientDao.getById(command.ingredientId.value) ?: throw ValidationError.WasteIngredientNotFound
            if (ingredient.restaurantId != activeRestaurant.value) throw ValidationError.WasteIngredientOwnershipMismatch
            
            // For updates, only reject inactive if they changed the reference
            if (existing.ingredientId != command.ingredientId.value && (!ingredient.isActive || ingredient.deletedAt != null)) {
                throw ValidationError.WasteIngredientInactive
            }

            val area = areaDao.getById(command.areaId.value) ?: throw ValidationError.WasteAreaNotFound
            if (area.restaurantId != activeRestaurant.value) throw ValidationError.WasteAreaOwnershipMismatch
            if (existing.areaId != command.areaId.value && (!area.isActive || area.deletedAt != null)) {
                throw ValidationError.WasteAreaInactive
            }

            val option = unitOptionDao.getById(command.ingredientUnitOptionId.value) ?: throw ValidationError.WasteUnitOptionNotFound
            if (option.ingredientId != command.ingredientId.value) throw ValidationError.WasteUnitOptionOwnershipMismatch
            if (existing.ingredientUnitOptionId != command.ingredientUnitOptionId.value && (!option.isActive || option.deletedAt != null)) {
                throw ValidationError.WasteUnitOptionInactive
            }
            if (option.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor

            if (command.quantityEntered <= BigDecimal.ZERO) throw ValidationError.InvalidWasteQuantity
            if (command.effectiveAt > timeProvider.now()) throw ValidationError.InvalidWasteEffectiveTime

            val qtyBase = command.quantityEntered.multiply(option.factorToBase, MathContext.DECIMAL128)
            val now = timeProvider.now().toEpochMilli()

            val updated = existing.copy(
                ingredientId = command.ingredientId.value,
                areaId = command.areaId.value,
                ingredientUnitOptionId = command.ingredientUnitOptionId.value,
                quantityEntered = command.quantityEntered.toPlainString(),
                quantityBase = qtyBase.toPlainString(),
                reason = command.reason.name,
                effectiveAt = command.effectiveAt.toEpochMilli(),
                notes = command.notes?.trim()?.ifBlank { null },
                attachmentPath = command.attachmentUri?.trim()?.ifBlank { null },
                updatedAt = now
            )
            val affected = wasteDao.update(updated)
            if (affected != 1) throw ValidationError.WasteEventNotFound
        }
    }

    override suspend fun deleteDraft(id: WasteEventId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val existing = wasteDao.getById(id.value) ?: throw ValidationError.WasteEventNotFound
            if (existing.restaurantId != activeRestaurant.value) throw ValidationError.WasteEventOwnershipMismatch
            if (existing.status != DocumentStatus.DRAFT.name) throw ValidationError.WasteEventNotDraft

            val movements = movementDao.getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, id.value)
            historyValidator.validateDraftHistory(movements)

            val affected = wasteDao.deleteDraft(id.value)
            if (affected != 1) throw ValidationError.WasteEventNotFound
        }
    }

    override suspend fun post(id: WasteEventId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val existing = wasteDao.getById(id.value) ?: throw ValidationError.WasteEventNotFound
            if (existing.restaurantId != activeRestaurant.value) throw ValidationError.WasteEventOwnershipMismatch

            val movements = movementDao.getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, id.value)
            
            if (existing.status == DocumentStatus.POSTED.name) {
                historyValidator.validatePostedHistory(existing, movements)
                return@withTransaction
            }
            
            if (existing.status != DocumentStatus.DRAFT.name) throw ValidationError.WasteEventAlreadyVoided
            historyValidator.validateDraftHistory(movements)

            val option = unitOptionDao.getById(existing.ingredientUnitOptionId) ?: throw ValidationError.WasteUnitOptionNotFound
            val qtyEntered = parseHistoryDecimal(existing.quantityEntered)
            val canonicalQtyBase = qtyEntered.multiply(option.factorToBase, MathContext.DECIMAL128)
            
            if (commandEffectiveAtIsFuture(existing.effectiveAt)) throw ValidationError.InvalidWasteEffectiveTime

            val snapshot = snapshotService.calculateAt(
                restaurantId = activeRestaurant,
                ingredientId = IngredientId(existing.ingredientId),
                areaId = InventoryAreaId(existing.areaId),
                effectiveAt = Instant.ofEpochMilli(existing.effectiveAt)
            )

            val now = timeProvider.now().toEpochMilli()
            val postedEvent = existing.copy(
                quantityBase = canonicalQtyBase.toPlainString(),
                status = DocumentStatus.POSTED.name,
                updatedAt = now,
                postedAt = now
            )

            val totalValue = snapshot.ingredientAverageCostBase?.multiply(canonicalQtyBase, MathContext.DECIMAL128)

            val movement = InventoryMovementEntity(
                id = idGenerator.newId(),
                restaurantId = activeRestaurant.value,
                ingredientId = existing.ingredientId,
                areaId = existing.areaId,
                movementType = InventoryMovementType.WASTE.name,
                quantityBaseSigned = canonicalQtyBase.negate().toPlainString(),
                unitCostBaseSnapshot = snapshot.ingredientAverageCostBase?.toPlainString(),
                totalValueSnapshot = totalValue?.negate()?.toPlainString(),
                effectiveAt = existing.effectiveAt,
                sourceDocumentType = SourceDocumentType.WASTE_EVENT.name,
                sourceDocumentId = existing.id,
                sourceLineId = existing.id,
                sourceOperationId = "waste-post:${existing.id}",
                reversalOfMovementId = null,
                createdAt = now
            )

            movementDao.insert(movement)
            wasteDao.update(postedEvent)
            projectionRebuilder.rebuildForIngredient(IngredientId(existing.ingredientId))
        }
    }

    override suspend fun void(id: WasteEventId) {
        database.withTransaction {
            val activeRestaurant = requireActiveRestaurant()
            val existing = wasteDao.getById(id.value) ?: throw ValidationError.WasteEventNotFound
            if (existing.restaurantId != activeRestaurant.value) throw ValidationError.WasteEventOwnershipMismatch

            val movements = movementDao.getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, id.value)
            
            if (existing.status == DocumentStatus.VOIDED.name) {
                historyValidator.validateVoidedHistory(existing, movements)
                return@withTransaction
            }

            if (existing.status != DocumentStatus.POSTED.name) throw ValidationError.WasteEventNotDraft
            historyValidator.validatePostedHistory(existing, movements)

            val original = movements.find { it.movementType == InventoryMovementType.WASTE.name } 
                ?: throw ValidationError.MalformedWasteMovementHistory

            val now = timeProvider.now().toEpochMilli()
            val totalValue = original.totalValueSnapshot?.let { parseHistoryDecimal(it) }

            val reversal = InventoryMovementEntity(
                id = idGenerator.newId(),
                restaurantId = original.restaurantId,
                ingredientId = original.ingredientId,
                areaId = original.areaId,
                movementType = InventoryMovementType.REVERSAL.name,
                quantityBaseSigned = parseHistoryDecimal(original.quantityBaseSigned).negate().toPlainString(),
                unitCostBaseSnapshot = original.unitCostBaseSnapshot,
                totalValueSnapshot = totalValue?.negate()?.toPlainString(),
                effectiveAt = now,
                sourceDocumentType = SourceDocumentType.WASTE_EVENT.name,
                sourceDocumentId = existing.id,
                sourceLineId = original.sourceLineId,
                sourceOperationId = "reversal:${original.id}",
                reversalOfMovementId = original.id,
                createdAt = now
            )

            val voidedEvent = existing.copy(
                status = DocumentStatus.VOIDED.name,
                updatedAt = now,
                voidedAt = now
            )

            movementDao.insert(reversal)
            wasteDao.update(voidedEvent)
            projectionRebuilder.rebuildForIngredient(IngredientId(existing.ingredientId))
        }
    }

    private fun commandEffectiveAtIsFuture(effectiveAt: Long): Boolean {
        return effectiveAt > timeProvider.now().toEpochMilli()
    }

    private fun WasteEventEntity.toDomainWaste() = WasteEvent(
        id = WasteEventId(id),
        restaurantId = RestaurantId(restaurantId),
        ingredientId = IngredientId(ingredientId),
        areaId = InventoryAreaId(areaId),
        ingredientUnitOptionId = IngredientUnitOptionId(ingredientUnitOptionId),
        quantityEntered = parseHistoryDecimal(quantityEntered),
        quantityBase = parseHistoryDecimal(quantityBase),
        reason = com.miara.cuentame.core.model.inventory.WasteReason.valueOf(reason),
        effectiveAt = Instant.ofEpochMilli(effectiveAt),
        notes = notes,
        attachmentPath = attachmentPath,
        status = com.miara.cuentame.core.model.inventory.DocumentStatus.valueOf(status),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        postedAt = postedAt?.let { Instant.ofEpochMilli(it) },
        voidedAt = voidedAt?.let { Instant.ofEpochMilli(it) }
    )
}
