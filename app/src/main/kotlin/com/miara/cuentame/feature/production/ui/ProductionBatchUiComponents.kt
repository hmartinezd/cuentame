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
    val triple = when (status) {
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
    val textRes = triple.first
    val color = triple.second
    val bgColor = triple.third

    val tag = when (status) {
        DocumentStatus.DRAFT -> "production_status_draft"
        DocumentStatus.POSTED -> "production_status_posted"
        DocumentStatus.VOIDED -> "production_status_voided"
    }

    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .testTag(tag)
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
    modifier: Modifier = Modifier,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
) {
    val localDateTime = java.time.LocalDateTime.ofInstant(effectiveAt, zoneId)
    
    val localDate = localDateTime.toLocalDate()
    val initialDatePickerMillis = localDate
        .atStartOfDay(java.time.ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDatePickerMillis
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
                    IconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.testTag("production_effective_date_button")
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.choose_effective_date))
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
                    IconButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.testTag("production_effective_time_button")
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = stringResource(R.string.choose_effective_time))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            modifier = Modifier.testTag("production_effective_date_dialog"),
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onEffectiveAtChanged(
                                calculateEffectiveAtWithNewDate(
                                    millis = millis,
                                    currentEffectiveAt = effectiveAt,
                                    zoneId = zoneId
                                )
                            )
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("production_effective_date_confirm")
                ) {
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
                modifier = Modifier
                    .width(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .background(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface)
                    .testTag("production_effective_time_dialog")
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
                        TextButton(
                            onClick = {
                                onEffectiveAtChanged(
                                    calculateEffectiveAtWithNewTime(
                                        hour = timePickerState.hour,
                                        minute = timePickerState.minute,
                                        currentEffectiveAt = effectiveAt,
                                        zoneId = zoneId
                                    )
                                )
                                showTimePicker = false
                            },
                            modifier = Modifier.testTag("production_effective_time_confirm")
                        ) {
                            Text(stringResource(R.string.action_confirm))
                        }
                    }
                }
            }
        }
    }
}

internal fun calculateEffectiveAtWithNewDate(
    millis: Long,
    currentEffectiveAt: Instant,
    zoneId: java.time.ZoneId
): Instant {
    val selectedDate = Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneOffset.UTC)
        .toLocalDate()
    val currentTime = currentEffectiveAt.atZone(zoneId).toLocalTime()
    val updatedLocalDateTime = java.time.LocalDateTime.of(selectedDate, currentTime)
        .withSecond(0).withNano(0)
    return updatedLocalDateTime.atZone(zoneId).toInstant()
}

internal fun calculateEffectiveAtWithNewTime(
    hour: Int,
    minute: Int,
    currentEffectiveAt: Instant,
    zoneId: java.time.ZoneId
): Instant {
    val currentDate = currentEffectiveAt.atZone(zoneId).toLocalDate()
    val updatedLocalDateTime = java.time.LocalDateTime.of(currentDate, java.time.LocalTime.of(hour, minute))
        .withSecond(0).withNano(0)
    return updatedLocalDateTime.atZone(zoneId).toInstant()
}
