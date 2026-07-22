package com.miara.cuentame.feature.categories.ui

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.feature.categories.viewmodel.CategoryManagementViewModel

@Composable
fun CategoryManagementRoute(
    viewModel: CategoryManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it.message ?: context.getString(R.string.error_generic))
            viewModel.clearError()
        }
    }

    CategoryManagementScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAddCategory = viewModel::onAddCategory,
        onUpdateCategory = viewModel::onUpdateCategory,
        onArchiveCategory = { viewModel.onArchiveCategory(it.id) },
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown
    )
}

@Composable
fun CategoryManagementScreen(
    uiState: com.miara.cuentame.feature.categories.viewmodel.CategoryManagementUiState,
    snackbarHostState: SnackbarHostState,
    onAddCategory: (String) -> Unit,
    onUpdateCategory: (com.miara.cuentame.core.model.ingredient.IngredientCategory) -> Unit,
    onArchiveCategory: (com.miara.cuentame.core.model.ingredient.IngredientCategory) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    var categoryToArchive by remember { mutableStateOf<com.miara.cuentame.core.model.ingredient.IngredientCategory?>(null) }
    var categoryToEdit by remember { mutableStateOf<com.miara.cuentame.core.model.ingredient.IngredientCategory?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = stringResource(R.string.settings_categories), style = MaterialTheme.typography.headlineSmall)
            
            var newCategoryName by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text(stringResource(R.string.onboarding_add_category)) },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSaving
                )
                IconButton(onClick = { 
                    onAddCategory(newCategoryName)
                    newCategoryName = ""
                }, enabled = !uiState.isSaving && newCategoryName.isNotBlank()) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(uiState.categories) { index, category ->
                    CategoryItem(
                        category = category,
                        canMoveUp = index > 0 && !uiState.isSaving,
                        canMoveDown = index < uiState.categories.size - 1 && !uiState.isSaving,
                        isEnabled = !uiState.isSaving,
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onArchive = { categoryToArchive = category },
                        onEdit = { categoryToEdit = category }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    categoryToArchive?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToArchive = null },
            title = { Text(stringResource(R.string.action_archive)) },
            text = { Text("Are you sure you want to archive ${cat.name}?") },
            confirmButton = {
                TextButton(onClick = { 
                    onArchiveCategory(cat)
                    categoryToArchive = null
                }) {
                    Text(stringResource(R.string.action_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToArchive = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    categoryToEdit?.let { cat ->
        var editName by remember { mutableStateOf(cat.name) }
        AlertDialog(
            onDismissRequest = { categoryToEdit = null },
            title = { Text(stringResource(R.string.action_edit)) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.onboarding_field_name)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    onUpdateCategory(cat.copy(name = editName))
                    categoryToEdit = null
                }, enabled = editName.isNotBlank()) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEdit = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun CategoryItem(
    category: com.miara.cuentame.core.model.ingredient.IngredientCategory,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isEnabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = category.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        
        IconButton(onClick = onEdit, enabled = isEnabled) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_item, category.name))
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.move_up, category.name))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.move_down, category.name))
        }
        IconButton(onClick = onArchive, enabled = isEnabled) {
            Icon(Icons.Default.Archive, contentDescription = stringResource(R.string.archive_item, category.name))
        }
    }
}
