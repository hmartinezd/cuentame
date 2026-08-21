package com.venkoi.restaurantops.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.*
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.*
import com.venkoi.restaurantops.core.domain.repository.*
import com.venkoi.restaurantops.core.model.menu.CashDiscountBehavior
import com.venkoi.restaurantops.core.model.menupackage.MenuPackageFactory
import com.venkoi.restaurantops.core.model.menupackage.MenuPackageJsonCodec
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.math.BigDecimal
import javax.inject.Inject

@HiltAndroidTest @RunWith(AndroidJUnit4::class)
class RoomMenuPublicationRepositoryTest {
    @get:Rule val hilt=HiltAndroidRule(this)
    @Inject lateinit var db:RestaurantInventoryDatabase
    @Inject lateinit var publications:MenuPublicationRepository
    private val menuId=MenuId("publication-menu")
    @Before fun setup(){hilt.inject()}

    @Test fun publish_snapshotsImmutableState_directComponents_andAllocatesRevisions()=runBlocking{
        seed(price=BigDecimal("13"))
        val firstId=publications.publish(menuId);val first=publications.observePublication(firstId).first()!!
        val firstExport=MenuPackageJsonCodec.encode(MenuPackageFactory.create(first))
        assertThat(first.publication.publicationRevision).isEqualTo(1)
        assertThat(first.publication.menuNameSnapshot).isEqualTo("Dinner")
        assertThat(first.publication.currencyCodeSnapshot).isEqualTo("USD")
        assertThat(first.categories.single().nameSnapshot).isEqualTo("Entrees")
        assertThat(first.items.single().displayNameSnapshot).isEqualTo("Burger")
        assertThat(first.items.single().sellingPriceSnapshot.compareTo(BigDecimal("13"))).isEqualTo(0)
        assertThat(first.items.single().cashDiscountBehaviorSnapshot).isEqualTo(CashDiscountBehavior.APPLY_DEFAULT)
        assertThat(first.items.single().commercialRevision).isEqualTo(2)
        assertThat(first.items.single().consumptionRevision).isEqualTo(3)
        assertThat(first.components.single().sourceMenuRecipeComponentId).isEqualTo(MenuRecipeComponentId("component"))
        assertThat(first.components.single().ingredientId).isEqualTo(IngredientId("ingredient"))
        assertThat(first.components.single().inventoryAreaIdSnapshot).isEqualTo(InventoryAreaId("area-a"))
        assertThat(first.components.single().quantityEnteredSnapshot.compareTo(BigDecimal("2"))).isEqualTo(0)

        db.menuCatalogDao().updateMenu(db.menuCatalogDao().getMenu(menuId.value)!!.copy(name="Late Dinner",normalizedName="late dinner",defaultCashDiscountPercent=BigDecimal("8")))
        db.menuCatalogDao().updateCategory(db.menuCatalogDao().getCategory("category")!!.copy(name="Mains",normalizedName="mains",sortOrder=20))
        db.menuRecipeDao().updateRecipe(db.menuRecipeDao().getRecipe("recipe")!!.copy(name="Big Burger",normalizedName="big burger",sellingPrice=BigDecimal("15"),cashDiscountBehavior=CashDiscountBehavior.NONE,commercialRevision=4,consumptionRevision=5))
        db.menuRecipeDao().upsertComponent(db.menuRecipeDao().getComponent("component")!!.copy(quantityEntered=BigDecimal("4"),quantityBase=BigDecimal("4"),sortOrder=10))
        db.ingredientDao().update(db.ingredientDao().getById("ingredient")!!.copy(defaultAreaId="area-b",updatedAt=200))
        val secondId=publications.publish(menuId);val second=publications.observePublication(secondId).first()!!;val unchanged=publications.observePublication(firstId).first()!!
        val unchangedExport=MenuPackageJsonCodec.encode(MenuPackageFactory.create(unchanged));val secondExport=MenuPackageJsonCodec.encode(MenuPackageFactory.create(second))
        assertThat(second.publication.publicationRevision).isEqualTo(2);assertThat(second.items.single().sellingPriceSnapshot.compareTo(BigDecimal("15"))).isEqualTo(0)
        assertThat(unchanged.publication.menuNameSnapshot).isEqualTo("Dinner");assertThat(unchanged.categories.single().nameSnapshot).isEqualTo("Entrees");assertThat(unchanged.items.single().sellingPriceSnapshot.compareTo(BigDecimal("13"))).isEqualTo(0);assertThat(unchanged.components.single().quantityEnteredSnapshot.compareTo(BigDecimal("2"))).isEqualTo(0)
        assertThat(unchanged.components.single().inventoryAreaIdSnapshot).isEqualTo(InventoryAreaId("area-a"));assertThat(second.components.single().inventoryAreaIdSnapshot).isEqualTo(InventoryAreaId("area-b"))
        assertThat(db.menuCatalogDao().getMenu(menuId.value)!!.publicationRevision).isEqualTo(2)
        assertThat(publications.observePublications(menuId).first().map{it.publicationRevision}).containsExactly(2L,1L).inOrder()
        assertThat(unchangedExport).isEqualTo(firstExport);assertThat(secondExport).isNotEqualTo(firstExport)
    }

