package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.domain.model.auth.AuthSessionState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val sessionState: Flow<AuthSessionState>

    suspend fun signUp(email: String, password: String): Result<Unit>

    suspend fun signIn(email: String, password: String): Result<Unit>

    suspend fun signOut(): Result<Unit>
}

class AuthOperationException : Exception("Authentication operation failed")
