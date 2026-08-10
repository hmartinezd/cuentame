package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupAttachmentMetadata
import com.miara.cuentame.core.model.backup.BackupAttachmentReference
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.TableMetadata
import com.miara.cuentame.core.common.database.DatabaseSchema
import com.miara.cuentame.core.model.inventory.InventoryMovementOperationIds
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import java.math.BigDecimal
import java.security.MessageDigest

object BackupTestFixtures {

    fun createEmptySnapshotDto() = BackupSnapshotDto(
        restaurants = emptyList(),
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
        preparationRecipeComponents = emptyList(),
        productionBatches = emptyList(),
        productionBatchComponents = emptyList(),
        purchaseInvoiceOcrResults = emptyList(),
        purchaseInvoiceOcrPages = emptyList(),
        purchaseInvoiceParseResults = emptyList(),
        purchaseInvoiceParsedLines = emptyList(),
        supplierItemMappings = emptyList(),
        purchaseInvoiceLineMatches = emptyList(),
        purchaseInvoiceDraftApplications = emptyList(),
        purchaseInvoiceLineOrigins = emptyList()
    )

    fun createPopulatedCurrentSnapshot(): BackupSnapshotDto {
        return createPopulatedSchema4Snapshot().copy(
            purchaseInvoiceOcrResults = emptyList(),
            purchaseInvoiceOcrPages = emptyList(),
            purchaseInvoiceParseResults = emptyList(),
            purchaseInvoiceParsedLines = emptyList(),
            supplierItemMappings = emptyList(),
            purchaseInvoiceLineMatches = emptyList(),
            purchaseInvoiceDraftApplications = emptyList(),
            purchaseInvoiceLineOrigins = emptyList()
        )
    }

    fun addPostedPurchase(
        snapshot: BackupSnapshotDto,
        receiptId: String,
        lineId: String,
        movementId: String,
        ingredientId: String,
        areaId: String,
        optionId: String,
        quantityBase: BigDecimal,
        unitCostBase: BigDecimal,
        effectiveAt: Long,
        createdAt: Long
    ): BackupSnapshotDto {
        val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
        val totalValue = quantityBase.multiply(unitCostBase, java.math.MathContext.DECIMAL128)

        val receipt = PurchaseReceiptBackupDto(
            id = receiptId,
            restaurantId = restaurantId,
            supplierId = null,
            invoiceNumber = "INV-$receiptId",
            purchaseDate = effectiveAt,
            status = "POSTED",
            notes = null,
            attachmentId = null,
            attachmentDisplayName = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            postedAt = createdAt,
            voidedAt = null
        )

        val line = PurchaseLineBackupDto(
            id = lineId,
            purchaseReceiptId = receiptId,
            ingredientId = ingredientId,
            areaId = areaId,
            ingredientUnitOptionId = optionId,
            quantityEntered = quantityBase.toPlainString(),
            quantityBase = quantityBase.toPlainString(),
            unitCostBase = unitCostBase.toPlainString(),
            lineTotal = totalValue.toPlainString(),
            notes = null,
            createdAt = createdAt,
            updatedAt = createdAt
        )

        val movement = InventoryMovementBackupDto(
            id = movementId,
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            movementType = "PURCHASE",
            quantityBaseSigned = quantityBase.toPlainString(),
            unitCostBaseSnapshot = unitCostBase.toPlainString(),
            totalValueSnapshot = totalValue.toPlainString(),
            effectiveAt = effectiveAt,
            sourceDocumentType = "PURCHASE_RECEIPT",
            sourceDocumentId = receiptId,
            sourceOperationId = InventoryMovementOperationIds.purchasePost(receiptId, lineId),
            sourceLineId = lineId,
            reversalOfMovementId = null,
            createdAt = createdAt
        )

        return snapshot.copy(
            purchaseReceipts = snapshot.purchaseReceipts + receipt,
            purchaseLines = snapshot.purchaseLines + line,
            inventoryMovements = snapshot.inventoryMovements + movement
        )
    }

