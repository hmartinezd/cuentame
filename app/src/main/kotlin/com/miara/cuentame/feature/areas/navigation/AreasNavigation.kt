package com.miara.cuentame.feature.areas.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.feature.areas.ui.AreaManagementRoute

fun NavGraphBuilder.areasGraph() {
    composable(Destination.SETTINGS_AREAS.route) {
        AreaManagementRoute()
    }
}
