package com.venkoi.restaurantops.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.IngredientEntity
import com.venkoi.restaurantops.core.database.entity.IngredientUnitOptionEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.database.entity.UnitEntity
import com.venkoi.restaurantops.core.domain.repository.MenuRecipeValidationException
import com.venkoi.restaurantops.core.domain.repository.NewMenuItem
import com.venkoi.restaurantops.core.domain.repository.NewMenuItemComponent
import com.venkoi.restaurantops.core.model.menu.CashDiscountBehavior
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class RoomMenuItemCreationRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var catalogs: RoomMenuCatalogRepository
    private lateinit var recipes: RoomMenuRecipeRepository
    private lateinit var creation: RoomMenuItemCreationRepository
    private val restaurantId = RestaurantId("restaurant")

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()
        db.restaurantDao().insert(RestaurantEntity("restaurant", "Test Restaurant", "USD", "en-US", 0, 0, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("each", "Each", "ea", "COUNT", BigDecimal.ONE, true, 0)))
        seedIngredient("beef", "Beef")
        seedIngredient("cheese", "Cheese")
        recipes = RoomMenuRecipeRepository(db, db.menuRecipeDao(), db.ingredientDao(), db.ingredientUnitOptionDao())
        catalogs = RoomMenuCatalogRepository(db, db.menuCatalogDao(), db.menuRecipeDao(), db.restaurantDao())
        creation = RoomMenuItemCreationRepository(db, catalogs, recipes)
    }

    @After
    fun close() = db.close()

    @Test
    fun successPersistsRecipeCommercialFieldsComponentsAndRequestedPlacement() = runBlocking {
        val menu = catalogs.createMenu(restaurantId, "Dinner", null, BigDecimal("4.5"))
        val category = catalogs.saveCategory(menu, null, "Mains", 0)

        val id = creation.create(request(menu.value, category.value, "Cheeseburger"))

        val persisted = recipes.observeRecipes(restaurantId, true).first()
        assertThat(persisted).hasSize(1)
        assertThat(persisted.single().id).isEqualTo(id)
        assertThat(persisted.single().sellingPrice).isEqualTo(BigDecimal("14.50"))
        assertThat(persisted.single().cashDiscountBehavior).isEqualTo(CashDiscountBehavior.NONE)
        val components = recipes.observeComponents(id).first()
        assertThat(components.map { it.ingredientId.value }).containsExactly("beef", "cheese").inOrder()
        assertThat(components.map { it.sortOrder }).containsExactly(0, 1).inOrder()
        val placements = catalogs.observePlacements(menu).first()
        assertThat(placements).hasSize(1)
        assertThat(placements.single().menuId).isEqualTo(menu)
        assertThat(placements.single().categoryId).isEqualTo(category)
        assertThat(placements.single().menuRecipeId).isEqualTo(id)
    }

    @Test
    fun invalidIngredientUnitRelationshipRollsBackRecipeThenCorrectedRetryIsClean() = runBlocking {
        val menu = catalogs.createMenu(restaurantId, "Dinner", null, BigDecimal.ZERO)
        val category = catalogs.saveCategory(menu, null, "Mains", 0)
        val existingRecipe = recipes.create(restaurantId, "Existing Item", BigDecimal.TEN, null)
        catalogs.savePlacement(menu, null, category, existingRecipe, 0)
        val existingRecipes = recipes.observeRecipes(restaurantId, true).first()
        val existingPlacements = catalogs.observePlacements(menu).first()

        val invalid = request(menu.value, category.value, "Cheeseburger").copy(
            components = listOf(
                NewMenuItemComponent(IngredientId("beef"), IngredientUnitOptionId("beef-option"), BigDecimal.ONE),
                // The option exists, but belongs to beef. Failure occurs while saving the second component,
                // after the recipe and first component have both been inserted.
                NewMenuItemComponent(IngredientId("cheese"), IngredientUnitOptionId("beef-option"), BigDecimal.ONE)
            )
        )
        var failure: Throwable? = null
        try {
            creation.create(invalid)
        } catch (throwable: Throwable) {
            failure = throwable
        }

        assertThat(failure).isInstanceOf(MenuRecipeValidationException.UnitOptionMismatch::class.java)
        assertThat(recipes.observeRecipes(restaurantId, true).first()).isEqualTo(existingRecipes)
        assertThat(db.menuRecipeDao().getComponents(existingRecipe.value)).isEmpty()
        assertThat(catalogs.observePlacements(menu).first()).isEqualTo(existingPlacements)

        val created = creation.create(request(menu.value, category.value, "Cheeseburger"))

        val recipesAfterRetry = recipes.observeRecipes(restaurantId, true).first()
        assertThat(recipesAfterRetry).hasSize(2)
        assertThat(recipesAfterRetry.count { it.name == "Cheeseburger" }).isEqualTo(1)
        assertThat(recipes.observeComponents(created).first()).hasSize(2)
        assertThat(recipes.observeComponents(created).first().map { it.ingredientId.value })
            .containsExactly("beef", "cheese").inOrder()
        val placementsAfterRetry = catalogs.observePlacements(menu).first()
        assertThat(placementsAfterRetry).hasSize(2)
        assertThat(placementsAfterRetry.count { it.menuRecipeId == created }).isEqualTo(1)
        assertThat(placementsAfterRetry).containsAtLeastElementsIn(existingPlacements)
        Unit
    }

    private fun request(menuId: String, categoryId: String, name: String) = NewMenuItem(
        menuId = com.venkoi.restaurantops.core.common.ids.MenuId(menuId),
        categoryId = com.venkoi.restaurantops.core.common.ids.MenuCategoryId(categoryId),
        name = name,
        sellingPrice = BigDecimal("14.50"),
        cashDiscountBehavior = CashDiscountBehavior.NONE,
        components = listOf(
            NewMenuItemComponent(IngredientId("beef"), IngredientUnitOptionId("beef-option"), BigDecimal("2")),
            NewMenuItemComponent(IngredientId("cheese"), IngredientUnitOptionId("cheese-option"), BigDecimal.ONE)
        )
    )

    private suspend fun seedIngredient(id: String, name: String) {
        db.ingredientDao().insert(
            IngredientEntity(id, "restaurant", name, name.lowercase(), null, "each", null, null, null, null, true, 0, 0, null)
        )
        db.ingredientUnitOptionDao().insert(
            IngredientUnitOptionEntity("$id-option", id, "Each", "ea", "each", BigDecimal.ONE, true, true, true, true, 0, 0, null)
        )
    }
}
