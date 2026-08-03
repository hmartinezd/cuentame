package com.miara.cuentame.feature.production.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea

@Composable
fun ProductionStatusBadge(
    status: DocumentStatus,
    modifier: Modifier = Modifier
) {
    val (textRes, color, bgColor) = when (status) {
        DocumentStatus.DRAFT -> Triple(
            R.string.status_draft,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
        DocumentStatus.POSTED -> Triple(
            R.string.status_posted,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        DocumentStatus.VOIDED -> Triple(
            R.string.status_voided,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.errorContainer
        )
    }

    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .background(bgColor, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaSelector(
    selectedId: InventoryAreaId?,
    areas: List<InventoryArea>,
    onSelected: (InventoryAreaId) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.area_label)
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedArea = areas.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedArea?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            areas.forEach { area ->
                DropdownMenuItem(
                    text = { Text(area.name) },
                    onClick = {
                        onSelected(area.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSelector(
    selectedId: IngredientUnitOptionId?,
    options: List<IngredientUnitOption>,
    onSelected: (IngredientUnitOptionId) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.output_unit)
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedOption?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSelected(option.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
