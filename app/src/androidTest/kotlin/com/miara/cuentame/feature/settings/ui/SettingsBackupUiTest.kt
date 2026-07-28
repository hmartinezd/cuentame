package com.miara.cuentame.feature.settings.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupResult
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.feature.settings.viewmodel.BackupUiState
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class SettingsBackupUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsScreen_showsBackupSection_inIdleState_andButtonIsEnabled() {
        var backupClicked = false
        renderSettings(BackupUiState.Idle, onCreateBackup = { backupClicked = true })

        composeTestRule.onNodeWithTag("create_backup_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("backup_idle", useUnmergedTree = true).assertExists()

        composeTestRule.onNodeWithTag("create_backup_button").performClick()
        assertThat(backupClicked).isTrue()
    }

    @Test
    fun settingsScreen_showsWaitingForDestinationState_andDisablesButton() {
        renderSettings(BackupUiState.WaitingForDestination)

        composeTestRule.onNodeWithTag("create_backup_button").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("backup_waiting_for_destination", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("backup_waiting_indicator", useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsScreen_showsCreatingState_andDisablesButton() {
        renderSettings(BackupUiState.Creating)

        composeTestRule.onNodeWithTag("create_backup_button").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("backup_creating", useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsScreen_showsValidatingState_andDisablesButton() {
        renderSettings(BackupUiState.Validating)

        composeTestRule.onNodeWithTag("create_backup_button").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("backup_validating", useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsScreen_showsSuccessState_andEnablesButton() {
        val manifest = BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1L,
            databaseSchemaVersion = 2,
            restaurantId = "rest-1",
            restaurantName = "Test Rest",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = emptyMap(),
            attachments = emptyList(),
            includedSections = emptyList()
        )
        renderSettings(BackupUiState.Success(manifest))

        composeTestRule.onNodeWithTag("create_backup_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("backup_success", useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsScreen_showsErrorState_andEnablesButton() {
        renderSettings(BackupUiState.Error(BackupResult.Error.PermissionDenied))

        composeTestRule.onNodeWithTag("create_backup_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("backup_error", useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsScreen_showsCancelledState_andEnablesButton() {
        renderSettings(BackupUiState.Cancelled)

        composeTestRule.onNodeWithTag("create_backup_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("backup_cancelled", useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsScreen_rendersVisibleText_inSpanishLocale() {
        composeTestRule.setContent {
            val context = LocalContext.current
            val spanishConfig = Configuration(context.resources.configuration).apply {
                setLocale(Locale("es", "US"))
            }
            val spanishContext = context.createConfigurationContext(spanishConfig)

            androidx.compose.runtime.CompositionLocalProvider(
                LocalContext provides spanishContext
            ) {
                Box(modifier = Modifier.size(800.dp, 2000.dp)) {
                    SettingsScreen(
                        themeMode = ThemeMode.SYSTEM,
                        dynamicColorEnabled = true,
                        appLocaleTag = "es-US",
                        isSaving = false,
                        backupUiState = BackupUiState.Idle,
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

        composeTestRule.onNodeWithText("Datos y copia de seguridad").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Crear copia de seguridad").performScrollTo().assertIsDisplayed()
    }

    private fun renderSettings(
        backupUiState: BackupUiState,
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        appLocaleTag: String = "en-US",
        onCreateBackup: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            Box(modifier = Modifier.size(800.dp, 2000.dp)) {
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
                    onCreateBackup = onCreateBackup,
                    onNavigateToAreas = {},
                    onNavigateToCategories = {},
                    onNavigateToRestaurant = {},
                    onNavigateToSuppliers = {}
                )
            }
        }
    }
}
