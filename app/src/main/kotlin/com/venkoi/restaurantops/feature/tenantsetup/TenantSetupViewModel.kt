package com.venkoi.restaurantops.feature.tenantsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.core.domain.repository.TenantRepository
import com.venkoi.restaurantops.core.domain.startup.SaaSStartupRefresh
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TenantSetupField { ORGANIZATION, RESTAURANT, CURRENCY, TIMEZONE, LOCALE }

data class TenantSetupUiState(
    val organizationName: String = "",
    val restaurantName: String = "",
    val currencyCode: String = "USD",
    val timezone: String,
    val localeTag: String,
    val submitting: Boolean = false,
    val validationErrors: Set<TenantSetupField> = emptySet(),
    val operationFailed: Boolean = false
)

@HiltViewModel
class TenantSetupViewModel @Inject constructor(
    private val tenantRepository: TenantRepository,
    private val startupRefresh: SaaSStartupRefresh,
    defaultsProvider: TenantSetupDefaultsProvider
) : ViewModel() {
    private val _state = MutableStateFlow(
        TenantSetupUiState(
            timezone = defaultsProvider.timezone(),
            localeTag = defaultsProvider.localeTag()
        )
    )
    val state = _state.asStateFlow()

    fun updateOrganizationName(value: String) = updateField(TenantSetupField.ORGANIZATION) { copy(organizationName = value) }
    fun updateRestaurantName(value: String) = updateField(TenantSetupField.RESTAURANT) { copy(restaurantName = value) }
    fun updateCurrencyCode(value: String) = updateField(TenantSetupField.CURRENCY) { copy(currencyCode = value) }
    fun updateTimezone(value: String) = updateField(TenantSetupField.TIMEZONE) { copy(timezone = value) }
    fun updateLocaleTag(value: String) = updateField(TenantSetupField.LOCALE) { copy(localeTag = value) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val normalizedCurrency = current.currencyCode.trim().uppercase()
        val errors = buildSet {
            if (current.organizationName.isBlank()) add(TenantSetupField.ORGANIZATION)
            if (current.restaurantName.isBlank()) add(TenantSetupField.RESTAURANT)
            if (!normalizedCurrency.matches(Regex("^[A-Z]{3}$"))) add(TenantSetupField.CURRENCY)
            if (current.timezone.isBlank()) add(TenantSetupField.TIMEZONE)
            if (current.localeTag.isBlank()) add(TenantSetupField.LOCALE)
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(currencyCode = normalizedCurrency, validationErrors = errors, operationFailed = false) }
            return
        }

        _state.update { it.copy(currencyCode = normalizedCurrency, submitting = true, validationErrors = emptySet(), operationFailed = false) }
        viewModelScope.launch {
            try {
                val result = tenantRepository.createOrganizationWithRestaurant(
                    organizationName = current.organizationName.trim(),
                    restaurantName = current.restaurantName.trim(),
                    currencyCode = normalizedCurrency,
                    timezone = current.timezone.trim(),
                    localeTag = current.localeTag.trim()
                )
                if (result.isSuccess) {
                    startupRefresh.requestRefresh()
                } else {
                    _state.update { it.copy(submitting = false, operationFailed = true) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(submitting = false, operationFailed = true) }
            }
        }
    }

    private fun updateField(field: TenantSetupField, transform: TenantSetupUiState.() -> TenantSetupUiState) {
        if (_state.value.submitting) return
        _state.update { it.transform().copy(validationErrors = it.validationErrors - field, operationFailed = false) }
    }
}
