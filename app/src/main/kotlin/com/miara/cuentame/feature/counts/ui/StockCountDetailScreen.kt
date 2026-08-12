package com.miara.cuentame.feature.counts.ui

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
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TextButton
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
import com.miara.cuentame.core.common.util.ShareHelper
import com.miara.cuentame.core.domain.repository.StockCountAreaDetails
import com.miara.cuentame.core.presentation.validation.toUserMessageRes
import com.miara.cuentame.core.presentation.ui.AreaStatusChip
import com.miara.cuentame.core.presentation.ui.StatusChip
import com.miara.cuentame.core.model.inventory.CountAreaStatus
import com.miara.cuentame.core.model.inventory.StockCountStatus
import com.miara.cuentame.feature.counts.viewmodel.ReviewWarning
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailEvent
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailScreenState
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailUiState
import com.miara.cuentame.feature.counts.viewmodel.StockCountDetailViewModel
import com.miara.cuentame.feature.counts.viewmodel.StockCountReviewLine
import com.miara.cuentame.feature.counts.viewmodel.StockCountDriftItemUi
import com.miara.cuentame.core.presentation.ui.ArchiveConfirmDialog
import java.math.BigDecimal
import com.miara.cuentame.core.domain.service.hasLargeCountVariance
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
    val exportTitle = stringResource(R.string.export_count)

    LaunchedEffect(Unit) {
        viewModel.exportFlow.collect { csv ->
            ShareHelper.shareCsv(context, "count_export.csv", csv, exportTitle)
        }
    }

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
        onReconfirm = viewModel::onReconfirm,
        onRecount = { areaId ->
            viewModel.onToggleReview(false)
            onAreaClick(areaId)
        },
        onVoidCount = viewModel::onVoid,
        onDeleteDraft = viewModel::onDelete,
        onExport = viewModel::onExportRequested
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
    onReconfirm: (com.miara.cuentame.core.common.ids.StockCountLineId) -> Unit,
    onRecount: (StockCountAreaId) -> Unit,
    onVoidCount: () -> Unit,
    onDeleteDraft: () -> Unit,
    onExport: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.count_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("stock_count_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.screenState == StockCountDetailScreenState.Ready && uiState.details != null) {
                        if (uiState.details.count.status == StockCountStatus.COMPLETED || uiState.details.count.status == StockCountStatus.VOIDED) {
                            IconButton(onClick = onExport, modifier = Modifier.testTag("count_export_button")) {
                                Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.export_csv))
                            }
                        }
                        
                        if (uiState.details.count.status == StockCountStatus.DRAFT) {
                            IconButton(onClick = { onShowDeleteConfirm(true) }, enabled = !uiState.isDeleting, modifier = Modifier.testTag("delete_draft_button")) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_draft))
                            }
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
            is StockCountDetailScreenState.OwnershipMismatch -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_count_ownership_mismatch))
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
                            Text(
                                text = stringResource(
                                    R.string.count_progress_format,
                                    uiState.areaProgress.sumOf { it.countedItemCount },
                                    uiState.areaProgress.sumOf { it.totalCountableItemCount }
                                ),
                                modifier = Modifier.testTag("overall_count_progress")
                            )
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
                                countedItemCount = uiState.areaProgress.find { it.countAreaId == areaDetail.area.id }?.countedItemCount ?: areaDetail.lines.size,
                                totalCountableItemCount = uiState.areaProgress.find { it.countAreaId == areaDetail.area.id }?.totalCountableItemCount ?: areaDetail.lines.size,
                                onClick = { onAreaClick(areaDetail.area.id) }
                            )
                            HorizontalDivider()
                        }
                    }

                    if (count.status == StockCountStatus.DRAFT) {
                        val allAreasCompleted = details.areas.isNotEmpty() && details.areas.all { it.area.status == CountAreaStatus.COMPLETED }
                        Button(
                            onClick = { onToggleReview(true) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("complete_count_button"),
                            enabled = !uiState.isCompleting && allAreasCompleted
                        ) {
                            Text(stringResource(R.string.complete_count))
                        }
                    } else if (count.status == StockCountStatus.COMPLETED) {
                        Button(
                            onClick = { onToggleReview(true) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("count_summary_button")
                        ) {
                            Text(stringResource(R.string.count_summary))
                        }
                        Button(
                            onClick = { onShowVoidConfirm(true) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("void_count_button"),
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
            missingWarnings = uiState.missingWarnings,
            archivedWarnings = uiState.archivedWarnings,
            driftItems = uiState.driftItems,
            currencyCode = uiState.currencyCode,
            isCompleting = uiState.isCompleting,
            isLoading = uiState.isReviewLoading,
            allowPosting = uiState.details?.count?.status == StockCountStatus.DRAFT,
            onDismiss = { if (!uiState.isCompleting) onToggleReview(false) },
            onConfirm = onCompleteCount,
            onReconfirm = onReconfirm,
            onRecount = onRecount
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustmentReviewSheet(
    lines: List<StockCountReviewLine>,
    missingWarnings: List<ReviewWarning>,
    archivedWarnings: List<ReviewWarning>,
    driftItems: List<StockCountDriftItemUi>,
    currencyCode: String,
    isCompleting: Boolean,
    isLoading: Boolean,
    allowPosting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReconfirm: (com.miara.cuentame.core.common.ids.StockCountLineId) -> Unit,
    onRecount: (StockCountAreaId) -> Unit
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
                val increased = lines.count { it.preview.provisionalAdjustmentBase > BigDecimal.ZERO }
                val decreased = lines.count { it.preview.provisionalAdjustmentBase < BigDecimal.ZERO }
                val unchanged = lines.count { it.preview.provisionalAdjustmentBase.compareTo(BigDecimal.ZERO) == 0 }
                val opening = lines.count { it.preview.willCreateOpeningBalance }
                Text(
                    text = stringResource(R.string.count_summary_totals, lines.size, increased + decreased, increased, decreased, unchanged, opening),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (driftItems.isNotEmpty()) {
                        item { Text(stringResource(R.string.inventory_changed_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium) }
                        items(driftItems) { drift ->
                            ListItem(
                                headlineContent = { Text(drift.ingredientName, fontWeight = FontWeight.Bold) },
                                supportingContent = {
                                    Column {
                                        Text(drift.areaName ?: stringResource(R.string.unknown_area))
                                        Text(stringResource(R.string.expected_when_counted_format, drift.expectedWhenCounted?.toPlainString() ?: "—"))
                                        Text(stringResource(R.string.current_inventory_format, drift.currentInventory?.toPlainString() ?: "—"))
                                        Text(stringResource(R.string.inventory_changed_after_item))
                                    }
                                },
                                trailingContent = {
                                    Column {
                                        TextButton(onClick = { onReconfirm(drift.lineId) }) { Text(stringResource(R.string.reconfirm_count)) }
                                        TextButton(onClick = { onRecount(drift.countAreaId) }) { Text(stringResource(R.string.reopen_and_recount)) }
                                    }
                                }
                            )
                        }
                    }
                    if (missingWarnings.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.missing_items),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(missingWarnings) { warning ->
                            ReviewWarningItem(warning)
                            HorizontalDivider()
                        }
                    }

                    if (archivedWarnings.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.archived_nonzero_warning),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(archivedWarnings) { warning ->
                            ReviewWarningItem(warning)
                            HorizontalDivider()
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.counted_item),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(lines) { line ->
                        ReviewLineItem(line, currencyCode)
                        HorizontalDivider()
                    }
                }
            }

            if (allowPosting) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("confirm_completion_button"),
                    enabled = !isCompleting && lines.isNotEmpty() && driftItems.isEmpty()
                ) {
                    if (isCompleting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.complete_count))
                }
            }
        }
    }
}

