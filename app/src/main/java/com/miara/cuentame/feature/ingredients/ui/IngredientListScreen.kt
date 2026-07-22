package com.miara.cuentame.feature.ingredients.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientCategory
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
        onCategorySelected = viewModel::onCategorySelected,
        onAddIngredient = onAddIngredient,
        onIngredientClick = onIngredientClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientListScreen(
    uiState: IngredientListUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (IngredientCategoryId?) -> Unit,
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
                selectedCategoryId = uiState.selectedCategoryId,
                onCategorySelected = onCategorySelected
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
                LazyColumn {
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
    selectedCategoryId: IngredientCategoryId?,
    onCategorySelected: (IngredientCategoryId?) -> Unit
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = { Text(stringResource(R.string.search_ingredients)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            Box {
                IconButton(onClick = { filterMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.filter_by_category),
                        tint = if (selectedCategoryId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { filterMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ingredients_title)) }, // All
                        onClick = {
                            onCategorySelected(null)
                            filterMenuExpanded = false
                        }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onCategorySelected(category.id)
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
}

@Composable
fun IngredientItem(
    ingredient: Ingredient,
    category: IngredientCategory?,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(ingredient.name) },
        supportingContent = { 
            Text(category?.name ?: stringResource(R.string.uncategorized))
        },
        trailingContent = {
            Text(ingredient.baseUnitId.value, style = MaterialTheme.typography.labelSmall)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
