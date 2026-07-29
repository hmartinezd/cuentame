package com.miara.cuentame.core.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
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
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        iconTextId = R.string.nav_home,
        titleTextId = R.string.home_title,
        route = "home",
        testTag = "nav_home"
    ),
    INVENTORY(
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List,
        iconTextId = R.string.nav_inventory,
        titleTextId = R.string.inventory_title,
        route = "inventory",
        testTag = "nav_inventory"
    ),
    COUNT(
        selectedIcon = Icons.Filled.QrCodeScanner,
        unselectedIcon = Icons.Outlined.QrCodeScanner,
        iconTextId = R.string.nav_count,
        titleTextId = R.string.count_title,
        route = "count",
        testTag = "nav_count"
    ),
    ACTIVITY(
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt,
        iconTextId = R.string.nav_activity,
        titleTextId = R.string.activity_title,
        route = "purchases",
        testTag = "nav_purchases"
    ),
    REPORTS(
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        iconTextId = R.string.nav_reports,
        titleTextId = R.string.reports_title,
        route = "reports",
        testTag = "nav_reports"
    ),
    SETTINGS(
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        iconTextId = R.string.nav_settings,
        titleTextId = R.string.nav_settings,
        route = "settings",
        testTag = "nav_settings"
    )
}

enum class Destination(val route: String) {
    ONBOARDING("onboarding"),
    SETTINGS("settings"),
    LOADING("loading"),
    INGREDIENT_LIST("ingredients"),
    INGREDIENT_CREATE("ingredient/create"),
    INGREDIENT_DETAIL("ingredient/{ingredientId}"),
    INGREDIENT_EDIT("ingredient/{ingredientId}/edit"),

    // Suppliers
    SUPPLIER_LIST("suppliers"),
    SUPPLIER_CREATE("supplier/create"),
    SUPPLIER_EDIT("supplier/{supplierId}/edit"),

    // Purchases
    PURCHASE_LIST("purchases"),
    PURCHASE_CREATE("purchase/create"),
    PURCHASE_DRAFT("purchase/{purchaseId}"),
    PURCHASE_LINE_CREATE("purchase/{purchaseId}/line/create"),
    PURCHASE_LINE_EDIT("purchase/{purchaseId}/line/{lineId}/edit"),
    PURCHASE_DETAIL("purchase/{purchaseId}/detail"),

    // Stock Counts
    STOCK_COUNT_LIST("counts"),
    STOCK_COUNT_START("count/start"),
    STOCK_COUNT_DRAFT("count/{countId}"),
    STOCK_COUNT_AREA("count/{countId}/area/{countAreaId}"),
    STOCK_COUNT_DETAIL("count/{countId}/detail"),

    // Waste
    WASTE_LIST("waste"),
    WASTE_CREATE("waste/create"),
    WASTE_DETAIL("waste/{wasteId}"),
    WASTE_DRAFT("waste/draft/{wasteId}"),
    WASTE_EDIT("waste/{wasteId}/edit"),

    // Reports
    REPORT_INVENTORY_DETAIL("reports/inventory"),
    REPORT_PURCHASE_DETAIL("reports/purchases"),
    REPORT_WASTE_DETAIL("reports/waste")
}
