package com.miara.cuentame.core.backup

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.RestoreDatabaseApplier
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.model.RestaurantBackupDto
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.restaurant.Restaurant
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
class BackupRoundTripTest {

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

    @Inject
    lateinit var appVersionProvider: AppVersionProvider

    private val restId = RestaurantId("rest-1")

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun schema4BackupRoundTrip_preservesAllRecipeData() = runBlocking {
        // 1. Seed database with recipes
        seedDatabaseWithRecipes()

        // 2. Create backup plan
        val restaurant = Restaurant(restId, "Test", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        val snapshotResult = snapshotSource.loadSnapshot(restId.value)
        
        assertThat(appVersionProvider.databaseSchemaVersion).isEqualTo(4)
        
        val planResult = planner.createPlan(restaurant, snapshotResult)
        assertThat(planResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        // 3. Clear database (via applier)
        val snapshotDto = plan.snapshotDto
        
        // 4. Restore
        applier.replaceWithBackup(snapshotDto)
        
        // 5. Verify equality
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        
        // Compare DTOs ignoring sorting if necessary (though mapper sorts)
        assertThat(restoredSnapshot.preparationRecipes).hasSize(3)
        assertThat(restoredSnapshot.preparationRecipeComponents).hasSize(2)
        
        assertThat(restoredSnapshot).isEqualTo(snapshotDto)
        
        // Verify specific fields
        val active = restoredSnapshot.preparationRecipes.find { it.status == PreparationRecipeStatus.ACTIVE.name }!!
        assertThat(active.standardYieldQuantity).isEqualTo("10")
        assertThat(active.archivedAt).isNull()
        
        val archived = restoredSnapshot.preparationRecipes.find { it.status == PreparationRecipeStatus.ARCHIVED.name }!!
        assertThat(archived.archivedAt).isNotNull()
    }

    private suspend fun seedDatabaseWithRecipes() {
        database.restaurantDao().insert(RestaurantEntity(restId.value, "Test", "USD", "en-US", 0, 0, null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        
        val ing1 = "ing-1"
        val ing2 = "ing-2"
        database.ingredientDao().insert(IngredientEntity(ing1, restId.value, "Output", "output", null, "u1", null, null, null, null, true, 100, 100, null))
        database.ingredientDao().insert(IngredientEntity(ing2, restId.value, "Comp", "comp", null, "u1", null, null, null, null, true, 100, 100, null))
        
        val opt1 = "opt-1"
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(opt1, ing1, "Unit", "unit", null, BigDecimal.ONE, true, true, true, true, 100, 100, null))
        val opt2 = "opt-2"
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(opt2, ing2, "Unit", "unit", null, BigDecimal.ONE, true, true, true, true, 100, 100, null))

        // Draft
        database.preparationRecipeDao().insert(PreparationRecipeEntity("r-draft", restId.value, ing1, "Draft", "draft", BigDecimal.ONE, BigDecimal.ONE, opt1, "DRAFT", "Notes", 100, 100, null))
        
        // Active
        database.preparationRecipeDao().insert(PreparationRecipeEntity("r-active", restId.value, ing2, "Active", "active", BigDecimal("10.0"), BigDecimal("10.0"), opt2, "ACTIVE", null, 200, 200, null))
        database.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity("c-1", "r-active", ing1, opt1, BigDecimal("1.0"), BigDecimal("1.0"), 0, "Comp notes", 200, 200))

        // Archived
        database.preparationRecipeDao().insert(PreparationRecipeEntity("r-archived", restId.value, ing1, "Old", "old", BigDecimal("1.0"), BigDecimal("1.0"), opt1, "ARCHIVED", null, 50, 50, 500))
        database.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity("c-2", "r-archived", ing2, opt2, BigDecimal("10.0"), BigDecimal("10.0"), 0, null, 50, 50))
    }

