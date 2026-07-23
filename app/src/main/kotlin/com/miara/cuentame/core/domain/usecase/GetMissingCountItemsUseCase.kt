package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.database.dao.StockCountDao
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.model.ingredient.Ingredient
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class GetMissingCountItemsUseCase @Inject constructor(
    private val ingredientRepository: IngredientRepository,
    private val snapshotService: InventorySnapshotService,
    private val countDao: StockCountDao
) {
    suspend operator fun invoke(
        restaurantId: RestaurantId,
        countId: StockCountId,
        areaId: InventoryAreaId,
        effectiveAt: Instant
    ): List<Ingredient> {
        // 1. Get all active ingredients
        val allActive = ingredientRepository.getIngredients(
            restaurantId = restaurantId,
            includeArchived = false
        )

        // 2. Get ingredients with nonzero expected balance in area
        val balances = snapshotService.calculateAreaBalancesAt(restaurantId, areaId, effectiveAt)
        val nonzeroBalanceIds = balances.filter { it.value.compareTo(BigDecimal.ZERO) != 0 }.keys

        // 3. Candidates = active ingredients with default area OR nonzero balance
        val candidates = allActive.filter { ingredient ->
            ingredient.defaultAreaId == areaId || nonzeroBalanceIds.contains(ingredient.id)
        }

        // 4. Get already counted ingredients for this count and area
        val areas = countDao.getAreasForCount(countId.value)
        val countAreaId = areas.find { it.areaId == areaId.value }?.id ?: return emptyList()
        val countedLines = countDao.getLinesForArea(countAreaId)
        val countedIngredientIds = countedLines.map { IngredientId(it.ingredientId) }.toSet()

        // 5. Missing = Candidates - Counted
        return candidates.filter { !countedIngredientIds.contains(it.id) }
    }
}
