package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.domain.repository.StockCountFilter
import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.repository.StockCountSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStockCountsUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    operator fun invoke(filter: StockCountFilter): Flow<List<StockCountSummary>> {
        return repository.observeCounts(filter)
    }
}