    fun addPostedWaste(
        snapshot: BackupSnapshotDto,
        eventId: String,
        movementId: String,
        ingredientId: String,
        areaId: String,
        optionId: String,
        quantityBase: BigDecimal,
        unitCostBase: BigDecimal?,
        effectiveAt: Long,
        createdAt: Long
    ): BackupSnapshotDto {
        val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
        val totalValue = unitCostBase?.multiply(quantityBase.negate(), java.math.MathContext.DECIMAL128)

        val event = WasteEventBackupDto(
            id = eventId,
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            ingredientUnitOptionId = optionId,
            quantityEntered = quantityBase.toPlainString(),
            quantityBase = quantityBase.toPlainString(),
            reason = "EXPIRED",
            effectiveAt = effectiveAt,
            notes = null,
            attachmentId = null,
            attachmentDisplayName = null,
            status = "POSTED",
            createdAt = createdAt,
            updatedAt = createdAt,
            postedAt = createdAt,
            voidedAt = null
        )

        val movement = InventoryMovementBackupDto(
            id = movementId,
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            movementType = "WASTE",
            quantityBaseSigned = quantityBase.negate().toPlainString(),
            unitCostBaseSnapshot = unitCostBase?.toPlainString(),
            totalValueSnapshot = totalValue?.toPlainString(),
            effectiveAt = effectiveAt,
            sourceDocumentType = "WASTE_EVENT",
            sourceDocumentId = eventId,
            sourceOperationId = InventoryMovementOperationIds.wastePost(eventId),
            sourceLineId = eventId,
            reversalOfMovementId = null,
            createdAt = createdAt
        )

        return snapshot.copy(
            wasteEvents = snapshot.wasteEvents + event,
            inventoryMovements = snapshot.inventoryMovements + movement
        )
    }

    fun createPopulatedSchema4Snapshot(): BackupSnapshotDto {
        val snapshot = createEmptySnapshotDto().copy(
            restaurants = listOf(RestaurantBackupDto("r1", "Test Rest", "USD", "en-US", 100, 100, null)),
            inventoryAreas = listOf(InventoryAreaBackupDto("a1", "r1", "Area 1", "area 1", 0, true, 100, 100, null)),
            units = listOf(UnitBackupDto("u1", "Unit", "u", "COUNT", "1.0", true, 0)),
            ingredients = listOf(
                IngredientBackupDto("i1", "r1", "Ing 1", "ing 1", null, "u1", "a1", null, null, null, true, 100, 100, null),
                IngredientBackupDto("i2", "r1", "Ing 2", "ing 2", null, "u1", "a1", null, null, null, true, 100, 100, null)
            ),
            ingredientUnitOptions = listOf(
                IngredientUnitOptionBackupDto("o1", "i1", "Opt 1", "o1", null, "1.0", true, true, true, true, 100, 100, null),
                IngredientUnitOptionBackupDto("o2", "i2", "Opt 2", "o2", null, "1.0", true, true, true, true, 100, 100, null)
            ),
            preparationRecipes = listOf(
                PreparationRecipeBackupDto("rec1", "r1", "i1", "Recipe 1", "recipe 1", "10", "10", "o1", "ACTIVE", null, 100, 100, null)
            ),
            preparationRecipeComponents = listOf(
                PreparationRecipeComponentBackupDto("rc1", "rec1", "i2", "o2", "5", "5", 0, null, 100, 100)
            ),
            supplierItemMappings = emptyList(),
            purchaseInvoiceLineMatches = emptyList()
        )
        
        val snapWithPurchase = addPostedPurchase(
            snapshot = snapshot,
            receiptId = "p1",
            lineId = "pl1",
            movementId = "m-purchase",
            ingredientId = "i2",
            areaId = "a1",
            optionId = "o2",
            quantityBase = BigDecimal("10"),
            unitCostBase = BigDecimal("10"),
            effectiveAt = 1000,
            createdAt = 1000
        )

        return addPostedProduction(
            snapshot = snapWithPurchase,
            batchId = "pb1",
            recipeId = "rec1",
            recipeComponentId = "rc1",
            componentId = "pbc1",
            componentIngredientId = "i2",
            componentAreaId = "a1",
            componentOptionId = "o2",
            componentQuantityBase = BigDecimal("5"),
            componentUnitCostBase = BigDecimal("10"),
            consumptionMovementId = "m-consume",
            outputMovementId = "m-out",
            outputIngredientId = "i1",
            outputAreaId = "a1",
            outputOptionId = "o1",
            quantityBase = BigDecimal("10"),
            effectiveAt = 2000,
            createdAt = 2000
        ).copy(
            inventoryBalanceProjections = listOf(
                InventoryBalanceProjectionBackupDto("r1", "i1", "a1", "10", 2000),
                InventoryBalanceProjectionBackupDto("r1", "i2", "a1", "5", 2000) // 10 - 5 = 5
            ),
            ingredientCostProjections = listOf(
                IngredientCostProjectionBackupDto("r1", "i1", "5", 2000),
                IngredientCostProjectionBackupDto("r1", "i2", "10", 2000)
            )
        )
    }

