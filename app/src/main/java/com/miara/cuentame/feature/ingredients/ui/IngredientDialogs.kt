package com.miara.cuentame.feature.ingredients.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.miara.cuentame.R
import com.miara.cuentame.core.common.text.DecimalParser
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import com.miara.cuentame.feature.ingredients.model.UnitConversionChoiceUiModel
import java.math.BigDecimal

@Composable
fun StandardUnitDialog(
    units: List<UnitOfMeasure>,
    excludedUnitIds: Set<com.miara.cuentame.core.common.ids.UnitId>,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    getPreview: (UnitOfMeasure) -> UnitConversionChoiceUiModel?,
    onSelect: (UnitOfMeasure) -> Unit
) {
    val filteredUnits = units.filter { it.id !in excludedUnitIds }
    var selectedUnit by remember { mutableStateOf<UnitOfMeasure?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.standard_unit)) },
        text = {
            if (selectedUnit == null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    filteredUnits.forEach { unit ->
                        ListItem(
                            headlineContent = { Text(unit.name) },
                            trailingContent = { Text(unit.symbol) },
                            modifier = Modifier.clickable(enabled = !isSaving) { selectedUnit = unit }
                        )
                    }
                }
            } else {
                Column {
                    Text(text = selectedUnit!!.name, style = MaterialTheme.typography.titleMedium)
                    getPreview(selectedUnit!!)?.let { preview ->
                        Text(
                            text = stringResource(
                                R.string.unit_conversion_format,
                                preview.sourceSymbol,
                                preview.factor,
                                preview.baseSymbol
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedUnit != null) {
                Button(
                    onClick = { onSelect(selectedUnit!!) },
                    modifier = Modifier.testTag("standard_unit_dialog_confirm"),
                    enabled = !isSaving
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (selectedUnit != null) selectedUnit = null else onDismiss() },
                enabled = !isSaving
            ) {
                Text(stringResource(if (selectedUnit != null) R.string.action_back else android.R.string.cancel))
            }
        }
    )
}

@Composable
fun AddPackageDialog(
    initialName: String = "",
    initialQty: BigDecimal? = null,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, BigDecimal) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var qtyText by remember { mutableStateOf(initialQty?.stripTrailingZeros()?.toPlainString() ?: "") }
    
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.package_option)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.package_name)) },
                    enabled = !isSaving
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text(stringResource(R.string.contains_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !isSaving
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    DecimalParser.parse(qtyText)?.let { onConfirm(name, it) }
                },
                modifier = Modifier.testTag("package_dialog_confirm"),
                enabled = !isSaving && name.isNotBlank() && DecimalParser.parse(qtyText)?.let { it > BigDecimal.ZERO } == true
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { 
                Text(stringResource(android.R.string.cancel)) 
            }
        }
    )
}

@Composable
fun ArchiveConfirmDialog(
    title: String,
    message: String,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier.testTag("archive_confirm_button")
            ) {
                Text(stringResource(R.string.archive_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
