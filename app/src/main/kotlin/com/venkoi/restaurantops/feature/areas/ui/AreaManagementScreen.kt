package com.venkoi.restaurantops.feature.areas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.presentation.validation.toUserMessageRes
import com.venkoi.restaurantops.feature.areas.viewmodel.AreaManagementEvent
import com.venkoi.restaurantops.feature.areas.viewmodel.AreaManagementViewModel
import com.venkoi.restaurantops.feature.areas.viewmodel.InventoryAreaConflictMessage
import com.venkoi.restaurantops.feature.areas.viewmodel.InventoryAreaManualSyncUiState
import com.venkoi.restaurantops.feature.areas.viewmodel.InventoryAreaManualSyncViewModel

@Composable
fun AreaManagementRoute(
    onBack: () -> Unit,
    onViewActivity: (InventoryAreaId) -> Unit,
    viewModel: AreaManagementViewModel = hiltViewModel()
) {
    val syncViewModel: InventoryAreaManualSyncViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncUiState by syncViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var areaToArchive by remember { mutableStateOf<com.venkoi.restaurantops.core.model.inventory.InventoryArea?>(null) }
    var areaToEdit by remember { mutableStateOf<com.venkoi.restaurantops.core.model.inventory.InventoryArea?>(null) }
    var newAreaName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AreaManagementEvent.OperationSuccess -> {
                    areaToArchive = null
                    areaToEdit = null
                    newAreaName = ""
                }
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    LaunchedEffect(syncUiState) {
        if (syncUiState is InventoryAreaManualSyncUiState.Success) {
            snackbarHostState.showSnackbar(context.getString(R.string.area_sync_success))
            syncViewModel.clearResult()
        }
    }

    AreaManagementScreen(
        uiState = uiState,
        syncUiState = syncUiState,
        areaToArchive = areaToArchive,
        areaToEdit = areaToEdit,
        newAreaName = newAreaName,
        snackbarHostState = snackbarHostState,
        onNewAreaNameChange = { newAreaName = it },
        onSetAreaToArchive = { areaToArchive = it },
        onSetAreaToEdit = { areaToEdit = it },
        onViewActivity = onViewActivity,
        onAddArea = viewModel::onAddArea,
        onUpdateArea = viewModel::onUpdateArea,
        onArchiveArea = { viewModel.onArchiveArea(it.id) },
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown,
        onSyncNow = syncViewModel::syncNow,
        onRetrySync = syncViewModel::syncNow,
        onRetryPreview = syncViewModel::retryPreview,
        onUseThisDevice = syncViewModel::useThisDevice,
        onUseCloudVersion = syncViewModel::useCloudVersion,
        onDismissConflict = syncViewModel::dismissConflict,
        onClearSyncResult = syncViewModel::clearResult,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaManagementScreen(
    uiState: com.venkoi.restaurantops.feature.areas.viewmodel.AreaManagementUiState,
    syncUiState: InventoryAreaManualSyncUiState,
    areaToArchive: com.venkoi.restaurantops.core.model.inventory.InventoryArea?,
    areaToEdit: com.venkoi.restaurantops.core.model.inventory.InventoryArea?,
    newAreaName: String,
    snackbarHostState: SnackbarHostState,
    onNewAreaNameChange: (String) -> Unit,
    onSetAreaToArchive: (com.venkoi.restaurantops.core.model.inventory.InventoryArea?) -> Unit,
    onSetAreaToEdit: (com.venkoi.restaurantops.core.model.inventory.InventoryArea?) -> Unit,
    onViewActivity: (InventoryAreaId) -> Unit,
    onAddArea: (String) -> Unit,
    onUpdateArea: (com.venkoi.restaurantops.core.model.inventory.InventoryArea) -> Unit,
    onArchiveArea: (com.venkoi.restaurantops.core.model.inventory.InventoryArea) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSyncNow: () -> Unit,
    onRetrySync: () -> Unit,
    onRetryPreview: () -> Unit,
    onUseThisDevice: () -> Unit,
    onUseCloudVersion: () -> Unit,
    onDismissConflict: () -> Unit,
    onClearSyncResult: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_areas)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSyncNow,
                        enabled = syncUiState !is InventoryAreaManualSyncUiState.Syncing &&
                            syncUiState !is InventoryAreaManualSyncUiState.LoadingConflict &&
                            !(syncUiState is InventoryAreaManualSyncUiState.Conflict && syncUiState.isResolving),
                        modifier = Modifier.testTag("area_sync_now")
                    ) {
                        if (syncUiState is InventoryAreaManualSyncUiState.Syncing ||
                            syncUiState is InventoryAreaManualSyncUiState.LoadingConflict
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).testTag("area_sync_progress"),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null)
                        }
                        Text(
                            text = stringResource(
                                if (syncUiState is InventoryAreaManualSyncUiState.Syncing) R.string.area_syncing
                                else R.string.area_sync_now
                            ),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (syncUiState) {
                InventoryAreaManualSyncUiState.RemoteFailure -> SyncMessage(
                    stringResource(R.string.area_sync_remote_failure), onRetrySync, onClearSyncResult
                )
                InventoryAreaManualSyncUiState.Error -> SyncMessage(
                    stringResource(R.string.area_sync_error), onRetrySync, onClearSyncResult
                )
                else -> Unit
            }
            Text(text = stringResource(R.string.settings_areas), style = MaterialTheme.typography.headlineSmall)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newAreaName,
                    onValueChange = onNewAreaNameChange,
                    label = { Text(stringResource(R.string.onboarding_add_area)) },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSaving
                )
                IconButton(onClick = { 
                    onAddArea(newAreaName)
                }, enabled = !uiState.isSaving && newAreaName.isNotBlank()) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_area))
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(uiState.areas) { index, area ->
                    AreaItem(
                        area = area,
                        canMoveUp = index > 0 && !uiState.isSaving,
                        canMoveDown = index < uiState.areas.size - 1 && !uiState.isSaving,
                        isEnabled = !uiState.isSaving,
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onArchive = { onSetAreaToArchive(area) },
                        onEdit = { onSetAreaToEdit(area) },
                        onViewActivity = { onViewActivity(area.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    areaToArchive?.let { area ->
        AlertDialog(
            onDismissRequest = { onSetAreaToArchive(null) },
            title = { Text(stringResource(R.string.action_archive)) },
            text = { Text(stringResource(R.string.archive_area_confirmation, area.name)) },
            confirmButton = {
                TextButton(onClick = { onArchiveArea(area) }, enabled = !uiState.isSaving) {
                    Text(stringResource(R.string.archive_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { onSetAreaToArchive(null) }, enabled = !uiState.isSaving) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    areaToEdit?.let { area ->
        var editName by remember { mutableStateOf(area.name) }
        AlertDialog(
            onDismissRequest = { if (!uiState.isSaving) onSetAreaToEdit(null) },
            title = { Text(stringResource(R.string.action_edit)) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.onboarding_field_name)) },
                    enabled = !uiState.isSaving
                )
            },
            confirmButton = {
                TextButton(onClick = { onUpdateArea(area.copy(name = editName)) }, enabled = !uiState.isSaving && editName.isNotBlank()) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { onSetAreaToEdit(null) }, enabled = !uiState.isSaving) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }


    when (val state = syncUiState) {
        is InventoryAreaManualSyncUiState.Conflict -> InventoryAreaConflictDialog(
            state = state,
            onUseThisDevice = onUseThisDevice,
            onUseCloudVersion = onUseCloudVersion,
            onDismiss = onDismissConflict
        )
        is InventoryAreaManualSyncUiState.PreviewUnavailable -> AlertDialog(
            onDismissRequest = onDismissConflict,
            title = { Text(stringResource(R.string.area_conflict_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.local?.let {
                        ConflictVersionCard(
                            stringResource(R.string.area_conflict_this_device), it,
                            nameDifferent = false, statusDifferent = false, positionDifferent = false,
                            modifier = Modifier.fillMaxWidth().testTag("conflict_this_device")
                        )
                    }
                    Text(stringResource(R.string.area_conflict_preview_failure))
                }
            },
            confirmButton = {
                TextButton(onClick = onRetryPreview) { Text(stringResource(R.string.area_sync_retry)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissConflict) { Text(stringResource(android.R.string.cancel)) }
            }
        )
        else -> Unit
    }
}

@Composable
private fun SyncMessage(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("area_sync_message")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.area_sync_retry)) }
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    }
}

@Composable
private fun InventoryAreaConflictDialog(
    state: InventoryAreaManualSyncUiState.Conflict,
    onUseThisDevice: () -> Unit,
    onUseCloudVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    val local = state.preview.local
    val cloud = state.preview.cloud
    AlertDialog(
        onDismissRequest = { if (!state.isResolving) onDismiss() },
        title = { Text(stringResource(R.string.area_conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.area_conflict_support))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ConflictVersionCard(
                        stringResource(R.string.area_conflict_this_device), local,
                        local.name != cloud.name, local.isActive != cloud.isActive,
                        local.sortOrder != cloud.sortOrder, Modifier.weight(1f).testTag("conflict_this_device")
                    )
                    ConflictVersionCard(
                        stringResource(R.string.area_conflict_cloud), cloud,
                        local.name != cloud.name, local.isActive != cloud.isActive,
                        local.sortOrder != cloud.sortOrder, Modifier.weight(1f).testTag("conflict_cloud")
                    )
                }
                when (state.message) {
                    InventoryAreaConflictMessage.LOCAL_CONSTRAINT -> Text(
                        stringResource(R.string.area_conflict_constraint),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("conflict_constraint_message")
                    )
                    InventoryAreaConflictMessage.REMOTE_FAILURE -> Text(
                        stringResource(R.string.area_sync_remote_failure),
                        color = MaterialTheme.colorScheme.error
                    )
                    InventoryAreaConflictMessage.GENERIC_FAILURE -> Text(
                        stringResource(R.string.area_sync_error), color = MaterialTheme.colorScheme.error
                    )
                    null -> Unit
                }
                if (state.isResolving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("conflict_progress"))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUseThisDevice, enabled = !state.isResolving) {
                    Text(stringResource(R.string.area_conflict_use_device))
                }
                Button(onClick = onUseCloudVersion, enabled = !state.isResolving) {
                    Text(stringResource(R.string.area_conflict_use_cloud))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isResolving) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ConflictVersionCard(
    heading: String,
    area: com.venkoi.restaurantops.core.model.inventory.InventoryArea,
    nameDifferent: Boolean,
    statusDifferent: Boolean,
    positionDifferent: Boolean,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(heading, style = MaterialTheme.typography.titleMedium)
            ConflictField(stringResource(R.string.area_conflict_name), area.name, nameDifferent)
            ConflictField(
                stringResource(R.string.area_conflict_status),
                stringResource(if (area.isActive) R.string.area_conflict_active else R.string.area_conflict_archived),
                statusDifferent
            )
            ConflictField(stringResource(R.string.area_conflict_position), (area.sortOrder + 1).toString(), positionDifferent)
        }
    }
}

@Composable
private fun ConflictField(label: String, value: String, different: Boolean) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        if (different) Text(
            stringResource(R.string.area_conflict_different),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AreaItem(
    area: com.venkoi.restaurantops.core.model.inventory.InventoryArea,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isEnabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
    onEdit: () -> Unit,
    onViewActivity: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = area.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.move_up, area.name))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.move_down, area.name))
        }

        Box {
            IconButton(onClick = { menuExpanded = true }, enabled = isEnabled, modifier = Modifier.testTag("area_menu_${area.id.value}")) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options, area.name))
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = { onEdit(); menuExpanded = false },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.area_view_activity)) },
                    onClick = { onViewActivity(); menuExpanded = false },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                    modifier = Modifier.testTag("area_view_activity_${area.id.value}")
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_archive)) },
                    onClick = { onArchive(); menuExpanded = false },
                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error)
                )
            }
        }
    }
}
