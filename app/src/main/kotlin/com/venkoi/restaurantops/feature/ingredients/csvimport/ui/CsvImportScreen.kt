package com.venkoi.restaurantops.feature.ingredients.csvimport.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.core.domain.repository.ImportFailure
import com.venkoi.restaurantops.core.domain.repository.ImportResult
import com.venkoi.restaurantops.feature.ingredients.csvimport.domain.CsvIngredientImportDocument
import com.venkoi.restaurantops.feature.ingredients.csvimport.domain.CsvIngredientImportRow
import com.venkoi.restaurantops.feature.ingredients.csvimport.domain.CsvImportIssueSeverity
import com.venkoi.restaurantops.feature.ingredients.csvimport.domain.CsvImportIssueCode
import com.venkoi.restaurantops.feature.ingredients.csvimport.domain.CsvImportRowStatus
import com.venkoi.restaurantops.feature.ingredients.csvimport.domain.CsvParser
import com.venkoi.restaurantops.feature.ingredients.csvimport.domain.IngredientImportField

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
                context.contentResolver.openInputStream(it)?.let(viewModel::loadCsv)
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
        onChooseFile = { filePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")) },
        onDownloadTemplate = { templatePicker.launch("cuentame_ingredient_template.csv") },
        onToggleSelection = viewModel::toggleRowSelection,
        onEditRow = { editingRow = it },
        onConfirm = viewModel::confirmImport,
        onDone = onViewIngredients,
        onResetResult = viewModel::resetImportResult,
        onRefreshPreview = viewModel::refreshPreview
        ,onUpdateMapping = viewModel::updateMapping
        ,onPreviewMapping = viewModel::previewMappedCsv
        ,onChangeMapping = viewModel::changeMapping
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
    onResetResult: () -> Unit,
    onRefreshPreview: () -> Unit
    ,onUpdateMapping: (Int, IngredientImportField?) -> Unit
    ,onPreviewMapping: () -> Unit
    ,onChangeMapping: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_csv)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                        onDismiss = onResetResult,
                        onRefresh = onRefreshPreview,
                        isRefreshing = uiState.isRefreshing
                    )
                }
                uiState.isParsing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.sourceTable != null && uiState.document == null -> {
                    ColumnMappingContent(uiState, onUpdateMapping, onPreviewMapping)
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
                        onConfirm = onConfirm,
                        onChangeMapping = onChangeMapping
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(onChooseFile: () -> Unit, onDownloadTemplate: () -> Unit, error: CsvParser.ParseErrorType?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.import_csv_desc), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.import_default_area_help), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onChooseFile) {
            Text(stringResource(R.string.choose_csv))
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            val message = when (error) {
                CsvParser.ParseErrorType.FILE_TOO_LARGE -> stringResource(R.string.import_parse_file_too_large)
                CsvParser.ParseErrorType.TOO_MANY_ROWS -> stringResource(R.string.import_parse_too_many_rows)
                CsvParser.ParseErrorType.EMPTY_FILE -> stringResource(R.string.import_parse_empty_file)
                CsvParser.ParseErrorType.MISSING_HEADERS -> stringResource(R.string.import_parse_missing_headers)
                CsvParser.ParseErrorType.DUPLICATE_HEADERS -> stringResource(R.string.import_parse_duplicate_headers)
                CsvParser.ParseErrorType.MALFORMED_CSV -> stringResource(R.string.import_parse_malformed)
                CsvParser.ParseErrorType.READ_FAILURE -> stringResource(R.string.import_error_file_read)
            }
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onDownloadTemplate) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.download_template))
        }
    }
}

