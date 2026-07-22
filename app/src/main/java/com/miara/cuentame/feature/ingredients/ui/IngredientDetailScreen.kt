package com.miara.cuentame.feature.ingredients.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.feature.ingredients.viewmodel.IngredientDetailEvent
import com.miara.cuentame.feature.ingredients.viewmodel.IngredientDetailUiState
import com.miara.cuentame.feature.ingredients.viewmodel.IngredientDetailViewModel

@Composable
fun IngredientDetailRoute(
    ingredientId: IngredientId,
    onEditClick: (IngredientId) -> Unit,
    onBack: () -> Unit,
    viewModel: IngredientDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is IngredientDetailEvent.ArchiveSuccess -> onBack()
            }
        }
    }

    IngredientDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onEditClick = { onEditClick(ingredientId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailScreen(
    uiState: IngredientDetailUiState,
    onBack: () -> Unit,
    onEditClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.ingredient?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.unit_options),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyColumn {
                    items(uiState.options) { option ->
                        ListItem(
                            headlineContent = { Text(option.displayName) },
                            supportingContent = {
                                Text("Factor: ${option.factorToBase}")
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
