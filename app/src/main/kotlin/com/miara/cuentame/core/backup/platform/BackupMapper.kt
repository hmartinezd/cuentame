package com.miara.cuentame.core.backup.platform

import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.database.backup.BackupSnapshot
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import com.miara.cuentame.core.common.decimal.toCanonicalDecimalString

object BackupMapper {

    fun mapToDto(
        snapshot: BackupSnapshot,
        attachmentIdMap: Map<String, String> // URI -> ID
    ): BackupSnapshotDto {
        return BackupSnapshotDto(
            restaurants = snapshot.restaurants.map { it.toDto() }.sortedBy { it.id },
            inventoryAreas = snapshot.inventoryAreas.map { it.toDto() }.sortedBy { it.id },
            ingredientCategories = snapshot.ingredientCategories.map { it.toDto() }.sortedBy { it.id },
            units = snapshot.units.map { it.toDto() }.sortedBy { it.id },
            ingredients = snapshot.ingredients.map { it.toDto() }.sortedBy { it.id },
            ingredientUnitOptions = snapshot.ingredientUnitOptions.map { it.toDto() }.sortedBy { it.id },
            suppliers = snapshot.suppliers.map { it.toDto() }.sortedBy { it.id },
            purchaseReceipts = snapshot.purchaseReceipts.map { it.toDto(attachmentIdMap) }.sortedBy { it.id },
            purchaseLines = snapshot.purchaseLines.map { it.toDto() }.sortedBy { it.id },
            stockCounts = snapshot.stockCounts.map { it.toDto() }.sortedBy { it.id },
            stockCountAreas = snapshot.stockCountAreas.map { it.toDto() }.sortedBy { it.id },
            stockCountLines = snapshot.stockCountLines.map { it.toDto() }.sortedBy { it.id },
            stockCountItemOrder = snapshot.stockCountItemOrder.map { it.toDto() }
                .sortedWith(compareBy({ it.areaId }, { it.sortOrder }, { it.ingredientId })),
            wasteEvents = snapshot.wasteEvents.map { it.toDto(attachmentIdMap) }.sortedBy { it.id },
            inventoryMovements = snapshot.inventoryMovements.map { it.toDto() }.sortedBy { it.id },
            inventoryBalanceProjections = snapshot.inventoryBalanceProjections.map { it.toDto() }
                .sortedWith(compareBy({ it.restaurantId }, { it.ingredientId }, { it.areaId })),
            ingredientCostProjections = snapshot.ingredientCostProjections.map { it.toDto() }
                .sortedWith(compareBy({ it.restaurantId }, { it.ingredientId })),
            preparationRecipes = snapshot.preparationRecipes.map { it.toDto() }.sortedBy { it.id },
            preparationRecipeComponents = snapshot.preparationRecipeComponents.map { it.toDto() }.sortedBy { it.id },
            productionBatches = snapshot.productionBatches.map { it.toDto() }.sortedBy { it.id },
            productionBatchComponents = snapshot.productionBatchComponents.map { it.toDto() }.sortedBy { it.id },
            purchaseInvoiceOcrResults = snapshot.purchaseInvoiceOcrResults.map { it.toDto() }.sortedBy { it.id },
            purchaseInvoiceOcrPages = snapshot.purchaseInvoiceOcrPages.map { it.toDto() }
                .sortedWith(compareBy({ it.ocrResultId }, { it.pageIndex })),
            purchaseInvoiceParseResults = snapshot.purchaseInvoiceParseResults.map { it.toDto() }.sortedBy { it.id },
            purchaseInvoiceParsedLines = snapshot.purchaseInvoiceParsedLines.map { it.toDto() }
                .sortedWith(compareBy({ it.parseResultId }, { it.lineIndex })),
            supplierItemMappings = snapshot.supplierItemMappings.map { it.toDto() }.sortedBy { it.id },
            purchaseInvoiceLineMatches = snapshot.purchaseInvoiceLineMatches.map { it.toDto() }
                .sortedWith(compareBy({ it.parseResultId }, { it.lineIndex })),
            purchaseInvoiceDraftApplications = snapshot.purchaseInvoiceDraftApplications.map { it.toDto() }.sortedBy { it.id },
            purchaseInvoiceLineOrigins = snapshot.purchaseInvoiceLineOrigins.map { it.toDto() }.sortedBy { it.purchaseLineId },
            menuRecipes = snapshot.menuRecipes.map { it.toDto() }.sortedBy { it.id },
            menuRecipeComponents = snapshot.menuRecipeComponents.map { it.toDto() }.sortedBy { it.id },
            menus = snapshot.menus.map { it.toDto() }.sortedBy { it.id },
            menuCategories = snapshot.menuCategories.map { it.toDto() }.sortedBy { it.id },
            menuPlacements = snapshot.menuPlacements.map { it.toDto() }.sortedBy { it.id },
            menuPublications = snapshot.menuPublications.map { it.toDto() }.sortedBy { it.id },
            menuPublicationCategories = snapshot.menuPublicationCategories.map { it.toDto() }.sortedBy { it.id },
            menuPublicationItems = snapshot.menuPublicationItems.map { it.toDto() }.sortedBy { it.id },
            menuPublicationItemComponents = snapshot.menuPublicationItemComponents.map { it.toDto() }.sortedBy { it.id }
        )
    }
}

