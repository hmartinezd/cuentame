package com.miara.cuentame.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.presentation.validation.toUserMessageRes
import com.miara.cuentame.feature.settings.presentation.toUserMessageRes
import androidx.compose.material.icons.filled.Restore
import com.miara.cuentame.core.model.locale.SupportedAppLocale
import com.miara.cuentame.feature.settings.viewmodel.BackupRestoreUiState
import com.miara.cuentame.feature.settings.viewmodel.BackupRestoreViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.backup.api.BackupRestoreProgress
import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
import com.miara.cuentame.feature.settings.viewmodel.BackupOperationId
import com.miara.cuentame.feature.settings.viewmodel.BackupUiEvent
import com.miara.cuentame.feature.settings.viewmodel.BackupUiState
import com.miara.cuentame.feature.settings.viewmodel.BackupViewModel
import com.miara.cuentame.feature.settings.viewmodel.SettingsViewModel

@Composable
fun SettingsRoute(
    onNavigateToAreas: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToRestaurant: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
    restoreViewModel: BackupRestoreViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    
    val backupUiState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val restoreUiState by restoreViewModel.uiState.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var pendingPickerOperationIdValue by rememberSaveable { mutableStateOf<Long?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            val opIdValue = pendingPickerOperationIdValue
            if (opIdValue != null) {
                val opId = BackupOperationId(opIdValue)
                if (uri != null) {
                    backupViewModel.onFileSelected(opId, uri.toString())
                } else {
                    backupViewModel.onPickerCancelled(opId)
                }
                pendingPickerOperationIdValue = null
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            restoreViewModel.onFileSelected(uri?.toString())
        }
    )

    LaunchedEffect(restoreUiState) {
        if (restoreUiState == BackupRestoreUiState.SelectingFile) {
            restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
        }
    }

    LaunchedEffect(backupViewModel.events) {
        backupViewModel.events.collect { event ->
            when (event) {
                is BackupUiEvent.LaunchFilePicker -> {
                    if (backupViewModel.consumePickerLaunch(event.operationId)) {
                        pendingPickerOperationIdValue = event.operationId.value
                        backupLauncher.launch(event.suggestedName)
                    }
                }
            }
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    LaunchedEffect(backupUiState) {
        when (val state = backupUiState) {
            is BackupUiState.Success -> {
                snackbarHostState.showSnackbar(context.getString(R.string.backup_success_message))
                backupViewModel.resetStatus()
            }
            is BackupUiState.Error -> {
                val message = context.getString(state.error.toUserMessageRes())
                snackbarHostState.showSnackbar(context.getString(R.string.backup_error_message, message))
                backupViewModel.resetStatus()
            }
            is BackupUiState.Cancelled -> {
                snackbarHostState.showSnackbar(context.getString(R.string.backup_cancelled_message))
                backupViewModel.resetStatus()
            }
            else -> {}
        }
    }

    SettingsScreen(
        themeMode = preferences.themeMode,
        dynamicColorEnabled = preferences.dynamicColorEnabled,
        appLocaleTag = preferences.appLocaleTag,
        isSaving = isSaving,
        backupUiState = backupUiState,
        restoreUiState = restoreUiState,
        snackbarHostState = snackbarHostState,
        onThemeChanged = viewModel::setThemeMode,
        onDynamicColorToggled = viewModel::setDynamicColorEnabled,
        onLocaleChanged = { viewModel.setAppLocaleTag(it.languageTag) },
        onCreateBackup = backupViewModel::onCreateBackupRequested,
        onRestoreBackup = restoreViewModel::onSelectFileClicked,
        onChooseAnotherRestore = restoreViewModel::onChooseAnotherClicked,
        onDismissRestore = restoreViewModel::onDismissRequest,
        onStartRestore = restoreViewModel::onRestoreClicked,
        onCancelConfirmRestore = restoreViewModel::onRestoreConfirmationCancelled,
        onConfirmRestore = restoreViewModel::onRestoreConfirmed,
        onRetryRecovery = restoreViewModel::onRetryRecoveryClicked,
        onNavigateToAreas = onNavigateToAreas,
        onNavigateToCategories = onNavigateToCategories,
        onNavigateToRestaurant = onNavigateToRestaurant,
        onNavigateToSuppliers = onNavigateToSuppliers
    )
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColorEnabled: Boolean,
    appLocaleTag: String,
    isSaving: Boolean,
    backupUiState: BackupUiState,
    restoreUiState: BackupRestoreUiState,
    snackbarHostState: SnackbarHostState,
    onThemeChanged: (ThemeMode) -> Unit,
    onDynamicColorToggled: (Boolean) -> Unit,
    onLocaleChanged: (SupportedAppLocale) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onChooseAnotherRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    onStartRestore: () -> Unit,
    onCancelConfirmRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onRetryRecovery: () -> Unit,
    onNavigateToAreas: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToRestaurant: () -> Unit,
    onNavigateToSuppliers: () -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("settings_screen"),
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
            SettingsItem(
                title = stringResource(R.string.suppliers),
                icon = Icons.Default.Store,
                onClick = onNavigateToSuppliers
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsHeader(stringResource(R.string.settings_data_backup_section))
            
            val isBackupActive = backupUiState is BackupUiState.Creating || 
                                backupUiState is BackupUiState.Validating || 
                                backupUiState is BackupUiState.WaitingForDestination
                                
            val isRestoreApplying = restoreUiState is BackupRestoreUiState.Applying || 
                                   restoreUiState is BackupRestoreUiState.RecoveryInProgress

            ListItem(
                headlineContent = { Text(stringResource(R.string.create_backup_title)) },
                supportingContent = {
                    val desc = when (backupUiState) {
                        is BackupUiState.WaitingForDestination -> stringResource(R.string.backup_waiting_for_destination)
                        is BackupUiState.Creating -> stringResource(R.string.backup_creating)
                        is BackupUiState.Validating -> stringResource(R.string.backup_validating)
                        else -> stringResource(R.string.create_backup_desc)
                    }
                    Text(
                        text = desc,
                        modifier = Modifier
                            .testTag(when(backupUiState) {
                                is BackupUiState.WaitingForDestination -> "backup_waiting_for_destination"
                                is BackupUiState.Creating -> "backup_creating"
                                is BackupUiState.Validating -> "backup_validating"
                                is BackupUiState.Success -> "backup_success"
                                is BackupUiState.Error -> "backup_error"
                                is BackupUiState.Cancelled -> "backup_cancelled"
                                else -> "backup_idle"
                            })
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                            }
                    )
                },
                leadingContent = { Icon(Icons.Default.Backup, contentDescription = null) },
                trailingContent = {
                    if (isBackupActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).testTag("backup_active_indicator")
                        )
                    }
                },
                modifier = Modifier
                    .testTag("create_backup_button")
                    .clickable(enabled = !isBackupActive && !isRestoreApplying && restoreUiState != BackupRestoreUiState.RecoveryRequired) { onCreateBackup() }
            )

            val isRestoreInspecting = restoreUiState is BackupRestoreUiState.Inspecting
            ListItem(
                headlineContent = { Text(stringResource(R.string.restore_backup_title)) },
                supportingContent = {
                    val desc = when {
                        isRestoreInspecting -> stringResource(R.string.restore_inspecting)
                        isRestoreApplying -> stringResource(R.string.restore_applying)
                        restoreUiState is BackupRestoreUiState.RecoveryRequired -> stringResource(R.string.restore_recovery_required_title)
                        else -> stringResource(R.string.restore_backup_desc)
                    }
                    Text(
                        text = desc,
                        modifier = Modifier
                            .testTag(if (isRestoreInspecting || isRestoreApplying) "restore_backup_active" else "restore_backup_idle")
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                },
                leadingContent = { Icon(Icons.Default.Restore, contentDescription = null) },
                trailingContent = {
                    if (isRestoreInspecting || isRestoreApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).testTag("restore_backup_progress")
                        )
                    }
                },
                modifier = Modifier
                    .testTag("settings_restore_backup_button")
                    .clickable(enabled = !isRestoreInspecting && !isRestoreApplying && !isBackupActive) { onRestoreBackup() }
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
                                SupportedAppLocale.SPANISH_US.languageTag -> stringResource(R.string.lang_es)
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
            }
        )
    }
    
    LaunchedEffect(appLocaleTag) {
        showLanguageDialog = false
    }

    when (val state = restoreUiState) {
        is BackupRestoreUiState.PreviewReady -> {
            RestorePreviewDialog(
                preview = state.preview,
                eligibility = state.eligibility,
                onDismiss = onDismissRestore,
                onChooseAnother = onChooseAnotherRestore,
                onRestore = onStartRestore
            )
        }
        is BackupRestoreUiState.ConfirmingRestore -> {
            RestoreConfirmDialog(
                onDismiss = onCancelConfirmRestore,
                onConfirm = onConfirmRestore
            )
        }
        is BackupRestoreUiState.Applying -> {
            RestoreApplyingDialog(progress = state.progress)
        }
        is BackupRestoreUiState.Success -> {
            RestoreSuccessDialog(
                summary = state.summary,
                onDismiss = onDismissRestore
            )
        }
        is BackupRestoreUiState.Error -> {
            RestoreErrorDialog(
                failure = state.reason,
                onDismiss = onDismissRestore,
                onRetry = if (state.canChooseAnotherFile) onChooseAnotherRestore else null,
                recoveryRequired = false
            )
        }
        BackupRestoreUiState.RecoveryRequired -> {
            RestoreErrorDialog(
                failure = com.miara.cuentame.core.model.backup.BackupRestoreFailure.RecoveryRequired,
                onDismiss = {},
                onRetry = onRetryRecovery,
                recoveryRequired = true
            )
        }
        BackupRestoreUiState.RecoveryInProgress -> {
             RestoreApplyingDialog(progress = null)
        }
        else -> {}
    }
}

