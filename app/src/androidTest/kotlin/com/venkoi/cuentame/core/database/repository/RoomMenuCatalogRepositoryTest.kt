package com.venkoi.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.*
import com.venkoi.cuentame.core.domain.repository.MenuCatalogPersistenceException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class RoomMenuCatalogRepositoryTest {
    private lateinit var db:RestaurantInventoryDatabase
    private lateinit var repository:RoomMenuCatalogRepository
    private lateinit var recipes:RoomMenuRecipeRepository
    private val restaurant=RestaurantId("r1")

    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RestaurantInventoryDatabase::class.java).allowMainThreadQueries().build()
        db.restaurantDao().insert(RestaurantEntity("r1","R","USD","en-US",0,0,null))
        repository=RoomMenuCatalogRepository(db,db.menuCatalogDao(),db.menuRecipeDao(),db.restaurantDao())
        recipes=RoomMenuRecipeRepository(db,db.menuRecipeDao(),db.ingredientDao(),db.ingredientUnitOptionDao())
    }
    @After fun close()=db.close()

    @Test fun menuLifecycleCanonicalizationAndArchivedNameReuse()=runBlocking {
        val first=repository.createMenu(restaurant,"  Dinner  ","  Main menu  ",BigDecimal("5.00"))
        var menu=repository.observeMenu(first).first()!!
        assertThat(menu.name).isEqualTo("Dinner");assertThat(menu.normalizedName).isEqualTo("dinner")
        assertThat(menu.description).isEqualTo("Main menu");assertThat(menu.publicationRevision).isEqualTo(0)
        repository.updateMenu(first,"Evening",null,BigDecimal("7.5"));menu=repository.observeMenu(first).first()!!
        assertThat(menu.name).isEqualTo("Evening");assertThat(menu.defaultCashDiscountPercent.compareTo(BigDecimal("7.5"))).isEqualTo(0)
        assertFailsWith<MenuCatalogPersistenceException.DuplicateName>{repository.createMenu(restaurant," EVENING ",null,BigDecimal.ZERO)}
        repository.setArchived(first,true);assertThat(repository.observeMenus(restaurant).first()).isEmpty()
        val replacement=repository.createMenu(restaurant,"Evening",null,BigDecimal.ZERO)
        assertFailsWith<MenuCatalogPersistenceException.DuplicateName>{repository.setArchived(first,false)}
        repository.setArchived(replacement,true);repository.setArchived(first,false)
        assertThat(repository.observeMenus(restaurant).first().single().id).isEqualTo(first)
    }

    @Test fun categoriesAreUniquePerMenuOrderedAndOwnershipSafe()=runBlocking {
        val m1=repository.createMenu(restaurant,"Dinner",null,BigDecimal.ZERO);val m2=repository.createMenu(restaurant,"Lunch",null,BigDecimal.ZERO)
        val c2=repository.saveCategory(m1,null,"  Sides ",1);val c1=repository.saveCategory(m1,null,"Mains",1)
        val rows=repository.observeCategories(m1).first();assertThat(rows.map{it.id.value}).containsExactlyElementsIn(listOf(c1.value,c2.value).sorted()).inOrder()
        assertThat(rows.single{it.id==c2}.normalizedName).isEqualTo("sides")
        assertFailsWith<MenuCatalogPersistenceException.DuplicateCategoryName>{repository.saveCategory(m1,null,"SIDES",2)}
        val entrees=repository.saveCategory(m1,null,"Entrees",2)
        assertFailsWith<MenuCatalogPersistenceException.DuplicateCategoryName>{repository.saveCategory(m1,entrees," SIDES ",3)}
        repository.saveCategory(m2,null,"Sides",0)
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.saveCategory(m2,c1,"Moved",0)}
    }

    @Test fun placementsEnforceMenuAndRestaurantOwnershipAndCascadeOnlyPlacements()=runBlocking {
        val recipe=recipes.create(restaurant,"Burger",BigDecimal.TEN,null)
        val secondRecipe=recipes.create(restaurant,"Fries",BigDecimal.ONE,null)
        val m1=repository.createMenu(restaurant,"Dinner",null,BigDecimal.ZERO);val m2=repository.createMenu(restaurant,"Delivery",null,BigDecimal.ZERO)
        val c1=repository.saveCategory(m1,null,"Mains",0);val c2=repository.saveCategory(m2,null,"Mains",0)
        val p1=repository.savePlacement(m1,null,c1,recipe,1);val p2=repository.savePlacement(m1,null,c1,secondRecipe,1);repository.savePlacement(m2,null,c2,recipe,1)
        assertThat(repository.observePlacements(m1).first().map{it.id.value}).containsExactlyElementsIn(listOf(p1.value,p2.value).sorted()).inOrder()
        assertFailsWith<MenuCatalogPersistenceException.DuplicateMenuRecipePlacement>{repository.savePlacement(m1,null,c1,recipe,2)}
        repository.savePlacement(m1,p1,c1,recipe,3)
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.savePlacement(m1,null,c2,recipe,0)}
        db.restaurantDao().insert(RestaurantEntity("r2","Other","USD","en-US",0,0,null));val other=recipes.create(RestaurantId("r2"),"Other",null,null)
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.savePlacement(m1,null,c1,other,0)}
        repository.removeCategory(m1,c1);assertThat(repository.observePlacements(m1).first()).isEmpty()
        assertThat(recipes.observeRecipe(recipe).first()).isNotNull()
    }

    @Test fun categoryReorderPersistsNormalizedOrderAndRejectsInvalidSetsAtomically()=runBlocking {
        val menu=repository.createMenu(restaurant,"Dinner",null,BigDecimal.ZERO)
        val a=repository.saveCategory(menu,null,"A",50);val b=repository.saveCategory(menu,null,"B",10);val c=repository.saveCategory(menu,null,"C",30)
        repository.reorderCategories(menu,listOf(c,a,b))
        assertThat(repository.observeCategories(menu).first().map{it.id}).containsExactly(c,a,b).inOrder()
        assertThat(repository.observeCategories(menu).first().map{it.sortOrder}).containsExactly(0,10,20).inOrder()

        val stable=repository.observeCategories(menu).first()
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.reorderCategories(menu,listOf(c,c,b))}
        assertThat(repository.observeCategories(menu).first()).isEqualTo(stable)
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.reorderCategories(menu,listOf(c,a))}
        assertThat(repository.observeCategories(menu).first()).isEqualTo(stable)
        val other=repository.createMenu(restaurant,"Lunch",null,BigDecimal.ZERO);val foreign=repository.saveCategory(other,null,"Other",0)
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.reorderCategories(menu,listOf(c,a,foreign))}
        assertThat(repository.observeCategories(menu).first()).isEqualTo(stable)
    }

    @Test fun placementReorderPersistsNormalizedOrderAndRejectsInvalidSetsAtomically()=runBlocking {
        val menu=repository.createMenu(restaurant,"Dinner",null,BigDecimal.ZERO);val category=repository.saveCategory(menu,null,"Items",0)
        val ra=recipes.create(restaurant,"A",null,null);val rb=recipes.create(restaurant,"B",null,null);val rc=recipes.create(restaurant,"C",null,null)
        val a=repository.savePlacement(menu,null,category,ra,50);val b=repository.savePlacement(menu,null,category,rb,10);val c=repository.savePlacement(menu,null,category,rc,30)
        repository.reorderPlacements(menu,listOf(c,a,b))
        assertThat(repository.observePlacements(menu).first().map{it.id}).containsExactly(c,a,b).inOrder()
        assertThat(repository.observePlacements(menu).first().map{it.sortOrder}).containsExactly(0,10,20).inOrder()

        val stable=repository.observePlacements(menu).first()
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.reorderPlacements(menu,listOf(c,c,b))}
        assertThat(repository.observePlacements(menu).first()).isEqualTo(stable)
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.reorderPlacements(menu,listOf(c,a))}
        assertThat(repository.observePlacements(menu).first()).isEqualTo(stable)
        val other=repository.createMenu(restaurant,"Lunch",null,BigDecimal.ZERO);val otherCategory=repository.saveCategory(other,null,"Items",0)
        val foreign=repository.savePlacement(other,null,otherCategory,ra,0)
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.reorderPlacements(menu,listOf(c,a,foreign))}
        assertThat(repository.observePlacements(menu).first()).isEqualTo(stable)
    }

    private suspend inline fun <reified T:Throwable> assertFailsWith(crossinline block:suspend()->Unit) {
        var failure:Throwable?=null;try{block()}catch(t:Throwable){failure=t};assertThat(failure).isInstanceOf(T::class.java)
    }
}
