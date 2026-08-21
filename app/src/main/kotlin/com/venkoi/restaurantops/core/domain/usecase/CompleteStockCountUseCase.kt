package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.common.ids.StockCountId
import com.venkoi.restaurantops.core.domain.repository.StockCountRepository
import javax.inject.Inject

class CompleteStockCountUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    suspend operator fun invoke(id: StockCountId) {
        repository.completeCount(id)
    }
}
