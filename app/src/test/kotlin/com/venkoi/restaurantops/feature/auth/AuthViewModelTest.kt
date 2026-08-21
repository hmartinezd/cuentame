package com.venkoi.restaurantops.feature.auth

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.domain.model.auth.AuthSessionState
import com.venkoi.restaurantops.core.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before fun setUp() { Dispatchers.setMain(dispatcher); repository = FakeAuthRepository(); viewModel = AuthViewModel(repository) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `successful sign in delegates without navigation state`() = runTest(dispatcher) {
        viewModel.updateEmail("owner@example.com"); viewModel.updatePassword("password"); viewModel.signIn(); runCurrent()
        assertThat(repository.signInCalls).isEqualTo(1)
        assertThat(viewModel.state.value.submitting).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test fun `successful sign up delegates without navigation state`() = runTest(dispatcher) {
        viewModel.toggleMode(); viewModel.updateEmail("owner@example.com"); viewModel.updatePassword("123456"); viewModel.signUp(); runCurrent()
        assertThat(repository.signUpCalls).isEqualTo(1)
        assertThat(viewModel.state.value.submitting).isFalse()
        assertThat(AuthUiState::class.java.declaredFields.map { it.name }).doesNotContain("navigation")
    }

    @Test fun `auth failure clears submitting and presents generic safe error`() = runTest(dispatcher) {
        repository.result = Result.failure(IllegalStateException("technical secret"))
        viewModel.updateEmail("owner@example.com"); viewModel.updatePassword("password"); viewModel.signIn(); runCurrent()
        assertThat(viewModel.state.value.submitting).isFalse()
        assertThat(viewModel.state.value.error).isEqualTo(AuthUiError.OPERATION_FAILED)
    }

    @Test fun `sign up validates project minimum password length`() {
        assertThat(validate("owner@example.com", "12345", AuthMode.SIGN_UP)).isEqualTo(AuthUiError.PASSWORD_TOO_SHORT)
        assertThat(validate("owner@example.com", "123456", AuthMode.SIGN_UP)).isNull()
    }

    private class FakeAuthRepository : AuthRepository {
        override val sessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.SignedOut)
        var result: Result<Unit> = Result.success(Unit); var signInCalls = 0; var signUpCalls = 0
        override suspend fun signUp(email: String, password: String): Result<Unit> { signUpCalls++; return result }
        override suspend fun signIn(email: String, password: String): Result<Unit> { signInCalls++; return result }
        override suspend fun signOut(): Result<Unit> = result
    }
}
