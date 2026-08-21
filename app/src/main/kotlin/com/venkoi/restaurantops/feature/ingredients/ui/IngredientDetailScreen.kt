package com.venkoi.restaurantops.feature.ingredients.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.UnitId
import com.venkoi.restaurantops.core.domain.repository.AddPackageUnitOptionCommand
import com.venkoi.restaurantops.core.domain.repository.AddStandardUnitOptionCommand
import com.venkoi.restaurantops.core.domain.repository.UpdatePackageUnitOptionCommand
import com.venkoi.restaurantops.core.presentation.ui.ArchiveConfirmDialog
import com.venkoi.restaurantops.core.presentation.validation.toUserMessageRes
import com.venkoi.restaurantops.core.model.ingredient.IngredientUnitOption
import com.venkoi.restaurantops.core.model.inventory.UnitOfMeasure
import com.venkoi.restaurantops.feature.ingredients.model.UnitConversionChoiceUiModel
import com.venkoi.restaurantops.feature.ingredients.viewmodel.IngredientDetailEvent
import com.venkoi.restaurantops.feature.ingredients.viewmodel.IngredientDetailUiState
import com.venkoi.restaurantops.feature.ingredients.viewmodel.IngredientDetailViewModel
import java.math.BigDecimal

@Composable
fun IngredientDetailRoute(
    ingredientId: IngredientId,
    onEditClick: (IngredientId) -> Unit,
    onViewActivity: (IngredientId) -> Unit,
    onViewPriceHistory: (IngredientId) -> Unit = {},
    onBack: () -> Unit,
    viewModel: IngredientDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showAddStandardDialog by remember { mutableStateOf(false) }
    var showAddPackageDialog by remember { mutableStateOf(false) }
    var packageToEdit by remember { mutableStateOf<IngredientUnitOption?>(null) }
    var optionToArchive by remember { mutableStateOf<IngredientUnitOption?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is IngredientDetailEvent.IngredientArchived -> onBack()
                is IngredientDetailEvent.StandardOptionAdded -> showAddStandardDialog = false
                is IngredientDetailEvent.PackageAdded -> showAddPackageDialog = false
                is IngredientDetailEvent.PackageUpdated -> packageToEdit = null
                is IngredientDetailEvent.OptionArchived -> optionToArchive = null
                is IngredientDetailEvent.CountDefaultChanged,
                is IngredientDetailEvent.PurchaseDefaultChanged -> {
                    // Defaults changed success - no dialog to close typically or already handled
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

    IngredientDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        showArchiveConfirm = showArchiveConfirm,
        showAddStandardDialog = showAddStandardDialog,
        showAddPackageDialog = showAddPackageDialog,
        packageToEdit = packageToEdit,
        optionToArchive = optionToArchive,
        onSetShowArchiveConfirm = { showArchiveConfirm = it },
        onSetShowAddStandardDialog = { showAddStandardDialog = it },
        onSetShowAddPackageDialog = { showAddPackageDialog = it },
        onSetPackageToEdit = { packageToEdit = it },
        onSetOptionToArchive = { optionToArchive = it },
        onBack = onBack,
        onEditClick = { onEditClick(ingredientId) },
        onViewActivity = { onViewActivity(ingredientId) },
        onViewPriceHistory = { onViewPriceHistory(ingredientId) },
        onArchiveIngredient = viewModel::onArchiveIngredient,
        onSetDefaultCount = viewModel::onSetDefaultCount,
        onSetDefaultPurchase = viewModel::onSetDefaultPurchase,
        onArchiveOption = viewModel::onArchiveOption,
        onAddStandardOption = { unitId -> 
            viewModel.onAddStandardOption(AddStandardUnitOptionCommand(ingredientId, unitId))
        },
        onAddPackageOption = { name, qty ->
            viewModel.onAddPackageOption(AddPackageUnitOptionCommand(ingredientId, name, qty))
        },
        onUpdatePackageOption = { optionId, name, qty ->
            viewModel.onUpdatePackageOption(UpdatePackageUnitOptionCommand(optionId, name, qty))
        },
        getStandardPreview = viewModel::getStandardPreview
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailScreen(
    uiState: IngredientDetailUiState,
    snackbarHostState: SnackbarHostState,
    showArchiveConfirm: Boolean,
    showAddStandardDialog: Boolean,
    showAddPackageDialog: Boolean,
    packageToEdit: IngredientUnitOption?,
    optionToArchive: IngredientUnitOption?,
    onSetShowArchiveConfirm: (Boolean) -> Unit,
    onSetShowAddStandardDialog: (Boolean) -> Unit,
    onSetShowAddPackageDialog: (Boolean) -> Unit,
    onSetPackageToEdit: (IngredientUnitOption?) -> Unit,
    onSetOptionToArchive: (IngredientUnitOption?) -> Unit,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onViewActivity: () -> Unit,
    onViewPriceHistory: () -> Unit = {},
    onArchiveIngredient: () -> Unit,
    onSetDefaultCount: (IngredientUnitOptionId) -> Unit,
    onSetDefaultPurchase: (IngredientUnitOptionId) -> Unit,
    onArchiveOption: (IngredientUnitOptionId) -> Unit,
    onAddStandardOption: (UnitId) -> Unit,
    onAddPackageOption: (String, BigDecimal) -> Unit,
    onUpdatePackageOption: (IngredientUnitOptionId, String, BigDecimal) -> Unit,
    getStandardPreview: (UnitOfMeasure) -> UnitConversionChoiceUiModel?
) {
    Scaffold(
        modifier = Modifier.testTag("ingredient_detail_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.ingredient?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ingredient_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.ingredient?.isActive == true) {
                        IconButton(onClick = onEditClick, modifier = Modifier.testTag("ingredient_edit_button")) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { onSetShowArchiveConfirm(true) }) {
                            Icon(Icons.Default.Archive, contentDescription = stringResource(R.string.archive_ingredient))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.ingredient == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_ingredient_not_found))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                val ingredient = uiState.ingredient
                val baseSymbol = uiState.baseUnit?.symbol ?: ingredient.baseUnitId.value

                Text(
                    text = if (ingredient.isActive) stringResource(R.string.active) else stringResource(R.string.archived_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ingredient.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ingredient_status")
                )
                
                Text(
                    text = uiState.category?.name ?: stringResource(R.string.uncategorized),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Button(
                    onClick = onViewActivity,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("ingredient_view_activity")
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ingredient_view_activity))
                }

                Button(
                    onClick = onViewPriceHistory,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("ingredient_price_history")
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.price_history_title))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.unit_options),
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (ingredient.isActive) {
                        Row {
                            TextButton(onClick = { onSetShowAddStandardDialog(true) }) { Text(stringResource(R.string.standard_unit)) }
                            TextButton(onClick = { onSetShowAddPackageDialog(true) }) { Text(stringResource(R.string.package_option)) }
                        }
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f).testTag("ingredient_unit_options_list")) {
                    items(uiState.options) { option ->
                        UnitOptionItem(
                            option = option,
                            baseSymbol = baseSymbol,
                            isIngredientActive = ingredient.isActive,
                            onSetDefaultCount = { onSetDefaultCount(option.id) },
                            onSetDefaultPurchase = { onSetDefaultPurchase(option.id) },
                            onEditPackage = { onSetPackageToEdit(option) },
                            onArchive = { onSetOptionToArchive(option) },
                            modifier = Modifier.testTag("ingredient_detail_option_${option.id.value}")
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showArchiveConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.archive_ingredient),
            message = stringResource(R.string.archive_ingredient_confirmation, uiState.ingredient?.name ?: ""),
            isSaving = uiState.isPerformingAction,
            onDismiss = { onSetShowArchiveConfirm(false) },
            onConfirm = onArchiveIngredient
        )
    }

    if (showAddStandardDialog) {
        StandardUnitDialog(
            units = uiState.compatibleUnits,
            excludedUnitIds = uiState.options.mapNotNull { it.standardUnitId }.toSet() + (uiState.ingredient?.baseUnitId?.let { setOf(it) } ?: emptySet()),
            isSaving = uiState.isPerformingAction,
            onDismiss = { onSetShowAddStandardDialog(false) },
            getPreview = getStandardPreview,
            onSelect = { onAddStandardOption(it.id) }
        )
    }

    if (showAddPackageDialog) {
        AddPackageDialog(
            isSaving = uiState.isPerformingAction,
            onDismiss = { onSetShowAddPackageDialog(false) },
            onConfirm = onAddPackageOption
        )
    }

    packageToEdit?.let { option ->
        AddPackageDialog(
            initialName = option.displayName,
            initialQty = option.factorToBase,
            isSaving = uiState.isPerformingAction,
            onDismiss = { onSetPackageToEdit(null) },
            onConfirm = { name, qty -> onUpdatePackageOption(option.id, name, qty) }
        )
    }

    optionToArchive?.let { option ->
        ArchiveConfirmDialog(
            title = stringResource(R.string.action_archive),
            message = stringResource(R.string.archive_item, option.displayName),
            isSaving = uiState.isPerformingAction,
            onDismiss = { onSetOptionToArchive(null) },
            onConfirm = { onArchiveOption(option.id) }
        )
    }
}

