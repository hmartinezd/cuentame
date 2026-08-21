package com.venkoi.cuentame.core.domain.usecase.locale

sealed interface LocaleReconciliationResult {
    data object InSync : LocaleReconciliationResult
    data class Reconciled(val restoredLocaleTag: String) : LocaleReconciliationResult
    data object RestaurantNotFound : LocaleReconciliationResult
    data class Failure(val cause: Throwable) : LocaleReconciliationResult
}

interface AppLocaleReconciler {
    suspend fun reconcile(): LocaleReconciliationResult
}
