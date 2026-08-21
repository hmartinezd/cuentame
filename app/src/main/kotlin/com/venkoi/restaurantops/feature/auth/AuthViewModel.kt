package com.venkoi.restaurantops.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, SIGN_UP }

enum class AuthUiError { INVALID_EMAIL, PASSWORD_REQUIRED, PASSWORD_TOO_SHORT, OPERATION_FAILED }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val mode: AuthMode = AuthMode.SIGN_IN,
    val submitting: Boolean = false,
    val error: AuthUiError? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun updateEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun updatePassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun toggleMode() = _state.update {
        if (it.submitting) it else it.copy(
            mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
            error = null
        )
    }

    fun signIn() = submit(AuthMode.SIGN_IN)
    fun signUp() = submit(AuthMode.SIGN_UP)

    fun signOut() {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val result = authRepository.signOut()
            _state.update { it.copy(submitting = false, error = result.exceptionOrNull()?.let { AuthUiError.OPERATION_FAILED }) }
        }
    }

    private fun submit(mode: AuthMode) {
        val current = _state.value
        if (current.submitting) return
        val error = validate(current.email, current.password, mode)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val result = try {
                if (mode == AuthMode.SIGN_IN) {
                    authRepository.signIn(current.email.trim(), current.password)
                } else {
                    authRepository.signUp(current.email.trim(), current.password)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
            _state.update { it.copy(
                submitting = false,
                error = if (result.isFailure) AuthUiError.OPERATION_FAILED else null
            ) }
        }
    }
}

internal fun validate(email: String, password: String, mode: AuthMode): AuthUiError? = when {
    !EMAIL_PATTERN.matches(email.trim()) -> AuthUiError.INVALID_EMAIL
    password.isBlank() -> AuthUiError.PASSWORD_REQUIRED
    mode == AuthMode.SIGN_UP && password.length < MIN_SIGN_UP_PASSWORD_LENGTH ->
        AuthUiError.PASSWORD_TOO_SHORT
    else -> null
}

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private const val MIN_SIGN_UP_PASSWORD_LENGTH = 6
