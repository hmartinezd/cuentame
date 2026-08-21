package com.venkoi.restaurantops.core.model.purchase

sealed interface SourceMutationResult {
    data object Success : SourceMutationResult
    data object SourceLocked : SourceMutationResult
    data object NotFound : SourceMutationResult
}
