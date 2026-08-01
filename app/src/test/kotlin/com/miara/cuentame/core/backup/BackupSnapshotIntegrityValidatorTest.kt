package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

class BackupSnapshotIntegrityValidatorTest {

    private val restId = "rest-1"
    private val manifest = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 2,
        restaurantId = restId,
        restaurantName = "Test Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = emptyMap(),
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments"),
        checksumAlgorithm = "SHA-256"
    )

    private fun createEmptyDto() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto(restId, "Test Rest", "USD", "en-US", 0, 0, null)),
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
    )

    private fun createValidRecipeSnapshot(): BackupSnapshotDto {
        val unit = mockkUnit("u1")
        val outputIng = mockkIngredient("ing-output")
        val yieldOpt = mockkOption("opt-yield", "ing-output")
        val compIng = mockkIngredient("ing-comp")
        val compOpt = mockkOption("opt-comp", "ing-comp")
        
        return createEmptyDto().copy(
            units = listOf(unit),
            ingredients = listOf(outputIng, compIng),
            ingredientUnitOptions = listOf(yieldOpt, compOpt),
            preparationRecipes = listOf(
                PreparationRecipeBackupDto(
                    "r1", restId, "ing-output", "Recipe", "recipe", 
                    "10.0", "10.0", "opt-yield", "ACTIVE", null, 1000, 1000, null
                )
            ),
            preparationRecipeComponents = listOf(
                PreparationRecipeComponentBackupDto(
                    "c1", "r1", "ing-comp", "opt-comp", "5.0", "5.0", 0, null, 1000, 1000
                )
            )
        )
    }

    @Test
    fun `validate accepts valid recipe snapshot`() {
        val result = BackupSnapshotIntegrityValidator.validate(createValidRecipeSnapshot(), manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate rejects orphan recipe component`() {
        val dto = createValidRecipeSnapshot().copy(
            preparationRecipes = emptyList()
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull() as BackupSnapshotIntegrityException
        assertThat(ex.code).isEqualTo(BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
        assertThat(ex.message).contains("Broken FK: preparation recipe component to recipe")
    }

    @Test
    fun `validate rejects active recipe with archived output ingredient`() {
        val dto = createValidRecipeSnapshot()
        val archivedOutput = dto.ingredients[0].copy(isActive = false, deletedAt = 2000L)
        val newDto = dto.copy(ingredients = listOf(archivedOutput, dto.ingredients[1]))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_STRUCTURE)
    }

    @Test
    fun `validate rejects active recipe with archived yield option`() {
        val dto = createValidRecipeSnapshot()
        val archivedOpt = dto.ingredientUnitOptions[0].copy(isActive = false, deletedAt = 2000L)
        val newDto = dto.copy(ingredientUnitOptions = listOf(archivedOpt, dto.ingredientUnitOptions[1]))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_STRUCTURE)
    }

    @Test
    fun `validate rejects draft recipe with blank name`() {
        val dto = createValidRecipeSnapshot()
        val draftRecipe = dto.preparationRecipes[0].copy(status = "DRAFT", name = "  ", normalizedName = "")
        val newDto = dto.copy(preparationRecipes = listOf(draftRecipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_STRUCTURE)
    }

    @Test
    fun `validate rejects recipe with incorrect normalizedName`() {
        val dto = createValidRecipeSnapshot()
        val recipe = dto.preparationRecipes[0].copy(name = "Tomato Sauce", normalizedName = "tomato")
        val newDto = dto.copy(preparationRecipes = listOf(recipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_STRUCTURE)
    }

    @Test
    fun `validate rejects draft recipe with zero yield`() {
        val dto = createValidRecipeSnapshot()
        val draftRecipe = dto.preparationRecipes[0].copy(status = "DRAFT", standardYieldQuantity = "0.0", standardYieldQuantityBase = "0.0")
        val newDto = dto.copy(preparationRecipes = listOf(draftRecipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `validate rejects recipe with invalid timestamp order`() {
        val dto = createValidRecipeSnapshot()
        val recipe = dto.preparationRecipes[0].copy(createdAt = 2000, updatedAt = 1000)
        val newDto = dto.copy(preparationRecipes = listOf(recipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_TIMESTAMP_ORDER)
    }

    @Test
    fun `validate rejects archived recipe with invalid archivedAt`() {
        val dto = createValidRecipeSnapshot()
        val recipe = dto.preparationRecipes[0].copy(status = "ARCHIVED", archivedAt = 500, updatedAt = 1000)
        val newDto = dto.copy(preparationRecipes = listOf(recipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_TIMESTAMP_ORDER)
    }

    @Test
    fun `validate accepts archived recipe with archived references`() {
        val dto = createValidRecipeSnapshot()
        val recipe = dto.preparationRecipes[0].copy(status = "ARCHIVED", archivedAt = 1000)
        val compIng = dto.ingredients[1].copy(isActive = false, deletedAt = 500L)
        val newDto = dto.copy(
            preparationRecipes = listOf(recipe),
            ingredients = listOf(dto.ingredients[0], compIng)
        )
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate rejects zero purchase quantity`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(mockkIngredient("ing-1")),
            units = listOf(mockkUnit("u1")),
            ingredientUnitOptions = listOf(mockkOption("opt-1", "ing-1")),
            inventoryAreas = listOf(mockkArea("area-1")),
            purchaseReceipts = listOf(mockkReceipt("p1", "POSTED")),
            purchaseLines = listOf(
                mockkPurchaseLine("l1", "p1", "ing-1", "area-1", "opt-1", "0.0")
            ),
            inventoryMovements = listOf(
                mockkMovement("m1", "PURCHASE", "0.0", "PURCHASE_RECEIPT", "p1", "l1")
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull() as BackupSnapshotIntegrityException
        assertThat(ex.code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `validate rejects balance projection mismatch`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(mockkIngredient("ing-1")),
            units = listOf(mockkUnit("u1")),
            ingredientUnitOptions = listOf(mockkOption("opt-1", "ing-1")),
            inventoryAreas = listOf(mockkArea("area-1")),
            inventoryMovements = listOf(
                mockkMovement("m1", "MANUAL_ADJUSTMENT", "10.0", "MANUAL", "op1", null)
            ),
            inventoryBalanceProjections = listOf(
                InventoryBalanceProjectionBackupDto(restId, "ing-1", "area-1", "15.0", 0L)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull() as BackupSnapshotIntegrityException
        assertThat(ex.code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_BALANCE_PROJECTION)
    }

    @Test
    fun `validate rejects posted purchase receipt without movement`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(mockkIngredient("ing-1")),
            units = listOf(mockkUnit("u1")),
            ingredientUnitOptions = listOf(mockkOption("opt-1", "ing-1")),
            inventoryAreas = listOf(mockkArea("area-1")),
            purchaseReceipts = listOf(mockkReceipt("p1", "POSTED")),
            purchaseLines = listOf(mockkPurchaseLine("l1", "p1", "ing-1", "area-1", "opt-1", "1.0")),
            inventoryMovements = emptyList()
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull() as BackupSnapshotIntegrityException
        assertThat(ex.code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE)
    }

    @Test
    fun `validate rejects broken output ingredient FK`() {
        val dto = createValidRecipeSnapshot().copy(
            ingredients = emptyList(),
            ingredientUnitOptions = emptyList(),
            preparationRecipeComponents = emptyList()
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
    }

    @Test
    fun `validate rejects multiple non-archived recipes for one output`() {
        val dto = createValidRecipeSnapshot()
        val recipe2 = dto.preparationRecipes[0].copy(id = "r2")
        val newDto = dto.copy(
            preparationRecipes = listOf(dto.preparationRecipes[0], recipe2)
        )
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_STRUCTURE)
    }

    @Test
    fun `validate rejects recipe cycle`() {
        val unit = mockkUnit("u1")
        val ingA = mockkIngredient("ing-A")
        val ingB = mockkIngredient("ing-B")
        val optA = mockkOption("opt-A", "ing-A")
        val optB = mockkOption("opt-B", "ing-B")

        val dto = createEmptyDto().copy(
            units = listOf(unit),
            ingredients = listOf(ingA, ingB),
            ingredientUnitOptions = listOf(optA, optB),
            preparationRecipes = listOf(
                PreparationRecipeBackupDto("r-A", restId, "ing-A", "A", "a", "1", "1", "opt-A", "DRAFT", null, 0, 0, null),
                PreparationRecipeBackupDto("r-B", restId, "ing-B", "B", "b", "1", "1", "opt-B", "DRAFT", null, 0, 0, null)
            ),
            preparationRecipeComponents = listOf(
                PreparationRecipeComponentBackupDto("c1", "r-A", "ing-B", "opt-B", "1.0", "1.0", 0, null, 0, 0),
                PreparationRecipeComponentBackupDto("c2", "r-B", "ing-A", "opt-A", "1.0", "1.0", 0, null, 0, 0)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_GRAPH)
    }

    @Test
    fun `validate rejects active recipe without yield`() {
        val dto = createValidRecipeSnapshot()
        val recipe = dto.preparationRecipes[0].copy(standardYieldQuantity = null, standardYieldQuantityBase = null)
        val newDto = dto.copy(preparationRecipes = listOf(recipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `validate rejects component from other restaurant`() {
        val otherRestId = "rest-2"
        val dto = createValidRecipeSnapshot()
        val otherIng = dto.ingredients[1].copy(restaurantId = otherRestId)
        val newDto = dto.copy(ingredients = listOf(dto.ingredients[0], otherIng))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.RESTAURANT_ISOLATION_FAILURE)
    }

    @Test
    fun `validate rejects duplicate component ingredient in one recipe`() {
        val dto = createValidRecipeSnapshot()
        val comp2 = dto.preparationRecipeComponents[0].copy(id = "c2")
        val newDto = dto.copy(
            preparationRecipeComponents = listOf(dto.preparationRecipeComponents[0], comp2)
        )
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_STRUCTURE)
    }

    // Helpers
    private fun mockkIngredient(id: String) = IngredientBackupDto(id, restId, "Name", "name", null, "u1", null, null, null, null, true, 0, 0, null)
    private fun mockkUnit(id: String) = UnitBackupDto(id, "Unit", "u", "MASS", "1.0", true, 0)
    private fun mockkOption(id: String, ingId: String) = IngredientUnitOptionBackupDto(id, ingId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null)
    private fun mockkArea(id: String) = InventoryAreaBackupDto(id, restId, "Area", "area", 0, true, 0, 0, null)
    private fun mockkReceipt(id: String, status: String) = PurchaseReceiptBackupDto(id, restId, null, null, 0L, status, null, null, 0L, 0L, if (status == "POSTED") 0L else null, null)
    private fun mockkPurchaseLine(id: String, rId: String, ingId: String, aId: String, oId: String, qty: String) = 
        PurchaseLineBackupDto(id, rId, ingId, aId, oId, qty, qty, "1.0", "1.0", null, 0L, 0L)
    private fun mockkMovement(id: String, type: String, qty: String, docType: String, docId: String, lineId: String?) =
        InventoryMovementBackupDto(id, restId, "ing-1", "area-1", type, qty, "1.0", "1.0", 0L, docType, docId, "op1", lineId, null, 0L)
}
