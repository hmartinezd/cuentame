package com.miara.cuentame.feature.ingredient.import.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.core.domain.repository.ImportResult
import com.miara.cuentame.feature.ingredient.import.domain.CsvIngredientImportRow
import com.miara.cuentame.feature.ingredient.import.domain.CsvImportIssueSeverity
import com.miara.cuentame.feature.ingredient.import.domain.CsvImportRowStatus
import com.miara.cuentame.feature.ingredient.import.domain.CsvParser

enum class CsvImportRowFilter {
    ALL, READY, WARNING, ERROR, SKIPPED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportRoute(
    onBack: () -> Unit,
    onViewIngredients: () -> Unit,
    viewModel: CsvImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    viewModel.loadCsv(stream)
                }
            }
        }
    )

    val templatePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    viewModel.generateTemplate(stream)
                }
            }
        }
    )

    var editingRow by remember { mutableStateOf<CsvIngredientImportRow?>(null) }

    CsvImportScreen(
        uiState = uiState,
        onBack = onBack,
        onChooseFile = { filePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) },
        onDownloadTemplate = { templatePicker.launch("cuentame_ingredient_template.csv") },
        onToggleSelection = viewModel::toggleRowSelection,
        onEditRow = { editingRow = it },
        onConfirm = viewModel::confirmImport,
        onDone = onViewIngredients,
        onResetResult = viewModel::resetImportResult
    )

    if (editingRow != null) {
        ImportRowEditDialog(
            row = editingRow!!,
            onDismiss = { editingRow = null },
            onSave = { updatedRow ->
                viewModel.updateRow(updatedRow)
                editingRow = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    uiState: CsvImportUiState,
    onBack: () -> Unit,
    onChooseFile: () -> Unit,
    onDownloadTemplate: () -> Unit,
    onToggleSelection: (Int) -> Unit,
    onEditRow: (CsvIngredientImportRow) -> Unit,
    onConfirm: () -> Unit,
    onDone: () -> Unit,
    onResetResult: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Ingredients") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.importResult != null -> {
                    ImportResultContent(
                        result = uiState.importResult,
                        onDone = onDone,
                        onDismiss = onResetResult
                    )
                }
                uiState.isParsing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.document == null -> {
                    EmptyState(
                        onChooseFile = onChooseFile,
                        onDownloadTemplate = onDownloadTemplate,
                        error = uiState.parseError
                    )
                }
                else -> {
                    ImportPreviewContent(
                        document = uiState.document,
                        isCommitting = uiState.isCommitting,
                        onToggleSelection = onToggleSelection,
                        onEditRow = onEditRow,
                        onConfirm = onConfirm
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(onChooseFile: () -> Unit, onDownloadTemplate: () -> Unit, error: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Import your ingredients from a CSV file", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onChooseFile) {
            Text("Choose File")
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onDownloadTemplate) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download Template")
        }
    }
}

@Composable
fun ImportPreviewContent(
    document: com.miara.cuentame.feature.ingredient.import.domain.CsvIngredientImportDocument,
    isCommitting: Boolean,
    onToggleSelection: (Int) -> Unit,
    onEditRow: (CsvIngredientImportRow) -> Unit,
    onConfirm: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(CsvImportRowFilter.ALL) }
    
    val filteredRows = remember(document.rows, selectedFilter) {
        when (selectedFilter) {
            CsvImportRowFilter.ALL -> document.rows
            CsvImportRowFilter.READY -> document.rows.filter { it.status == CsvImportRowStatus.READY && it.isIncluded }
            CsvImportRowFilter.WARNING -> document.rows.filter { it.status == CsvImportRowStatus.WARNING && it.isIncluded }
            CsvImportRowFilter.ERROR -> document.rows.filter { it.status == CsvImportRowStatus.ERROR && it.isIncluded }
            CsvImportRowFilter.SKIPPED -> document.rows.filter { !it.isIncluded }
        }
    }

    val hasErrors = document.rows.any { it.isIncluded && it.status == CsvImportRowStatus.ERROR }
    
    Column(modifier = Modifier.fillMaxSize()) {
        ImportSummaryHeader(document, selectedFilter, onFilterSelected = { selectedFilter = it })
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredRows) { row ->
                ImportRowItem(
                    row = row,
                    onToggleSelection = { onToggleSelection(row.rowNumber) },
                    onClick = { onEditRow(row) }
                )
                HorizontalDivider()
            }
        }
        
        Surface(tonalElevation = 2.dp) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = onConfirm,
                    enabled = !hasErrors && !isCommitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCommitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        val count = document.rows.count { it.isIncluded && it.status != CsvImportRowStatus.ERROR }
                        Text("Confirm Import ($count rows)")
                    }
                }
            }
        }
    }
}