internal fun PurchaseInvoiceDraftApplicationEntity.toDto(): PurchaseInvoiceDraftApplicationBackupDto = 
    PurchaseInvoiceDraftApplicationBackupDto(
        id = id,
        purchaseReceiptId = purchaseReceiptId,
        parseResultId = parseResultId,
        sourceDocumentSha256 = sourceDocumentSha256,
        sourceStateFingerprint = sourceStateFingerprint,
        appliedAt = appliedAt,
        duplicateOverrideType = duplicateOverrideType,
        duplicateExistingReceiptId = duplicateExistingReceiptId,
        duplicateNormalizedInvoiceNumber = duplicateNormalizedInvoiceNumber,
        duplicateSourceSha256 = duplicateSourceSha256,
        duplicateOverriddenAt = duplicateOverriddenAt
    )

internal fun PurchaseInvoiceLineOriginEntity.toDto(): PurchaseInvoiceLineOriginBackupDto = 
    PurchaseInvoiceLineOriginBackupDto(
        purchaseLineId = purchaseLineId,
        applicationId = applicationId,
        sourceLineIndex = sourceLineIndex,
        sourceStateFingerprint = sourceStateFingerprint,
        lastMaterializedSnapshotJson = lastMaterializedSnapshotJson
    )

internal fun MenuRecipeEntity.toDto() = MenuRecipeBackupDto(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    sellingPrice = sellingPrice?.toNormalizedString(),
    notes = notes,
    cashDiscountBehavior = cashDiscountBehavior.name,
    commercialRevision = commercialRevision,
    consumptionRevision = consumptionRevision,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun MenuEntity.toDto() = MenuBackupDto(id,restaurantId,name,normalizedName,description,defaultCashDiscountPercent.toNormalizedString(),publicationRevision,archivedAt,createdAt,updatedAt)
internal fun MenuCategoryEntity.toDto() = MenuCategoryBackupDto(id,menuId,name,normalizedName,sortOrder)
internal fun MenuPlacementEntity.toDto() = MenuPlacementBackupDto(id,menuId,categoryId,menuRecipeId,sortOrder)
internal fun MenuPublicationEntity.toDto()=MenuPublicationBackupDto(id,restaurantId,sourceMenuId,publicationRevision,menuNameSnapshot,menuDescriptionSnapshot,defaultCashDiscountPercentSnapshot.toNormalizedString(),currencyCodeSnapshot,publishedAt)
internal fun MenuPublicationCategoryEntity.toDto()=MenuPublicationCategoryBackupDto(id,publicationId,sourceMenuCategoryId,nameSnapshot,sortOrder)
internal fun MenuPublicationItemEntity.toDto()=MenuPublicationItemBackupDto(id,publicationId,publicationCategoryId,sourceMenuPlacementId,menuRecipeId,displayNameSnapshot,sellingPriceSnapshot.toNormalizedString(),cashDiscountBehaviorSnapshot.name,commercialRevision,consumptionRevision,sortOrder)
internal fun MenuPublicationItemComponentEntity.toDto()=MenuPublicationItemComponentBackupDto(id,publicationItemId,sourceMenuRecipeComponentId,ingredientId,ingredientUnitOptionId,quantityEnteredSnapshot.toNormalizedString(),quantityBaseSnapshot.toNormalizedString(),sortOrder)

internal fun MenuRecipeComponentEntity.toDto() = MenuRecipeComponentBackupDto(
    id = id,
    menuRecipeId = menuRecipeId,
    ingredientId = ingredientId,
    ingredientUnitOptionId = ingredientUnitOptionId,
    quantityEntered = quantityEntered.toNormalizedString(),
    quantityBase = quantityBase.toNormalizedString(),
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun RestaurantEntity.toDto() = RestaurantBackupDto(
    id = id,
    name = name,
    currencyCode = currencyCode,
    localeTag = localeTag,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

internal fun InventoryAreaEntity.toDto() = InventoryAreaBackupDto(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

internal fun IngredientCategoryEntity.toDto() = IngredientCategoryBackupDto(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

internal fun UnitEntity.toDto() = UnitBackupDto(
    id = id,
    name = name,
    symbol = symbol,
    dimension = dimension,
    factorToCanonical = factorToCanonical.toNormalizedString(),
    isSystem = isSystem,
    sortOrder = sortOrder
)

internal fun IngredientEntity.toDto() = IngredientBackupDto(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    categoryId = categoryId,
    baseUnitId = baseUnitId,
    defaultAreaId = defaultAreaId,
    sku = sku,
    notes = notes,
    reorderPointBase = reorderPointBase?.toNormalizedString(),
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    parLevelBase = parLevelBase?.toNormalizedString()
)

fun MenuBackupDto.toEntity() = MenuEntity(id,restaurantId,name,normalizedName,description,java.math.BigDecimal(defaultCashDiscountPercent),publicationRevision,archivedAt,createdAt,updatedAt)
fun MenuPublicationBackupDto.toEntity()=MenuPublicationEntity(id,restaurantId,sourceMenuId,publicationRevision,menuNameSnapshot,menuDescriptionSnapshot,java.math.BigDecimal(defaultCashDiscountPercentSnapshot),currencyCodeSnapshot,publishedAt)
fun MenuPublicationCategoryBackupDto.toEntity()=MenuPublicationCategoryEntity(id,publicationId,sourceMenuCategoryId,nameSnapshot,sortOrder)
fun MenuPublicationItemBackupDto.toEntity()=MenuPublicationItemEntity(id,publicationId,publicationCategoryId,sourceMenuPlacementId,menuRecipeId,displayNameSnapshot,java.math.BigDecimal(sellingPriceSnapshot),com.miara.cuentame.core.model.menu.CashDiscountBehavior.valueOf(cashDiscountBehaviorSnapshot),commercialRevision,consumptionRevision,sortOrder)
fun MenuPublicationItemComponentBackupDto.toEntity()=MenuPublicationItemComponentEntity(id,publicationItemId,sourceMenuRecipeComponentId,ingredientId,ingredientUnitOptionId,java.math.BigDecimal(quantityEnteredSnapshot),java.math.BigDecimal(quantityBaseSnapshot),sortOrder)
fun MenuCategoryBackupDto.toEntity() = MenuCategoryEntity(id,menuId,name,normalizedName,sortOrder)
fun MenuPlacementBackupDto.toEntity() = MenuPlacementEntity(id,menuId,categoryId,menuRecipeId,sortOrder)

internal fun IngredientUnitOptionEntity.toDto() = IngredientUnitOptionBackupDto(
    id = id,
    ingredientId = ingredientId,
    displayName = displayName,
    shortLabel = shortLabel,
    standardUnitId = standardUnitId,
    factorToBase = factorToBase.toNormalizedString(),
    isBase = isBase,
    isDefaultCount = isDefaultCount,
    isDefaultPurchase = isDefaultPurchase,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

internal fun SupplierEntity.toDto() = SupplierBackupDto(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    phone = phone,
    email = email,
    notes = notes,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

internal fun PurchaseReceiptEntity.toDto(attachmentIdMap: Map<String, String>) = PurchaseReceiptBackupDto(
    id = id,
    restaurantId = restaurantId,
    supplierId = supplierId,
    invoiceNumber = invoiceNumber,
    purchaseDate = purchaseDate,
    status = status,
    notes = notes, 
    attachmentId = attachmentPath?.let { attachmentIdMap[it] }, 
    attachmentDisplayName = attachmentDisplayName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    postedAt = postedAt,
    voidedAt = voidedAt
)

internal fun PurchaseLineEntity.toDto() = PurchaseLineBackupDto(
    id = id,
    purchaseReceiptId = purchaseReceiptId,
    ingredientId = ingredientId,
    areaId = areaId,
    ingredientUnitOptionId = ingredientUnitOptionId,
    quantityEntered = quantityEntered,
    quantityBase = quantityBase,
    unitCostBase = unitCostBase,
    lineTotal = lineTotal,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun StockCountEntity.toDto() = StockCountBackupDto(
    id = id,
    restaurantId = restaurantId,
    name = name,
    startedAt = startedAt,
    effectiveAt = effectiveAt,
    completedAt = completedAt,
    status = status,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    voidedAt = voidedAt
)

internal fun StockCountAreaEntity.toDto() = StockCountAreaBackupDto(
    id = id,
    stockCountId = stockCountId,
    areaId = areaId,
    status = status,
    startedAt = startedAt,
    completedAt = completedAt,
    sortOrder = sortOrder
)

internal fun StockCountLineEntity.toDto() = StockCountLineBackupDto(
    id = id,
    stockCountAreaId = stockCountAreaId,
    ingredientId = ingredientId,
    ingredientUnitOptionId = ingredientUnitOptionId,
    quantityEntered = quantityEntered,
    quantityBase = quantityBase,
    expectedQuantityBaseSnapshot = expectedQuantityBaseSnapshot,
    adjustmentQuantityBase = adjustmentQuantityBase,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun StockCountItemOrderEntity.toDto() = StockCountItemOrderBackupDto(
    restaurantId, areaId, ingredientId, sortOrder, updatedAt
)

internal fun WasteEventEntity.toDto(attachmentIdMap: Map<String, String>) = WasteEventBackupDto(
    id = id,
    restaurantId = restaurantId,
    ingredientId = ingredientId,
    areaId = areaId,
    ingredientUnitOptionId = ingredientUnitOptionId,
    quantityEntered = quantityEntered,
    quantityBase = quantityBase,
    reason = reason,
    effectiveAt = effectiveAt,
    notes = notes, 
    attachmentId = attachmentPath?.let { attachmentIdMap[it] }, 
    attachmentDisplayName = attachmentDisplayName,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    postedAt = postedAt,
    voidedAt = voidedAt
)

internal fun InventoryMovementEntity.toDto() = InventoryMovementBackupDto(
    id = id,
    restaurantId = restaurantId,
    ingredientId = ingredientId,
    areaId = areaId,
    movementType = movementType,
    quantityBaseSigned = quantityBaseSigned,
    unitCostBaseSnapshot = unitCostBaseSnapshot,
    totalValueSnapshot = totalValueSnapshot,
    effectiveAt = effectiveAt,
    sourceDocumentType = sourceDocumentType,
    sourceDocumentId = sourceDocumentId,
    sourceOperationId = sourceOperationId,
    sourceLineId = sourceLineId,
    reversalOfMovementId = reversalOfMovementId,
    createdAt = createdAt
)

internal fun InventoryBalanceProjectionEntity.toDto() = InventoryBalanceProjectionBackupDto(
    restaurantId = restaurantId,
    ingredientId = ingredientId,
    areaId = areaId,
    quantityBase = quantityBase,
    updatedAt = updatedAt
)

internal fun IngredientCostProjectionEntity.toDto() = IngredientCostProjectionBackupDto(
    restaurantId = restaurantId,
    ingredientId = ingredientId,
    averageUnitCostBase = averageUnitCostBase,
    updatedAt = updatedAt
)

internal fun PreparationRecipeEntity.toDto() = PreparationRecipeBackupDto(
    id = id,
    restaurantId = restaurantId,
    outputIngredientId = outputIngredientId,
    name = name,
    normalizedName = normalizedName,
    standardYieldQuantity = standardYieldQuantity?.toNormalizedString(),
    standardYieldQuantityBase = standardYieldQuantityBase?.toNormalizedString(),
    yieldUnitOptionId = yieldUnitOptionId,
    status = status,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt
)

internal fun PreparationRecipeComponentEntity.toDto() = PreparationRecipeComponentBackupDto(
    id = id,
    recipeId = recipeId,
    componentIngredientId = componentIngredientId,
    unitOptionId = unitOptionId,
    quantityEntered = quantityEntered.toNormalizedString(),
    quantityBase = quantityBase.toNormalizedString(),
    sortOrder = sortOrder,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun ProductionBatchEntity.toDto() = ProductionBatchBackupDto(
    id = id,
    restaurantId = restaurantId,
    recipeId = recipeId,
    recipeNameSnapshot = recipeNameSnapshot,
    outputIngredientId = outputIngredientId,
    batchMultiplier = batchMultiplier,
    recipeStandardYieldQuantitySnapshot = recipeStandardYieldQuantitySnapshot,
    recipeStandardYieldBaseSnapshot = recipeStandardYieldBaseSnapshot,
    recipeYieldUnitOptionIdSnapshot = recipeYieldUnitOptionIdSnapshot,
    expectedOutputQuantityEntered = expectedOutputQuantityEntered,
    expectedOutputQuantityBase = expectedOutputQuantityBase,
    actualOutputQuantityEntered = actualOutputQuantityEntered,
    actualOutputQuantityBase = actualOutputQuantityBase,
    outputUnitOptionId = outputUnitOptionId,
    outputAreaId = outputAreaId,
    hasManualOutputQuantityOverride = hasManualOutputQuantityOverride,
    totalComponentCostSnapshot = totalComponentCostSnapshot,
    outputUnitCostBaseSnapshot = outputUnitCostBaseSnapshot,
    effectiveAt = effectiveAt,
    status = status,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    postedAt = postedAt,
    voidedAt = voidedAt
)

internal fun ProductionBatchComponentEntity.toDto() = ProductionBatchComponentBackupDto(
    id = id,
    productionBatchId = productionBatchId,
    sourceRecipeComponentIdSnapshot = sourceRecipeComponentIdSnapshot,
    componentIngredientId = componentIngredientId,
    recipeQuantityEnteredSnapshot = recipeQuantityEnteredSnapshot,
    recipeQuantityBaseSnapshot = recipeQuantityBaseSnapshot,
    recipeUnitOptionIdSnapshot = recipeUnitOptionIdSnapshot,
    expectedQuantityEntered = expectedQuantityEntered,
    expectedQuantityBase = expectedQuantityBase,
    actualQuantityEntered = actualQuantityEntered,
    actualQuantityBase = actualQuantityBase,
    unitOptionId = unitOptionId,
    hasManualQuantityOverride = hasManualQuantityOverride,
    sourceAreaId = sourceAreaId,
    unitCostBaseSnapshot = unitCostBaseSnapshot,
    totalCostSnapshot = totalCostSnapshot,
    sortOrder = sortOrder,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun PurchaseInvoiceOcrResultEntity.toDto() = PurchaseInvoiceOcrResultBackupDto(
    id = id,
    purchaseReceiptId = purchaseReceiptId,
    sourceDocumentSha256 = sourceDocumentSha256,
    sourceMimeType = sourceMimeType,
    engine = engine,
    evidenceSchemaVersion = evidenceSchemaVersion,
    pageCount = pageCount,
    fullText = fullText,
    processedAt = processedAt
)

internal fun PurchaseInvoiceOcrPageEntity.toDto() = PurchaseInvoiceOcrPageBackupDto(
    ocrResultId = ocrResultId,
    pageIndex = pageIndex,
    widthPx = widthPx,
    heightPx = heightPx,
    text = text,
    evidenceJson = evidenceJson
)

internal fun PurchaseInvoiceParseResultEntity.toDto() = PurchaseInvoiceParseResultBackupDto(
    id = id,
    purchaseReceiptId = purchaseReceiptId,
    ocrResultId = ocrResultId,
    sourceDocumentSha256 = sourceDocumentSha256,
    parserEngine = parserEngine,
    parserSchemaVersion = parserSchemaVersion,
    headerEvidenceJson = headerEvidenceJson,
    totalsEvidenceJson = totalsEvidenceJson,
    correctionsJson = correctionsJson,
    warningsJson = warningsJson,
    processedAt = processedAt,
    reviewedAt = reviewedAt
)

internal fun PurchaseInvoiceParsedLineEntity.toDto() = PurchaseInvoiceParsedLineBackupDto(
    parseResultId = parseResultId,
    lineIndex = lineIndex,
    evidenceJson = evidenceJson,
    correctionJson = correctionJson,
    isIgnored = isIgnored
)

internal fun SupplierItemMappingEntity.toDto() = SupplierItemMappingBackupDto(
    id = id,
    restaurantId = restaurantId,
    supplierId = supplierId,
    keyType = keyType.name,
    normalizedKey = normalizedKey,
    sourceVendorCode = sourceVendorCode,
    sourceDescription = sourceDescription,
    sourcePackageText = sourcePackageText,
    ingredientId = ingredientId,
    unitOptionId = unitOptionId,
    inventoryAreaId = inventoryAreaId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastConfirmedAt = lastConfirmedAt
)

internal fun PurchaseInvoiceLineMatchEntity.toDto() = PurchaseInvoiceLineMatchBackupDto(
    parseResultId = parseResultId,
    lineIndex = lineIndex,
    status = status.name,
    supplierId = supplierId,
    ingredientId = ingredientId,
    unitOptionId = unitOptionId,
    inventoryAreaId = inventoryAreaId,
    mappingId = mappingId,
    matchMethod = matchMethod,
    matchConfidence = matchConfidence,
    confirmedAt = confirmedAt
)

private fun java.math.BigDecimal.toNormalizedString(): String = toCanonicalDecimalString()


fun RestaurantBackupDto.toEntity() = RestaurantEntity(
    id = id,
    name = name,
    currencyCode = currencyCode,
    localeTag = localeTag,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun InventoryAreaBackupDto.toEntity() = InventoryAreaEntity(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun IngredientCategoryBackupDto.toEntity() = IngredientCategoryEntity(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun UnitBackupDto.toEntity() = UnitEntity(
    id = id,
    name = name,
    symbol = symbol,
    dimension = dimension,
    factorToCanonical = java.math.BigDecimal(factorToCanonical),
    isSystem = isSystem,
    sortOrder = sortOrder
)

fun SupplierBackupDto.toEntity() = SupplierEntity(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    phone = phone,
    email = email,
    notes = notes,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun IngredientBackupDto.toEntity() = IngredientEntity(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    categoryId = categoryId,
    baseUnitId = baseUnitId,
    defaultAreaId = defaultAreaId,
    sku = sku,
    notes = notes,
    reorderPointBase = reorderPointBase?.let { java.math.BigDecimal(it) },
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    parLevelBase = parLevelBase?.let { java.math.BigDecimal(it) }
)

fun IngredientUnitOptionBackupDto.toEntity() = IngredientUnitOptionEntity(
    id = id,
    ingredientId = ingredientId,
    displayName = displayName,
    shortLabel = shortLabel,
    standardUnitId = standardUnitId,
    factorToBase = java.math.BigDecimal(factorToBase),
    isBase = isBase,
    isDefaultCount = isDefaultCount,
    isDefaultPurchase = isDefaultPurchase,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun PurchaseReceiptBackupDto.toEntity() = PurchaseReceiptEntity(
    id = id,
    restaurantId = restaurantId,
    supplierId = supplierId,
    invoiceNumber = invoiceNumber,
    purchaseDate = purchaseDate,
    status = status,
    notes = notes,
    attachmentPath = attachmentId,
    attachmentDisplayName = attachmentDisplayName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    postedAt = postedAt,
    voidedAt = voidedAt
)

fun PurchaseLineBackupDto.toEntity() = PurchaseLineEntity(
    id = id,
    purchaseReceiptId = purchaseReceiptId,
    ingredientId = ingredientId,
    areaId = areaId,
    ingredientUnitOptionId = ingredientUnitOptionId,
    quantityEntered = quantityEntered,
    quantityBase = quantityBase,
    lineTotal = lineTotal,
    unitCostBase = unitCostBase,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun StockCountBackupDto.toEntity() = StockCountEntity(
    id = id,
    restaurantId = restaurantId,
    name = name,
    startedAt = startedAt,
    effectiveAt = effectiveAt,
    completedAt = completedAt,
    status = status,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    voidedAt = voidedAt
)

fun StockCountAreaBackupDto.toEntity() = StockCountAreaEntity(
    id = id,
    stockCountId = stockCountId,
    areaId = areaId,
    status = status,
    startedAt = startedAt,
    completedAt = completedAt,
    sortOrder = sortOrder
)

fun StockCountLineBackupDto.toEntity() = StockCountLineEntity(
    id = id,
    stockCountAreaId = stockCountAreaId,
    ingredientId = ingredientId,
    ingredientUnitOptionId = ingredientUnitOptionId,
    quantityEntered = quantityEntered,
    quantityBase = quantityBase,
    expectedQuantityBaseSnapshot = expectedQuantityBaseSnapshot,
    adjustmentQuantityBase = adjustmentQuantityBase,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun StockCountItemOrderBackupDto.toEntity() = StockCountItemOrderEntity(
    restaurantId, areaId, ingredientId, sortOrder, updatedAt
)

fun WasteEventBackupDto.toEntity() = WasteEventEntity(
    id = id,
    restaurantId = restaurantId,
    ingredientId = ingredientId,
    areaId = areaId,
    ingredientUnitOptionId = ingredientUnitOptionId,
    quantityEntered = quantityEntered,
    quantityBase = quantityBase,
    reason = reason,
    effectiveAt = effectiveAt,
    notes = notes, 
    attachmentPath = attachmentId,
    attachmentDisplayName = attachmentDisplayName,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    postedAt = postedAt,
    voidedAt = voidedAt
)

fun InventoryMovementBackupDto.toEntity() = InventoryMovementEntity(
    id = id,
    restaurantId = restaurantId,
    ingredientId = ingredientId,
    areaId = areaId,
    movementType = movementType,
    quantityBaseSigned = quantityBaseSigned,
    unitCostBaseSnapshot = unitCostBaseSnapshot,
    totalValueSnapshot = totalValueSnapshot,
    effectiveAt = effectiveAt,
    sourceDocumentType = sourceDocumentType,
    sourceDocumentId = sourceDocumentId,
    sourceOperationId = sourceOperationId,
    sourceLineId = sourceLineId,
    reversalOfMovementId = reversalOfMovementId,
    createdAt = createdAt
)

fun InventoryBalanceProjectionBackupDto.toEntity() = InventoryBalanceProjectionEntity(
    restaurantId = restaurantId,
    ingredientId = ingredientId,
    areaId = areaId,
    quantityBase = quantityBase,
    updatedAt = updatedAt
)

fun IngredientCostProjectionBackupDto.toEntity() = IngredientCostProjectionEntity(
    restaurantId = restaurantId,
    ingredientId = ingredientId,
    averageUnitCostBase = averageUnitCostBase,
    updatedAt = updatedAt
)

fun PreparationRecipeBackupDto.toEntity() = PreparationRecipeEntity(
    id = id,
    restaurantId = restaurantId,
    outputIngredientId = outputIngredientId,
    name = name,
    normalizedName = normalizedName,
    standardYieldQuantity = standardYieldQuantity?.let { java.math.BigDecimal(it) },
    standardYieldQuantityBase = standardYieldQuantityBase?.let { java.math.BigDecimal(it) },
    yieldUnitOptionId = yieldUnitOptionId,
    status = status,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt
)

fun PreparationRecipeComponentBackupDto.toEntity() = PreparationRecipeComponentEntity(
    id = id,
    recipeId = recipeId,
    componentIngredientId = componentIngredientId,
    unitOptionId = unitOptionId,
    quantityEntered = java.math.BigDecimal(quantityEntered),
    quantityBase = java.math.BigDecimal(quantityBase),
    sortOrder = sortOrder,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ProductionBatchBackupDto.toEntity() = ProductionBatchEntity(
    id = id,
    restaurantId = restaurantId,
    recipeId = recipeId,
    recipeNameSnapshot = recipeNameSnapshot,
    outputIngredientId = outputIngredientId,
    batchMultiplier = batchMultiplier,
    recipeStandardYieldQuantitySnapshot = recipeStandardYieldQuantitySnapshot,
    recipeStandardYieldBaseSnapshot = recipeStandardYieldBaseSnapshot,
    recipeYieldUnitOptionIdSnapshot = recipeYieldUnitOptionIdSnapshot,
    expectedOutputQuantityEntered = expectedOutputQuantityEntered,
    expectedOutputQuantityBase = expectedOutputQuantityBase,
    actualOutputQuantityEntered = actualOutputQuantityEntered,
    actualOutputQuantityBase = actualOutputQuantityBase,
    outputUnitOptionId = outputUnitOptionId,
    outputAreaId = outputAreaId,
    hasManualOutputQuantityOverride = hasManualOutputQuantityOverride,
    totalComponentCostSnapshot = totalComponentCostSnapshot,
    outputUnitCostBaseSnapshot = outputUnitCostBaseSnapshot,
    effectiveAt = effectiveAt,
    status = status,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    postedAt = postedAt,
    voidedAt = voidedAt
)

fun ProductionBatchComponentBackupDto.toEntity() = ProductionBatchComponentEntity(
    id = id,
    productionBatchId = productionBatchId,
    sourceRecipeComponentIdSnapshot = sourceRecipeComponentIdSnapshot,
    componentIngredientId = componentIngredientId,
    recipeQuantityEnteredSnapshot = recipeQuantityEnteredSnapshot,
    recipeQuantityBaseSnapshot = recipeQuantityBaseSnapshot,
    recipeUnitOptionIdSnapshot = recipeUnitOptionIdSnapshot,
    expectedQuantityEntered = expectedQuantityEntered,
    expectedQuantityBase = expectedQuantityBase,
    actualQuantityEntered = actualQuantityEntered,
    actualQuantityBase = actualQuantityBase,
    unitOptionId = unitOptionId,
    hasManualQuantityOverride = hasManualQuantityOverride,
    sourceAreaId = sourceAreaId,
    unitCostBaseSnapshot = unitCostBaseSnapshot,
    totalCostSnapshot = totalCostSnapshot,
    sortOrder = sortOrder,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PurchaseInvoiceOcrResultBackupDto.toEntity() = PurchaseInvoiceOcrResultEntity(
    id = id,
    purchaseReceiptId = purchaseReceiptId,
    sourceDocumentSha256 = sourceDocumentSha256,
    sourceMimeType = sourceMimeType,
    engine = engine,
    evidenceSchemaVersion = evidenceSchemaVersion,
    pageCount = pageCount,
    fullText = fullText,
    processedAt = processedAt
)

fun PurchaseInvoiceOcrPageBackupDto.toEntity() = PurchaseInvoiceOcrPageEntity(
    ocrResultId = ocrResultId,
    pageIndex = pageIndex,
    widthPx = widthPx,
    heightPx = heightPx,
    text = text,
    evidenceJson = evidenceJson
)

fun PurchaseInvoiceParseResultBackupDto.toEntity() = PurchaseInvoiceParseResultEntity(
    id = id,
    purchaseReceiptId = purchaseReceiptId,
    ocrResultId = ocrResultId,
    sourceDocumentSha256 = sourceDocumentSha256,
    parserEngine = parserEngine,
    parserSchemaVersion = parserSchemaVersion,
    headerEvidenceJson = headerEvidenceJson,
    totalsEvidenceJson = totalsEvidenceJson,
    correctionsJson = correctionsJson,
    warningsJson = warningsJson,
    processedAt = processedAt,
    reviewedAt = reviewedAt
)

fun PurchaseInvoiceParsedLineBackupDto.toEntity() = PurchaseInvoiceParsedLineEntity(
    parseResultId = parseResultId,
    lineIndex = lineIndex,
    evidenceJson = evidenceJson,
    correctionJson = correctionJson,
    isIgnored = isIgnored
)

fun SupplierItemMappingBackupDto.toEntity() = SupplierItemMappingEntity(
    id = id,
    restaurantId = restaurantId,
    supplierId = supplierId,
    keyType = SupplierItemMappingKeyType.valueOf(keyType),
    normalizedKey = normalizedKey,
    sourceVendorCode = sourceVendorCode,
    sourceDescription = sourceDescription,
    sourcePackageText = sourcePackageText,
    ingredientId = ingredientId,
    unitOptionId = unitOptionId,
    inventoryAreaId = inventoryAreaId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastConfirmedAt = lastConfirmedAt
)

fun PurchaseInvoiceLineMatchBackupDto.toEntity() = PurchaseInvoiceLineMatchEntity(
    parseResultId = parseResultId,
    lineIndex = lineIndex,
    status = InvoiceLineMatchStatus.valueOf(status),
    supplierId = supplierId,
    ingredientId = ingredientId,
    unitOptionId = unitOptionId,
    inventoryAreaId = inventoryAreaId,
    mappingId = mappingId,
    matchMethod = matchMethod,
    matchConfidence = matchConfidence,
    confirmedAt = confirmedAt
)

fun PurchaseInvoiceDraftApplicationBackupDto.toEntity() = PurchaseInvoiceDraftApplicationEntity(
    id = id,
    purchaseReceiptId = purchaseReceiptId,
    parseResultId = parseResultId,
    sourceDocumentSha256 = sourceDocumentSha256,
    sourceStateFingerprint = sourceStateFingerprint,
    appliedAt = appliedAt,
    duplicateOverrideType = duplicateOverrideType,
    duplicateExistingReceiptId = duplicateExistingReceiptId,
    duplicateNormalizedInvoiceNumber = duplicateNormalizedInvoiceNumber,
    duplicateSourceSha256 = duplicateSourceSha256,
    duplicateOverriddenAt = duplicateOverriddenAt
)

fun PurchaseInvoiceLineOriginBackupDto.toEntity() = PurchaseInvoiceLineOriginEntity(
    purchaseLineId = purchaseLineId,
    applicationId = applicationId,
    sourceLineIndex = sourceLineIndex,
    sourceStateFingerprint = sourceStateFingerprint,
    lastMaterializedSnapshotJson = lastMaterializedSnapshotJson
)

fun MenuRecipeBackupDto.toEntity() = MenuRecipeEntity(
    id = id,
    restaurantId = restaurantId,
    name = name,
    normalizedName = normalizedName,
    sellingPrice = sellingPrice?.let { java.math.BigDecimal(it) },
    notes = notes,
    cashDiscountBehavior = com.miara.cuentame.core.model.menu.CashDiscountBehavior.valueOf(cashDiscountBehavior),
    commercialRevision = commercialRevision,
    consumptionRevision = consumptionRevision,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MenuRecipeComponentBackupDto.toEntity() = MenuRecipeComponentEntity(
    id = id,
    menuRecipeId = menuRecipeId,
    ingredientId = ingredientId,
    ingredientUnitOptionId = ingredientUnitOptionId,
    quantityEntered = java.math.BigDecimal(quantityEntered),
    quantityBase = java.math.BigDecimal(quantityBase),
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)
