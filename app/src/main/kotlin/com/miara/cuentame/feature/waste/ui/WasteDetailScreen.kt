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
                    // Reactive update will handle UI
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
        onEdit = { uiState.details?.event?.id?.let { onEdit(it) } },
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

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.waste_event)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.screenState == WasteDetailScreenState.Ready && uiState.details?.event?.status == DocumentStatus.DRAFT) {
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
            is WasteDetailScreenState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_waste_not_found))
                }
            }
            is WasteDetailScreenState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_generic))
                }
            }
            is WasteDetailScreenState.OwnershipMismatch -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_waste_ownership))
                }
            }
            is WasteDetailScreenState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(state.throwable.toUserMessageRes()))
                }
            }
            is WasteDetailScreenState.Ready -> {
                val details = uiState.details!!
                val event = details.event
                
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
                            Text(text = details.ingredientName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(text = details.areaName ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                        StatusChip(status = event.status)
                    }

                    HorizontalDivider()

                    DetailItem(label = stringResource(R.string.quantity_wasted), value = "${event.quantityEntered} ${details.unitLabel}")
                    DetailItem(label = stringResource(R.string.base_unit), value = "${event.quantityBase} ${details.baseUnitSymbol ?: ""}")
                    DetailItem(label = stringResource(R.string.waste_reason), value = stringResource(event.reason.toLabelRes()))
                    DetailItem(label = stringResource(R.string.effective_date), value = dateFormatter.format(event.effectiveAt))

                    if (event.postedAt != null) {
                        DetailItem(label = stringResource(R.string.posted_label), value = dateFormatter.format(event.postedAt))
                    }
                    if (event.voidedAt != null) {
                        DetailItem(label = stringResource(R.string.voided_label), value = dateFormatter.format(event.voidedAt))
                    }

                    if (!event.notes.isNullOrBlank()) {
                        DetailItem(label = stringResource(R.string.notes), value = event.notes)
                    }

                    if (event.attachmentPath != null) {
                        Text(text = stringResource(R.string.add_photo), style = MaterialTheme.typography.titleMedium)
                        AsyncImage(
                            model = event.attachmentPath,
                            contentDescription = "Waste photo",
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
                    } else if (event.status != DocumentStatus.DRAFT) {
                        Text(text = stringResource(R.string.unknown_cost), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }

                    if (event.status == DocumentStatus.DRAFT) {
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
                    } else if (event.status == DocumentStatus.POSTED) {
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

    if (showPostConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.post_waste),
            message = stringResource(R.string.waste_confirm_post),
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
fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
