package com.venkoi.restaurantops.core.domain.model.auth

data class AuthUser(
    val id: String,
    val email: String?
)

sealed interface AuthSessionState {
    data object Initializing : AuthSessionState
    data object SignedOut : AuthSessionState
    data object RefreshFailed : AuthSessionState
    data class SignedIn(val user: AuthUser) : AuthSessionState
}
