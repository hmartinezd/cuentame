package com.venkoi.restaurantops.core.cloud.auth

import com.venkoi.restaurantops.core.domain.model.auth.AuthSessionState
import com.venkoi.restaurantops.core.domain.model.auth.AuthUser
import com.venkoi.restaurantops.core.domain.repository.AuthOperationException
import com.venkoi.restaurantops.core.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient
) : AuthRepository {

    override val sessionState: Flow<AuthSessionState> =
        supabase.auth.sessionStatus.map(::mapSessionStatus)

    override suspend fun signUp(email: String, password: String): Result<Unit> =
        authOperation {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
        }

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        authOperation {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }

    override suspend fun signOut(): Result<Unit> = authOperation {
        supabase.auth.signOut()
    }
}

internal fun mapSessionStatus(status: SessionStatus): AuthSessionState = when (status) {
    SessionStatus.Initializing -> AuthSessionState.Initializing
    is SessionStatus.NotAuthenticated,
    is SessionStatus.RefreshFailure -> AuthSessionState.SignedOut
    is SessionStatus.Authenticated -> status.session.user?.let { user ->
        AuthSessionState.SignedIn(
            AuthUser(
                id = user.id,
                email = user.email
            )
        )
    } ?: AuthSessionState.SignedOut
}

private suspend inline fun authOperation(
    crossinline operation: suspend () -> Unit
): Result<Unit> = try {
    operation()
    Result.success(Unit)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    Result.failure(AuthOperationException())
}
