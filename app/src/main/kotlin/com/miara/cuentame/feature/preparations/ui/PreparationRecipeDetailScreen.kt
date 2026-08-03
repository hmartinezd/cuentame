package com.miara.cuentame.feature.preparations.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import com.miara.cuentame.core.presentation.ui.UiMessage
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeDetailEvent
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeDetailUiState
import com.miara.cuentame.feature.preparations.viewmodel.PreparationRecipeDetailViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PreparationRecipeDetailRoute(
    onBack: () -> Unit,
    onEdit: (PreparationRecipeId) -> Unit,
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
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("preparation_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
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
            com.miara.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            com.miara.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.RecipeNotFound -> {
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
            is com.miara.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.LoadError -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val message = loadState.message.toRecipeDisplayText()
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
            com.miara.cuentame.feature.preparations.viewmodel.PreparationScreenLoadState.InvalidRoute -> {
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
                                        text = message.toRecipeDisplayText(),
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

                            item {
                                Text(stringResource(R.string.components), style = MaterialTheme.typography.titleMedium)
                            }

                            items(recipe.components.sortedBy { it.sortOrder }) { component ->
                                val ingredientName = uiState.componentNames[component.id] ?: component.componentIngredientId.value
                                val unitLabel = uiState.componentUnitLabels[component.id] ?: component.unitOptionId.value
                                
                                ListItem(
                                    headlineContent = { Text(ingredientName) },
                                    supportingContent = {
                                        Text("${Formatters.formatQuantity(component.quantityEntered)} $unitLabel")
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
private fun DetailHeader(
    recipe: com.miara.cuentame.core.model.ingredient.PreparationRecipe,
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
private fun AuditInfo(recipe: com.miara.cuentame.core.model.ingredient.PreparationRecipe) {
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

@Composable
private fun UiMessage.toRecipeDisplayText(): String =
    when (this) {
        is UiMessage.Resource ->
            stringResource(id, *args.toTypedArray())

        is UiMessage.PlainTextInternalOnly ->
            stringResource(R.string.error_generic)
    }
