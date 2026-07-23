package com.miara.cuentame.core.domain.repository

import kotlinx.coroutines.flow.Flow

sealed interface LocalSetupResult {
    data object Success : LocalSetupResult
    data object AlreadyCompleted : LocalSetupResult
    data class Failure(val error: Throwable) : LocalSetupResult
}

data class SetupAreaInput(
    val name: String,
    val sortOrder: Int
)

data class SetupCategoryInput(
    val name: String,
    val sortOrder: Int
)

data class CompleteLocalSetupCommand(
    val restaurantName: String,
    val currencyCode: String,
    val localeTag: String,
    val areas: List<SetupAreaInput>,
    val categories: List<SetupCategoryInput>
)

interface LocalSetupRepository {
    suspend fun isSetupComplete(): Boolean
    fun observeIsSetupComplete(): Flow<Boolean>
    suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult
}