@Composable
fun UnitOptionItem(
    option: IngredientUnitOption,
    baseSymbol: String,
    isIngredientActive: Boolean,
    onSetDefaultCount: () -> Unit,
    onSetDefaultPurchase: () -> Unit,
    onEditPackage: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier,
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.displayName,
                    color = if (option.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ingredient_option_name_${option.id.value}")
                )
                if (option.isBase) {
                    Text(
                        text = " (${stringResource(R.string.base_label)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                if (!option.isActive) {
                    Text(
                        text = " (${stringResource(R.string.archived_label)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        supportingContent = {
            Column {
                val factorStr = if (option.isBase) "1" else option.factorToBase.stripTrailingZeros().toPlainString()
                Text(
                    text = stringResource(R.string.unit_conversion_format, option.shortLabel, factorStr, baseSymbol),
                    modifier = Modifier.testTag("ingredient_option_factor_${option.id.value}")
                )
                
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    if (option.isDefaultCount) {
                        Icon(
                            Icons.Default.Straighten, 
                            contentDescription = stringResource(R.string.desc_is_default_count, option.displayName), 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (option.isDefaultPurchase) {
                        Icon(
                            Icons.Default.ShoppingCart, 
                            contentDescription = stringResource(R.string.desc_is_default_purchase, option.displayName), 
                            modifier = Modifier.size(16.dp).padding(start = 8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (isIngredientActive && !option.isBase && option.isActive) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.desc_option_actions, option.displayName))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (!option.isDefaultCount) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.set_default_count)) },
                                onClick = {
                                    onSetDefaultCount()
                                    menuExpanded = false
                                }
                            )
                        }
                        if (!option.isDefaultPurchase) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.set_default_purchase)) },
                                onClick = {
                                    onSetDefaultPurchase()
                                    menuExpanded = false
                                }
                            )
                        }
                        if (option.standardUnitId == null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = {
                                    onEditPackage()
                                    menuExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_archive)) },
                            onClick = {
                                onArchive()
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}
