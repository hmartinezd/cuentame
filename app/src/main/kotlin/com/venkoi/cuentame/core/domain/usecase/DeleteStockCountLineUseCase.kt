package com.venkoi.cuentame.core.domain.usecase

import com.venkoi.cuentame.core.common.ids.StockCountAreaId
import com.venkoi.cuentame.core.common.ids.StockCountId
import com.venkoi.cuentame.core.common.ids.StockCountLineId
import com.venkoi.cuentame.core.domain.repository.StockCountRepository
import javax.inject.Inject

class DeleteStockCountLineUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    suspend operator fun invoke(countId: StockCountId, countAreaId: StockCountAreaId, lineId: StockCountLineId) {
        repository.deleteLine(countId, countAreaId, lineId)
    }
}
