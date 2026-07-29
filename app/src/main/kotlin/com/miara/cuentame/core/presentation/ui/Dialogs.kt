package com.miara.cuentame.core.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.miara.cuentame.R

@Composable
fun ArchiveConfirmDialog(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(R.string.archive_confirm_action),
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(title) },
        text = { Text(message) },
        modifier = modifier.semantics(mergeDescendants = true) {},
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier.testTag("archive_confirm_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp).size(16.dp).testTag("dialog_progress"),
                        strokeWidth = 2.dp
                    )
                }
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { 
                Text(stringResource(android.R.string.cancel)) 
            }
        }
    )
}
