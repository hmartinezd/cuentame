package com.miara.cuentame.core.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.ui.graphics.vector.ImageVector
import com.miara.cuentame.R

enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val iconTextId: Int,
    @StringRes val titleTextId: Int,
    val route: String,
    val testTag: String
) {
    HOME(
        selectedIcon = Icons.Default.Home,
        unselectedIcon = Icons.Outlined.Home,
        iconTextId = R.string.nav_home,
        titleTextId = R.string.nav_home,
        route = "home",
        testTag = "nav_home"
    ),
    INVENTORY(
        selectedIcon = Icons.Default.Inventory,
        unselectedIcon = Icons.Outlined.Inventory,
        iconTextId = R.string.nav_inventory,
        titleTextId = R.string.nav_inventory,
        route = "inventory",
        testTag = "nav_inventory"
    ),
    COUNT(
        selectedIcon = Icons.Default.Assignment,
        unselectedIcon = Icons.Outlined.Assignment,
        iconTextId = R.string.nav_count,
        titleTextId = R.string.nav_count,
        route = "count",
        testTag = "nav_count"
    ),
    ACTIVITY(
        selectedIcon = Icons.Default.History,
        unselectedIcon = Icons.Outlined.History,
        iconTextId = R.string.nav_activity,
        titleTextId = R.string.nav_activity,
        route = "activity",
        testTag = "nav_activity"
    ),
    REPORTS(
        selectedIcon = Icons.Default.Restore,
        unselectedIcon = Icons.Outlined.Restore,
        iconTextId = R.string.nav_reports,
        titleTextId = R.string.nav_reports,
        route = "reports",
        testTag = "nav_reports"
    )
}
