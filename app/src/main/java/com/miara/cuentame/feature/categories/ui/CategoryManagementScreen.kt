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
import com.miara.cuentame.feature.categories.viewmodel.CategoryManagementViewModel

@Composable
fun CategoryManagementRoute(
    viewModel: CategoryManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryManagementScreen(
        uiState = uiState,
        onAddCategory = viewModel::onAddCategory,
        onArchiveCategory = { viewModel.onArchiveCategory(it.id) },
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown
    )
}

@Composable
fun CategoryManagementScreen(
    uiState: com.miara.cuentame.feature.categories.viewmodel.CategoryManagementUiState,
    onAddCategory: (String) -> Unit,
    onArchiveCategory: (com.miara.cuentame.core.model.ingredient.IngredientCategory) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { 
                onAddCategory(newCategoryName)
                newCategoryName = ""
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(uiState.categories) { index, category ->
                CategoryItem(
                    category = category,
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.categories.size - 1,
                    onMoveUp = { onMoveUp(index) },
                    onMoveDown = { onMoveDown(index) },
                    onArchive = { onArchiveCategory(category) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun CategoryItem(
    category: com.miara.cuentame.core.model.ingredient.IngredientCategory,
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
        Text(text = category.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        
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
