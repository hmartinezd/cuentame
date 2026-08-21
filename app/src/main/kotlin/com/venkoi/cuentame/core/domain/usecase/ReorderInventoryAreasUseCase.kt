package com.venkoi.cuentame.core.domain.usecase

import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.domain.repository.InventoryAreaRepository
import javax.inject.Inject

class ReorderInventoryAreasUseCase @Inject constructor(
    private val repository: InventoryAreaRepository
) {
    suspend operator fun invoke(ids: List<InventoryAreaId>) = repository.reorder(ids)
}
