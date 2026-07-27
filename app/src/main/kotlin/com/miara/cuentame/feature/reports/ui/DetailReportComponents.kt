package com.miara.cuentame.feature.reports.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.miara.cuentame.R

@Composable
fun RefreshIndicator(
    testTag: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.updating_report)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        )
    }
}

@Composable
fun RefreshErrorBanner(
    testTag: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.refresh_error_message)
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Error, contentDescription = null)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_retry_desc))
            }
        }
    }
}

@Composable
fun DetailReportLoading(testTag: String) {
    Box(modifier = Modifier.fillMaxSize().testTag(testTag), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun DetailReportSetupRequired(testTag: String) {
    Box(modifier = Modifier.fillMaxSize().testTag(testTag), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.setup_required_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.setup_required_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun DetailReportError(testTag: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().testTag(testTag), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.reports_error_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.reports_error_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp).testTag("${testTag}_retry")) {
                Text(stringResource(R.string.action_retry_desc))
            }
        }
    }
}

@Composable
fun DetailReportHeader(
    restaurantName: String,
    reportTitle: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Text(
            text = restaurantName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = reportTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun WarningLabel(
    text: String,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.error
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}
