package com.venkoi.restaurantops.core.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.venkoi.restaurantops.R

sealed interface UiMessage {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList()
    ) : UiMessage

    data class PlainTextInternalOnly(
        val value: String
    ) : UiMessage
}

@Composable
fun UiMessage.toDisplayText(): String = when (this) {
    is UiMessage.Resource -> stringResource(id, *args.toTypedArray())
    is UiMessage.PlainTextInternalOnly -> stringResource(R.string.error_generic)
}
