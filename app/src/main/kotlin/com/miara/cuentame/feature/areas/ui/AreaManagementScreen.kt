package com.miara.cuentame.feature.areas.ui

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
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.presentation.validation.toUserMessageRes
import com.miara.cuentame.feature.areas.viewmodel.AreaManagementEvent
import com.miara.cuentame.feature.areas.viewmodel.AreaManagementViewModel

@Composable
fun AreaManagementRoute(
    onBack: () -> Unit,
    onViewActivity: (InventoryAreaId) -> Unit,
    viewModel: AreaManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var areaToArchive by remember { mutableStateOf<com.miara.cuentame.core.model.inventory.InventoryArea?>(null) }
    var areaToEdit by remember { mutableStateOf<com.miara.cuentame.core.model.inventory.InventoryArea?>(null) }
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

    AreaManagementScreen(
        uiState = uiState,
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
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaManagementScreen(
    uiState: com.miara.cuentame.feature.areas.viewmodel.AreaManagementUiState,
    areaToArchive: com.miara.cuentame.core.model.inventory.InventoryArea?,
    areaToEdit: com.miara.cuentame.core.model.inventory.InventoryArea?,
    newAreaName: String,
    snackbarHostState: SnackbarHostState,
    onNewAreaNameChange: (String) -> Unit,
    onSetAreaToArchive: (com.miara.cuentame.core.model.inventory.InventoryArea?) -> Unit,
    onSetAreaToEdit: (com.miara.cuentame.core.model.inventory.InventoryArea?) -> Unit,
    onViewActivity: (InventoryAreaId) -> Unit,
    onAddArea: (String) -> Unit,
    onUpdateArea: (com.miara.cuentame.core.model.inventory.InventoryArea) -> Unit,
    onArchiveArea: (com.miara.cuentame.core.model.inventory.InventoryArea) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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
}

@Composable
fun AreaItem(
    area: com.miara.cuentame.core.model.inventory.InventoryArea,
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
