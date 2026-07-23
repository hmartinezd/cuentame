package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.domain.repository.StockCountAreaDetails
import com.miara.cuentame.core.domain.repository.StockCountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStockCountAreaUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    operator fun invoke(id: StockCountAreaId): Flow<StockCountAreaDetails?> {
        return repository.observeCountArea(id)
    }
}