@Composable
fun RestorePreviewDialog(
    preview: com.miara.cuentame.core.model.backup.BackupRestorePreview,
    eligibility: BackupRestoreEligibility,
    onDismiss: () -> Unit,
    onChooseAnother: () -> Unit,
    onRestore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_preview_title)) },
        text = {
            Column(modifier = Modifier.testTag("restore_backup_preview")) {
                Text(
                    text = stringResource(R.string.restore_preview_restaurant, preview.restaurantName),
                    style = MaterialTheme.typography.bodyLarge
                )
                preview.createdAt?.let {
                    val date = java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
                    Text(text = stringResource(R.string.restore_preview_created_at, date))
                }
                Text(text = stringResource(R.string.restore_preview_locale, preview.localeTag))
                Text(text = stringResource(R.string.restore_preview_records, preview.totalRecordCount))
                
                val sizeStr = Formatters.formatFileSize(preview.totalAttachmentBytes)
                Text(text = stringResource(R.string.restore_preview_attachments, preview.attachmentCount, sizeStr))
                
                if (eligibility is BackupRestoreEligibility.AttachmentsNotSupported) {
                    Spacer(modifier = Modifier.size(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = stringResource(R.string.restore_preview_attachments_unsupported),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.restore_preview_read_only_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRestore, 
                enabled = eligibility == BackupRestoreEligibility.Eligible,
                modifier = Modifier.testTag("restore_backup_action")
            ) {
                Text(stringResource(R.string.restore_action_restore))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onChooseAnother, modifier = Modifier.testTag("restore_backup_choose_another")) {
                    Text(stringResource(R.string.restore_action_choose_another))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )
}

@Composable
fun RestoreConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = {
            Text(stringResource(R.string.restore_confirm_message))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.restore_action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun RestoreApplyingDialog(progress: BackupRestoreProgress?) {
    AlertDialog(
        onDismissRequest = {}, // non-interruptible
        confirmButton = {},
        title = { Text(stringResource(if (progress == null) R.string.state_loading_desc else R.string.restore_applying_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                if (progress != null) {
                    val progressText = when (progress) {
                        BackupRestoreProgress.ValidatingBackup -> stringResource(R.string.restore_phase_staging)
                        BackupRestoreProgress.PreparingRollback -> stringResource(R.string.restore_phase_preparing)
                        BackupRestoreProgress.RestoringData -> stringResource(R.string.restore_phase_database)
                        BackupRestoreProgress.RestoringSettings -> stringResource(R.string.restore_phase_preferences)
                        BackupRestoreProgress.Finalizing -> stringResource(R.string.restore_phase_finalizing)
                        BackupRestoreProgress.RollingBack -> stringResource(R.string.restore_phase_rolling_back)
                    }
                    Text(text = progressText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    )
}

@Composable
fun RestoreSuccessDialog(
    summary: com.miara.cuentame.feature.settings.viewmodel.BackupRestoreSuccessSummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_success_title)) },
        text = {
            Text(stringResource(R.string.restore_success_message, summary.restaurantName, summary.recordCount))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
fun RestoreErrorDialog(
    failure: com.miara.cuentame.core.model.backup.BackupRestoreFailure,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)?,
    recoveryRequired: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!recoveryRequired) onDismiss() },
        title = { Text(stringResource(if (recoveryRequired) R.string.restore_recovery_required_title else R.string.restore_error_title)) },
        text = {
            val message = if (recoveryRequired) {
                stringResource(R.string.restore_recovery_required_message)
            } else {
                stringResource(R.string.restore_error_message, stringResource(failure.toUserMessageRes()))
            }
            Text(text = message, modifier = Modifier.testTag("restore_backup_error"))
        },
        confirmButton = {
            if (!recoveryRequired) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        },
        dismissButton = {
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(if (recoveryRequired) R.string.action_retry_desc else R.string.restore_action_choose_another))
                }
            }
        }
    )
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
    onSelect: (SupportedAppLocale) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                LanguageOption(SupportedAppLocale.ENGLISH_US, stringResource(R.string.lang_en), currentTag == SupportedAppLocale.ENGLISH_US.languageTag, isSaving, onSelect)
                LanguageOption(SupportedAppLocale.SPANISH_US, stringResource(R.string.lang_es), currentTag == SupportedAppLocale.SPANISH_US.languageTag, isSaving, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
fun LanguageOption(locale: SupportedAppLocale, label: String, selected: Boolean, isSaving: Boolean, onSelect: (SupportedAppLocale) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = { if (!isSaving) onSelect(locale) },
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
