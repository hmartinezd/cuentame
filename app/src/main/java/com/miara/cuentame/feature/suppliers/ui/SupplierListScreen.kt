package com.miara.cuentame.feature.suppliers.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.model.supplier.Supplier
import com.miara.cuentame.feature.suppliers.viewmodel.SupplierListUiState
import com.miara.cuentame.feature.suppliers.viewmodel.SupplierListViewModel

@Composable
fun SupplierListRoute(
    onBack: () -> Unit,
    onAddSupplier: () -> Unit,
    onEditSupplier: (SupplierId) -> Unit,
    viewModel: SupplierListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    SupplierListScreen(
        uiState = uiState,
        onBack = onBack,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onShowArchivedToggled = viewModel::onShowArchivedToggled,
        onAddSupplier = onAddSupplier,
        onEditSupplier = onEditSupplier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierListScreen(
    uiState: SupplierListUiState,
    onBack: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onShowArchivedToggled: (Boolean) -> Unit,
    onAddSupplier: () -> Unit,
    onEditSupplier: (SupplierId) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.suppliers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSupplier) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_supplier))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text(stringResource(R.string.action_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.show_archived)) },
                trailingContent = {
                    Switch(checked = uiState.showArchived, onCheckedChange = onShowArchivedToggled)
                }
            )
            HorizontalDivider()

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.suppliers) { supplier ->
                        SupplierItem(
                            supplier = supplier,
                            onClick = { onEditSupplier(supplier.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun SupplierItem(
    supplier: Supplier,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                text = supplier.name,
                color = if (supplier.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        supportingContent = {
            Column {
                if (!supplier.phone.isNullOrBlank()) Text(supplier.phone)
                if (!supplier.isActive) {
                    Text(
                        text = stringResource(R.string.archived_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        tonalElevation = if (supplier.isActive) 0.dp else 2.dp
    )
}
