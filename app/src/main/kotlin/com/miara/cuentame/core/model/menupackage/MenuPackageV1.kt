package com.miara.cuentame.core.model.menupackage

import kotlinx.serialization.Serializable

@Serializable
data class MenuPackageV1(
    val format:String,
    val formatVersion:Int,
    val packageId:String,
    val restaurantId:String,
    val publishedAt:String,
    val currency:String,
    val menu:MenuPackageMenuV1
)

@Serializable
data class MenuPackageMenuV1(
    val menuId:String,
    val name:String,
    val description:String?,
    val publicationRevision:Long,
    val defaultCashDiscountPercent:String,
    val categories:List<MenuPackageCategoryV1>
)

@Serializable
data class MenuPackageCategoryV1(
    val categoryId:String,
    val name:String,
    val sortOrder:Int,
    val items:List<MenuPackageItemV1>
)

@Serializable
data class MenuPackageItemV1(
    val sellableItemId:String,
    val displayName:String,
    val price:String,
    val sortOrder:Int,
    val cashDiscountBehavior:String,
    val commercialRevision:Long,
    val consumptionRevision:Long
)

const val MENU_PACKAGE_FORMAT="cuentame-menu-package"
const val MENU_PACKAGE_FORMAT_VERSION=1
