package com.venkoi.restaurantops.core.cloud.auth

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.domain.model.auth.AuthSessionState
import com.venkoi.restaurantops.core.domain.model.auth.AuthUser
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class SessionStatusMapperTest {

    @Test
    fun `initializing maps to initializing`() {
        assertThat(mapSessionStatus(SessionStatus.Initializing))
            .isEqualTo(AuthSessionState.Initializing)
    }

    @Test
    fun `not authenticated maps to signed out`() {
        assertThat(mapSessionStatus(SessionStatus.NotAuthenticated()))
            .isEqualTo(AuthSessionState.SignedOut)
    }

    @Test
    fun `authenticated maps user id and email`() {
        val user = mockk<UserInfo> {
            every { id } returns "user-123"
            every { email } returns "owner@example.com"
        }
        val session = mockk<UserSession> {
            every { this@mockk.user } returns user
        }

        assertThat(mapSessionStatus(SessionStatus.Authenticated(session)))
            .isEqualTo(
                AuthSessionState.SignedIn(
                    AuthUser(id = "user-123", email = "owner@example.com")
                )
            )
    }

    @Test
    fun `refresh failure maps to refresh failed and not signed out`() {
        val status = SessionStatus.RefreshFailure(
            RefreshFailureCause.NetworkError(IllegalStateException("offline"))
        )

        val mapped = mapSessionStatus(status)

        assertThat(mapped).isEqualTo(AuthSessionState.RefreshFailed)
        assertThat(mapped).isNotEqualTo(AuthSessionState.SignedOut)
    }
}
