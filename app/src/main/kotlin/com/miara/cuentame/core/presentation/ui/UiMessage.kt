package com.miara.cuentame.core.presentation.ui

import androidx.annotation.StringRes

sealed interface UiMessage {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList()
    ) : UiMessage

    data class PlainTextInternalOnly(
        val value: String
    ) : UiMessage
}
