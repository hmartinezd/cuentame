package com.miara.cuentame.core.presentation.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Safely resolves an Activity from a Context, handling ContextWrappers.
 */
tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
