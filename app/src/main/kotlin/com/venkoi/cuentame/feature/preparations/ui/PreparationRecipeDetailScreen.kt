package com.venkoi.cuentame.feature.preparations.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.common.ids.PreparationRecipeId
import com.venkoi.cuentame.core.designsystem.util.Formatters
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.venkoi.cuentame.core.model.ingredient.*
import com.venkoi.cuentame.core.presentation.ui.UiMessage
import com.venkoi.cuentame.core.presentation.ui.toDisplayText
import com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeDetailEvent
import com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeDetailUiState
import com.venkoi.cuentame.feature.preparations.viewmodel.PreparationRecipeDetailViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PreparationRecipeDetailRoute(
    onBack: () -> Unit,
    onEdit: (PreparationRecipeId) -> Unit,
    onCreateProduction: (PreparationRecipeId) -> Unit,
    onNavigateToEditor: (PreparationRecipeId) -> Unit,
    viewModel: PreparationRecipeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events = viewModel.events

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is PreparationRecipeDetailEvent.NavigateToEditor -> onNavigateToEditor(event.recipeId)
                PreparationRecipeDetailEvent.LifecycleUpdated -> { /* Maybe show a snackbar */ }
            }
        }
    }

    PreparationRecipeDetailScreen(
        uiState = uiState,
        onBackClick = onBack,
        onEditClick = { uiState.recipe?.let { onEdit(it.id) } },
        onCreateProductionClick = { uiState.recipe?.let { onCreateProduction(it.id) } },
        onActivate = viewModel::onActivate,
        onMoveToDraft = viewModel::onMoveToDraft,
        onArchive = viewModel::onArchive,
        onRestoreToDraft = viewModel::onRestoreToDraft,
        onClearError = viewModel::clearInlineError,
        onRetry = viewModel::onRetry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationRecipeDetailScreen(
    uiState: PreparationRecipeDetailUiState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onCreateProductionClick: () -> Unit,
    onActivate: () -> Unit,
    onMoveToDraft: () -> Unit,
    onArchive: () -> Unit,
    onRestoreToDraft: () -> Unit,
    onClearError: () -> Unit,
    onRetry: () -> Unit
) {
    val loadState = uiState.loadState
    val recipe = uiState.recipe
    val status = recipe?.status

    var showActivateConfirm by remember { mutableStateOf(false) }
    var showDraftConfirm by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("preparation_recipe_detail_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_details)) },
                modifier = Modifier.testTag("recipe_detail_app_bar"),
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("preparation_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (status == PreparationRecipeStatus.ACTIVE) {
                        IconButton(onClick = onCreateProductionClick, modifier = Modifier.testTag("create_production_from_recipe")) {
                            Icon(Icons.Default.PrecisionManufacturing, contentDescription = stringResource(R.string.new_production_batch))
                        }
                    }
                    if (status == PreparationRecipeStatus.DRAFT) {
                        IconButton(onClick = onEditClick, modifier = Modifier.testTag("recipe_detail_edit")) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (loadState) {
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.RecipeNotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.error_recipe_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_detail_not_found")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            is com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.LoadError -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val message = loadState.message.toDisplayText()
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_detail_load_error")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRetry, modifier = Modifier.testTag("recipe_detail_retry")) {
                            Text(stringResource(R.string.action_retry_desc))
                        }
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            com.venkoi.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.error_generic),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("recipe_detail_invalid_route")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            else -> {
                if (recipe != null) {
                    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                        uiState.inlineError?.let { message ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.fillMaxWidth().testTag("recipe_detail_inline_error")
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = message.toDisplayText(),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton(onClick = onClearError, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                                        Text(stringResource(android.R.string.ok))
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                DetailHeader(recipe, uiState.outputIngredientName, uiState.yieldUnitLabel)
                            }

                            uiState.currentCost?.let { cost ->
                                item { CurrentCostSection(cost) }
                            }

                            item {
                                Text(stringResource(R.string.components), style = MaterialTheme.typography.titleMedium)
                            }

                            items(recipe.components.sortedBy { it.sortOrder }) { component ->
                                val componentCost = uiState.currentCost?.components?.find { it.recipeComponentId == component.id }
                                val ingredientName = uiState.componentNames[component.id] ?: componentCost?.ingredientName.orEmpty()
                                val unitLabel = uiState.componentUnitLabels[component.id] ?: component.unitOptionId.value
                                
                                ListItem(
                                    headlineContent = { Text(ingredientName) },
                                    supportingContent = {
                                        Column {
                                            Text("${Formatters.formatQuantity(component.quantityEntered)} $unitLabel")
                                            if (componentCost != null) ComponentCostDetails(componentCost, uiState.currentCost?.currencyCode ?: "USD")
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }

                            if (!recipe.notes.isNullOrBlank()) {
                                item {
                                    Column {
                                        Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleMedium)
                                        Text(recipe.notes, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }

                            item {
                                AuditInfo(recipe)
                            }
                        }

                        ActionButtons(
                            status = status,
                            isOperating = uiState.isOperating,
                            onActivateClick = { showActivateConfirm = true },
                            onDraftClick = { showDraftConfirm = true },
                            onArchiveClick = { showArchiveConfirm = true },
                            onRestoreClick = { showRestoreConfirm = true }
                        )
                    }
                }
            }
        }
    }

    if (showActivateConfirm) {
        LifecycleConfirmationDialog(
            title = stringResource(R.string.confirm_activation_title),
            message = stringResource(R.string.confirm_activation_message),
            onConfirm = {
                onActivate()
                showActivateConfirm = false
            },
            onDismiss = { showActivateConfirm = false }
        )
    }

    if (showDraftConfirm) {
        LifecycleConfirmationDialog(
            title = stringResource(R.string.confirm_move_to_draft_title),
            message = stringResource(R.string.confirm_move_to_draft_message),
            onConfirm = {
                onMoveToDraft()
                showDraftConfirm = false
            },
            onDismiss = { showDraftConfirm = false }
        )
    }

    if (showArchiveConfirm) {
        LifecycleConfirmationDialog(
            title = stringResource(R.string.confirm_archive_recipe_title),
            message = stringResource(R.string.confirm_archive_recipe_message),
            onConfirm = {
                onArchive()
                showArchiveConfirm = false
            },
            onDismiss = { showArchiveConfirm = false }
        )
    }

    if (showRestoreConfirm) {
        LifecycleConfirmationDialog(
            title = stringResource(R.string.confirm_restore_recipe_title),
            message = stringResource(R.string.confirm_restore_recipe_message),
            onConfirm = {
                onRestoreToDraft()
                showRestoreConfirm = false
            },
            onDismiss = { showRestoreConfirm = false }
        )
    }
}

@Composable
private fun CurrentCostSection(cost: PreparationRecipeCost) {
    val locale = LocalConfiguration.current.locales[0]
    val productionDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
    }
    val status = when (cost.status) {
        PreparationCostStatus.FULLY_COSTED -> stringResource(R.string.cost_status_fully)
        PreparationCostStatus.PARTIALLY_COSTED -> stringResource(R.string.cost_status_partially)
        PreparationCostStatus.UNCOSTED -> stringResource(R.string.cost_status_uncosted)
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("current_recipe_cost")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.current_cost_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.cost_coverage_format, status, cost.costedComponentCount, cost.totalComponentCount))
            cost.totalBatchCost?.let { Text(stringResource(R.string.current_batch_cost_format, Formatters.formatCurrency(it, cost.currencyCode)), fontWeight = FontWeight.Bold) }
            if (cost.status == PreparationCostStatus.PARTIALLY_COSTED) {
                Text(stringResource(R.string.known_cost_subtotal_format, Formatters.formatCurrency(cost.knownCostSubtotal, cost.currencyCode)))
            }
            cost.standardYieldQuantity?.let { Text(stringResource(R.string.standard_yield_format, Formatters.formatQuantity(it, cost.yieldUnitLabel))) }
            cost.costPerYieldUnit?.let { Text(stringResource(R.string.cost_per_yield_format, Formatters.formatCurrency(it, cost.currencyCode), cost.yieldUnitLabel.orEmpty())) }
            cost.costPerOutputBaseUnit?.let { Text(stringResource(R.string.cost_per_base_format, Formatters.formatCurrency(it, cost.currencyCode), cost.outputBaseUnitSymbol)) }
            if (cost.yieldWarnings.isNotEmpty()) Text(stringResource(R.string.cost_yield_warning), color = MaterialTheme.colorScheme.error)
            val impact = cost.priceImpact
            if (impact.coveredLeafCount > 0) Text(stringResource(
                if (impact.isComplete) R.string.vendor_impact_complete_format else R.string.vendor_impact_partial_format,
                Formatters.formatCurrency(impact.knownSubtotal, cost.currencyCode), impact.coveredLeafCount, impact.totalLeafCount
            ))
            cost.components.filter { it.missingReason != null }.forEach {
                Text("• ${it.ingredientName} — ${missingReasonText(it.missingReason!!)}", color = MaterialTheme.colorScheme.error)
            }
            cost.lastProduction?.let { historical ->
                HorizontalDivider()
                Text(stringResource(R.string.last_production_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.production_date_format, productionDateFormatter.format(historical.producedAt)))
                Text(stringResource(R.string.historical_batch_cost_format, historical.batchCost?.let { Formatters.formatCurrency(it, cost.currencyCode) } ?: stringResource(R.string.status_unavailable)))
                historical.outputUnitCostBase?.let { Text(stringResource(R.string.historical_output_cost_format, Formatters.formatCurrency(it, cost.currencyCode), cost.outputBaseUnitSymbol)) }
            }
        }
    }
}