    data class ValidAttachmentArchiveFixture(
        val manifest: BackupManifest,
        val snapshot: BackupSnapshotDto,
        val attachmentId: String,
        val attachmentPath: String,
        val attachmentBytes: ByteArray,
        val archiveBytes: ByteArray
    )

    fun createValidAttachmentArchiveFixture(
        jsonCodecs: com.miara.cuentame.core.backup.api.BackupJsonCodecs
    ): ValidAttachmentArchiveFixture {
        val attachmentId = "a1b2c3d4e5f60708"
        val attachmentBytes = "attachment-content".toByteArray(Charsets.UTF_8)
        val checksumSha256 = MessageDigest.getInstance("SHA-256")
            .digest(attachmentBytes)
            .joinToString("") { "%02x".format(it) }
        
        val snapshot = createPopulatedSchema4Snapshot()
        val sabotagedReceipts = snapshot.purchaseReceipts.map {
            if (it.id == "p1") it.copy(attachmentId = attachmentId) else it
        }
        val snapshotWithAttachment = snapshot.copy(purchaseReceipts = sabotagedReceipts)

        val manifest = BackupManifest(
            backupFormatVersion = 2,
            createdAtUtc = "2026-08-02T12:00:00Z",
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = DatabaseSchema.VERSION,
            restaurantId = "r1",
            restaurantName = "Test Rest",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = mapOf(
                "restaurants" to TableMetadata(1, false),
                "inventory_areas" to TableMetadata(1, false),
                "units" to TableMetadata(1, false),
                "ingredients" to TableMetadata(2, false),
                "ingredient_unit_options" to TableMetadata(2, false),
                "purchase_receipts" to TableMetadata(snapshotWithAttachment.purchaseReceipts.size, false),
                "purchase_lines" to TableMetadata(snapshotWithAttachment.purchaseLines.size, false),
                "inventory_movements" to TableMetadata(snapshotWithAttachment.inventoryMovements.size, false),
                "inventory_balance_projections" to TableMetadata(snapshotWithAttachment.inventoryBalanceProjections.size, true),
                "ingredient_cost_projections" to TableMetadata(snapshotWithAttachment.ingredientCostProjections.size, true),
                "preparation_recipes" to TableMetadata(1, false),
                "preparation_recipe_components" to TableMetadata(1, false),
                "production_batches" to TableMetadata(1, false),
                "production_batch_components" to TableMetadata(1, false),
                "ingredient_categories" to TableMetadata(0, false),
                "suppliers" to TableMetadata(0, false),
                "stock_counts" to TableMetadata(0, false),
                "stock_count_areas" to TableMetadata(0, false),
                "stock_count_lines" to TableMetadata(0, false),
                "waste_events" to TableMetadata(0, false),
                "purchase_invoice_ocr_results" to TableMetadata(0, false),
                "purchase_invoice_ocr_pages" to TableMetadata(0, false),
                "purchase_invoice_parse_results" to TableMetadata(0, false),
                "purchase_invoice_parsed_lines" to TableMetadata(0, false),
                "supplier_item_mappings" to TableMetadata(0, false),
                "purchase_invoice_line_matches" to TableMetadata(0, false),
                "purchase_invoice_draft_applications" to TableMetadata(0, false),
                "purchase_invoice_line_origins" to TableMetadata(0, false)
            ).entries.sortedBy { it.key }.associate { it.key to it.value },
            attachments = listOf(
                BackupAttachmentMetadata(
                    attachmentId = attachmentId,
                    archivePath = "attachments/$attachmentId/file.jpg",
                    displayName = "file.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = attachmentBytes.size.toLong(),
                    checksumSha256 = checksumSha256,
                    referencedBy = listOf(BackupAttachmentReference("PURCHASE_RECEIPT", "p1"))
                )
            ),
            includedSections = listOf("data", "preferences", "attachments")
        )

        val preferences = BackupPreferencesDto(
            themeMode = "SYSTEM",
            dynamicColorEnabled = true,
            appLocaleTag = "en-US"
        )
        val snapshotJson = jsonCodecs.writer.encodeToString(snapshotWithAttachment).toByteArray(Charsets.UTF_8)
        val manifestJson = jsonCodecs.writer.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        val settingsJson = jsonCodecs.writer.encodeToString(preferences).toByteArray(Charsets.UTF_8)

        val checksumMap = mapOf(
            "data/database.json" to MessageDigest.getInstance("SHA-256").digest(snapshotJson).joinToString("") { "%02x".format(it) },
            "manifest.json" to MessageDigest.getInstance("SHA-256").digest(manifestJson).joinToString("") { "%02x".format(it) },
            "preferences/settings.json" to MessageDigest.getInstance("SHA-256").digest(settingsJson).joinToString("") { "%02x".format(it) },
            "attachments/$attachmentId/file.jpg" to checksumSha256
        )
        val serializer = MapSerializer(String.serializer(), String.serializer())
        val checksumsJson = jsonCodecs.writer.encodeToString(serializer, checksumMap.toSortedMap()).toByteArray(Charsets.UTF_8)

        val baos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(baos)
        
        fun addEntry(name: String, bytes: ByteArray) {
            val entry = java.util.zip.ZipEntry(name)
            zos.putNextEntry(entry)
            zos.write(bytes)
            zos.closeEntry()
        }

        addEntry("manifest.json", manifestJson)
        addEntry("data/database.json", snapshotJson)
        addEntry("preferences/settings.json", settingsJson)
        addEntry("checksums.json", checksumsJson)
        addEntry("attachments/$attachmentId/file.jpg", attachmentBytes)
        
        zos.close()
        
        return ValidAttachmentArchiveFixture(
            manifest = manifest,
            snapshot = snapshotWithAttachment,
            attachmentId = attachmentId,
            attachmentPath = "attachments/$attachmentId/file.jpg",
            attachmentBytes = attachmentBytes,
            archiveBytes = baos.toByteArray()
        )
    }

