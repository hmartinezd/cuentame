package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.domain.repository.StockCountFilter
import com.venkoi.restaurantops.core.domain.repository.StockCountRepository
import com.venkoi.restaurantops.core.domain.repository.StockCountSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStockCountsUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    operator fun invoke(filter: StockCountFilter): Flow<List<StockCountSummary>> {
        return repository.observeCounts(filter)
    }
}
