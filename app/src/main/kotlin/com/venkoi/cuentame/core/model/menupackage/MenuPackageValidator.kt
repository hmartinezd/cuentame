package com.venkoi.cuentame.core.model.menupackage

import com.venkoi.cuentame.core.model.menu.CashDiscountBehavior
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

enum class MenuPackageValidationCode { WRONG_FORMAT,UNSUPPORTED_VERSION,BLANK_PACKAGE_ID,BLANK_RESTAURANT_ID,INVALID_TIMESTAMP,INVALID_CURRENCY,BLANK_MENU_ID,BLANK_MENU_NAME,INVALID_PUBLICATION_REVISION,INVALID_DISCOUNT,NO_CATEGORIES,BLANK_CATEGORY_ID,BLANK_CATEGORY_NAME,NEGATIVE_CATEGORY_SORT_ORDER,DUPLICATE_CATEGORY_ID,NO_ITEMS,BLANK_SELLABLE_ITEM_ID,BLANK_ITEM_NAME,INVALID_PRICE,NEGATIVE_ITEM_SORT_ORDER,INVALID_CASH_DISCOUNT_BEHAVIOR,NEGATIVE_COMMERCIAL_REVISION,NEGATIVE_CONSUMPTION_REVISION,DUPLICATE_SELLABLE_ITEM_ID }
data class MenuPackageValidationFailure(val code:MenuPackageValidationCode)

object MenuPackageValidator {
    fun validate(value:MenuPackageV1):MenuPackageValidationFailure? {
        if(value.format!=MENU_PACKAGE_FORMAT)return failure(MenuPackageValidationCode.WRONG_FORMAT)
        if(value.formatVersion!=MENU_PACKAGE_FORMAT_VERSION)return failure(MenuPackageValidationCode.UNSUPPORTED_VERSION)
        if(value.packageId.isBlank())return failure(MenuPackageValidationCode.BLANK_PACKAGE_ID)
        if(value.restaurantId.isBlank())return failure(MenuPackageValidationCode.BLANK_RESTAURANT_ID)
        if(runCatching{Instant.parse(value.publishedAt)}.isFailure)return failure(MenuPackageValidationCode.INVALID_TIMESTAMP)
        if(runCatching{Currency.getInstance(value.currency)}.isFailure)return failure(MenuPackageValidationCode.INVALID_CURRENCY)
        val menu=value.menu
        if(menu.menuId.isBlank())return failure(MenuPackageValidationCode.BLANK_MENU_ID)
        if(menu.name.isBlank())return failure(MenuPackageValidationCode.BLANK_MENU_NAME)
        if(menu.publicationRevision<=0)return failure(MenuPackageValidationCode.INVALID_PUBLICATION_REVISION)
        val discount=menu.defaultCashDiscountPercent.decimalOrNull()?:return failure(MenuPackageValidationCode.INVALID_DISCOUNT)
        if(discount<BigDecimal.ZERO||discount>=BigDecimal("100"))return failure(MenuPackageValidationCode.INVALID_DISCOUNT)
        if(menu.categories.isEmpty())return failure(MenuPackageValidationCode.NO_CATEGORIES)
        val categoryIds=mutableSetOf<String>();val itemIds=mutableSetOf<String>();var itemCount=0
        menu.categories.forEach{category->
            if(category.categoryId.isBlank())return failure(MenuPackageValidationCode.BLANK_CATEGORY_ID)
            if(category.name.isBlank())return failure(MenuPackageValidationCode.BLANK_CATEGORY_NAME)
            if(category.sortOrder<0)return failure(MenuPackageValidationCode.NEGATIVE_CATEGORY_SORT_ORDER)
            if(!categoryIds.add(category.categoryId))return failure(MenuPackageValidationCode.DUPLICATE_CATEGORY_ID)
            category.items.forEach{item->itemCount++
                if(item.sellableItemId.isBlank())return failure(MenuPackageValidationCode.BLANK_SELLABLE_ITEM_ID)
                if(item.displayName.isBlank())return failure(MenuPackageValidationCode.BLANK_ITEM_NAME)
                val price=item.price.decimalOrNull()?:return failure(MenuPackageValidationCode.INVALID_PRICE)
                if(price<BigDecimal.ZERO)return failure(MenuPackageValidationCode.INVALID_PRICE)
                if(item.sortOrder<0)return failure(MenuPackageValidationCode.NEGATIVE_ITEM_SORT_ORDER)
                if(runCatching{CashDiscountBehavior.valueOf(item.cashDiscountBehavior)}.isFailure)return failure(MenuPackageValidationCode.INVALID_CASH_DISCOUNT_BEHAVIOR)
                if(item.commercialRevision<0)return failure(MenuPackageValidationCode.NEGATIVE_COMMERCIAL_REVISION)
                if(item.consumptionRevision<0)return failure(MenuPackageValidationCode.NEGATIVE_CONSUMPTION_REVISION)
                if(!itemIds.add(item.sellableItemId))return failure(MenuPackageValidationCode.DUPLICATE_SELLABLE_ITEM_ID)
            }
        }
        return if(itemCount==0)failure(MenuPackageValidationCode.NO_ITEMS) else null
    }
    private fun failure(code:MenuPackageValidationCode)=MenuPackageValidationFailure(code)
    private fun String.decimalOrNull()=runCatching{BigDecimal(this)}.getOrNull()
}
