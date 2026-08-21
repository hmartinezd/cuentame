package com.venkoi.restaurantops.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keeps operational content readable on large screens while continuing to fill
 * the available width on compact devices.
 */
fun Modifier.adaptiveContentWidth(maxWidth: Dp = 840.dp): Modifier =
    wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)
        .fillMaxWidth()
