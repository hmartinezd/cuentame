package com.miara.cuentame.feature.counts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailScreenState
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailUiState
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailViewModel
import com.miara.cuentame.feature.counts.viewmodel.StockCountReviewLine
import com.miara.cuentame.feature.ingredients.ui.ArchiveConfirmDialog
import java.math.BigDecimal
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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showVoidConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StockCountDetailEvent.Deleted -> {
                    showDeleteConfirm = false
                    onBack()
                }
                is StockCountDetailEvent.Completed -> {
                    // Reactive update will handle UI
                }
                is StockCountDetailEvent.Voided -> {
                    showVoidConfirm = false
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

    StockCountDetailScreen(
        uiState = uiState,
        showDeleteConfirm = showDeleteConfirm,
        showVoidConfirm = showVoidConfirm,
        onShowDeleteConfirm = { showDeleteConfirm = it },
        onShowVoidConfirm = { showVoidConfirm = it },
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAreaClick = onAreaClick,
        onToggleReview = viewModel::onToggleReview,
        onCompleteCount = viewModel::onComplete,
        onVoidCount = viewModel::onVoid,
        onDeleteDraft = viewModel::onDelete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCountDetailScreen(
    uiState: StockCountDetailUiState,
    showDeleteConfirm: Boolean,
    showVoidConfirm: Boolean,
    onShowDeleteConfirm: (Boolean) -> Unit,
    onShowVoidConfirm: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAreaClick: (StockCountAreaId) -> Unit,
    onToggleReview: (Boolean) -> Unit,
    onCompleteCount: () -> Unit,
    onVoidCount: () -> Unit,
    onDeleteDraft: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.count_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("count_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.details?.count?.status == StockCountStatus.DRAFT) {
                        IconButton(onClick = { onShowDeleteConfirm(true) }, enabled = !uiState.isDeleting) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_draft))
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState.screenState) {
            is StockCountDetailScreenState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is StockCountDetailScreenState.InvalidRoute -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_invalid_count_route))
                }
            }
            is StockCountDetailScreenState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_count_not_found))
                }
            }
            is StockCountDetailScreenState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(state.throwable.toUserMessageRes()))
                }
            }
            is StockCountDetailScreenState.Ready -> {
                val details = uiState.details!!
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
                                onClick = { onAreaClick(areaDetail.area.id) }
                            )
                            HorizontalDivider()
                        }
                    }

                    if (count.status == StockCountStatus.DRAFT) {
                        val allAreasCompleted = details.areas.isNotEmpty() && details.areas.all { it.area.status == CountAreaStatus.COMPLETED }
                        Button(
                            onClick = { onToggleReview(true) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            enabled = !uiState.isCompleting && allAreasCompleted
                        ) {
                            Text(stringResource(R.string.complete_count))
                        }
                    } else if (count.status == StockCountStatus.COMPLETED) {
                        Button(
                            onClick = { onShowVoidConfirm(true) },
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
    }

    if (showDeleteConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.delete_draft),
            message = stringResource(R.string.delete_draft_desc),
            isSaving = uiState.isDeleting,
            onDismiss = { if (!uiState.isDeleting) onShowDeleteConfirm(false) },
            onConfirm = onDeleteDraft
        )
    }

    if (showVoidConfirm) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.void_count),
            message = stringResource(R.string.void_count_desc),
            confirmText = stringResource(R.string.action_confirm),
            isSaving = uiState.isVoiding,
            onDismiss = { if (!uiState.isVoiding) onShowVoidConfirm(false) },
            onConfirm = onVoidCount
        )
    }

    if (uiState.showReview) {
        AdjustmentReviewSheet(
            lines = uiState.reviewLines,
            currencyCode = uiState.currencyCode,
            isCompleting = uiState.isCompleting,
            isLoading = uiState.isReviewLoading,
            onDismiss = { if (!uiState.isCompleting) onToggleReview(false) },
            onConfirm = onCompleteCount
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustmentReviewSheet(
    lines: List<StockCountReviewLine>,
    currencyCode: String,
    isCompleting: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.count_review_title), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onDismiss, enabled = !isCompleting) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
            
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(text = stringResource(R.string.review_loading), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(lines) { line ->
                        ReviewLineItem(line, currencyCode)
                        HorizontalDivider()
                    }
                }
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = !isCompleting && lines.isNotEmpty()
            ) {
                if (isCompleting) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(R.string.complete_count))
            }
        }
    }
}

@Composable
fun ReviewLineItem(line: StockCountReviewLine, currencyCode: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = line.ingredientName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = line.areaName, style = MaterialTheme.typography.labelSmall)
            }
            Text(text = "${line.quantityEntered} ${line.unitName}", style = MaterialTheme.typography.bodyLarge)
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (line.preview.willCreateOpeningBalance) 
                    stringResource(R.string.opening_balance) 
                else 
                    stringResource(R.string.expected_quantity_format, line.preview.expectedQuantityBase?.toPlainString() ?: "0", line.baseUnitName),
                style = MaterialTheme.typography.labelSmall
            )
            
            val adjustment = line.preview.provisionalAdjustmentBase
            val color = when {
                adjustment > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
                adjustment < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
            Text(
                text = stringResource(R.string.adjustment_format, (if (adjustment > BigDecimal.ZERO) "+" else "") + adjustment.toPlainString(), line.baseUnitName),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }

        if (line.preview.estimatedValueChange != null) {
            Text(
                text = stringResource(R.string.value_change_format, line.preview.estimatedValueChange.toPlainString(), currencyCode),
                style = MaterialTheme.typography.labelSmall,
                color = if (line.preview.estimatedValueChange > BigDecimal.ZERO) MaterialTheme.colorScheme.primary else if (line.preview.estimatedValueChange < BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun CountAreaItem(
    areaDetail: StockCountAreaDetails,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(areaDetail.areaName, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Text(text = stringResource(R.string.items_counted, areaDetail.lines.size))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AreaStatusChip(status = areaDetail.area.status)
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    )
}