@Composable
private fun ComponentCostDetails(cost: PreparationComponentCost, currencyCode: String) {
    cost.componentCurrentCost?.let { Text(stringResource(R.string.component_current_cost_format, Formatters.formatCurrency(it, currencyCode)), style = MaterialTheme.typography.bodySmall) }
    cost.currentUnitCostBase?.let { Text(stringResource(R.string.current_unit_cost_format, Formatters.formatCurrency(it, currencyCode), cost.baseUnitSymbol), style = MaterialTheme.typography.labelSmall) }
    cost.costSource?.let { source ->
        Text(stringResource(if (source == PreparationCostSource.ACTIVE_PREPARATION_RECIPE) R.string.cost_source_preparation else R.string.cost_source_average), style = MaterialTheme.typography.labelSmall)
    }
    cost.vendorPriceImpact?.let { Text(stringResource(R.string.component_vendor_impact_format, Formatters.formatCurrency(it, currencyCode)), style = MaterialTheme.typography.labelSmall) }
    cost.missingReason?.let { Text(missingReasonText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun missingReasonText(reason: PreparationCostMissingReason): String = stringResource(when (reason) {
    PreparationCostMissingReason.INGREDIENT_COST_MISSING -> R.string.cost_missing_ingredient
    PreparationCostMissingReason.INGREDIENT_COST_INVALID -> R.string.cost_invalid_ingredient
    PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_PARTIAL -> R.string.cost_nested_partial
    PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_UNCOSTED -> R.string.cost_nested_uncosted
    PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_YIELD_UNAVAILABLE -> R.string.cost_nested_yield_unavailable
    PreparationCostMissingReason.RECIPE_DEPENDENCY_CYCLE -> R.string.cost_dependency_cycle
})

@Composable
private fun DetailHeader(
    recipe: com.venkoi.cuentame.core.model.ingredient.PreparationRecipe,
    outputIngredientName: String,
    yieldUnitLabel: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                RecipeStatusBadge(status = recipe.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.output_ingredient) + ": $outputIngredientName",
                style = MaterialTheme.typography.bodyLarge
            )

            if (recipe.standardYieldQuantity != null) {
                Text(
                    text = stringResource(R.string.standard_yield) + ": ${Formatters.formatQuantity(recipe.standardYieldQuantity)} $yieldUnitLabel",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AuditInfo(recipe: com.venkoi.cuentame.core.model.ingredient.PreparationRecipe) {
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.audit_created, dateTimeFormatter.format(recipe.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = stringResource(R.string.audit_updated, dateTimeFormatter.format(recipe.updatedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        if (recipe.archivedAt != null) {
            Text(
                text = stringResource(R.string.audit_archived, dateTimeFormatter.format(recipe.archivedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ActionButtons(
    status: PreparationRecipeStatus?,
    isOperating: Boolean,
    onActivateClick: () -> Unit,
    onDraftClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (status) {
                PreparationRecipeStatus.DRAFT -> {
                    LoadingActionButton(
                        text = stringResource(R.string.activate_recipe),
                        onClick = onActivateClick,
                        isLoading = isOperating,
                        modifier = Modifier.weight(1f).testTag("recipe_detail_activate")
                    )
                    OutlinedButton(
                        onClick = onArchiveClick,
                        enabled = !isOperating,
                        modifier = Modifier.weight(1f).testTag("recipe_detail_archive")
                    ) {
                        Text(stringResource(R.string.archive_recipe))
                    }
                }
                PreparationRecipeStatus.ACTIVE -> {
                    Button(
                        onClick = onDraftClick,
                        enabled = !isOperating,
                        modifier = Modifier.weight(1f).testTag("recipe_detail_move_to_draft")
                    ) {
                        Text(stringResource(R.string.move_recipe_to_draft))
                    }
                    OutlinedButton(
                        onClick = onArchiveClick,
                        enabled = !isOperating,
                        modifier = Modifier.weight(1f).testTag("recipe_detail_archive")
                    ) {
                        Text(stringResource(R.string.archive_recipe))
                    }
                }
                PreparationRecipeStatus.ARCHIVED -> {
                    LoadingActionButton(
                        text = stringResource(R.string.restore_recipe),
                        onClick = onRestoreClick,
                        isLoading = isOperating,
                        modifier = Modifier.fillMaxWidth().testTag("recipe_detail_restore")
                    )
                }
                else -> {}
            }
        }
    }
}
