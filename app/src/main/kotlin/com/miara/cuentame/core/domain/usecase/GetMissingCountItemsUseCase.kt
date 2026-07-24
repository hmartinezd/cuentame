package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.domain.model.count.ArchivedCountCandidate
import com.miara.cuentame.core.domain.model.count.CountCandidateResult
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class GetMissingCountItemsUseCase @Inject constructor(
    private val ingredientRepository: IngredientRepository,
    private val stockCountRepository: StockCountRepository,
    private val snapshotService: InventorySnapshotService
) {
    suspend operator fun invoke(
        restaurantId: RestaurantId,
        countId: StockCountId,
        areaId: InventoryAreaId,
        effectiveAt: Instant
    ): CountCandidateResult {
        // 1. Get all ingredients (including archived for warnings)
        val allIngredients = ingredientRepository.getIngredients(
            restaurantId = restaurantId,
            includeArchived = true
        )

        val balances = snapshotService.calculateAreaBalancesAt(restaurantId, areaId, effectiveAt)
        val countedIngredientIds = stockCountRepository.getCountedIngredientIds(countId, areaId)

        val activeCandidates = mutableListOf<com.miara.cuentame.core.model.ingredient.Ingredient>()
        val missingActiveCandidates = mutableListOf<com.miara.cuentame.core.model.ingredient.Ingredient>()
        val archivedBalanceWarnings = mutableListOf<ArchivedCountCandidate>()

        allIngredients.forEach { ingredient ->
            val balance = balances.getOrDefault(ingredient.id, BigDecimal.ZERO)
            val hasBalance = balance.compareTo(BigDecimal.ZERO) != 0
            val isCounted = countedIngredientIds.contains(ingredient.id)

            if (ingredient.isActive && ingredient.deletedAt == null) {
                val isSuggested = ingredient.defaultAreaId == areaId || hasBalance
                if (isSuggested) {
                    activeCandidates.add(ingredient)
                    if (!isCounted) {
                        missingActiveCandidates.add(ingredient)
                    }
                }
            } else if (hasBalance) {
                // Archived with nonzero balance
                archivedBalanceWarnings.add(
                    ArchivedCountCandidate(
                        ingredientId = ingredient.id.value,
                        name = ingredient.name,
                        expectedBalanceBase = balance
                    )
                )
            }
        }

        return CountCandidateResult(
            activeCandidates = activeCandidates,
            missingActiveCandidates = missingActiveCandidates,
            archivedBalanceWarnings = archivedBalanceWarnings
        )
    }
}
