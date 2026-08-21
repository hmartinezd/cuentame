package com.venkoi.restaurantops.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.restaurantops.R

@Composable
fun AuthRoute(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(state, viewModel::updateEmail, viewModel::updatePassword, viewModel::toggleMode) {
        if (state.mode == AuthMode.SIGN_IN) viewModel.signIn() else viewModel.signUp()
    }
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(24.dp).testTag("auth_content"), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(if (state.mode == AuthMode.SIGN_IN) R.string.auth_sign_in_title else R.string.auth_sign_up_title),
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = state.email, onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.auth_email)) }, singleLine = true,
                    enabled = !state.submitting, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("auth_email")
                )
                OutlinedTextField(
                    value = state.password, onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.auth_password)) }, singleLine = true,
                    enabled = !state.submitting,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.testTag("auth_password_visibility")) {
                        Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password))
                    } },
                    modifier = Modifier.fillMaxWidth().testTag("auth_password")
                )
                state.error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("auth_error")) }
                Button(onClick = onSubmit, enabled = !state.submitting, modifier = Modifier.fillMaxWidth().testTag("auth_submit")) {
                    if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(if (state.mode == AuthMode.SIGN_IN) R.string.auth_sign_in_action else R.string.auth_sign_up_action))
                }
                TextButton(onClick = onToggleMode, enabled = !state.submitting, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("auth_toggle")) {
                    Text(stringResource(if (state.mode == AuthMode.SIGN_IN) R.string.auth_switch_to_sign_up else R.string.auth_switch_to_sign_in))
                }
            }
        }
    }
}

private fun AuthUiError.messageRes() = when (this) {
    AuthUiError.INVALID_EMAIL -> R.string.auth_error_email
    AuthUiError.PASSWORD_REQUIRED -> R.string.auth_error_password_required
    AuthUiError.PASSWORD_TOO_SHORT -> R.string.auth_error_password_short
    AuthUiError.OPERATION_FAILED -> R.string.auth_error_generic
}