@Composable
fun ImportPreviewContent(
    document: CsvIngredientImportDocument,
    isCommitting: Boolean,
    onToggleSelection: (Int) -> Unit,
    onEditRow: (CsvIngredientImportRow) -> Unit,
    onConfirm: () -> Unit,
    onChangeMapping: () -> Unit
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
        TextButton(onClick = onChangeMapping, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.import_change_mapping))
        }
        if (document.rows.isEmpty()) Text(stringResource(R.string.import_no_data_rows), modifier = Modifier.padding(16.dp))
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
                    enabled = !hasErrors && !isCommitting && document.rows.any { it.isIncluded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCommitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        val count = document.rows.count { it.isIncluded && it.status != CsvImportRowStatus.ERROR }
                        Text(stringResource(R.string.import_confirm_button, count))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMappingContent(
    uiState: CsvImportUiState,
    onUpdateMapping: (Int, IngredientImportField?) -> Unit,
    onPreview: () -> Unit
) {
    val table = uiState.sourceTable ?: return
    val mapping = uiState.columnMapping ?: return
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.import_map_columns), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        Text(stringResource(R.string.import_map_columns_help), modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(table.columns, key = { it.index }) { column ->
                var expanded by remember { mutableStateOf(false) }
                val target = mapping.sourceToTarget[column.index]
                ListItem(
                    headlineContent = { Text(column.header) },
                    supportingContent = {
                        val samples = table.samples(column.index).joinToString(" • ")
                        if (samples.isNotEmpty()) Text(samples, maxLines = 2)
                    },
                    trailingContent = {
                        Box {
                            OutlinedButton(onClick = { expanded = true }) {
                                Text(target?.let { fieldLabel(it) } ?: stringResource(R.string.import_ignore))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.import_ignore)) }, onClick = {
                                    onUpdateMapping(column.index, null); expanded = false
                                })
                                IngredientImportField.entries.forEach { field ->
                                    DropdownMenuItem(text = { Text(fieldLabel(field)) }, onClick = {
                                        onUpdateMapping(column.index, field); expanded = false
                                    })
                                }
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
        mapping.missingRequiredFields.forEach { field ->
            Text(stringResource(R.string.import_required_mapping_missing, fieldLabel(field)), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Button(onClick = onPreview, enabled = mapping.isValid, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.import_preview_action))
        }
    }
}

@Composable
private fun fieldLabel(field: IngredientImportField): String = when (field) {
    IngredientImportField.INGREDIENT_NAME -> stringResource(R.string.import_field_ingredient_name)
    IngredientImportField.SKU -> stringResource(R.string.import_field_sku)
    IngredientImportField.CATEGORY -> stringResource(R.string.import_field_category)
    IngredientImportField.BASE_UNIT -> stringResource(R.string.import_field_base_unit)
    IngredientImportField.COUNT_UNIT -> stringResource(R.string.import_field_count_unit)
    IngredientImportField.PURCHASE_PACKAGE -> stringResource(R.string.import_field_purchase_package)
    IngredientImportField.PACKAGE_CONVERSION_FACTOR -> stringResource(R.string.import_field_package_conversion)
    IngredientImportField.DEFAULT_AREA -> stringResource(R.string.import_field_default_area)
    IngredientImportField.SUPPLIER -> stringResource(R.string.import_field_supplier)
    IngredientImportField.VENDOR_ITEM_CODE -> stringResource(R.string.import_field_vendor_code)
    IngredientImportField.CURRENT_COST_PER_BASE_UNIT -> stringResource(R.string.import_field_current_cost)
    IngredientImportField.REORDER_POINT_BASE -> stringResource(R.string.import_field_reorder_point)
}

@Composable
fun ImportSummaryHeader(
    document: CsvIngredientImportDocument,
    selectedFilter: CsvImportRowFilter,
    onFilterSelected: (CsvImportRowFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SummaryItem(stringResource(R.string.import_summary_total), document.rows.size.toString(), isSelected = selectedFilter == CsvImportRowFilter.ALL, onClick = { onFilterSelected(CsvImportRowFilter.ALL) })
        SummaryItem(stringResource(R.string.import_summary_ready), document.rows.count { it.status == CsvImportRowStatus.READY && it.isIncluded }.toString(), isSelected = selectedFilter == CsvImportRowFilter.READY, onClick = { onFilterSelected(CsvImportRowFilter.READY) })
        SummaryItem(stringResource(R.string.import_summary_warnings), document.rows.count { it.status == CsvImportRowStatus.WARNING && it.isIncluded }.toString(), color = MaterialTheme.colorScheme.tertiary, isSelected = selectedFilter == CsvImportRowFilter.WARNING, onClick = { onFilterSelected(CsvImportRowFilter.WARNING) })
        SummaryItem(stringResource(R.string.import_summary_errors), document.rows.count { it.status == CsvImportRowStatus.ERROR && it.isIncluded }.toString(), color = MaterialTheme.colorScheme.error, isSelected = selectedFilter == CsvImportRowFilter.ERROR, onClick = { onFilterSelected(CsvImportRowFilter.ERROR) })
        SummaryItem(stringResource(R.string.import_summary_skipped), document.rows.count { !it.isIncluded }.toString(), isSelected = selectedFilter == CsvImportRowFilter.SKIPPED, onClick = { onFilterSelected(CsvImportRowFilter.SKIPPED) })
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
            enabled = true
        )
        Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
            Text(row.normalizedData?.name ?: row.rawData["ingredient_name"] ?: stringResource(R.string.import_unknown), style = MaterialTheme.typography.bodyLarge)
            Text("${stringResource(R.string.import_row)} ${row.rowNumber} • ${row.normalizedData?.categoryName ?: stringResource(R.string.import_no_category)}", style = MaterialTheme.typography.bodySmall)
            
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
                        csvIssueMessage(firstIssue.code, firstIssue.parameters),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (firstIssue.severity == CsvImportIssueSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        TextButton(onClick = onClick) {
            Text(stringResource(R.string.action_edit))
        }
    }
}

@Composable
private fun csvIssueMessage(code: CsvImportIssueCode, parameters: List<String>): String = when (code) {
    CsvImportIssueCode.INGREDIENT_NAME_REQUIRED -> stringResource(R.string.csv_issue_ingredient_name_required)
    CsvImportIssueCode.INVALID_INGREDIENT_NAME -> stringResource(R.string.csv_issue_invalid_ingredient_name)
    CsvImportIssueCode.DUPLICATE_INGREDIENT_NAME_IN_FILE -> stringResource(R.string.csv_issue_duplicate_ingredient_name)
    CsvImportIssueCode.EXISTING_ACTIVE_INGREDIENT -> stringResource(R.string.csv_issue_existing_active_ingredient)
    CsvImportIssueCode.EXISTING_ARCHIVED_INGREDIENT -> stringResource(R.string.csv_issue_existing_archived_ingredient)
    CsvImportIssueCode.DUPLICATE_SKU_IN_FILE -> stringResource(R.string.csv_issue_duplicate_sku)
    CsvImportIssueCode.EXISTING_SKU -> stringResource(R.string.csv_issue_existing_sku)
    CsvImportIssueCode.UNIT_REQUIRED -> stringResource(R.string.csv_issue_unit_required)
    CsvImportIssueCode.UNKNOWN_UNIT -> stringResource(R.string.csv_issue_unknown_unit, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.AMBIGUOUS_UNIT -> stringResource(R.string.csv_issue_ambiguous_unit, parameters.getOrElse(0) { "" }, parameters.getOrElse(1) { "" })
    CsvImportIssueCode.INCOMPATIBLE_COUNT_UNIT -> stringResource(R.string.csv_issue_incompatible_count_unit, parameters.getOrElse(0) { "" }, parameters.getOrElse(1) { "" })
    CsvImportIssueCode.CATEGORY_WILL_BE_CREATED -> stringResource(R.string.csv_issue_category_created, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.CATEGORY_ARCHIVED -> stringResource(R.string.csv_issue_category_archived, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.CATEGORY_INACTIVE -> stringResource(R.string.csv_issue_category_inactive, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.CATEGORY_DATA_CONFLICT -> stringResource(R.string.csv_issue_category_conflict, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.SUPPLIER_WILL_BE_CREATED -> stringResource(R.string.csv_issue_supplier_created, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.SUPPLIER_ARCHIVED -> stringResource(R.string.csv_issue_supplier_archived, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.SUPPLIER_INACTIVE -> stringResource(R.string.csv_issue_supplier_inactive, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.SUPPLIER_DATA_CONFLICT -> stringResource(R.string.csv_issue_supplier_conflict, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.UNKNOWN_AREA -> stringResource(R.string.csv_issue_unknown_area, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.VENDOR_CODE_REQUIRES_SUPPLIER -> stringResource(R.string.csv_issue_vendor_requires_supplier)
    CsvImportIssueCode.DUPLICATE_VENDOR_CODE -> stringResource(R.string.csv_issue_duplicate_vendor_code)
    CsvImportIssueCode.EXISTING_VENDOR_MAPPING -> stringResource(R.string.csv_issue_existing_vendor_mapping, parameters.getOrElse(0) { "" })
    CsvImportIssueCode.INVALID_PACKAGE_CONVERSION -> stringResource(R.string.csv_issue_invalid_package_conversion)
    CsvImportIssueCode.PACKAGE_CONVERSION_REQUIRED -> stringResource(R.string.csv_issue_package_conversion_required)
    CsvImportIssueCode.INVALID_CURRENT_COST -> stringResource(R.string.csv_issue_invalid_current_cost)
    CsvImportIssueCode.INVALID_REORDER_POINT -> stringResource(R.string.csv_issue_invalid_reorder_point)
}

@Composable
fun ImportResultContent(
    result: ImportResult,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
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
                Text(stringResource(R.string.import_complete), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.import_ingredients_created, result.ingredientsCreated))
                Text(stringResource(R.string.import_categories_created, result.categoriesCreated))
                Text(stringResource(R.string.import_suppliers_created, result.suppliersCreated))
                Text(stringResource(R.string.import_mappings_created, result.mappingsCreated))
                if (result.rowsSkipped > 0) {
                    Text(stringResource(R.string.import_rows_skipped, result.rowsSkipped))
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_view_ingredients))
                }
            }
            is ImportResult.Failure -> {
                val isStale = result.failure == ImportFailure.StateChanged
                val errorMessage = when (result.failure) {
                    ImportFailure.InvalidPlan -> stringResource(R.string.import_error_invalid_plan)
                    ImportFailure.StateChanged -> stringResource(R.string.import_stale_desc)
                    ImportFailure.RestaurantUnavailable -> stringResource(R.string.import_error_restaurant_unavailable)
                    ImportFailure.PersistenceFailure -> stringResource(R.string.import_error_persistence)
                    ImportFailure.FileReadFailure -> stringResource(R.string.import_error_file_read)
                    ImportFailure.Unexpected -> stringResource(R.string.import_error_unknown)
                }
                
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(if (isStale) R.string.import_stale_title else R.string.import_failed),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = if (isStale) onRefresh else onDismiss,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(if (isStale) R.string.import_refresh else R.string.import_retry))
                    }
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
        title = { Text(stringResource(R.string.import_edit_row_title, row.rowNumber)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImportEditField(
                    label = stringResource(R.string.import_field_ingredient_name),
                    value = rawData[CsvParser.HEADER_INGREDIENT_NAME] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_INGREDIENT_NAME to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_sku),
                    value = rawData[CsvParser.HEADER_SKU] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_SKU to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_category),
                    value = rawData[CsvParser.HEADER_CATEGORY] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_CATEGORY to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_base_unit),
                    value = rawData[CsvParser.HEADER_BASE_UNIT] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_BASE_UNIT to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_count_unit),
                    value = rawData[CsvParser.HEADER_COUNT_UNIT] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_COUNT_UNIT to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_purchase_package),
                    value = rawData[CsvParser.HEADER_PURCHASE_PACKAGE] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_PURCHASE_PACKAGE to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_package_conversion),
                    value = rawData[CsvParser.HEADER_PACKAGE_CONVERSION] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_PACKAGE_CONVERSION to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_default_area),
                    value = rawData[CsvParser.HEADER_DEFAULT_AREA] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_DEFAULT_AREA to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_supplier),
                    value = rawData[CsvParser.HEADER_SUPPLIER] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_SUPPLIER to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_vendor_code),
                    value = rawData[CsvParser.HEADER_VENDOR_CODE] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_VENDOR_CODE to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_current_cost),
                    value = rawData[CsvParser.HEADER_CURRENT_COST] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_CURRENT_COST to it) }
                )
                ImportEditField(
                    label = stringResource(R.string.import_field_reorder_point),
                    value = rawData[CsvParser.HEADER_REORDER_POINT] ?: "",
                    onValueChange = { rawData = rawData + (CsvParser.HEADER_REORDER_POINT to it) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(row.copy(rawData = rawData)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
