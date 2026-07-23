package com.miara.cuentame.feature.counts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.domain.repository.StockCountAreaDetails
import com.miara.cuentame.core.domain.validation.toUserMessageRes
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import com.miara.cuentame.core.model.inventory.StockCountStatus
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailEvent
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailUiState
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailViewModel
import com.miara.cuentame.feature.ingredients.ui.ArchiveConfirmDialog
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StockCountDetailRoute(
    onBack: () -> Unit,
    onAreaClick: (StockCountAreaId) -> Unit,
    viewModel: StockCountDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StockCountDetailEvent.Deleted -> onBack()
                is StockCountDetailEvent.Completed -> { /* Reactively updated */ }
                is StockCountDetailEvent.Voided -> { /* Reactively updated */ }
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    StockCountDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAreaClick = onAreaClick,
        onCompleteCount = viewModel::onComplete,
        onVoidCount = viewModel::onVoid,
        onDeleteDraft = viewModel::onDelete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCountDetailScreen(
    uiState: StockCountDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAreaClick: (StockCountAreaId) -> Unit,
    onCompleteCount: () -> Unit,
    onVoidCount: () -> Unit,
    onDeleteDraft: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showVoidConfirm by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.count_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.details?.count?.status == StockCountStatus.DRAFT) {
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = !uiState.isDeleting) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_draft))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.details == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_purchase_not_found)) // TODO: Specific error
            }
        } else {
            val details = uiState.details
            val count = details.count
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = count.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("count_detail_name"))
                        Text(text = dateFormatter.format(count.effectiveAt), style = MaterialTheme.typography.bodyMedium)
                    }
                    StatusChip(status = count.status)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(text = stringResource(R.string.count_by_area), style = MaterialTheme.typography.titleLarge)
                    }
                    items(details.areas) { areaDetail ->
                        CountAreaItem(
                            areaDetail = areaDetail,
                            onClick = { onAreaClick(areaDetail.area.id) },
                            enabled = count.status == StockCountStatus.DRAFT
                        )
                        HorizontalDivider()
                    }
                }

                if (count.status == StockCountStatus.DRAFT) {
                    val allAreasCompleted = details.areas.isNotEmpty() && details.areas.all { it.area.status == CountAreaStatus.COMPLETED }
                    Button(
                        onClick = { showCompleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        enabled = !uiState.isCompleting && allAreasCompleted
                    ) {
                        if (uiState.isCompleting) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                        }
                        Text(stringResource(R.string.complete_count))
                    }
                } else if (count.status == StockCountStatus.COMPLETED) {
                    Button(
                        onClick = { showVoidConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !uiState.isVoiding
                    ) {
                        if (uiState.isVoiding) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                        }
                        Text(stringResource(R.string.void_count))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.delete_draft),
            message = stringResource(R.string.delete_draft_desc),
            isSaving = uiState.isDeleting,
            onDismiss = { if (!uiState.isDeleting) showDeleteConfirm = false },
            onConfirm = onDeleteDraft
        )
    }

    if (showCompleteConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.complete_count),
            message = stringResource(R.string.complete_count_desc),
            confirmText = stringResource(R.string.action_confirm),
            isSaving = uiState.isCompleting,
            onDismiss = { if (!uiState.isCompleting) showCompleteConfirm = false },
            onConfirm = onCompleteCount
        )
    }

    if (showVoidConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.void_count),
            message = stringResource(R.string.void_count_desc),
            confirmText = stringResource(R.string.action_confirm),
            isSaving = uiState.isVoiding,
            onDismiss = { if (!uiState.isVoiding) showVoidConfirm = false },
            onConfirm = onVoidCount
        )
    }
}

@Composable
fun CountAreaItem(
    areaDetail: StockCountAreaDetails,
    onClick: () -> Unit,
    enabled: Boolean
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(areaDetail.areaName, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Text(text = "${areaDetail.lines.size} items counted") // TODO: Resource
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AreaStatusChip(status = areaDetail.area.status)
                if (enabled) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    )
}
