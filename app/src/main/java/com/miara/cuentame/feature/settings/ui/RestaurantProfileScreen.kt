package com.miara.cuentame.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.feature.settings.viewmodel.RestaurantSettingsViewModel

@Composable
fun RestaurantProfileRoute(
    onBack: () -> Unit,
    viewModel: RestaurantSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    if (uiState.isLoading) {
        CircularProgressIndicator()
        return
    }

    val restaurant = uiState.restaurant ?: return

    var name by remember { mutableStateOf(restaurant.name) }
    var currency by remember { mutableStateOf(restaurant.currencyCode) }
    var locale by remember { mutableStateOf(restaurant.localeTag) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = stringResource(R.string.settings_restaurant), style = MaterialTheme.typography.headlineSmall)
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.onboarding_field_name)) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )

        // Add proper selectors for currency and locale if time permits, or use text for now
        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it },
            label = { Text(stringResource(R.string.onboarding_field_currency)) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )

        Button(
            onClick = { 
                viewModel.onUpdateRestaurant(name, currency, locale)
                onBack()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}
