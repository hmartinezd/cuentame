package com.miara.cuentame.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.domain.validation.toUserMessageRes
import com.miara.cuentame.feature.settings.viewmodel.RestaurantSettingsViewModel

@Composable
fun RestaurantProfileRoute(
    onBack: () -> Unit,
    viewModel: RestaurantSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    if (uiState.isLoading) {
        CircularProgressIndicator()
        return
    }

    val restaurant = uiState.restaurant ?: return

    RestaurantProfileScreen(
        name = restaurant.name,
        currency = restaurant.currencyCode,
        locale = restaurant.localeTag,
        isSaving = uiState.isSaving,
        snackbarHostState = snackbarHostState,
        onUpdate = { name, currency, locale ->
            viewModel.onUpdateRestaurant(name, currency, locale, onSuccess = onBack)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantProfileScreen(
    name: String,
    currency: String,
    locale: String,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onUpdate: (String, String, String) -> Unit
) {
    var editName by remember { mutableStateOf(name) }
    var editCurrency by remember { mutableStateOf(currency) }
    var editLocale by remember { mutableStateOf(locale) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = stringResource(R.string.settings_restaurant), style = MaterialTheme.typography.headlineSmall)
            
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text(stringResource(R.string.onboarding_field_name)) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = !isSaving
            )

            // Currency
            var currencyExpanded by remember { mutableStateOf(false) }
            val currencies = listOf("USD", "EUR", "GBP", "CAD", "MXN")
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { if (!isSaving) currencyExpanded = !currencyExpanded },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                OutlinedTextField(
                    value = editCurrency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.onboarding_field_currency)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = !isSaving
                )
                ExposedDropdownMenu(
                    expanded = currencyExpanded,
                    onDismissRequest = { currencyExpanded = false }
                ) {
                    currencies.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code) },
                            onClick = {
                                editCurrency = code
                                currencyExpanded = false
                            }
                        )
                    }
                }
            }

            // Language
            var langExpanded by remember { mutableStateOf(false) }
            val locales = mapOf("en-US" to stringResource(R.string.lang_en), "es-US" to stringResource(R.string.lang_es))
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { if (!isSaving) langExpanded = !langExpanded },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                OutlinedTextField(
                    value = locales[editLocale] ?: editLocale,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.onboarding_field_language)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = !isSaving
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false }
                ) {
                    locales.forEach { (tag, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                editLocale = tag
                                langExpanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = { onUpdate(editName, editCurrency, editLocale) },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                enabled = !isSaving && editName.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
