package com.miara.cuentame.feature.settings.ui

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.feature.settings.viewmodel.BackupUiState
import org.junit.Rule
import org.junit.Test

class SettingsBackupUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsScreen_showsBackupSection() {
        renderSettings(BackupUiState.Idle)

        composeTestRule.onNodeWithText("Data and backup", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Create backup", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsCreatingState() {
        renderSettings(BackupUiState.Creating)

        composeTestRule.onNodeWithText("Creating backup…").assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsValidatingState() {
        renderSettings(BackupUiState.Validating)

        composeTestRule.onNodeWithText("Validating backup…").assertIsDisplayed()
    }

    private fun renderSettings(backupUiState: BackupUiState) {
        composeTestRule.setContent {
            SettingsScreen(
                themeMode = ThemeMode.SYSTEM,
                dynamicColorEnabled = true,
                appLocaleTag = "en-US",
                isSaving = false,
                backupUiState = backupUiState,
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onThemeChanged = {},
                onDynamicColorToggled = {},
                onLocaleChanged = {},
                onCreateBackup = {},
                onNavigateToAreas = {},
                onNavigateToCategories = {},
                onNavigateToRestaurant = {},
                onNavigateToSuppliers = {}
            )
        }
    }
}
