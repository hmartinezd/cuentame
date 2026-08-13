package com.miara.cuentame.core.backup

import com.miara.cuentame.core.common.database.DatabaseSchema

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
import com.miara.cuentame.core.model.inventory.InventoryMovementOperationIds
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
    fun currentSchemaBackupRoundTrip_preservesAllRecipeData() = runBlocking {
        // 1. Seed database with recipes
        seedDatabaseWithRecipes()

        // 2. Create backup plan
        val restaurant = Restaurant(restId, "Test", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        val snapshotResult = snapshotSource.loadSnapshot(restId.value)
        
        assertThat(appVersionProvider.databaseSchemaVersion).isEqualTo(DatabaseSchema.VERSION)
        
        val planResult = planner.createPlan(restaurant, snapshotResult)
        assertThat(planResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        // 3. Clear database (via applier)
        val snapshotDto = plan.snapshotDto
        
        // 4. Restore
        applier.replaceWithBackup(snapshotDto, plan.manifest)
        
        // 5. Verify equality
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        
        // Compare DTOs ignoring sorting if necessary (though mapper sorts)
        assertThat(restoredSnapshot.preparationRecipes).hasSize(3)
        assertThat(restoredSnapshot.preparationRecipeComponents).hasSize(2)
        assertThat(restoredSnapshot.productionBatches).hasSize(1)
        assertThat(restoredSnapshot.productionBatchComponents).hasSize(1)
        
        assertThat(restoredSnapshot).isEqualTo(snapshotDto)

        val restoredConfiguredIngredient = database.ingredientDao().getById("ing-1")!!
        assertThat(restoredConfiguredIngredient.parLevelBase?.compareTo(BigDecimal("23.75"))).isEqualTo(0)
        assertThat(restoredConfiguredIngredient.reorderPointBase?.compareTo(BigDecimal("8.125"))).isEqualTo(0)

        val restoredNullIngredient = database.ingredientDao().getById("ing-2")!!
        assertThat(restoredNullIngredient.parLevelBase).isNull()
        assertThat(restoredNullIngredient.reorderPointBase).isNull()
        
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
        
        val area1 = "area-1"
        database.inventoryAreaDao().upsert(InventoryAreaEntity(area1, restId.value, "Kitchen", "kitchen", 0, true, 100, 100, null))

        val ing1 = "ing-1"
        val ing2 = "ing-2"
        database.ingredientDao().insert(IngredientEntity(ing1, restId.value, "Output", "output", null, "u1", null, null, null, BigDecimal("8.125"), true, 100, 100, null, BigDecimal("23.75")))
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

        // Add a Purchase for ing-1 to establish cost before production
        database.purchaseDao().insertReceipt(PurchaseReceiptEntity("p-1", restId.value, null, "INV-P1", 100, "POSTED", null, null, null, 100, 100, 100, null))
        database.purchaseDao().insertLine(PurchaseLineEntity("pl-1", "p-1", ing1, area1, opt1, "10.0", "10.0", "100.0", "10.0", null, 100, 100))
        database.inventoryMovementDao().insertAll(listOf(
            InventoryMovementEntity("m-p1", restId.value, ing1, area1, "PURCHASE", "10.0", "10.0", "100.0", 100, "PURCHASE_RECEIPT", "p-1", InventoryMovementOperationIds.purchasePost("p-1", "pl-1"), "pl-1", null, 100)
        ))

        // Production Batch
        database.productionBatchDao().insert(ProductionBatchEntity(
            id = "pb-1",
            restaurantId = restId.value,
            recipeId = "r-active",
            recipeNameSnapshot = "Active",
            outputIngredientId = ing2,
            batchMultiplier = "1.0",
            recipeStandardYieldQuantitySnapshot = "10.0",
            recipeStandardYieldBaseSnapshot = "10.0",
            recipeYieldUnitOptionIdSnapshot = opt2,
            expectedOutputQuantityEntered = "10.0",
            expectedOutputQuantityBase = "10.0",
            actualOutputQuantityEntered = "10.0",
            actualOutputQuantityBase = "10.0",
            outputUnitOptionId = opt2,
            outputAreaId = area1,
            hasManualOutputQuantityOverride = false,
            totalComponentCostSnapshot = "10.0",
            outputUnitCostBaseSnapshot = "1.0",
            effectiveAt = 300,
            status = "POSTED",
            notes = "Test batch",
            createdAt = 300,
            updatedAt = 300,
            postedAt = 300,
            voidedAt = null
        ))
        database.productionBatchDao().insertComponents(listOf(ProductionBatchComponentEntity(
            id = "pbc-1",
            productionBatchId = "pb-1",
            sourceRecipeComponentIdSnapshot = "c-1",
            componentIngredientId = ing1,
            recipeQuantityEnteredSnapshot = "1.0",
            recipeQuantityBaseSnapshot = "1.0",
            recipeUnitOptionIdSnapshot = opt1,
            expectedQuantityEntered = "1.0",
            expectedQuantityBase = "1.0",
            actualQuantityEntered = "1.0",
            actualQuantityBase = "1.0",
            unitOptionId = opt1,
            hasManualQuantityOverride = false,
            sourceAreaId = area1,
            unitCostBaseSnapshot = "10.0",
            totalCostSnapshot = "10.0",
            sortOrder = 0,
            notes = null,
            createdAt = 300,
            updatedAt = 300
        )))
        
        // Add movements for POSTED batch
        database.inventoryMovementDao().insertAll(listOf(
            InventoryMovementEntity("m-consume", restId.value, ing1, area1, "PRODUCTION_CONSUMPTION", "-1.0", "10.0", "-10.0", 300, "PRODUCTION_BATCH", "pb-1", InventoryMovementOperationIds.productionConsumption("pb-1", "pbc-1"), "pbc-1", null, 300),
            InventoryMovementEntity("m-out", restId.value, ing2, area1, "PRODUCTION_OUTPUT", "10.0", "1.0", "10.0", 300, "PRODUCTION_BATCH", "pb-1", InventoryMovementOperationIds.productionOutput("pb-1"), "pb-1", null, 300)
        ))
        
        // Add projections
        database.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, ing1, area1, "9.0", 300)) // 10 - 1 = 9
        database.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId.value, ing2, area1, "10.0", 300))
        database.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId.value, ing1, "10.0", 300))
        database.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId.value, ing2, "1.0", 300))
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
        val manifest = com.miara.cuentame.core.model.backup.BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 2,
            restaurantId = restId.value,
            restaurantName = "Legacy",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = emptyMap(),
            attachments = emptyList(),
            includedSections = listOf("data", "preferences", "attachments")
        )
        applier.replaceWithBackup(legacySnapshot, manifest)

        // 3. Verify
        val restored = snapshotSource.loadSnapshot(restId.value).dto
        assertThat(restored.restaurants[0].name).isEqualTo("Legacy")
        assertThat(restored.preparationRecipes).isEmpty()
        assertThat(restored.preparationRecipeComponents).isEmpty()
        assertThat(restored.productionBatches).isEmpty()
        assertThat(restored.productionBatchComponents).isEmpty()
    }

    @Test
    fun schema12BackupRoundTrip_restoresOverPopulatedStateExactly() = runBlocking {
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
        applier.replaceWithBackup(snapshotDto, plan.manifest)

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
        database.restoreDao().clearAllInOrder()
        assertThat(database.preparationRecipeDao().getAllRecipesForRestaurant(restId.value)).isEmpty()
        
        // 4. Restore rollback
        applier.restoreRollback(rollback)
        
        // 5. Verify equality
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        assertThat(restoredSnapshot.preparationRecipes).hasSize(3)
        assertThat(restoredSnapshot).isEqualTo(originalSnapshot)
    }
}
