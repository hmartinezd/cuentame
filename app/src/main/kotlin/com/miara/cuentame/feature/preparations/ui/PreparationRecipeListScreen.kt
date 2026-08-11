package com.miara.cuentame.feature.preparations.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.ingredient.PreparationRecipeSummary
import com.miara.cuentame.core.model.ingredient.PreparationRecipeCostSummary
import com.miara.cuentame.core.model.ingredient.PreparationCostStatus
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeListUiState
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeListViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PreparationRecipeListRoute(
    onBackClick: () -> Unit,
    onCreateRecipe: () -> Unit,
    onViewProduction: () -> Unit,
    onRecipeClick: (PreparationRecipeId, PreparationRecipeStatus) -> Unit,
    viewModel: PreparationRecipeListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PreparationRecipeListScreen(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onStatusFilterChanged = viewModel::onStatusFilterChanged,
        onIncludeArchivedToggled = viewModel::onIncludeArchivedToggled,
        onRetry = viewModel::onRetry,
        onBackClick = onBackClick,
        onCreateRecipe = onCreateRecipe,
        onViewProduction = onViewProduction,
        onRecipeClick = onRecipeClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationRecipeListScreen(
    uiState: PreparationRecipeListUiState,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterChanged: (PreparationRecipeStatus?) -> Unit,
    onIncludeArchivedToggled: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    onCreateRecipe: () -> Unit,
    onViewProduction: () -> Unit,
    onRecipeClick: (PreparationRecipeId, PreparationRecipeStatus) -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("preparation_recipe_list_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preparation_recipes)) },
                modifier = Modifier.testTag("preparation_list_app_bar"),
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("preparation_list_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onViewProduction, modifier = Modifier.testTag("open_production_from_recipes")) {
                        Icon(Icons.Default.PrecisionManufacturing, contentDescription = stringResource(R.string.production_batches))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateRecipe,
                modifier = Modifier.testTag("add_preparation_recipe_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_preparation_recipe))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            RecipeListFilters(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChanged,
                selectedStatus = uiState.selectedStatus,
                onStatusChange = onStatusFilterChanged,
                includeArchived = uiState.includeArchived,
                onIncludeArchivedToggle = onIncludeArchivedToggled
            )

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize().testTag("preparation_recipe_error"), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(R.string.state_error_desc))
                            Button(onClick = onRetry, modifier = Modifier.testTag("preparation_recipe_retry")) {
                                Text(stringResource(R.string.action_retry_desc))
                            }
                        }
                    }
                }
                uiState.recipes.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().testTag("preparation_recipe_empty"), contentAlignment = Alignment.Center) {
                        val emptyText = when {
                            uiState.selectedStatus == PreparationRecipeStatus.ARCHIVED && !uiState.includeArchived -> 
                                stringResource(R.string.no_recipes_match_filters) // Or a more specific one about the toggle
                            uiState.searchQuery.isNotBlank() || uiState.selectedStatus != null ->
                                stringResource(R.string.no_recipes_match_filters)
                            else ->
                                stringResource(R.string.no_preparation_recipes)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Text(text = emptyText, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            if (uiState.selectedStatus == PreparationRecipeStatus.ARCHIVED && !uiState.includeArchived) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.show_archived_to_see_results),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("preparation_recipe_list"),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(uiState.recipes, key = { it.id.value }) { recipe ->
                            PreparationRecipeItem(
                                recipe = recipe,
                                cost = uiState.costs[recipe.id],
                                onClick = { onRecipeClick(recipe.id, recipe.status) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeListFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedStatus: PreparationRecipeStatus?,
    onStatusChange: (PreparationRecipeStatus?) -> Unit,
    includeArchived: Boolean,
    onIncludeArchivedToggle: (Boolean) -> Unit
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    Column {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("preparation_recipe_search"),
            placeholder = { Text(stringResource(R.string.search_recipes)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Box {
                    IconButton(onClick = { statusMenuExpanded = true }, modifier = Modifier.testTag("preparation_recipe_status_filter")) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.status_label),
                            tint = if (selectedStatus != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.all)) },
                            onClick = {
                                onStatusChange(null)
                                statusMenuExpanded = false
                            }
                        )
                        PreparationRecipeStatus.entries.forEach { status ->
                            val labelRes = when (status) {
                                PreparationRecipeStatus.DRAFT -> R.string.status_draft
                                PreparationRecipeStatus.ACTIVE -> R.string.status_active
                                PreparationRecipeStatus.ARCHIVED -> R.string.status_archived
                                PreparationRecipeStatus.UNKNOWN -> R.string.status_unavailable
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    onStatusChange(status)
                                    statusMenuExpanded = false
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
            ),
            singleLine = true
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
                checked = includeArchived,
                onCheckedChange = onIncludeArchivedToggle,
                modifier = Modifier.testTag("preparation_recipe_include_archived")
            )
        }
    }
}

@Composable
private fun PreparationRecipeItem(
    recipe: PreparationRecipeSummary,
    cost: PreparationRecipeCostSummary?,
    onClick: () -> Unit
) {
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
    }

    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("preparation_recipe_item_${recipe.id.value}"),
        headlineContent = {
            Text(
                text = recipe.recipeName.ifBlank { stringResource(R.string.new_preparation_recipe) },
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = stringResource(R.string.output_ingredient) + ": ${recipe.outputIngredientName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.component_count_format, recipe.componentCount),
                    style = MaterialTheme.typography.bodySmall
                )
                cost?.let {
                    Text(when (it.status) {
                        PreparationCostStatus.FULLY_COSTED -> stringResource(R.string.cost_status_fully)
                        PreparationCostStatus.PARTIALLY_COSTED -> stringResource(R.string.cost_status_partially)
                        PreparationCostStatus.UNCOSTED -> stringResource(R.string.cost_status_uncosted)
                    }, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = stringResource(R.string.last_updated_format, dateTimeFormatter.format(recipe.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                RecipeStatusBadge(status = recipe.status)
                Spacer(modifier = Modifier.height(4.dp))
                if (recipe.standardYieldQuantity != null && recipe.yieldUnitLabel != null) {
                    Text(
                        text = Formatters.formatQuantity(recipe.standardYieldQuantity, recipe.yieldUnitLabel),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}
