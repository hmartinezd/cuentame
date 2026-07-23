package com.miara.cuentame.feature.ingredients.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import com.miara.cuentame.feature.ingredients.viewmodel.IngredientCategoryFilter
import com.miara.cuentame.feature.ingredients.viewmodel.IngredientListUiState
import com.miara.cuentame.feature.ingredients.viewmodel.IngredientListViewModel

@Composable
fun IngredientListRoute(
    onAddIngredient: () -> Unit,
    onIngredientClick: (IngredientId) -> Unit,
    viewModel: IngredientListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    IngredientListScreen(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onCategoryFilterChanged = viewModel::onCategoryFilterChanged,
        onShowArchivedToggled = viewModel::onShowArchivedToggled,
        onAddIngredient = onAddIngredient,
        onIngredientClick = onIngredientClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientListScreen(
    uiState: IngredientListUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCategoryFilterChanged: (IngredientCategoryFilter) -> Unit,
    onShowArchivedToggled: (Boolean) -> Unit,
    onAddIngredient: () -> Unit,
    onIngredientClick: (IngredientId) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddIngredient) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_ingredient))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchAndFilterBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChanged,
                categories = uiState.categories,
                currentFilter = uiState.categoryFilter,
                onFilterChange = onCategoryFilterChanged,
                showArchived = uiState.showArchived,
                onShowArchivedToggle = onShowArchivedToggled
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.ingredients.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.no_ingredients))
                }
            } else {
                LazyColumn(modifier = Modifier.testTag("ingredient_list")) {
                    items(uiState.ingredients) { ingredient ->
                        IngredientItem(
                            ingredient = ingredient,
                            category = uiState.categories.find { it.id == ingredient.categoryId },
                            onClick = { onIngredientClick(ingredient.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun SearchAndFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<IngredientCategory>,
    currentFilter: IngredientCategoryFilter,
    onFilterChange: (IngredientCategoryFilter) -> Unit,
    showArchived: Boolean,
    onShowArchivedToggle: (Boolean) -> Unit
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }

    Column {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_ingredients)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Box {
                    IconButton(onClick = { filterMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.filter_by_category),
                            tint = if (currentFilter !is IngredientCategoryFilter.All) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ingredients_title)) },
                            onClick = {
                                onFilterChange(IngredientCategoryFilter.All)
                                filterMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.uncategorized)) },
                            onClick = {
                                onFilterChange(IngredientCategoryFilter.Uncategorized)
                                filterMenuExpanded = false
                            }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    onFilterChange(IngredientCategoryFilter.Category(category.id))
                                    filterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
            )
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.show_archived),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = showArchived,
                onCheckedChange = onShowArchivedToggle
            )
        }
    }
}

@Composable
fun IngredientItem(
    ingredient: Ingredient,
    category: IngredientCategory?,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                text = ingredient.name,
                color = if (ingredient.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        supportingContent = { 
            Text(category?.name ?: stringResource(R.string.uncategorized))
        },
        trailingContent = {
            if (!ingredient.isActive) {
                Text(
                    text = stringResource(R.string.archived_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier
            .testTag("ingredient_item_${ingredient.name}")
            .clickable(onClick = onClick)
    )
}
