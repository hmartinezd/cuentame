package com.miara.cuentame.feature.areas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.feature.areas.viewmodel.AreaManagementViewModel

@Composable
fun AreaManagementRoute(
    viewModel: AreaManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AreaManagementScreen(
        uiState = uiState,
        onAddArea = viewModel::onAddArea,
        onArchiveArea = { viewModel.onArchiveArea(it.id) },
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown
    )
}

@Composable
fun AreaManagementScreen(
    uiState: com.miara.cuentame.feature.areas.viewmodel.AreaManagementUiState,
    onAddArea: (String) -> Unit,
    onArchiveArea: (com.miara.cuentame.core.model.inventory.InventoryArea) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = stringResource(R.string.settings_areas), style = MaterialTheme.typography.headlineSmall)
        
        var newAreaName by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newAreaName,
                onValueChange = { newAreaName = it },
                label = { Text(stringResource(R.string.onboarding_add_area)) },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { 
                onAddArea(newAreaName)
                newAreaName = ""
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(uiState.areas) { index, area ->
                AreaItem(
                    area = area,
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.areas.size - 1,
                    onMoveUp = { onMoveUp(index) },
                    onMoveDown = { onMoveDown(index) },
                    onArchive = { onArchiveArea(area) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun AreaItem(
    area: com.miara.cuentame.core.model.inventory.InventoryArea,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = area.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
        }
        IconButton(onClick = onArchive) {
            Icon(Icons.Default.Archive, contentDescription = "Archive")
        }
    }
}
