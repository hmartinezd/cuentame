package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.common.ids.StockCountAreaId
import com.venkoi.restaurantops.core.domain.repository.StockCountAreaDetails
import com.venkoi.restaurantops.core.domain.repository.StockCountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStockCountAreaUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    operator fun invoke(id: StockCountAreaId): Flow<StockCountAreaDetails?> {
        return repository.observeCountArea(id)
    }
}
