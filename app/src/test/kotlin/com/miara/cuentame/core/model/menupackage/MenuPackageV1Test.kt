package com.miara.cuentame.core.model.menupackage

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.model.menu.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class MenuPackageV1Test {
    @Test fun `factory encoding matches golden and golden strictly decodes`() {
        val encoded=MenuPackageJsonCodec.encode(MenuPackageFactory.create(snapshot()))
        val golden=requireNotNull(javaClass.getResource("/menupackage/menu-package-v1-golden.json")).readText().trimEnd()
        assertThat(encoded).isEqualTo(golden)
        assertThat(MenuPackageJsonCodec.decodeAndValidate(golden)).isInstanceOf(MenuPackageDecodeResult.Success::class.java)
    }

    @Test fun `encoding is byte deterministic and independent of aggregate order`() {
        val original=snapshot();val a=MenuPackageJsonCodec.encode(MenuPackageFactory.create(original));val b=MenuPackageJsonCodec.encode(MenuPackageFactory.create(original.copy(categories=original.categories.reversed(),items=original.items.reversed())))
        assertThat(a).isEqualTo(b);assertThat(a.toByteArray()).isEqualTo(b.toByteArray())
    }

    @Test fun `factory rejects malformed publication relationships`() {
        val original=snapshot();val foreign=original.items.first().copy(publicationCategoryId=MenuPublicationCategoryId("missing"))
        assertThat(runCatching{MenuPackageFactory.create(original.copy(items=listOf(foreign)))}.exceptionOrNull()).isInstanceOf(MenuPackageMappingException.UnknownCategory::class.java)
    }

    @Test fun `validator rejects every required invalid contract condition`() {
        val valid=MenuPackageFactory.create(snapshot());val menu=valid.menu;val category=menu.categories.first();val item=category.items.first()
        val cases=listOf(
            valid.copy(format="wrong") to MenuPackageValidationCode.WRONG_FORMAT,
            valid.copy(formatVersion=2) to MenuPackageValidationCode.UNSUPPORTED_VERSION,
            valid.copy(packageId=" ") to MenuPackageValidationCode.BLANK_PACKAGE_ID,
            valid.copy(restaurantId="") to MenuPackageValidationCode.BLANK_RESTAURANT_ID,
            valid.copy(publishedAt="yesterday") to MenuPackageValidationCode.INVALID_TIMESTAMP,
            valid.copy(currency="NOPE") to MenuPackageValidationCode.INVALID_CURRENCY,
            valid.copy(menu=menu.copy(menuId="")) to MenuPackageValidationCode.BLANK_MENU_ID,
            valid.copy(menu=menu.copy(name=" ")) to MenuPackageValidationCode.BLANK_MENU_NAME,
            valid.copy(menu=menu.copy(publicationRevision=0)) to MenuPackageValidationCode.INVALID_PUBLICATION_REVISION,
            valid.copy(menu=menu.copy(defaultCashDiscountPercent="-1")) to MenuPackageValidationCode.INVALID_DISCOUNT,
            valid.copy(menu=menu.copy(defaultCashDiscountPercent="100")) to MenuPackageValidationCode.INVALID_DISCOUNT,
            valid.copy(menu=menu.copy(categories=emptyList())) to MenuPackageValidationCode.NO_CATEGORIES,
            valid.copy(menu=menu.copy(categories=listOf(category.copy(categoryId="")))) to MenuPackageValidationCode.BLANK_CATEGORY_ID,
            valid.copy(menu=menu.copy(categories=listOf(category,category))) to MenuPackageValidationCode.DUPLICATE_CATEGORY_ID,
            valid.copy(menu=menu.copy(categories=listOf(category.copy(name="")))) to MenuPackageValidationCode.BLANK_CATEGORY_NAME,
            valid.copy(menu=menu.copy(categories=listOf(category.copy(sortOrder=-1)))) to MenuPackageValidationCode.NEGATIVE_CATEGORY_SORT_ORDER,
            valid.copy(menu=menu.copy(categories=menu.categories.map{it.copy(items=emptyList())})) to MenuPackageValidationCode.NO_ITEMS,
            valid.copy(menu=menu.copy(categories=listOf(category.copy(items=listOf(item,item))))) to MenuPackageValidationCode.DUPLICATE_SELLABLE_ITEM_ID,
            withItem(valid,item.copy(sellableItemId="")) to MenuPackageValidationCode.BLANK_SELLABLE_ITEM_ID,
            withItem(valid,item.copy(displayName=" ")) to MenuPackageValidationCode.BLANK_ITEM_NAME,
            withItem(valid,item.copy(price="-1")) to MenuPackageValidationCode.INVALID_PRICE,
            withItem(valid,item.copy(cashDiscountBehavior="SOMETIMES")) to MenuPackageValidationCode.INVALID_CASH_DISCOUNT_BEHAVIOR,
            withItem(valid,item.copy(commercialRevision=-1)) to MenuPackageValidationCode.NEGATIVE_COMMERCIAL_REVISION,
            withItem(valid,item.copy(consumptionRevision=-1)) to MenuPackageValidationCode.NEGATIVE_CONSUMPTION_REVISION,
            withItem(valid,item.copy(sortOrder=-1)) to MenuPackageValidationCode.NEGATIVE_ITEM_SORT_ORDER
        )
        cases.forEach{(value,code)->assertThat(MenuPackageValidator.validate(value)?.code).isEqualTo(code)}
    }

    @Test fun `strict codec rejects unknown keys field types and enum values`() {
        val json=MenuPackageJsonCodec.encode(MenuPackageFactory.create(snapshot()))
        assertThat(MenuPackageJsonCodec.decodeAndValidate(json.replaceFirst("{","{\n  \"extra\": true,"))).isInstanceOf(MenuPackageDecodeResult.InvalidJson::class.java)
        assertThat(MenuPackageJsonCodec.decodeAndValidate(json.replace("\"formatVersion\": 1","\"formatVersion\": \"1\""))).isInstanceOf(MenuPackageDecodeResult.InvalidJson::class.java)
        assertThat(MenuPackageJsonCodec.decodeAndValidate(json.replace("APPLY_DEFAULT","UNKNOWN"))).isInstanceOf(MenuPackageDecodeResult.InvalidPackage::class.java)
    }

    private fun withItem(value:MenuPackageV1,item:MenuPackageItemV1)=value.copy(menu=value.menu.copy(categories=value.menu.categories.mapIndexed{i,c->if(i==0)c.copy(items=listOf(item))else c}))
    private fun snapshot():MenuPublicationSnapshot{
        val pid=MenuPublicationId("publication-3");val appetizer=MenuPublicationCategory(MenuPublicationCategoryId("pc-app"),pid,MenuCategoryId("appetizers"),"Appetizers",10);val entree=MenuPublicationCategory(MenuPublicationCategoryId("pc-entree"),pid,MenuCategoryId("entrees"),"Entrees",20)
        fun item(id:String,category:MenuPublicationCategory,name:String,price:String,order:Int,behavior:CashDiscountBehavior,commercial:Long,consumption:Long)=MenuPublicationItem(MenuPublicationItemId("pi-$id"),pid,category.id,MenuPlacementId("placement-$id"),MenuRecipeId(id),name,BigDecimal(price),behavior,commercial,consumption,order)
        return MenuPublicationSnapshot(MenuPublication(pid,RestaurantId("restaurant-1"),MenuId("menu-1"),3,"Dinner Menu","Dinner service",BigDecimal("3.00"),"USD",Instant.parse("2026-08-16T17:30:00Z")),listOf(entree,appetizer),listOf(item("steak",entree,"Steak","25.500",20,CashDiscountBehavior.APPLY_DEFAULT,6,8),item("wings",appetizer,"Wings","12.00",20,CashDiscountBehavior.APPLY_DEFAULT,2,5),item("burger",entree,"Burger","13.0",10,CashDiscountBehavior.NONE,4,7)),emptyList())
    }
}
