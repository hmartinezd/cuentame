package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand
import com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.UpdatePackageUnitOptionCommand
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class ObserveIngredientUnitOptionsUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    operator fun invoke(ingredientId: IngredientId, includeArchived: Boolean = false): Flow<List<IngredientUnitOption>> =
        repository.observeUnitOptions(ingredientId, includeArchived)
}

class AddStandardUnitOptionUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(command: AddStandardUnitOptionCommand) = 
        repository.addStandardUnitOption(command)
}

class AddPackageUnitOptionUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(command: AddPackageUnitOptionCommand) =
        repository.addPackageUnitOption(command)
}

class UpdatePackageUnitOptionUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(command: UpdatePackageUnitOptionCommand) =
        repository.updatePackageUnitOption(command)
}

class SetDefaultCountUnitUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(ingredientId: IngredientId, optionId: IngredientUnitOptionId) =
        repository.setDefaultCountOption(ingredientId, optionId)
}

class SetDefaultPurchaseUnitUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(ingredientId: IngredientId, optionId: IngredientUnitOptionId) =
        repository.setDefaultPurchaseOption(ingredientId, optionId)
}

class ArchiveIngredientUnitOptionUseCase @Inject constructor(
    private val repository: IngredientRepository
) {
    suspend operator fun invoke(id: IngredientUnitOptionId, at: Instant) =
        repository.archiveUnitOption(id, at)
}
