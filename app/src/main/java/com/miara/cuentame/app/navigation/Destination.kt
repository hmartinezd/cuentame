package com.miara.cuentame.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.miara.cuentame.R

enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val iconTextId: Int,
    @StringRes val titleTextId: Int,
    val route: String
) {
    HOME(
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        iconTextId = R.string.nav_home,
        titleTextId = R.string.home_title,
        route = "home"
    ),
    INVENTORY(
        selectedIcon = Icons.Filled.Inventory,
        unselectedIcon = Icons.Outlined.Inventory,
        iconTextId = R.string.nav_inventory,
        titleTextId = R.string.inventory_title,
        route = "inventory"
    ),
    COUNT(
        selectedIcon = Icons.Filled.QrCodeScanner,
        unselectedIcon = Icons.Outlined.QrCodeScanner,
        iconTextId = R.string.nav_count,
        titleTextId = R.string.count_title,
        route = "count"
    ),
    ACTIVITY(
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        iconTextId = R.string.nav_activity,
        titleTextId = R.string.activity_title,
        route = "activity"
    ),
    REPORTS(
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
        iconTextId = R.string.nav_reports,
        titleTextId = R.string.reports_title,
        route = "reports"
    )
}

enum class Destination(val route: String) {
    ONBOARDING("onboarding"),
    SETTINGS("settings"),
    LOADING("loading")
}

