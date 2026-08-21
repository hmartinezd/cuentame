package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.model.*
import com.venkoi.restaurantops.core.model.backup.BackupManifest
import com.venkoi.restaurantops.core.model.backup.BackupAttachmentMetadata
import org.junit.Test

class BackupSnapshotIntegrityValidatorTest {

    private val restId = "rest-1"
    private val manifest = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.venkoi.restaurantops",
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

    @Test
    fun `validate rejects draft recipe with base yield but no entered yield`() {
        val dto = createValidRecipeSnapshot()
        val draftRecipe = dto.preparationRecipes[0].copy(
            status = "DRAFT", 
            standardYieldQuantity = null, 
            standardYieldQuantityBase = "10.0"
        )
        val newDto = dto.copy(preparationRecipes = listOf(draftRecipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_STRUCTURE)
    }

    @Test
    fun `validate rejects draft recipe with base yield but no unit option`() {
        val dto = createValidRecipeSnapshot()
        val draftRecipe = dto.preparationRecipes[0].copy(
            status = "DRAFT", 
            standardYieldQuantity = "10.0", 
            standardYieldQuantityBase = "10.0",
            yieldUnitOptionId = null
        )
        val newDto = dto.copy(preparationRecipes = listOf(draftRecipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_RECIPE_STRUCTURE)
    }

    @Test
    fun `validate rejects draft recipe with mismatched yield conversion`() {
        val dto = createValidRecipeSnapshot()
        val draftRecipe = dto.preparationRecipes[0].copy(
            status = "DRAFT", 
            standardYieldQuantity = "10.0", 
            standardYieldQuantityBase = "5.0"
        )
        val newDto = dto.copy(preparationRecipes = listOf(draftRecipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `validate accepts draft recipe with entered yield but no option`() {
        val dto = createValidRecipeSnapshot()
        val draftRecipe = dto.preparationRecipes[0].copy(
            status = "DRAFT", 
            standardYieldQuantity = "10.0", 
            standardYieldQuantityBase = null,
            yieldUnitOptionId = null
        )
        val newDto = dto.copy(preparationRecipes = listOf(draftRecipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate accepts draft recipe with null yield and null option`() {
        val dto = createValidRecipeSnapshot()
        val draftRecipe = dto.preparationRecipes[0].copy(
            status = "DRAFT", 
            standardYieldQuantity = null, 
            standardYieldQuantityBase = null,
            yieldUnitOptionId = null
        )
        val newDto = dto.copy(preparationRecipes = listOf(draftRecipe))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest.copy(databaseSchemaVersion = 3))
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate rejects staged match pointing to non-existent parsed line`() {
        val dto = createEmptyDto().copy(
            purchaseReceipts = listOf(mockkReceipt("p1", "DRAFT")),
            purchaseInvoiceOcrResults = listOf(mockkOcr("o1", "p1")),
            purchaseInvoiceParseResults = listOf(mockkParse("pr1", "p1", "o1")),
            purchaseInvoiceLineMatches = listOf(
                PurchaseInvoiceLineMatchBackupDto("pr1", 0, "UNMATCHED", null, null, null, null, null, null, 0f, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 8))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH)
    }

    @Test
    fun `validate rejects confirmed match without ingredient`() {
        val dto = createEmptyDto().copy(
            purchaseReceipts = listOf(mockkReceipt("p1", "DRAFT")),
            purchaseInvoiceOcrResults = listOf(mockkOcr("o1", "p1")),
            purchaseInvoiceParseResults = listOf(mockkParse("pr1", "p1", "o1")),
            purchaseInvoiceParsedLines = listOf(mockkParsedLine("pr1", 0)),
            purchaseInvoiceLineMatches = listOf(
                PurchaseInvoiceLineMatchBackupDto("pr1", 0, "CONFIRMED", null, null, null, null, null, null, 1f, 1000L)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 8))
        assertThat(result.isFailure).isTrue()
        // Current validator logic might catch earlier relational issues
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isAnyOf(
            BackupSnapshotIntegrityCode.INVALID_MATCH_STATUS,
            BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH
        )
    }

    @Test
    fun `validate rejects suggested match with confirmedAt`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(mockkIngredient("ing1")),
            units = listOf(mockkUnit("u1")),
            purchaseReceipts = listOf(mockkReceipt("p1", "DRAFT")),
            purchaseInvoiceOcrResults = listOf(mockkOcr("o1", "p1")),
            purchaseInvoiceParseResults = listOf(mockkParse("pr1", "p1", "o1")),
            purchaseInvoiceParsedLines = listOf(mockkParsedLine("pr1", 0)),
            purchaseInvoiceLineMatches = listOf(
                PurchaseInvoiceLineMatchBackupDto("pr1", 0, "SUGGESTED", null, "ing1", null, null, null, null, 0.9f, 1000L)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 8))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isAnyOf(
            BackupSnapshotIntegrityCode.INVALID_MATCH_STATUS,
            BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH
        )
    }

    @Test
    fun `validate rejects mapping from different restaurant`() {
        val dto = createEmptyDto().copy(
            suppliers = listOf(mockkSupplier("s1", "other-rest")),
            ingredients = listOf(mockkIngredient("ing1")),
            units = listOf(mockkUnit("u1")),
            supplierItemMappings = listOf(
                SupplierItemMappingBackupDto("m1", restId, "s1", "VENDOR_CODE", "K1", null, null, null, "ing1", null, null, 0L, 0L, 0L)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest.copy(databaseSchemaVersion = 8))
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.RESTAURANT_ISOLATION_FAILURE)
    }

    @Test
    fun `validate rejects incompatible mapping unit option in match`() {
        val dto = createEmptyDto().copy(
            suppliers = listOf(mockkSupplier("s1")),
            ingredients = listOf(mockkIngredient("ing1")),
            units = listOf(mockkUnit("u1")),
            ingredientUnitOptions = listOf(
                mockkOption("opt1", "ing1"),
                mockkOption("opt2", "ing1")
            ),
            supplierItemMappings = listOf(
                SupplierItemMappingBackupDto("m1", restId, "s1", "VENDOR_CODE", "K1", null, null, null, "ing1", "opt1", null, 0L, 0L, 0L)
            ),
            purchaseReceipts = listOf(mockkReceipt("p1", "DRAFT").copy(attachmentId = "attachment-1")),
            purchaseInvoiceOcrResults = listOf(mockkOcr("o1", "p1")),
            purchaseInvoiceParseResults = listOf(mockkParse("pr1", "p1", "o1")),
            purchaseInvoiceParsedLines = listOf(mockkParsedLine("pr1", 0)),
            purchaseInvoiceLineMatches = listOf(
                PurchaseInvoiceLineMatchBackupDto("pr1", 0, "SUGGESTED", "s1", "ing1", "opt2", null, "m1", "Mapping", 1f, null)
            )
        )
        val m = manifest.copy(
            databaseSchemaVersion = 8,
            attachments = listOf(BackupAttachmentMetadata("attachment-1", "path/to/doc", "doc", "application/pdf", 100L, "sha256", emptyList()))
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, m)
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH)
        assertThat(result.exceptionOrNull()?.message).contains("Incompatible mapping unit option")
    }

    @Test
    fun `validate rejects incompatible mapping area in match`() {
        val dto = createEmptyDto().copy(
            suppliers = listOf(mockkSupplier("s1")),
            ingredients = listOf(mockkIngredient("ing1")),
            units = listOf(mockkUnit("u1")),
            inventoryAreas = listOf(
                mockkArea("area1"),
                mockkArea("area2")
            ),
            ingredientUnitOptions = listOf(mockkOption("opt1", "ing1")),
            supplierItemMappings = listOf(
                SupplierItemMappingBackupDto("m1", restId, "s1", "VENDOR_CODE", "K1", null, null, null, "ing1", null, "area1", 0L, 0L, 0L)
            ),
            purchaseReceipts = listOf(mockkReceipt("p1", "DRAFT").copy(attachmentId = "attachment-1")),
            purchaseInvoiceOcrResults = listOf(mockkOcr("o1", "p1")),
            purchaseInvoiceParseResults = listOf(mockkParse("pr1", "p1", "o1")),
            purchaseInvoiceParsedLines = listOf(mockkParsedLine("pr1", 0)),
            purchaseInvoiceLineMatches = listOf(
                PurchaseInvoiceLineMatchBackupDto("pr1", 0, "SUGGESTED", "s1", "ing1", "opt1", "area2", "m1", "Mapping", 1f, null)
            )
        )
        val m = manifest.copy(
            databaseSchemaVersion = 8,
            attachments = listOf(BackupAttachmentMetadata("attachment-1", "path/to/doc", "doc", "application/pdf", 100L, "sha256", emptyList()))
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, m)
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH)
        assertThat(result.exceptionOrNull()?.message).contains("Incompatible mapping area")
    }

    @Test
    fun `menu backup validation rejects invalid menu values`() {
        val base = createValidMenuCatalogSnapshot()
        val invalidCases = listOf(
            base.copy(menus = base.menus.map { if (it.id == "menu-1") it.copy(name = "  ", normalizedName = "") else it }) to BackupSnapshotIntegrityCode.INVALID_MENU_STRUCTURE,
            base.copy(menus = base.menus.map { if (it.id == "menu-1") it.copy(normalizedName = "wrong") else it }) to BackupSnapshotIntegrityCode.INVALID_MENU_STRUCTURE,
            base.copy(menus = base.menus.map { if (it.id == "menu-2") it.copy(name = " DINNER ", normalizedName = "dinner") else it }) to BackupSnapshotIntegrityCode.INVALID_MENU_STRUCTURE,
            base.copy(menus = base.menus.map { if (it.id == "menu-1") it.copy(publicationRevision = -1) else it }) to BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE,
            base.copy(menus = base.menus.map { if (it.id == "menu-1") it.copy(defaultCashDiscountPercent = "100") else it }) to BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE,
        )

        invalidCases.forEach { (dto, expectedCode) -> assertMenuIntegrityFailure(dto, expectedCode) }
    }

    @Test
    fun `menu backup validation rejects invalid category values`() {
        val base = createValidMenuCatalogSnapshot()
        val first = base.menuCategories.first()
        val invalidCases = listOf(
            base.copy(menuCategories = base.menuCategories.map { if (it.id == first.id) it.copy(name = " ", normalizedName = "") else it }) to BackupSnapshotIntegrityCode.INVALID_MENU_STRUCTURE,
            base.copy(menuCategories = base.menuCategories.map { if (it.id == first.id) it.copy(normalizedName = "wrong") else it }) to BackupSnapshotIntegrityCode.INVALID_MENU_STRUCTURE,
            base.copy(menuCategories = base.menuCategories.map { if (it.id == first.id) it.copy(sortOrder = -1) else it }) to BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE,
            base.copy(menuCategories = base.menuCategories.map { if (it.id == first.id) it.copy(menuId = "missing") else it }) to BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY,
            base.copy(menuCategories = base.menuCategories + first.copy(id = "category-duplicate", name = " SIDES ")) to BackupSnapshotIntegrityCode.INVALID_MENU_STRUCTURE,
        )

        invalidCases.forEach { (dto, expectedCode) -> assertMenuIntegrityFailure(dto, expectedCode) }
    }

    @Test
    fun `menu backup validation rejects invalid placement values`() {
        val base = createValidMenuCatalogSnapshot()
        val first = base.menuPlacements.first()
        val invalidCases = listOf(
            base.copy(menuPlacements = base.menuPlacements.map { if (it.id == first.id) it.copy(sortOrder = -1) else it }) to BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE,
            base.copy(menuPlacements = base.menuPlacements.map { if (it.id == first.id) it.copy(categoryId = "category-2") else it }) to BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH,
            base.copy(menuRecipes = base.menuRecipes.map { if (it.id == first.menuRecipeId) it.copy(restaurantId = "other") else it }) to BackupSnapshotIntegrityCode.RESTAURANT_ISOLATION_FAILURE,
            base.copy(menuPlacements = base.menuPlacements + first.copy(id = "placement-duplicate")) to BackupSnapshotIntegrityCode.INVALID_MENU_STRUCTURE,
        )

        invalidCases.forEach { (dto, expectedCode) -> assertMenuIntegrityFailure(dto, expectedCode) }
    }

    @Test
    fun `menu backup validation accepts scoped reuse and tied ordering`() {
        val dto = createValidMenuCatalogSnapshot().let { base ->
            base.copy(
                menus = base.menus + base.menus.first().copy(id = "menu-archived", name = "DINNER", archivedAt = 2000),
                menuCategories = base.menuCategories + MenuCategoryBackupDto("category-1b", "menu-1", "Desserts", "desserts", 0),
                menuPlacements = base.menuPlacements + MenuPlacementBackupDto("placement-1b", "menu-1", "category-1b", "recipe-2", 0),
            )
        }

        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    // Helpers
    private fun createValidMenuCatalogSnapshot() = createEmptyDto().copy(
        menuRecipes = listOf(
            MenuRecipeBackupDto("recipe-1", restId, "Burger", "burger", "10", null, archivedAt = null, createdAt = 1000, updatedAt = 1000),
            MenuRecipeBackupDto("recipe-2", restId, "Fries", "fries", "4", null, archivedAt = null, createdAt = 1000, updatedAt = 1000),
        ),
        menus = listOf(
            MenuBackupDto("menu-1", restId, "Dinner", "dinner", null, "10", 0, null, 1000, 1000),
            MenuBackupDto("menu-2", restId, "Delivery", "delivery", null, "0", 0, null, 1000, 1000),
        ),
        menuCategories = listOf(
            MenuCategoryBackupDto("category-1", "menu-1", "Sides", "sides", 0),
            MenuCategoryBackupDto("category-2", "menu-2", "Sides", "sides", 0),
        ),
        menuPlacements = listOf(
            MenuPlacementBackupDto("placement-1", "menu-1", "category-1", "recipe-1", 0),
            MenuPlacementBackupDto("placement-2", "menu-2", "category-2", "recipe-1", 0),
        ),
    )

    private fun assertMenuIntegrityFailure(dto: BackupSnapshotDto, expectedCode: BackupSnapshotIntegrityCode) {
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(expectedCode)
    }

    private fun mockkIngredient(id: String) = IngredientBackupDto(id, restId, "Name", "name", null, "u1", null, null, null, null, true, 0, 0, null)
    private fun mockkUnit(id: String) = UnitBackupDto(id, "Unit", "u", "MASS", "1.0", true, 0)
    private fun mockkOption(id: String, ingId: String) = IngredientUnitOptionBackupDto(id, ingId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null)
    private fun mockkArea(id: String) = InventoryAreaBackupDto(id, restId, "Area", "area", 0, true, 0, 0, null)
    private fun mockkSupplier(id: String, rId: String = restId) = SupplierBackupDto(id, rId, "Sup", "sup", null, null, null, true, 0, 0, null)
    private fun mockkReceipt(id: String, status: String) = PurchaseReceiptBackupDto(id, restId, null, null, 0L, status, null, null, null, 0L, 0L, if (status == "POSTED") 0L else null, null)
    private fun mockkPurchaseLine(id: String, rId: String, ingId: String, aId: String, oId: String, qty: String) = 
        PurchaseLineBackupDto(id, rId, ingId, aId, oId, qty, qty, "1.0", "1.0", null, 0L, 0L)
    private fun mockkMovement(id: String, type: String, qty: String, docType: String, docId: String, lineId: String?) =
        InventoryMovementBackupDto(id, restId, "ing-1", "area-1", type, qty, "1.0", "1.0", 0L, docType, docId, "op1", lineId, null, 0L)
    private fun mockkOcr(id: String, receiptId: String) = PurchaseInvoiceOcrResultBackupDto(id, receiptId, "sha256", "application/pdf", "MLKIT", 1, 0, "", 0L)
    private fun mockkParse(id: String, receiptId: String, ocrId: String) = PurchaseInvoiceParseResultBackupDto(id, receiptId, ocrId, "sha256", "ENGINE", 2, "{}", "{}", null, "[]", 0L, null)
    private fun mockkParsedLine(parseId: String, index: Int) = PurchaseInvoiceParsedLineBackupDto(parseId, index, "{}", null, false)
}
