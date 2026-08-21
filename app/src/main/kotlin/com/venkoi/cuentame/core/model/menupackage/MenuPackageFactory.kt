package com.venkoi.cuentame.core.model.menupackage

import com.venkoi.cuentame.core.common.decimal.toCanonicalDecimalString
import com.venkoi.cuentame.core.model.menu.MenuPublicationSnapshot

sealed class MenuPackageMappingException(message:String):IllegalArgumentException(message){
    class PublicationMismatch:MenuPackageMappingException("Publication snapshot contains rows from another publication")
    class UnknownCategory:MenuPackageMappingException("Publication item references an unknown category")
}

object MenuPackageFactory {
    fun create(snapshot:MenuPublicationSnapshot):MenuPackageV1 {
        val publication=snapshot.publication
        if(snapshot.categories.any{it.publicationId!=publication.id}||snapshot.items.any{it.publicationId!=publication.id})throw MenuPackageMappingException.PublicationMismatch()
        val categoryIds=snapshot.categories.map{it.id}.toSet()
        if(snapshot.items.any{it.publicationCategoryId !in categoryIds})throw MenuPackageMappingException.UnknownCategory()
        val categories=snapshot.categories.sortedWith(compareBy({it.sortOrder},{it.sourceMenuCategoryId.value})).map{category->
            MenuPackageCategoryV1(category.sourceMenuCategoryId.value,category.nameSnapshot,category.sortOrder,
                snapshot.items.filter{it.publicationCategoryId==category.id}.sortedWith(compareBy({it.sortOrder},{it.menuRecipeId.value})).map{item->
                    MenuPackageItemV1(item.menuRecipeId.value,item.displayNameSnapshot,item.sellingPriceSnapshot.toCanonicalDecimalString(),item.sortOrder,item.cashDiscountBehaviorSnapshot.name,item.commercialRevision,item.consumptionRevision)
                })
        }
        return MenuPackageV1(MENU_PACKAGE_FORMAT,MENU_PACKAGE_FORMAT_VERSION,publication.id.value,publication.restaurantId.value,publication.publishedAt.toString(),publication.currencyCodeSnapshot,
            MenuPackageMenuV1(publication.sourceMenuId.value,publication.menuNameSnapshot,publication.menuDescriptionSnapshot,publication.publicationRevision,publication.defaultCashDiscountPercentSnapshot.toCanonicalDecimalString(),categories))
    }
}
