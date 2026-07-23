package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.domain.repository.StockCountRepository
import com.miara.cuentame.core.domain.repository.UpdateStockCountDraftCommand
import javax.inject.Inject

class UpdateStockCountDraftUseCase @Inject constructor(
    private val repository: StockCountRepository
) {
    suspend operator fun invoke(command: UpdateStockCountDraftCommand) {
        repository.updateDraft(command)
    }
}
