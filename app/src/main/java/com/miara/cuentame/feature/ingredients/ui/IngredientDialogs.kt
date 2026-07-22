package com.miara.cuentame.feature.ingredients.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.miara.cuentame.R
import com.miara.cuentame.core.common.text.DecimalParser
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import java.math.BigDecimal

@Composable
fun StandardUnitDialog(
    units: List<UnitOfMeasure>,
    excludedUnitIds: Set<com.miara.cuentame.core.common.ids.UnitId>,
    onDismiss: () -> Unit,
    onSelect: (UnitOfMeasure) -> Unit
) {
    val filteredUnits = units.filter { it.id !in excludedUnitIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.standard_unit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                filteredUnits.forEach { unit ->
                    ListItem(
                        headlineContent = { Text(unit.name) },
                        trailingContent = { Text(unit.symbol) },
                        modifier = Modifier.clickable { onSelect(unit) }
                    )
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun AddPackageDialog(
    initialName: String = "",
    initialQty: BigDecimal? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, BigDecimal) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var qtyText by remember { mutableStateOf(initialQty?.toPlainString() ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.package_option)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.package_name)) }
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text(stringResource(R.string.contains_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    DecimalParser.parse(qtyText)?.let { onConfirm(name, it) }
                },
                modifier = Modifier.testTag("package_dialog_confirm"),
                enabled = name.isNotBlank() && DecimalParser.parse(qtyText)?.let { it > BigDecimal.ZERO } == true
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
