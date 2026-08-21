package com.venkoi.cuentame.core.domain.usecase

import com.venkoi.cuentame.core.domain.repository.SaveStockCountLineCommand
import com.venkoi.cuentame.core.domain.repository.StockCountRepository
import com.venkoi.cuentame.core.model.count.StockCountLine
import javax.inject.Inject

class SaveStockCountLineUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    suspend operator fun invoke(command: SaveStockCountLineCommand): StockCountLine {
        return repository.saveLine(command)
    }
}
