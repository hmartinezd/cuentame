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
        ingredientCostProjections = emptyList()
    )

    @Test
    fun `validate accepts valid simple snapshot`() {
        val result = BackupSnapshotIntegrityValidator.validate(createEmptyDto(), manifest)
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
        val dto = createEmptyDto().copy(
            preparationRecipes = listOf(
                PreparationRecipeBackupDto("r1", restId, "fake-ing", "Name", "name", null, null, null, "DRAFT", null, 0, 0, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
    }

    @Test
    fun `validate rejects multiple non-archived recipes for one output`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(mockkIngredient("ing-1")),
            preparationRecipes = listOf(
                PreparationRecipeBackupDto("r1", restId, "ing-1", "Name 1", "name 1", null, null, null, "DRAFT", null, 0, 0, null),
                PreparationRecipeBackupDto("r2", restId, "ing-1", "Name 2", "name 2", null, null, null, "ACTIVE", null, 0, 0, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_STRUCTURE)
    }

    @Test
    fun `validate rejects recipe cycle`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(mockkIngredient("ing-A"), mockkIngredient("ing-B")),
            preparationRecipes = listOf(
                PreparationRecipeBackupDto("r-A", restId, "ing-A", "A", "a", null, null, null, "DRAFT", null, 0, 0, null),
                PreparationRecipeBackupDto("r-B", restId, "ing-B", "B", "b", null, null, null, "DRAFT", null, 0, 0, null)
            ),
            preparationRecipeComponents = listOf(
                PreparationRecipeComponentBackupDto("c1", "r-A", "ing-B", "opt-B", "1.0", "1.0", 0, null, 0, 0),
                PreparationRecipeComponentBackupDto("c2", "r-B", "ing-A", "opt-A", "1.0", "1.0", 0, null, 0, 0)
            ),
            ingredientUnitOptions = listOf(
                mockkOption("opt-A", "ing-A"),
                mockkOption("opt-B", "ing-B")
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_GRAPH)
    }

    @Test
    fun `validate rejects active recipe without yield`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(mockkIngredient("ing-1")),
            preparationRecipes = listOf(
                PreparationRecipeBackupDto("r1", restId, "ing-1", "Name", "name", null, null, null, "ACTIVE", null, 0, 0, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `validate rejects component from other restaurant`() {
        val otherRestId = "rest-2"
        val dto = createEmptyDto().copy(
            ingredients = listOf(
                mockkIngredient("ing-1"),
                IngredientBackupDto("ing-2", otherRestId, "Other", "other", null, "u1", null, null, null, null, true, 0, 0, null)
            ),
            preparationRecipes = listOf(
                PreparationRecipeBackupDto("r1", restId, "ing-1", "Name", "name", null, null, null, "DRAFT", null, 0, 0, null)
            ),
            preparationRecipeComponents = listOf(
                PreparationRecipeComponentBackupDto("c1", "r1", "ing-2", "opt-2", "1.0", "1.0", 0, null, 0, 0)
            ),
            ingredientUnitOptions = listOf(
                mockkOption("opt-1", "ing-1"),
                IngredientUnitOptionBackupDto("opt-2", "ing-2", "O", "o", null, "1.0", true, true, true, true, 0, 0, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.RESTAURANT_ISOLATION_FAILURE)
    }

    @Test
    fun `validate rejects duplicate component ingredient in one recipe`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(mockkIngredient("ing-1"), mockkIngredient("ing-2")),
            preparationRecipes = listOf(
                PreparationRecipeBackupDto("r1", restId, "ing-1", "Name", "name", null, null, null, "DRAFT", null, 0, 0, null)
            ),
            preparationRecipeComponents = listOf(
                PreparationRecipeComponentBackupDto("c1", "r1", "ing-2", "opt-2", "1.0", "1.0", 0, null, 0, 0),
                PreparationRecipeComponentBackupDto("c2", "r1", "ing-2", "opt-2", "2.0", "2.0", 1, null, 0, 0)
            ),
            ingredientUnitOptions = listOf(mockkOption("opt-2", "ing-2"))
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 3))
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
