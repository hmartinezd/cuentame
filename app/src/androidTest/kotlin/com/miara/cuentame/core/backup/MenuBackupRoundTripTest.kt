package com.miara.cuentame.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.RestoreDatabaseApplier
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.backup.platform.toEntity
import com.miara.cuentame.core.model.menu.CashDiscountBehavior
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MenuBackupRoundTripTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var planner: BackupCreationPlanner

    @Inject
    lateinit var snapshotSource: BackupSnapshotSource

    @Inject
    lateinit var applier: RestoreDatabaseApplier

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    private val restId = RestaurantId("rest-menu")

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun menuRecipeBackupRoundTrip_preservesAllData() = runBlocking {
        // 1. Seed database with menu recipes
        seedDatabaseWithMenuRecipes()

        // 2. Create backup plan
        val restaurant = Restaurant(restId, "Menu Test", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        val snapshotResult = snapshotSource.loadSnapshot(restId.value)
        
        val planResult = planner.createPlan(restaurant, snapshotResult)
        assertThat(planResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        // 3. Restore
        applier.replaceWithBackup(plan.snapshotDto, plan.manifest)
        
        // 4. Verify equality
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        
        assertThat(restoredSnapshot.menuRecipes).hasSize(1)
        assertThat(restoredSnapshot.menuRecipeComponents).hasSize(1)
        assertThat(restoredSnapshot.menus).hasSize(1)
        assertThat(restoredSnapshot.menuCategories).hasSize(1)
        assertThat(restoredSnapshot.menuPlacements).hasSize(1)
        assertThat(restoredSnapshot.menuPublications).hasSize(1)
        assertThat(restoredSnapshot.menuPublicationCategories).hasSize(1)
        assertThat(restoredSnapshot.menuPublicationItems).hasSize(1)
        assertThat(restoredSnapshot.menuPublicationItemComponents).hasSize(1)
        
        val originalRecipe = plan.snapshotDto.menuRecipes[0]
        val restoredRecipe = restoredSnapshot.menuRecipes[0]
        assertThat(restoredRecipe.id).isEqualTo(originalRecipe.id)
        assertThat(restoredRecipe.name).isEqualTo(originalRecipe.name)
        assertThat(restoredRecipe.sellingPrice).isEqualTo(originalRecipe.sellingPrice)
        assertThat(restoredRecipe.cashDiscountBehavior).isEqualTo("NONE")
        assertThat(restoredRecipe.commercialRevision).isEqualTo(3)
        assertThat(restoredRecipe.consumptionRevision).isEqualTo(4)
        assertThat(restoredSnapshot.menus.single().publicationRevision).isEqualTo(1)
        assertThat(restoredSnapshot.menus.single().normalizedName).isEqualTo("dinner")
        assertThat(restoredSnapshot.menuCategories.single().menuId).isEqualTo("menu-1")
        assertThat(restoredSnapshot.menuPlacements.single().menuRecipeId).isEqualTo(originalRecipe.id)
        
        val originalComp = plan.snapshotDto.menuRecipeComponents[0]
        val restoredComp = restoredSnapshot.menuRecipeComponents[0]
        assertThat(restoredComp.id).isEqualTo(originalComp.id)
        assertThat(restoredComp.quantityBase).isEqualTo(originalComp.quantityBase)
        assertThat(restoredSnapshot.menuPublications.single().currencyCodeSnapshot).isEqualTo("USD")
        assertThat(restoredSnapshot.menuPublicationItems.single().sellingPriceSnapshot).isEqualTo("15.99")
    }

    private suspend fun seedDatabaseWithMenuRecipes() {
        database.restaurantDao().insert(RestaurantEntity(restId.value, "Menu Test", "USD", "en-US", 0, 0, null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        
        val ing1 = "ing-menu-1"
        database.ingredientDao().insert(IngredientEntity(ing1, restId.value, "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 100, 100, null))
        
        val opt1 = "opt-menu-1"
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(opt1, ing1, "Unit", "unit", null, BigDecimal.ONE, true, true, true, true, 100, 100, null))

        val recipeId = "menu-rec-1"
        database.menuRecipeDao().insertRecipe(MenuRecipeEntity(
            id = recipeId,
            restaurantId = restId.value,
            name = "Test Dish",
            normalizedName = "test dish",
            sellingPrice = BigDecimal("15.99"),
            notes = "Delicious",
            cashDiscountBehavior = CashDiscountBehavior.NONE,
            commercialRevision = 3,
            consumptionRevision = 4,
            archivedAt = null,
            createdAt = 100,
            updatedAt = 100
        ))
        
        database.menuRecipeDao().upsertComponent(MenuRecipeComponentEntity(
            id = "menu-comp-1",
            menuRecipeId = recipeId,
            ingredientId = ing1,
            ingredientUnitOptionId = opt1,
            quantityEntered = BigDecimal("2.5"),
            quantityBase = BigDecimal("2.5"),
            sortOrder = 0,
            createdAt = 100,
            updatedAt = 100
        ))
        database.menuCatalogDao().insertMenu(MenuEntity("menu-1",restId.value,"Dinner","dinner","Main",BigDecimal("5.00"),1,null,100,100))
        database.menuCatalogDao().insertCategory(MenuCategoryEntity("category-1","menu-1","Mains","mains",0))
        database.menuCatalogDao().insertPlacement(MenuPlacementEntity("placement-1","menu-1","category-1",recipeId,0))
        database.menuPublicationDao().insertPublication(MenuPublicationEntity("publication-1",restId.value,"menu-1",1,"Dinner","Main",BigDecimal("5.00"),"USD",200))
        database.menuPublicationDao().insertCategories(listOf(MenuPublicationCategoryEntity("publication-category-1","publication-1","category-1","Mains",0)))
        database.menuPublicationDao().insertItems(listOf(MenuPublicationItemEntity("publication-item-1","publication-1","publication-category-1","placement-1",recipeId,"Test Dish",BigDecimal("15.99"),CashDiscountBehavior.NONE,3,4,0)))
        database.menuPublicationDao().insertComponents(listOf(MenuPublicationItemComponentEntity("publication-component-1","publication-item-1","menu-comp-1",ing1,opt1,BigDecimal("2.5"),BigDecimal("2.5"),0)))
    }
}
