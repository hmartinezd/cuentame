package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.domain.repository.InventoryAreaRepository
import javax.inject.Inject

class ReorderInventoryAreasUseCase @Inject constructor(
    private val repository: InventoryAreaRepository
) {
    suspend operator fun invoke(ids: List<InventoryAreaId>) = repository.reorder(ids)
}
