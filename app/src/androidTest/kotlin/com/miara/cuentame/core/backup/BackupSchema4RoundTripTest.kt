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
import com.miara.cuentame.core.domain.repository.CreateProductionBatchDraftCommand
import com.miara.cuentame.core.domain.repository.ProductionBatchRepository
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import com.miara.cuentame.test.PostedPurchaseFixture
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
    lateinit var repository: ProductionBatchRepository

    @Inject
    lateinit var purchaseRepository: PurchaseRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restId = RestaurantId("rest-1")

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun schema4BackupRoundTrip_realPostingAndMutation_preservesAllProductionData() = runBlocking {
        // 1. Seed base data
        testStateManager.resetAll()
        seedBaseData()
        
        // 2. Establish component quantity and cost through a real purchase
        val scenarioTime = Instant.parse("2026-01-01T10:00:00Z")
        val compIngId = IngredientId("ing-comp")
        val areaId = InventoryAreaId("a1")
        val optId = IngredientUnitOptionId("opt-2")

        TestSeeder.seedPostedPurchase(
            db = database,
            repo = purchaseRepository,
            restaurantId = restId,
            ingredientId = compIngId,
            areaId = areaId,
            unitOptionId = optId,
            quantityEntered = BigDecimal("10"),
            unitCostBase = BigDecimal("10.00"),
            effectiveAt = scenarioTime
        )

        // 3. Create Production Draft and Post
        val recipeId = PreparationRecipeId("r-1")
        val effectiveAt = scenarioTime.plusSeconds(3600)
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restaurantId = restId, recipeId = recipeId, batchMultiplier = BigDecimal("2"),
            outputAreaId = areaId, actualOutputQuantityEntered = null,
            outputUnitOptionId = null, effectiveAt = effectiveAt, notes = "Initial Notes"
        ))
        repository.post(batchId)

        // Confirm the state passes BackupSnapshotIntegrityValidator
        val snapshotBefore = snapshotSource.loadSnapshot(restId.value).dto
        val manifest = com.miara.cuentame.core.model.backup.BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = Instant.now().toString(),
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0.0",
            appVersionCode = 1L,
            databaseSchemaVersion = 4,
            restaurantId = restId.value,
            restaurantName = "Test Restaurant",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = emptyMap(),
            attachments = emptyList(),
            includedSections = emptyList()
        )
        val integrityResult = BackupSnapshotIntegrityValidator.validate(snapshotBefore, manifest)
        assertThat(integrityResult.isSuccess).isTrue()

        // 4. Capture backup plan
        val restaurant = Restaurant(restId, "Test Restaurant", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        val planResult = planner.createPlan(restaurant, BackupSnapshotResult(snapshotBefore, emptyList()))
        val plan = (planResult as BackupPlanningResult.Success).plan

        // 5. Mutate database
        // Rename a recipe
        database.preparationRecipeDao().update(database.preparationRecipeDao().getById("r-1")!!.copy(name = "Renamed Recipe"))
        // Create another valid Draft batch
        repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, effectiveAt.plusSeconds(3600), "Another Draft"
        ))
        
        // 6. Restore backup
        applier.replaceWithBackup(plan.snapshotDto)

        // 7. Verify exact equality
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        assertThat(restoredSnapshot).isEqualTo(snapshotBefore)
        
        // Verify mutation records are gone
        assertThat(restoredSnapshot.preparationRecipes[0].name).isEqualTo("Recipe")
        assertThat(restoredSnapshot.productionBatches).hasSize(1)
        assertThat(restoredSnapshot.productionBatches[0].notes).isEqualTo("Initial Notes")
    }

    private suspend fun seedBaseData() {
        database.restaurantDao().insert(RestaurantEntity(restId.value, "Test Restaurant", "USD", "en-US", 0, 0, null))
        database.inventoryAreaDao().upsert(InventoryAreaEntity("a1", restId.value, "Area", "area", 0, true, 0, 0, null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        
        val ingOutput = "ing-output"
        val ingComp = "ing-comp"
        database.ingredientDao().insert(IngredientEntity(ingOutput, restId.value, "Output", "output", null, "u1", "a1", null, null, null, true, 100, 100, null))
        database.ingredientDao().insert(IngredientEntity(ingComp, restId.value, "Comp", "comp", null, "u1", "a1", null, null, null, true, 100, 100, null))
        
        val opt1 = "opt-1"
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(opt1, ingOutput, "Unit", "unit", null, BigDecimal.ONE, true, true, true, true, 100, 100, null))
        val opt2 = "opt-2"
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(opt2, ingComp, "Unit", "unit", null, BigDecimal.ONE, true, true, true, true, 100, 100, null))

        // Recipe
        database.preparationRecipeDao().insert(PreparationRecipeEntity("r-1", restId.value, ingOutput, "Recipe", "recipe", BigDecimal.ONE, BigDecimal.ONE, opt1, "ACTIVE", null, 100, 100, null))
        database.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity("c-1", "r-1", ingComp, opt2, BigDecimal("0.5"), BigDecimal("0.5"), 0, null, 100, 100))
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