    @Test fun missingPrice_failureIsAtomic()=runBlocking{
        seed(price=null)
        val error=runCatching{publications.publish(menuId)}.exceptionOrNull()
        assertThat(error).isInstanceOf(MenuPublicationException.ItemPriceMissing::class.java)
        assertThat(db.menuCatalogDao().getMenu(menuId.value)!!.publicationRevision).isEqualTo(0)
        assertThat(publications.observePublications(menuId).first()).isEmpty()
    }

    @Test fun missingDefaultArea_failureIsAtomic()=runBlocking{
        seed(price=BigDecimal("13"));db.ingredientDao().update(db.ingredientDao().getById("ingredient")!!.copy(defaultAreaId=null))
        assertThat(runCatching{publications.publish(menuId)}.exceptionOrNull()).isInstanceOf(MenuPublicationException.ComponentDefaultAreaMissing::class.java)
        assertThat(db.menuCatalogDao().getMenu(menuId.value)!!.publicationRevision).isEqualTo(0)
        assertThat(publications.observePublications(menuId).first()).isEmpty()
        assertThat(db.menuPublicationDao().getCategories("missing")).isEmpty();assertThat(db.menuPublicationDao().getItems("missing")).isEmpty();assertThat(db.menuPublicationDao().getComponents("missing")).isEmpty()
    }

    @Test fun archivedPlacedItem_failureIsAtomic()=runBlocking{
        seed(price=BigDecimal.ZERO);db.menuRecipeDao().setArchived("recipe",200,200)
        assertThat(runCatching{publications.publish(menuId)}.exceptionOrNull()).isInstanceOf(MenuPublicationException.ItemArchived::class.java)
        assertThat(db.menuCatalogDao().getMenu(menuId.value)!!.publicationRevision).isEqualTo(0);assertThat(publications.observePublications(menuId).first()).isEmpty()
    }

    @Test fun archivedMenu_failureIsAtomic()=runBlocking{
        seed(price=BigDecimal("13"));db.menuCatalogDao().updateMenu(db.menuCatalogDao().getMenu(menuId.value)!!.copy(archivedAt=200))
        assertThat(runCatching{publications.publish(menuId)}.exceptionOrNull()).isInstanceOf(MenuPublicationException.MenuArchived::class.java)
        assertThat(db.menuCatalogDao().getMenu(menuId.value)!!.publicationRevision).isEqualTo(0);assertThat(publications.observePublications(menuId).first()).isEmpty()
    }

    @Test fun preparationRecipe_isNotRecursivelyExpanded()=runBlocking<Unit>{
        seed(price=BigDecimal("13"))
        db.ingredientDao().insert(IngredientEntity("raw","restaurant-publication","Raw","raw",null,"unit",null,null,null,null,true,1,1,null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("raw-option","raw","Unit","unit",null,BigDecimal.ONE,true,true,true,true,1,1,null))
        db.preparationRecipeDao().insert(PreparationRecipeEntity("prep","restaurant-publication","ingredient","Sauce","sauce",BigDecimal.ONE,BigDecimal.ONE,"option","ACTIVE",null,1,1,null))
        db.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity("prep-component","prep","raw","raw-option",BigDecimal("3"),BigDecimal("3"),0,null,1,1))
        val snapshot=publications.observePublication(publications.publish(menuId)).first()!!
        assertThat(snapshot.components.map{it.ingredientId}).containsExactly(IngredientId("ingredient"))
    }

    private suspend fun seed(price:BigDecimal?){
        db.restaurantDao().insert(RestaurantEntity("restaurant-publication","Restaurant","USD","en-US",1,1,null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("unit","Unit","unit","COUNT",BigDecimal.ONE,true,0)))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area-a","restaurant-publication","Area A","area a",0,true,1,1,null));db.inventoryAreaDao().upsert(InventoryAreaEntity("area-b","restaurant-publication","Area B","area b",1,true,1,1,null))
        db.ingredientDao().insert(IngredientEntity("ingredient","restaurant-publication","Sauce","sauce",null,"unit","area-a",null,null,null,true,1,1,null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("option","ingredient","Unit","unit",null,BigDecimal.ONE,true,true,true,true,1,1,null))
        db.menuRecipeDao().insertRecipe(MenuRecipeEntity("recipe","restaurant-publication","Burger","burger",price,null,CashDiscountBehavior.APPLY_DEFAULT,2,3,null,1,1))
        db.menuRecipeDao().upsertComponent(MenuRecipeComponentEntity("component","recipe","ingredient","option",BigDecimal("2"),BigDecimal("2"),0,1,1))
        db.menuCatalogDao().insertMenu(MenuEntity(menuId.value,"restaurant-publication","Dinner","dinner","Menu",BigDecimal("3"),0,null,1,1))
        db.menuCatalogDao().insertCategory(MenuCategoryEntity("category",menuId.value,"Entrees","entrees",0))
        db.menuCatalogDao().insertPlacement(MenuPlacementEntity("placement",menuId.value,"category","recipe",0))
    }
}