@Composable
fun ImportSummaryHeader(
    document: com.miara.cuentame.feature.ingredient.import.domain.CsvIngredientImportDocument,
    selectedFilter: CsvImportRowFilter,
    onFilterSelected: (CsvImportRowFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SummaryItem("Total", document.rows.size.toString(), isSelected = selectedFilter == CsvImportRowFilter.ALL, onClick = { onFilterSelected(CsvImportRowFilter.ALL) })
        SummaryItem("Ready", document.rows.count { it.status == CsvImportRowStatus.READY && it.isIncluded }.toString(), isSelected = selectedFilter == CsvImportRowFilter.READY, onClick = { onFilterSelected(CsvImportRowFilter.READY) })
        SummaryItem("Warnings", document.rows.count { it.status == CsvImportRowStatus.WARNING && it.isIncluded }.toString(), color = MaterialTheme.colorScheme.tertiary, isSelected = selectedFilter == CsvImportRowFilter.WARNING, onClick = { onFilterSelected(CsvImportRowFilter.WARNING) })
        SummaryItem("Errors", document.rows.count { it.status == CsvImportRowStatus.ERROR && it.isIncluded }.toString(), color = MaterialTheme.colorScheme.error, isSelected = selectedFilter == CsvImportRowFilter.ERROR, onClick = { onFilterSelected(CsvImportRowFilter.ERROR) })
        SummaryItem("Skipped", document.rows.count { !it.isIncluded }.toString(), isSelected = selectedFilter == CsvImportRowFilter.SKIPPED, onClick = { onFilterSelected(CsvImportRowFilter.SKIPPED) })
    }
}

@Composable
fun SummaryItem(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.small
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ImportRowItem(
    row: CsvIngredientImportRow,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = row.isIncluded,
            onCheckedChange = { onToggleSelection() },
            enabled = row.status != CsvImportRowStatus.ERROR
        )
        Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
            Text(row.normalizedData?.name ?: row.rawData["ingredient_name"] ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
            Text("Row ${row.rowNumber} • ${row.normalizedData?.categoryName ?: "No category"}", style = MaterialTheme.typography.bodySmall)
            
            if (row.issues.isNotEmpty()) {
                val firstIssue = row.issues.first()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (firstIssue.severity == CsvImportIssueSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        firstIssue.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (firstIssue.severity == CsvImportIssueSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        TextButton(onClick = onClick) {
            Text("Edit")
        }
    }
}

@Composable
fun ImportResultContent(
    result: ImportResult,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (result) {
            is ImportResult.Success -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Import Complete", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text("${result.ingredientsCreated} ingredients created")
                Text("${result.categoriesCreated} categories created")
                Text("${result.suppliersCreated} suppliers created")
                Text("${result.mappingsCreated} vendor mappings created")
                if (result.rowsSkipped > 0) {
                    Text("${result.rowsSkipped} rows skipped")
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("View Ingredients")
                }
            }
            is ImportResult.Failure -> {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Import Failed", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(result.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry")
                }
            }
            ImportResult.StaleData -> {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Data Stale", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("The database has changed since you previewed the import. Please refresh and try again.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh Preview")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportRowEditDialog(
    row: CsvIngredientImportRow,
    onDismiss: () -> Unit,
    onSave: (CsvIngredientImportRow) -> Unit
) {
    var rawData by remember { mutableStateOf(row.rawData) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Row ${row.rowNumber}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImportEditField(
                    label = "Ingredient Name",
                    value = rawData[CsvParser.HEADER_INGREDIENT_NAME] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_INGREDIENT_NAME to it) }
                )
                ImportEditField(
                    label = "SKU",
                    value = rawData[CsvParser.HEADER_SKU] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_SKU to it) }
                )
                ImportEditField(
                    label = "Category",
                    value = rawData[CsvParser.HEADER_CATEGORY] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_CATEGORY to it) }
                )
                ImportEditField(
                    label = "Base Unit",
                    value = rawData[CsvParser.HEADER_BASE_UNIT] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_BASE_UNIT to it) }
                )
                ImportEditField(
                    label = "Count Unit",
                    value = rawData[CsvParser.HEADER_COUNT_UNIT] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_COUNT_UNIT to it) }
                )
                ImportEditField(
                    label = "Purchase Package",
                    value = rawData[CsvParser.HEADER_PURCHASE_PACKAGE] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_PURCHASE_PACKAGE to it) }
                )
                ImportEditField(
                    label = "Package Conversion Factor",
                    value = rawData[CsvParser.HEADER_PACKAGE_CONVERSION] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_PACKAGE_CONVERSION to it) }
                )
                ImportEditField(
                    label = "Default Area",
                    value = rawData[CsvParser.HEADER_DEFAULT_AREA] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_DEFAULT_AREA to it) }
                )
                ImportEditField(
                    label = "Supplier",
                    value = rawData[CsvParser.HEADER_SUPPLIER] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_SUPPLIER to it) }
                )
                ImportEditField(
                    label = "Vendor Item Code",
                    value = rawData[CsvParser.HEADER_VENDOR_CODE] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_VENDOR_CODE to it) }
                )
                ImportEditField(
                    label = "Current Cost",
                    value = rawData[CsvParser.HEADER_CURRENT_COST] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_CURRENT_COST to it) }
                )
                ImportEditField(
                    label = "Reorder Point",
                    value = rawData[CsvParser.HEADER_REORDER_POINT] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_REORDER_POINT to it) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(row.copy(rawData = rawData)) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ImportEditField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