    fun addPostedProduction(
        snapshot: BackupSnapshotDto,
        batchId: String,
        recipeId: String,
        recipeComponentId: String,
        componentId: String,
        componentIngredientId: String,
        componentAreaId: String,
        componentOptionId: String,
        componentQuantityBase: BigDecimal,
        componentUnitCostBase: BigDecimal,
        consumptionMovementId: String,
        outputMovementId: String,
        outputIngredientId: String,
        outputAreaId: String,
        outputOptionId: String,
        quantityBase: BigDecimal,
        effectiveAt: Long,
        createdAt: Long
    ): BackupSnapshotDto {
        val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
        val recipe = snapshot.preparationRecipes.find { it.id == recipeId }
        val recipeName = recipe?.name ?: "Test Recipe"
        
        val componentTotal = componentQuantityBase.multiply(componentUnitCostBase, java.math.MathContext.DECIMAL128)
        
        val outputUnitCost = componentTotal.divide(quantityBase, java.math.MathContext.DECIMAL128)
        val outputTotal = componentTotal // Value conservation

        val batch = ProductionBatchBackupDto(
            id = batchId,
            restaurantId = restaurantId,
            recipeId = recipeId,
            recipeNameSnapshot = recipeName,
            outputIngredientId = outputIngredientId,
            batchMultiplier = "1",
            recipeStandardYieldQuantitySnapshot = quantityBase.toPlainString(),
            recipeStandardYieldBaseSnapshot = quantityBase.toPlainString(),
            recipeYieldUnitOptionIdSnapshot = outputOptionId,
            expectedOutputQuantityEntered = quantityBase.toPlainString(),
            expectedOutputQuantityBase = quantityBase.toPlainString(),
            actualOutputQuantityEntered = quantityBase.toPlainString(),
            actualOutputQuantityBase = quantityBase.toPlainString(),
            outputUnitOptionId = outputOptionId,
            outputAreaId = outputAreaId,
            hasManualOutputQuantityOverride = false,
            totalComponentCostSnapshot = componentTotal.toPlainString(),
            outputUnitCostBaseSnapshot = outputUnitCost.toPlainString(),
            status = "POSTED",
            effectiveAt = effectiveAt,
            notes = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            postedAt = createdAt,
            voidedAt = null
        )

        val component = ProductionBatchComponentBackupDto(
            id = componentId,
            productionBatchId = batchId,
            sourceRecipeComponentIdSnapshot = recipeComponentId,
            componentIngredientId = componentIngredientId,
            recipeQuantityEnteredSnapshot = componentQuantityBase.toPlainString(),
            recipeQuantityBaseSnapshot = componentQuantityBase.toPlainString(),
            recipeUnitOptionIdSnapshot = componentOptionId,
            expectedQuantityEntered = componentQuantityBase.toPlainString(),
            expectedQuantityBase = componentQuantityBase.toPlainString(),
            actualQuantityEntered = componentQuantityBase.toPlainString(),
            actualQuantityBase = componentQuantityBase.toPlainString(),
            unitOptionId = componentOptionId,
            hasManualQuantityOverride = false,
            sourceAreaId = componentAreaId,
            unitCostBaseSnapshot = componentUnitCostBase.toPlainString(),
            totalCostSnapshot = componentTotal.toPlainString(),
            sortOrder = 0,
            notes = null,
            createdAt = createdAt,
            updatedAt = createdAt
        )

        val consumption = InventoryMovementBackupDto(
            id = consumptionMovementId,
            restaurantId = restaurantId,
            ingredientId = componentIngredientId,
            areaId = componentAreaId,
            movementType = "PRODUCTION_CONSUMPTION",
            quantityBaseSigned = componentQuantityBase.negate().toPlainString(),
            unitCostBaseSnapshot = componentUnitCostBase.toPlainString(),
            totalValueSnapshot = componentTotal.negate().toPlainString(),
            effectiveAt = effectiveAt,
            sourceDocumentType = "PRODUCTION_BATCH",
            sourceDocumentId = batchId,
            sourceOperationId = InventoryMovementOperationIds.productionConsumption(batchId, componentId),
            sourceLineId = componentId,
            reversalOfMovementId = null,
            createdAt = createdAt
        )

        val output = InventoryMovementBackupDto(
            id = outputMovementId,
            restaurantId = restaurantId,
            ingredientId = outputIngredientId,
            areaId = outputAreaId,
            movementType = "PRODUCTION_OUTPUT",
            quantityBaseSigned = quantityBase.toPlainString(),
            unitCostBaseSnapshot = outputUnitCost.toPlainString(),
            totalValueSnapshot = outputTotal.toPlainString(),
            effectiveAt = effectiveAt,
            sourceDocumentType = "PRODUCTION_BATCH",
            sourceDocumentId = batchId,
            sourceOperationId = InventoryMovementOperationIds.productionOutput(batchId),
            sourceLineId = batchId,
            reversalOfMovementId = null,
            createdAt = createdAt
        )

        return snapshot.copy(
            productionBatches = snapshot.productionBatches + batch,
            productionBatchComponents = snapshot.productionBatchComponents + component,
            inventoryMovements = snapshot.inventoryMovements + consumption + output
        )
    }


