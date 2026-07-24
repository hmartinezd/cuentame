package com.miara.cuentame.feature.waste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.domain.validation.toUserMessageRes
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.feature.counts.ui.StatusChip
import com.miara.cuentame.feature.ingredients.ui.ArchiveConfirmDialog
import com.miara.cuentame.feature.waste.viewmodel.WasteDetailEvent
import com.miara.cuentame.feature.waste.viewmodel.WasteDetailScreenState
import com.miara.cuentame.feature.waste.viewmodel.WasteDetailUiState
import com.miara.cuentame.feature.waste.viewmodel.WasteDetailViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WasteDetailRoute(
    onBack: () -> Unit,
    onEdit: (WasteEventId) -> Unit,
    viewModel: WasteDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WasteDetailEvent.Deleted -> onBack()
                is WasteDetailEvent.Posted -> {
                    // Reactive update will handle UI, but we can show success snackbar if needed
                }
                is WasteDetailEvent.Voided -> {
                    // Reactive update will handle UI
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

    WasteDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEdit = { 
            val state = uiState.screenState
            if (state is WasteDetailScreenState.Ready) {
                onEdit(state.details.event.id)
            }
        },
        onDelete = viewModel::onDelete,
        onPost = viewModel::onPost,
        onVoid = viewModel::onVoid
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteDetailScreen(
    uiState: WasteDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPost: () -> Unit,
    onVoid: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPostConfirm by remember { mutableStateOf(false) }
    var showVoidConfirm by remember { mutableStateOf(false) }

    val readyState = uiState.screenState as? WasteDetailScreenState.Ready
    val event = readyState?.details?.event
    
    // Close dialogs on success (when status changes)
    LaunchedEffect(event?.status) {
        if (event?.status == DocumentStatus.POSTED) showPostConfirm = false
        if (event?.status == DocumentStatus.VOIDED) showVoidConfirm = false
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.waste_event)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("waste_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (event?.status == DocumentStatus.DRAFT) {
                        IconButton(onClick = onEdit, enabled = !uiState.isDeleting) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = !uiState.isDeleting) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_waste_draft))
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState.screenState) {
            is WasteDetailScreenState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is WasteDetailScreenState.SetupRequired -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_no_restaurant))
                }
            }
            is WasteDetailScreenState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_waste_not_found), modifier = Modifier.testTag("not_found_text"))
                }
            }
            is WasteDetailScreenState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_generic), modifier = Modifier.testTag("invalid_route_text"))
                }
            }
            is WasteDetailScreenState.OwnershipMismatch -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_waste_ownership), modifier = Modifier.testTag("ownership_mismatch_text"))
                }
            }
            is WasteDetailScreenState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(state.throwable.toUserMessageRes()))
                }
            }
            is WasteDetailScreenState.Ready -> {
                val details = state.details
                val e = details.event
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("waste_detail_screen"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            val ingredientLabel = details.ingredientName ?: stringResource(R.string.error_ingredient_not_found)
                            val finalIngredientLabel = if (details.isIngredientActive) ingredientLabel else "$ingredientLabel (${stringResource(R.string.archived_label)})"
                            Text(text = finalIngredientLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            
                            val areaLabel = details.areaName ?: stringResource(R.string.unknown_area)
                            val finalAreaLabel = if (details.isAreaActive) areaLabel else "$areaLabel (${stringResource(R.string.archived_label)})"
                            Text(text = finalAreaLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                        StatusChip(status = e.status)
                    }

                    HorizontalDivider()

                    val unitLabel = details.unitLabel ?: ""
                    val finalUnitLabel = if (details.isUnitActive) unitLabel else "$unitLabel (${stringResource(R.string.archived_label)})"
                    DetailItem(label = stringResource(R.string.quantity_wasted), value = "${e.quantityEntered} $finalUnitLabel")
                    DetailItem(label = stringResource(R.string.base_unit), value = "${e.quantityBase} ${details.baseUnitSymbol ?: ""}")
                    DetailItem(label = stringResource(R.string.waste_reason), value = stringResource(e.reason.toLabelRes()))
                    DetailItem(label = stringResource(R.string.effective_date), value = dateFormatter.format(e.effectiveAt))

                    if (e.status == DocumentStatus.DRAFT) {
                        HorizontalDivider()
                        if (details.currentAreaQuantityBase != null) {
                            DetailItem(label = stringResource(R.string.current_balance), value = "${details.currentAreaQuantityBase} ${details.baseUnitSymbol ?: ""}")
                        }
                        if (details.remainingAreaQuantityBase != null) {
                            DetailItem(
                                label = stringResource(R.string.remaining_balance),
                                value = "${details.remainingAreaQuantityBase} ${details.baseUnitSymbol ?: ""}",
                                valueColor = if (details.createsNegativeBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (details.createsNegativeBalance) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Text(text = stringResource(R.string.negative_inventory_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (e.postedAt != null) {
                        DetailItem(label = stringResource(R.string.posted_label), value = dateFormatter.format(e.postedAt))
                    }
                    if (e.voidedAt != null) {
                        DetailItem(label = stringResource(R.string.voided_label), value = dateFormatter.format(e.voidedAt))
                    }

                    if (!e.notes.isNullOrBlank()) {
                        DetailItem(label = stringResource(R.string.notes), value = e.notes)
                    }

                    if (e.attachmentPath != null) {
                        Text(text = stringResource(R.string.add_photo), style = MaterialTheme.typography.titleMedium)
                        AsyncImage(
                            model = e.attachmentPath,
                            contentDescription = stringResource(R.string.add_photo),
                            modifier = Modifier.fillMaxWidth().size(200.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (details.averageCostBase != null) {
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = stringResource(R.string.estimated_waste_value), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = Formatters.formatCurrency(details.estimatedValue ?: java.math.BigDecimal.ZERO, uiState.currencyCode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "${stringResource(R.string.cost_per_base_unit, details.baseUnitSymbol ?: "")}: ${Formatters.formatCurrency(details.averageCostBase, uiState.currencyCode)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (e.status != DocumentStatus.DRAFT) {
                        Text(text = stringResource(R.string.unknown_cost), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }

                    if (e.status == DocumentStatus.DRAFT) {
                        Button(
                            onClick = { showPostConfirm = true },
                            modifier = Modifier.fillMaxWidth().testTag("waste_post_button"),
                            enabled = !uiState.isPosting
                        ) {
                            if (uiState.isPosting) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.post_waste))
                        }
                    } else if (e.status == DocumentStatus.POSTED) {
                        Button(
                            onClick = { showVoidConfirm = true },
                            modifier = Modifier.fillMaxWidth().testTag("waste_void_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !uiState.isVoiding
                        ) {
                            if (uiState.isVoiding) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.void_waste))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.delete_waste_draft),
            message = stringResource(R.string.waste_confirm_delete),
            isSaving = uiState.isDeleting,
            onDismiss = { if (!uiState.isDeleting) showDeleteConfirm = false },
            onConfirm = onDelete
        )
    }

    if (showPostConfirm && readyState != null) {
        val details = readyState.details
        val message = buildString {
            append(stringResource(R.string.waste_confirm_post))
            append("\n\n")
            append(stringResource(R.string.quantity_wasted))
            append(": ${details.event.quantityEntered} ${details.unitLabel ?: ""}\n")
            if (details.currentAreaQuantityBase != null) {
                append(stringResource(R.string.current_balance))
                append(": ${details.currentAreaQuantityBase} ${details.baseUnitSymbol ?: ""}\n")
            }
            if (details.remainingAreaQuantityBase != null) {
                append(stringResource(R.string.remaining_balance))
                append(": ${details.remainingAreaQuantityBase} ${details.baseUnitSymbol ?: ""}\n")
            }
            if (details.estimatedValue != null) {
                append(stringResource(R.string.estimated_waste_value))
                append(": ${Formatters.formatCurrency(details.estimatedValue, uiState.currencyCode)}\n")
            }
            if (details.createsNegativeBalance) {
                append("\n⚠️ ")
                append(stringResource(R.string.negative_inventory_warning))
            }
        }
        ArchiveConfirmDialog(
            title = stringResource(R.string.post_waste),
            message = message,
            confirmText = stringResource(R.string.action_confirm),
            isSaving = uiState.isPosting,
            onDismiss = { if (!uiState.isPosting) showPostConfirm = false },
            onConfirm = onPost
        )
    }

    if (showVoidConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.void_waste),
            message = stringResource(R.string.waste_confirm_void),
            confirmText = stringResource(R.string.action_confirm),
            isSaving = uiState.isVoiding,
            onDismiss = { if (!uiState.isVoiding) showVoidConfirm = false },
            onConfirm = onVoid
        )
    }
}

@Composable
fun DetailItem(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}
