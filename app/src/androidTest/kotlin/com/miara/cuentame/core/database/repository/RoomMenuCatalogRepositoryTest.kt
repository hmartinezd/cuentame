package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.MenuCatalogPersistenceException
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
        assertFailsWith<Exception>{repository.saveCategory(m1,null,"SIDES",2)}
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
        assertFailsWith<Exception>{repository.savePlacement(m1,null,c1,recipe,2)}
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.savePlacement(m1,null,c2,recipe,0)}
        db.restaurantDao().insert(RestaurantEntity("r2","Other","USD","en-US",0,0,null));val other=recipes.create(RestaurantId("r2"),"Other",null,null)
        assertFailsWith<MenuCatalogPersistenceException.OwnershipMismatch>{repository.savePlacement(m1,null,c1,other,0)}
        repository.removeCategory(m1,c1);assertThat(repository.observePlacements(m1).first()).isEmpty()
        assertThat(recipes.observeRecipe(recipe).first()).isNotNull()
    }

    private suspend inline fun <reified T:Throwable> assertFailsWith(crossinline block:suspend()->Unit) {
        var failure:Throwable?=null;try{block()}catch(t:Throwable){failure=t};assertThat(failure).isInstanceOf(T::class.java)
    }
}
