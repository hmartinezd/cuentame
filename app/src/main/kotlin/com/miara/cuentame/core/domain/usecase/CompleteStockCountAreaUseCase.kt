package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.domain.repository.StockCountRepository
import javax.inject.Inject

class CompleteStockCountAreaUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    suspend operator fun invoke(countId: StockCountId, countAreaId: StockCountAreaId) {
        repository.completeArea(countId, countAreaId)
    }
}
