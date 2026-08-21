package com.venkoi.restaurantops.core.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.venkoi.restaurantops.R

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
fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    valueTestTag: String? = testTag?.let { "${it}_value" },
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier.then(
            if (testTag != null) {
                Modifier.testTag(testTag)
            } else {
                Modifier
            }
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.then(
                if (valueTestTag != null) {
                    Modifier.testTag(valueTestTag)
                } else {
                    Modifier
                }
            )
        )
    }
}