    @Test
    fun schema2Compatibility_restoresLegacyDataAndLeavesRecipesEmpty() = runBlocking {
        // 1. Create a Schema 2 DTO (no recipes)
        val legacySnapshot = BackupSnapshotDto(
            restaurants = listOf(RestaurantBackupDto(restId.value, "Legacy", "USD", "en-US", 0, 0, null)),
            inventoryAreas = emptyList(),
            ingredientCategories = emptyList(),
            units = emptyList(),
            ingredients = emptyList(),
            ingredientUnitOptions = emptyList(),
            suppliers = emptyList(),
            purchaseReceipts = emptyList(),
            purchaseLines = emptyList(),
            stockCounts = emptyList(),
            stockCountAreas = emptyList(),
            stockCountLines = emptyList(),
            wasteEvents = emptyList(),
            inventoryMovements = emptyList(),
            inventoryBalanceProjections = emptyList(),
            ingredientCostProjections = emptyList()
            // recipe fields default to empty
        )

        // 2. Restore
        applier.replaceWithBackup(legacySnapshot)

        // 3. Verify
        val restored = snapshotSource.loadSnapshot(restId.value).dto
        assertThat(restored.restaurants[0].name).isEqualTo("Legacy")
        assertThat(restored.preparationRecipes).isEmpty()
        assertThat(restored.preparationRecipeComponents).isEmpty()
        assertThat(restored.productionBatches).isEmpty()
        assertThat(restored.productionBatchComponents).isEmpty()
    }

    @Test
    fun schema4BackupRoundTrip_restoresOverPopulatedStateExactly() = runBlocking {
        // 1. Seed state A
        seedDatabaseWithRecipes()
        val originalSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        val restaurant = Restaurant(restId, "Test", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)

        // 2. Capture backup A
        val planResult = planner.createPlan(restaurant, snapshotSource.loadSnapshot(restId.value))
        val plan = (planResult as BackupPlanningResult.Success).plan
        val snapshotDto = plan.snapshotDto

        // 3. Mutate live database into state B
        database.withTransaction {
            // Rename recipe
            val draft = database.preparationRecipeDao().getById("r-draft")!!
            database.preparationRecipeDao().update(
                draft.copy(name = "Mutated Draft", normalizedName = "mutated draft")
            )
            // Change notes
            val active = database.preparationRecipeDao().getById("r-active")!!
            database.preparationRecipeDao().update(
                active.copy(notes = "Mutated Notes")
            )
            // Delete components
            database.restoreDao().deleteAllPreparationRecipeComponents()
            // Add a different Draft recipe where valid
            val newIng = "ing-new"
            database.ingredientDao().insert(IngredientEntity(newIng, restId.value, "New", "new", null, "u1", null, null, null, null, true, 0, 0, null))
            database.preparationRecipeDao().insert(PreparationRecipeEntity("r-new", restId.value, newIng, "New Recipe", "new recipe", BigDecimal.ONE, BigDecimal.ONE, null, "DRAFT", null, 100, 100, null))
        }

        // 4. Restore backup A
        applier.replaceWithBackup(snapshotDto)

        // 5. Verify database state matches original exactly
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        assertThat(restoredSnapshot).isEqualTo(snapshotDto)
        assertThat(restoredSnapshot).isEqualTo(originalSnapshot)
    }

    @Test
    fun rollback_restoresRecipesAndComponentsExactly() = runBlocking {
        // 1. Seed
        seedDatabaseWithRecipes()
        val originalSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        
        // 2. Capture rollback snapshot
        val rollback = applier.captureRollbackSnapshot()
        
        // 3. Mutate database
        database.restoreDao().deleteAllPreparationRecipeComponents()
        database.restoreDao().deleteAllPreparationRecipes()
        assertThat(database.preparationRecipeDao().getAllRecipesForRestaurant(restId.value)).isEmpty()
        
        // 4. Restore rollback
        applier.restoreRollback(rollback)
        
        // 5. Verify equality
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        assertThat(restoredSnapshot.preparationRecipes).hasSize(3)
        assertThat(restoredSnapshot).isEqualTo(originalSnapshot)
    }
}
