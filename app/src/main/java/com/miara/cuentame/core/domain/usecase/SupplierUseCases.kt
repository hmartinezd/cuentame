package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.domain.repository.CreateSupplierCommand
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UpdateSupplierCommand
import com.miara.cuentame.core.model.supplier.Supplier
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class ObserveSuppliersUseCase @Inject constructor(
    private val repository: SupplierRepository
) {
    operator fun invoke(restaurantId: RestaurantId, includeArchived: Boolean = false): Flow<List<Supplier>> =
        repository.observeSuppliers(restaurantId, includeArchived)
}

class GetSupplierUseCase @Inject constructor(
    private val repository: SupplierRepository
) {
    suspend operator fun invoke(id: SupplierId): Supplier? = repository.getSupplier(id)
    fun observe(id: SupplierId): Flow<Supplier?> = repository.observeSupplier(id)
}

class CreateSupplierUseCase @Inject constructor(
    private val repository: SupplierRepository
) {
    suspend operator fun invoke(command: CreateSupplierCommand): SupplierId =
        repository.createSupplier(command)
}

class UpdateSupplierUseCase @Inject constructor(
    private val repository: SupplierRepository
) {
    suspend operator fun invoke(command: UpdateSupplierCommand) =
        repository.updateSupplier(command)
}

class ArchiveSupplierUseCase @Inject constructor(
    private val repository: SupplierRepository
) {
    suspend operator fun invoke(id: SupplierId, at: Instant) =
        repository.archiveSupplier(id, at)
}
