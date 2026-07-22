package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.model.inventory.InventoryArea
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class ObserveInventoryAreasUseCase @Inject constructor(
    private val repository: InventoryAreaRepository
) {
    operator fun invoke(activeOnly: Boolean = true): Flow<List<InventoryArea>> =
        if (activeOnly) repository.observeActiveAreas() else repository.observeAllAreas()
}

class CreateInventoryAreaUseCase @Inject constructor(
    private val repository: InventoryAreaRepository
) {
    suspend operator fun invoke(area: InventoryArea) = repository.save(area)
}

class UpdateInventoryAreaUseCase @Inject constructor(
    private val repository: InventoryAreaRepository
) {
    suspend operator fun invoke(area: InventoryArea) = repository.save(area)
}

class ArchiveInventoryAreaUseCase @Inject constructor(
    private val repository: InventoryAreaRepository
) {
    suspend operator fun invoke(id: InventoryAreaId, at: Instant) = repository.archive(id, at)
}