@Composable
fun ReviewWarningItem(warning: ReviewWarning) {
    ListItem(
        headlineContent = { Text(warning.name, fontWeight = FontWeight.Bold) },
        supportingContent = { 
            Column {
                Text(warning.areaName ?: stringResource(R.string.unknown_area))
                Text(stringResource(R.string.expected_quantity_format, warning.expectedBalanceBase.toPlainString(), warning.baseUnitName ?: stringResource(R.string.unknown_unit)))
            }
        },
        leadingContent = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
    )
}

@Composable
fun ReviewLineItem(line: StockCountReviewLine, currencyCode: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp).testTag("review_line_${line.areaName}_${line.ingredientId}")) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = line.ingredientName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = line.areaName ?: stringResource(R.string.unknown_area), style = MaterialTheme.typography.labelSmall)
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
                    stringResource(R.string.expected_quantity_format, line.preview.expectedQuantityBase?.toPlainString() ?: "0", line.baseUnitName ?: stringResource(R.string.unknown_unit)),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("historical_expected_${line.areaId}_${line.ingredientId}")
            )
            
            val adjustment = line.preview.provisionalAdjustmentBase
            val color = when {
                adjustment > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
                adjustment < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
            Text(
                text = stringResource(R.string.adjustment_format, (if (adjustment > BigDecimal.ZERO) "+" else "") + adjustment.toPlainString(), line.baseUnitName ?: stringResource(R.string.unknown_unit)),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.testTag("historical_adjustment_${line.areaId}_${line.ingredientId}")
            )
        }

        if (line.preview.estimatedValueChange != null) {
            Text(
                text = stringResource(R.string.value_change_format, line.preview.estimatedValueChange.toPlainString(), currencyCode),
                style = MaterialTheme.typography.labelSmall,
                color = if (line.preview.estimatedValueChange > BigDecimal.ZERO) MaterialTheme.colorScheme.primary else if (line.preview.estimatedValueChange < BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )
        }
        if (hasLargeCountVariance(line.preview.expectedQuantityBase, line.preview.countedQuantityBase)) {
            Text(
                text = stringResource(R.string.count_large_variance_message),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun CountAreaItem(
    areaDetail: StockCountAreaDetails,
    countedItemCount: Int,
    totalCountableItemCount: Int,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).testTag("area_item_${areaDetail.area.id.value}"),
        headlineContent = { Text(areaDetail.areaName ?: stringResource(R.string.unknown_area), fontWeight = FontWeight.Bold) },
        supportingContent = {
            Text(text = stringResource(R.string.count_progress_format, countedItemCount, totalCountableItemCount))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AreaStatusChip(status = areaDetail.area.status)
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    )
}
