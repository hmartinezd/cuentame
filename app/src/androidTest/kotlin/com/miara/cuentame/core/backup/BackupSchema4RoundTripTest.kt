package com.miara.cuentame.core.backup

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
import com.miara.cuentame.core.model.inventory.DocumentStatus
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
class BackupSchema4RoundTripTest {

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
    fun schema4BackupRoundTrip_preservesAllProductionData() = runBlocking {
        // 1. Seed database with Production Batches
        seedDatabaseWithProduction()

        // 2. Create backup plan
        val restaurant = Restaurant(restId, "Test", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        val snapshotResult = snapshotSource.loadSnapshot(restId.value)
        
        assertThat(appVersionProvider.databaseSchemaVersion).isEqualTo(4)
        
        val planResult = planner.createPlan(restaurant, snapshotResult)
        assertThat(planResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        // 3. Clear and Restore
        val snapshotDto = plan.snapshotDto
        applier.replaceWithBackup(snapshotDto)
        
        // 4. Verify equality
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        
        assertThat(restoredSnapshot.productionBatches).hasSize(1)
        assertThat(restoredSnapshot.productionBatchComponents).hasSize(1)
        assertThat(restoredSnapshot).isEqualTo(snapshotDto)
        
        // Verify specific fields
        val batch = restoredSnapshot.productionBatches[0]
        assertThat(batch.status).isEqualTo(DocumentStatus.POSTED.name)
        assertThat(batch.totalComponentCostSnapshot).isEqualTo("10.00")
        
        val component = restoredSnapshot.productionBatchComponents[0]
        assertThat(component.unitCostBaseSnapshot).isEqualTo("10.00")
    }

    private suspend fun seedDatabaseWithProduction() {
        database.restaurantDao().insert(RestaurantEntity(restId.value, "Test", "USD", "en-US", 0, 0, null))
        database.inventoryAreaDao().upsert(InventoryAreaEntity("a1", restId.value, "Area", "area", 0, true, 0, 0, null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        
        val ing1 = "ing-1"
        val ing2 = "ing-2"
        database.ingredientDao().insert(IngredientEntity(ing1, restId.value, "Output", "output", null, "u1", "a1", null, null, null, true, 100, 100, null))
        database.ingredientDao().insert(IngredientEntity(ing2, restId.value, "Comp", "comp", null, "u1", "a1", null, null, null, true, 100, 100, null))
        
        val opt1 = "opt-1"
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(opt1, ing1, "Unit", "unit", null, BigDecimal.ONE, true, true, true, true, 100, 100, null))
        val opt2 = "opt-2"
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(opt2, ing2, "Unit", "unit", null, BigDecimal.ONE, true, true, true, true, 100, 100, null))

        // Recipe
        database.preparationRecipeDao().insert(PreparationRecipeEntity("r-1", restId.value, ing1, "Recipe", "recipe", BigDecimal.ONE, BigDecimal.ONE, opt1, "ACTIVE", null, 100, 100, null))
        database.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity("c-1", "r-1", ing2, opt2, BigDecimal("0.5"), BigDecimal("0.5"), 0, null, 100, 100))

        // Production Batch
        database.productionBatchDao().insert(ProductionBatchEntity(
            id = "batch-1",
            restaurantId = restId.value,
            recipeId = "r-1",
            recipeNameSnapshot = "Recipe",
            outputIngredientId = ing1,
            batchMultiplier = "2.0",
            recipeStandardYieldQuantitySnapshot = "1.0",
            recipeStandardYieldBaseSnapshot = "1.0",
            recipeYieldUnitOptionIdSnapshot = opt1,
            expectedOutputQuantityEntered = "2.0",
            expectedOutputQuantityBase = "2.0",
            actualOutputQuantityEntered = "2.0",
            actualOutputQuantityBase = "2.0",
            outputUnitOptionId = opt1,
            outputAreaId = "a1",
            hasManualOutputQuantityOverride = false,
            totalComponentCostSnapshot = "10.00",
            outputUnitCostBaseSnapshot = "5.00",
            effectiveAt = 1000L,
            status = DocumentStatus.POSTED.name,
            notes = "Test Batch",
            createdAt = 1000L,
            updatedAt = 1000L,
            postedAt = 1000L,
            voidedAt = null
        ))
        database.productionBatchDao().insertComponents(listOf(ProductionBatchComponentEntity(
            id = "bc-1",
            productionBatchId = "batch-1",
            sourceRecipeComponentIdSnapshot = "c-1",
            componentIngredientId = ing2,
            recipeQuantityEnteredSnapshot = "0.5",
            recipeQuantityBaseSnapshot = "0.5",
            recipeUnitOptionIdSnapshot = opt2,
            expectedQuantityEntered = "1.0",
            expectedQuantityBase = "1.0",
            actualQuantityEntered = "1.0",
            actualQuantityBase = "1.0",
            unitOptionId = opt2,
            hasManualQuantityOverride = false,
            sourceAreaId = "a1",
            unitCostBaseSnapshot = "10.00",
            totalCostSnapshot = "10.00",
            sortOrder = 0,
            notes = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )))
    }

    @Test
    fun backwardCompatibility_restoresSchema3BackupSuccessfully() = runBlocking {
        // 1. Create a Schema 3 DTO (no production)
        val schema3Snapshot = BackupSnapshotDto(
            restaurants = listOf(RestaurantBackupDto(restId.value, "Schema 3", "USD", "en-US", 0, 0, null)),
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
            ingredientCostProjections = emptyList(),
            preparationRecipes = emptyList(),
            preparationRecipeComponents = emptyList()
            // production fields default to empty
        )

        // 2. Restore
        applier.replaceWithBackup(schema3Snapshot)

        // 3. Verify
        val restored = snapshotSource.loadSnapshot(restId.value).dto
        assertThat(restored.restaurants[0].name).isEqualTo("Schema 3")
        assertThat(restored.productionBatches).isEmpty()
        assertThat(restored.productionBatchComponents).isEmpty()
    }
}
