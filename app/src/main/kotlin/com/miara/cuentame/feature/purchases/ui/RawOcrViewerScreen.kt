package com.miara.cuentame.feature.purchases.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miara.cuentame.R
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrPage
import com.miara.cuentame.feature.purchases.viewmodel.RawOcrViewerUiState
import com.miara.cuentame.feature.purchases.viewmodel.RawOcrViewerViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawOcrViewerScreen(
    onBack: () -> Unit,
    viewModel: RawOcrViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_viewer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                OcrContent(uiState)
            }
        }
    }
}

@Composable
private fun OcrContent(uiState: RawOcrViewerUiState) {
    val result = uiState.result ?: return
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OcrHeader(result)
        }
        
        items(uiState.pages) { page ->
            OcrPageItem(page)
        }
    }
}

@Composable
private fun OcrHeader(result: com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ocr_viewer_metadata),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault())
            
            MetadataRow("Processed", formatter.format(result.processedAt))
            MetadataRow("Engine", result.engine)
            MetadataRow("Pages", result.pageCount.toString())
            MetadataRow("Schema", "V${result.evidenceSchemaVersion}")
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun OcrPageItem(page: PurchaseInvoiceOcrPage) {
    Column {
        Text(
            text = "Page ${page.pageIndex + 1} (${page.widthPx}x${page.heightPx} px)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
            tonalElevation = 1.dp
        ) {
            Text(
                text = page.text.ifBlank { "[No text detected]" },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}
