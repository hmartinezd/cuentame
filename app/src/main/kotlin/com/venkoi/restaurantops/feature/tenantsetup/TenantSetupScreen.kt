package com.venkoi.restaurantops.feature.tenantsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.restaurantops.R

@Composable
fun TenantSetupRoute(viewModel: TenantSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TenantSetupScreen(
        state = state,
        onOrganizationChange = viewModel::updateOrganizationName,
        onRestaurantChange = viewModel::updateRestaurantName,
        onCurrencyChange = viewModel::updateCurrencyCode,
        onTimezoneChange = viewModel::updateTimezone,
        onLocaleChange = viewModel::updateLocaleTag,
        onSubmit = viewModel::submit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantSetupScreen(
    state: TenantSetupUiState,
    onOrganizationChange: (String) -> Unit,
    onRestaurantChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onTimezoneChange: (String) -> Unit,
    onLocaleChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(24.dp).testTag("tenant_setup_content"), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.tenant_setup_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.tenant_setup_description), style = MaterialTheme.typography.bodyMedium)
                SetupTextField(state.organizationName, onOrganizationChange, R.string.tenant_setup_organization, state, TenantSetupField.ORGANIZATION, "tenant_setup_organization")
                SetupTextField(state.restaurantName, onRestaurantChange, R.string.tenant_setup_restaurant, state, TenantSetupField.RESTAURANT, "tenant_setup_restaurant")

                var currencyExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(currencyExpanded, { if (!state.submitting) currencyExpanded = !currencyExpanded }) {
                    OutlinedTextField(
                        value = state.currencyCode, onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.tenant_setup_currency)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(currencyExpanded) },
                        isError = TenantSetupField.CURRENCY in state.validationErrors,
                        enabled = !state.submitting,
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("tenant_setup_currency")
                    )
                    ExposedDropdownMenu(currencyExpanded, { currencyExpanded = false }) {
                        listOf("USD", "EUR", "GBP", "CAD", "MXN").forEach { currency ->
                            DropdownMenuItem({ Text(currency) }, {
                                onCurrencyChange(currency)
                                currencyExpanded = false
                            })
                        }
                    }
                }

                SetupTextField(state.timezone, onTimezoneChange, R.string.tenant_setup_timezone, state, TenantSetupField.TIMEZONE, "tenant_setup_timezone")

                var localeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(localeExpanded, { if (!state.submitting) localeExpanded = !localeExpanded }) {
                    OutlinedTextField(
                        value = state.localeTag, onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.tenant_setup_locale)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(localeExpanded) },
                        isError = TenantSetupField.LOCALE in state.validationErrors,
                        enabled = !state.submitting,
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("tenant_setup_locale")
                    )
                    ExposedDropdownMenu(localeExpanded, { localeExpanded = false }) {
                        listOf("en-US", "es-US").forEach { locale ->
                            DropdownMenuItem({ Text(locale) }, {
                                onLocaleChange(locale)
                                localeExpanded = false
                            })
                        }
                    }
                }

                if (state.operationFailed) {
                    Text(stringResource(R.string.tenant_setup_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("tenant_setup_error"))
                }
                Button(onClick = onSubmit, enabled = !state.submitting, modifier = Modifier.fillMaxWidth().testTag("tenant_setup_submit")) {
                    if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.tenant_setup_action))
                }
            }
        }
    }
}

@Composable
private fun SetupTextField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    state: TenantSetupUiState,
    field: TenantSetupField,
    tag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        enabled = !state.submitting,
        isError = field in state.validationErrors,
        supportingText = if (field in state.validationErrors) {{ Text(stringResource(R.string.tenant_setup_required)) }} else null,
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}
