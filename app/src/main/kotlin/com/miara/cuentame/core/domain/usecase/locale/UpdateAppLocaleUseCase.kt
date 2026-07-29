package com.miara.cuentame.core.domain.usecase.locale

import com.miara.cuentame.core.model.locale.SupportedAppLocale

sealed interface LocaleUpdateResult {
    data object Success : LocaleUpdateResult
    sealed interface Error : LocaleUpdateResult {
        data object RestaurantNotFound : Error
        data class RoomUpdateFailed(val cause: Throwable) : Error
        data class PreferenceUpdateFailed(val cause: Throwable, val compensationSucceeded: Boolean) : Error
    }
}

interface UpdateAppLocaleUseCase {
    suspend operator fun invoke(locale: SupportedAppLocale): LocaleUpdateResult
}
