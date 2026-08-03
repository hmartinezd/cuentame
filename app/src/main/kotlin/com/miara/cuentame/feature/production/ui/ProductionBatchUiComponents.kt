package com.miara.cuentame.feature.production.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Timer
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
import java.time.Instant

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionEffectiveTimeEditor(
    effectiveAt: Instant,
    onEffectiveAtChanged: (Instant) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoneId = java.time.ZoneId.systemDefault()
    val localDateTime = java.time.LocalDateTime.ofInstant(effectiveAt, zoneId)
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = effectiveAt.toEpochMilli()
    )
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM) }
    val timeFormatter = remember { java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT) }

    Column(modifier = modifier.fillMaxWidth().testTag("production_effective_time")) {
        Text(
            text = stringResource(R.string.production_effective_time),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        OutlinedCard(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.effective_date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = localDateTime.format(dateFormatter),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.effective_time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = localDateTime.format(timeFormatter),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    IconButton(onClick = { showTimePicker = true }) {
                        Icon(Icons.Default.Timer, contentDescription = null)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = java.time.Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
                        val newDateTime = java.time.LocalDateTime.of(newDate, localDateTime.toLocalTime())
                        onEffectiveAtChanged(newDateTime.atZone(zoneId).toInstant())
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_back))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = localDateTime.hour,
            initialMinute = localDateTime.minute
        )
        
        androidx.compose.ui.window.Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                modifier = Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Min).height(androidx.compose.foundation.layout.IntrinsicSize.Min).background(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        text = stringResource(R.string.effective_time),
                        style = MaterialTheme.typography.labelMedium
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.action_back))
                        }
                        TextButton(onClick = {
                            val newDateTime = java.time.LocalDateTime.of(localDateTime.toLocalDate(), java.time.LocalTime.of(timePickerState.hour, timePickerState.minute))
                            onEffectiveAtChanged(newDateTime.atZone(zoneId).toInstant())
                            showTimePicker = false
                        }) {
                            Text(stringResource(R.string.action_confirm))
                        }
                    }
                }
            }
        }
    }
}
