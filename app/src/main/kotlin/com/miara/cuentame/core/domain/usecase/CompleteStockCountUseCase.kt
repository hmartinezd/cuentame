package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.domain.repository.StockCountRepository
import javax.inject.Inject

class CompleteStockCountUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    suspend operator fun invoke(id: StockCountId) {
        repository.completeCount(id)
    }
}
