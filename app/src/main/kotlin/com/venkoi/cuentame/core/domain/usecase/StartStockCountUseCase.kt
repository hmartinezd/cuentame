package com.venkoi.cuentame.core.domain.usecase

import com.venkoi.cuentame.core.common.ids.StockCountId
import com.venkoi.cuentame.core.domain.repository.StartStockCountCommand
import com.venkoi.cuentame.core.domain.repository.StockCountRepository
import javax.inject.Inject

class StartStockCountUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    suspend operator fun invoke(command: StartStockCountCommand): StockCountId {
        return repository.start(command)
    }
}
