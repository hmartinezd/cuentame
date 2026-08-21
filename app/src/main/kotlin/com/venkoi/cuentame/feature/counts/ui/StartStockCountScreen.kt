package com.venkoi.cuentame.feature.counts.ui

import androidx.compose.foundation.clickable
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
import com.venkoi.cuentame.core.designsystem.component.adaptiveContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.StockCountId
import com.venkoi.cuentame.core.presentation.validation.toUserMessageRes
import com.venkoi.cuentame.feature.counts.viewmodel.StartStockCountEvent
import com.venkoi.cuentame.feature.counts.viewmodel.StartStockCountUiState
import com.venkoi.cuentame.feature.counts.viewmodel.StartStockCountViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StartStockCountRoute(
    onBack: () -> Unit,
    onCountStarted: (StockCountId) -> Unit,
    viewModel: StartStockCountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault()) }
    val defaultName = stringResource(R.string.count_default_name, dateFormatter.format(uiState.effectiveAt))

    LaunchedEffect(defaultName) {
        viewModel.onDefaultNameChanged(defaultName)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StartStockCountEvent.Success -> onCountStarted(event.countId)
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it.toUserMessageRes()))
            viewModel.clearError()
        }
    }

    StartStockCountScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNameChanged = viewModel::onNameChanged,
        onAreaToggle = viewModel::onAreaToggle,
        onNotesChanged = viewModel::onNotesChanged,
        onStartCount = viewModel::onStart
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartStockCountScreen(
    uiState: StartStockCountUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onAreaToggle: (InventoryAreaId) -> Unit,
    onNotesChanged: (String) -> Unit,
    onStartCount: () -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("stock_count_start_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_start_count)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("count_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .adaptiveContentWidth(760.dp)
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(R.string.count_name_label)) },
                    placeholder = { Text(stringResource(R.string.count_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth().testTag("count_name_input"),
                    enabled = !uiState.isStarting
                )

                Text(
                    text = stringResource(R.string.count_live_observation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(text = stringResource(R.string.onboarding_areas_title), style = MaterialTheme.typography.titleMedium)
                
                uiState.availableAreas.forEach { area ->
                    val isSelected = uiState.selectedAreaIds.contains(area.id)
                    val isInAnotherDraft = uiState.draftAreaUsage.contains(area.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("area_item_${area.id.value}")
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !uiState.isStarting && !isInAnotherDraft) { onAreaToggle(area.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onAreaToggle(area.id) },
                            enabled = !uiState.isStarting && !isInAnotherDraft,
                            modifier = Modifier.testTag("area_checkbox_${area.id.value}")
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = area.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isInAnotherDraft) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                            if (isInAnotherDraft) {
                                Text(
                                    text = stringResource(R.string.error_overlapping_area, area.name),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }

                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChanged,
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = !uiState.isStarting
                )

                Button(
                    onClick = onStartCount,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("start_count_button"),
                    enabled = !uiState.isStarting && uiState.selectedAreaIds.isNotEmpty()
                ) {
                    if (uiState.isStarting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.action_start_count))
                }
            }
        }
    }

}
