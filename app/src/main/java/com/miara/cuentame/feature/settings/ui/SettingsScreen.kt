package com.miara.cuentame.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.feature.settings.viewmodel.SettingsViewModel

@Composable
fun SettingsRoute(
    onNavigateToAreas: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToRestaurant: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    SettingsScreen(
        themeMode = preferences.themeMode,
        dynamicColorEnabled = preferences.dynamicColorEnabled,
        onThemeChanged = viewModel::setThemeMode,
        onDynamicColorToggled = viewModel::setDynamicColorEnabled,
        onNavigateToAreas = onNavigateToAreas,
        onNavigateToCategories = onNavigateToCategories,
        onNavigateToRestaurant = onNavigateToRestaurant
    )
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColorEnabled: Boolean,
    onThemeChanged: (ThemeMode) -> Unit,
    onDynamicColorToggled: (Boolean) -> Unit,
    onNavigateToAreas: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToRestaurant: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsHeader(stringResource(R.string.settings_restaurant))
        SettingsItem(
            title = stringResource(R.string.settings_restaurant),
            icon = Icons.Default.Store,
            onClick = onNavigateToRestaurant
        )
        SettingsItem(
            title = stringResource(R.string.settings_areas),
            icon = Icons.Default.List,
            onClick = onNavigateToAreas
        )
        SettingsItem(
            title = stringResource(R.string.settings_categories),
            icon = Icons.Default.Palette,
            onClick = onNavigateToCategories
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsHeader(stringResource(R.string.settings_appearance))
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_theme)) },
            trailingContent = {
                // Simplified theme picker for foundation
                Text(themeMode.name, modifier = Modifier.clickable { 
                    val next = when(themeMode) {
                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.SYSTEM
                    }
                    onThemeChanged(next)
                })
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
            trailingContent = {
                Switch(checked = dynamicColorEnabled, onCheckedChange = onDynamicColorToggled)
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsHeader(stringResource(R.string.settings_about))
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_name)) },
            supportingContent = { Text(stringResource(R.string.about_desc)) }
        )
    }
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