    fun addProductionConsumption(
        snapshot: BackupSnapshotDto,
        batchId: String,
        componentId: String,
        movementId: String,
        ingredientId: String,
        areaId: String,
        quantityBase: BigDecimal,
        unitCostBase: BigDecimal,
        effectiveAt: Long,
        createdAt: Long
    ): BackupSnapshotDto {
        val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
        val totalValue = quantityBase.multiply(unitCostBase, java.math.MathContext.DECIMAL128)

        val component = ProductionBatchComponentBackupDto(
            id = componentId,
            productionBatchId = batchId,
            sourceRecipeComponentIdSnapshot = "rc1",
            componentIngredientId = ingredientId,
            recipeQuantityEnteredSnapshot = quantityBase.toPlainString(),
            recipeQuantityBaseSnapshot = quantityBase.toPlainString(),
            recipeUnitOptionIdSnapshot = "opt1",
            expectedQuantityEntered = quantityBase.toPlainString(),
            expectedQuantityBase = quantityBase.toPlainString(),
            actualQuantityEntered = quantityBase.toPlainString(),
            actualQuantityBase = quantityBase.toPlainString(),
            unitOptionId = "opt1",
            hasManualQuantityOverride = false,
            sourceAreaId = areaId,
            unitCostBaseSnapshot = unitCostBase.toPlainString(),
            totalCostSnapshot = totalValue.toPlainString(),
            sortOrder = 0,
            notes = null,
            createdAt = createdAt,
            updatedAt = createdAt
        )

        val movement = InventoryMovementBackupDto(
            id = movementId,
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            movementType = "PRODUCTION_CONSUMPTION",
            quantityBaseSigned = quantityBase.negate().toPlainString(),
            unitCostBaseSnapshot = unitCostBase.toPlainString(),
            totalValueSnapshot = totalValue.negate().toPlainString(),
            effectiveAt = effectiveAt,
            sourceDocumentType = "PRODUCTION_BATCH",
            sourceDocumentId = batchId,
            sourceOperationId = InventoryMovementOperationIds.productionConsumption(batchId, componentId),
            sourceLineId = componentId,
            reversalOfMovementId = null,
            createdAt = createdAt
        )

        return snapshot.copy(
            productionBatchComponents = snapshot.productionBatchComponents + component,
            inventoryMovements = snapshot.inventoryMovements + movement
        )
    }

    fun addReversal(
        snapshot: BackupSnapshotDto,
        originalMovementId: String,
        reversalMovementId: String,
        effectiveAt: Long,
        createdAt: Long
    ): BackupSnapshotDto {
        val original = snapshot.inventoryMovements.find { it.id == originalMovementId }
            ?: throw IllegalArgumentException("Original movement not found")
        
        val reversal = original.copy(
            id = reversalMovementId,
            movementType = "REVERSAL",
            quantityBaseSigned = BigDecimal(original.quantityBaseSigned).negate().toPlainString(),
            totalValueSnapshot = original.totalValueSnapshot?.let { BigDecimal(it).negate().toPlainString() },
            effectiveAt = effectiveAt,
            sourceOperationId = InventoryMovementOperationIds.reversal(originalMovementId),
            reversalOfMovementId = originalMovementId,
            createdAt = createdAt
        )

        return snapshot.copy(
            inventoryMovements = snapshot.inventoryMovements + reversal
        )
    }
}
