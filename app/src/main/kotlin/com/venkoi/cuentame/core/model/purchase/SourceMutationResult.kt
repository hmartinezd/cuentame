package com.venkoi.cuentame.core.model.purchase

sealed interface SourceMutationResult {
    data object Success : SourceMutationResult
    data object SourceLocked : SourceMutationResult
    data object NotFound : SourceMutationResult
}
