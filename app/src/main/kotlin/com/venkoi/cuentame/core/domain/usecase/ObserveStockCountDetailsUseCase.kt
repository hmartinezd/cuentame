package com.venkoi.cuentame.core.domain.usecase

import com.venkoi.cuentame.core.common.ids.StockCountId
import com.venkoi.cuentame.core.domain.repository.StockCountDetails
import com.venkoi.cuentame.core.domain.repository.StockCountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStockCountDetailsUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    operator fun invoke(id: StockCountId): Flow<StockCountDetails?> {
        return repository.observeCount(id)
    }
}
