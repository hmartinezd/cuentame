package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSystemUnitsUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    operator fun invoke(): Flow<List<UnitOfMeasure>> = repository.observeAll()
}

class ObserveCompatibleSystemUnitsUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    operator fun invoke(dimension: UnitDimension): Flow<List<UnitOfMeasure>> =
        repository.observeByDimension(dimension)
}
