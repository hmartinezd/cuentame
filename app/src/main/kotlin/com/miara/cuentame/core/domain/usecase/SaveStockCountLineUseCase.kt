package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.StockCountLineId
import com.miara.cuentame.core.domain.repository.SaveStockCountLineCommand
import com.miara.cuentame.core.domain.repository.StockCountRepository
import javax.inject.Inject

class SaveStockCountLineUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    suspend operator fun invoke(command: SaveStockCountLineCommand): StockCountLineId {
        return repository.saveLine(command)
    }
}
