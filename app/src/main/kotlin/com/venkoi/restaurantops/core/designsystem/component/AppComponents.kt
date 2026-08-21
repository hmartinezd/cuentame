package com.venkoi.restaurantops.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.venkoi.restaurantops.app.ui.theme.AppElevation
import com.venkoi.restaurantops.app.ui.theme.AppSpacing
import com.venkoi.restaurantops.app.ui.theme.AppTheme

enum class StatusTone { NEUTRAL, SUCCESS, WARNING, ERROR, INFO }

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = AppTheme.semanticColors.elevatedSurface),
        border = BorderStroke(1.dp, AppTheme.semanticColors.divider),
        elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.level1),
    ) { content() }
}

@Composable
fun AppPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = AppTheme.semanticColors.disabled,
            disabledContentColor = AppTheme.semanticColors.onDisabled,
        ),
        content = content,
    )
}

@Composable
fun AppStatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val (container, content) = statusColors(tone)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        contentColor = content,
    ) {
        Row(modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.padding(end = AppSpacing.xs))
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        androidx.compose.material3.Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun statusColors(tone: StatusTone): Pair<Color, Color> {
    val semantic = AppTheme.semanticColors
    return when (tone) {
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.SUCCESS -> semantic.successContainer to semantic.onSuccessContainer
        StatusTone.WARNING -> semantic.warningContainer to semantic.onWarningContainer
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.INFO -> semantic.infoContainer to semantic.onInfoContainer
    }
}
