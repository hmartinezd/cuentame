package com.miara.cuentame.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.domain.validation.toUserMessageRes
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
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    SettingsScreen(
        themeMode = preferences.themeMode,
        dynamicColorEnabled = preferences.dynamicColorEnabled,
        appLocaleTag = preferences.appLocaleTag,
        isSaving = isSaving,
        snackbarHostState = snackbarHostState,
        onThemeChanged = viewModel::setThemeMode,
        onDynamicColorToggled = viewModel::setDynamicColorEnabled,
        onLocaleChanged = viewModel::setAppLocaleTag,
        onNavigateToAreas = onNavigateToAreas,
        onNavigateToCategories = onNavigateToCategories,
        onNavigateToRestaurant = onNavigateToRestaurant
    )
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColorEnabled: Boolean,
    appLocaleTag: String,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onThemeChanged: (ThemeMode) -> Unit,
    onDynamicColorToggled: (Boolean) -> Unit,
    onLocaleChanged: (String) -> Unit,
    onNavigateToAreas: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToRestaurant: () -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                icon = Icons.AutoMirrored.Filled.List,
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
                supportingContent = {
                    Text(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        }
                    )
                },
                modifier = Modifier.clickable { showThemeDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                trailingContent = {
                    Switch(checked = dynamicColorEnabled, onCheckedChange = onDynamicColorToggled)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsHeader(stringResource(R.string.settings_language))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (appLocaleTag) {
                                "es-US" -> stringResource(R.string.lang_es)
                                else -> stringResource(R.string.lang_en)
                            }
                        )
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                },
                modifier = Modifier.clickable(enabled = !isSaving) { showLanguageDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsHeader(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.about_desc)) }
            )
        }
    }

    if (showThemeDialog) {
        ThemeDialog(
            currentMode = themeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = {
                onThemeChanged(it)
                showThemeDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentTag = appLocaleTag,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showLanguageDialog = false },
            onSelect = {
                onLocaleChanged(it)
                // We don't close the dialog here. 
                // We'll close it in a LaunchedEffect or when preferences change successfully.
            }
        )
    }
    
    // Close language dialog on success
    LaunchedEffect(appLocaleTag) {
        showLanguageDialog = false
    }
}

@Composable
fun ThemeDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeOption(ThemeMode.SYSTEM, stringResource(R.string.theme_system), currentMode == ThemeMode.SYSTEM, onSelect)
                ThemeOption(ThemeMode.LIGHT, stringResource(R.string.theme_light), currentMode == ThemeMode.LIGHT, onSelect)
                ThemeOption(ThemeMode.DARK, stringResource(R.string.theme_dark), currentMode == ThemeMode.DARK, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
fun ThemeOption(mode: ThemeMode, label: String, selected: Boolean, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = { onSelect(mode) },
                role = Role.RadioButton
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
fun LanguageDialog(
    currentTag: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                LanguageOption("en-US", stringResource(R.string.lang_en), currentTag == "en-US", isSaving, onSelect)
                LanguageOption("es-US", stringResource(R.string.lang_es), currentTag == "es-US", isSaving, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
fun LanguageOption(tag: String, label: String, selected: Boolean, isSaving: Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = { if (!isSaving) onSelect(tag) },
                role = Role.RadioButton,
                enabled = !isSaving
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = !isSaving)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
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
