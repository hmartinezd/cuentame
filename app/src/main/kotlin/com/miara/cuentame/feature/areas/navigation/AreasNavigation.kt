package com.miara.cuentame.feature.areas.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miara.cuentame.feature.areas.ui.AreaManagementRoute

fun NavGraphBuilder.areasGraph() {
    composable("settings/areas") {
        AreaManagementRoute()
    }
}
