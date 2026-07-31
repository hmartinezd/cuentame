package com.miara.cuentame.core.presentation.navigation

/**
 * Navigation destinations for the app.
 */
enum class Destination(val route: String) {
    ONBOARDING("onboarding"),
    SETTINGS("settings"),
    SETTINGS_RESTAURANT("settings/restaurant"),
    SETTINGS_AREAS("settings/areas"),
    SETTINGS_CATEGORIES("settings/categories"),
    
    STOCK_COUNT_START("count/start"),
    STOCK_COUNT_DRAFT("count/{countId}"),
    STOCK_COUNT_AREA("count/{countId}/area/{countAreaId}"),
    STOCK_COUNT_DETAIL("count/{countId}/detail"),
    
    WASTE_LIST("waste"),
    WASTE_CREATE("waste/create"),
    WASTE_DRAFT("waste/draft/{wasteId}"),
    WASTE_EDIT("waste/{wasteId}/edit"),
    WASTE_DETAIL("waste/{wasteId}"),
    
    PURCHASE_CREATE("purchases/create"),
    PURCHASE_DRAFT("purchases/{receiptId}"),
    PURCHASE_LINE_CREATE("purchases/{receiptId}/line"),
    PURCHASE_LINE_EDIT("purchases/{receiptId}/line/{lineId}"),
    PURCHASE_DETAIL("purchases/{receiptId}/detail"),
    
    INGREDIENT_CREATE("inventory/create"),
    INGREDIENT_DETAIL("inventory/{ingredientId}"),
    INGREDIENT_EDIT("inventory/{ingredientId}/edit"),
    
    REPORT_INVENTORY_DETAIL("reports/inventory"),
    REPORT_PURCHASE_DETAIL("reports/purchases"),
    REPORT_WASTE_DETAIL("reports/waste"),
    
    SUPPLIER_LIST("suppliers"),
    SUPPLIER_CREATE("suppliers/create"),
    SUPPLIER_EDIT("suppliers/{supplierId}/edit")
}
