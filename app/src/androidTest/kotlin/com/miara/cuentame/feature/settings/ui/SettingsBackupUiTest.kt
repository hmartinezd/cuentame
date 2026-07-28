package com.miara.cuentame.feature.settings.ui

import androidx.compose.material3.SnackbarHostState
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
    fun settingsScreen_showsBackupSection_inIdleState() {
        renderSettings(BackupUiState.Idle)

        composeTestRule.onNodeWithText("Data and backup", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Create backup", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_idle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_backup_button").assertHasClickAction()
    }

    @Test
    fun settingsScreen_showsWaitingForDestinationState() {
        renderSettings(BackupUiState.WaitingForDestination)

        composeTestRule.onNodeWithTag("backup_waiting_for_destination").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_waiting_indicator").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsCreatingState_andDisablesButton() {
        renderSettings(BackupUiState.Creating)

        composeTestRule.onNodeWithTag("backup_creating").assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_backup_button").assertIsNotEnabled()
    }

    @Test
    fun settingsScreen_showsValidatingState_andDisablesButton() {
        renderSettings(BackupUiState.Validating)

        composeTestRule.onNodeWithTag("backup_validating").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_backup_button").assertIsNotEnabled()
    }

    @Test
    fun settingsScreen_rendersInDarkTheme() {
        renderSettings(BackupUiState.Idle, themeMode = ThemeMode.DARK)

        composeTestRule.onNodeWithText("Data and backup", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_rendersInSpanishLocale() {
        renderSettings(BackupUiState.Idle, appLocaleTag = "es-US")

        composeTestRule.onNodeWithText("Data and backup", ignoreCase = true).assertIsDisplayed()
    }

    private fun renderSettings(
        backupUiState: BackupUiState,
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        appLocaleTag: String = "en-US"
    ) {
        composeTestRule.setContent {
            SettingsScreen(
                themeMode = themeMode,
                dynamicColorEnabled = true,
                appLocaleTag = appLocaleTag,
                isSaving = false,
                backupUiState = backupUiState,
                snackbarHostState = SnackbarHostState(),
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
