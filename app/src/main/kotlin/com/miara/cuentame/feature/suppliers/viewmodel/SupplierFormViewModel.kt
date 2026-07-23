package com.miara.cuentame.feature.suppliers.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.CreateSupplierCommand
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.UpdateSupplierCommand
import com.miara.cuentame.core.domain.usecase.ArchiveSupplierUseCase
import com.miara.cuentame.core.domain.usecase.CreateSupplierUseCase
import com.miara.cuentame.core.domain.usecase.GetSupplierUseCase
import com.miara.cuentame.core.domain.usecase.UpdateSupplierUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupplierFormUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val supplierId: SupplierId? = null,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val error: Throwable? = null
)

sealed interface SupplierFormEvent {
    data class Success(val supplierId: SupplierId) : SupplierFormEvent
}

@HiltViewModel
class SupplierFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSupplierUseCase: GetSupplierUseCase,
    private val createSupplierUseCase: CreateSupplierUseCase,
    private val updateSupplierUseCase: UpdateSupplierUseCase,
    private val archiveSupplierUseCase: ArchiveSupplierUseCase,
    private val restaurantRepository: RestaurantRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val supplierIdString: String? = savedStateHandle["supplierId"]
    private val supplierId = supplierIdString?.let { SupplierId(it) }

    private val _uiState = MutableStateFlow(SupplierFormUiState(supplierId = supplierId))
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<SupplierFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadSupplier()
    }

    private fun loadSupplier() {
        if (supplierId == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            val supplier = getSupplierUseCase(supplierId)
            if (supplier != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = supplier.name,
                        phone = supplier.phone ?: "",
                        email = supplier.email ?: "",
                        notes = supplier.notes ?: "",
                        isActive = supplier.isActive
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = Exception("Supplier not found")) }
            }
        }
    }

    fun onNameChanged(name: String) = _uiState.update { it.copy(name = name) }
    fun onPhoneChanged(phone: String) = _uiState.update { it.copy(phone = phone) }
    fun onEmailChanged(email: String) = _uiState.update { it.copy(email = email) }
    fun onNotesChanged(notes: String) = _uiState.update { it.copy(notes = notes) }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || state.name.isBlank()) return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                if (supplierId == null) {
                    val restaurant = restaurantRepository.getRestaurant() ?: throw Exception("No restaurant")
                    val newId = createSupplierUseCase(
                        CreateSupplierCommand(
                            restaurantId = restaurant.id,
                            name = state.name,
                            phone = state.phone,
                            email = state.email,
                            notes = state.notes
                        )
                    )
                    _events.send(SupplierFormEvent.Success(newId))
                } else {
                    updateSupplierUseCase(
                        UpdateSupplierCommand(
                            supplierId = supplierId,
                            name = state.name,
                            phone = state.phone,
                            email = state.email,
                            notes = state.notes
                        )
                    )
                    _events.send(SupplierFormEvent.Success(supplierId))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e) }
            }
        }
    }

    fun onArchive() {
        if (supplierId == null) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                archiveSupplierUseCase(supplierId, timeProvider.now())
                _events.send(SupplierFormEvent.Success(supplierId))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
