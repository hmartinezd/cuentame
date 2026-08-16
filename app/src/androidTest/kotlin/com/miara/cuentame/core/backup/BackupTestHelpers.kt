package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.api.BackupFormatV1Contract
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.TableMetadata

fun createManifestForSnapshot(
    snapshot: BackupSnapshotDto,
    schemaVersion: Int,
    restaurantName: String,
    localeTag: String,
    currencyCode: String
): BackupManifest {
    val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
    
    val tableMetadata = mutableMapOf<String, TableMetadata>()
    val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(schemaVersion)
    val derivedTables = BackupFormatV1Contract.DERIVED_TABLES
    
    val counts = mapOf(
        "restaurants" to snapshot.restaurants.size,
        "inventory_areas" to snapshot.inventoryAreas.size,
        "ingredient_categories" to snapshot.ingredientCategories.size,
        "units" to snapshot.units.size,
        "ingredients" to snapshot.ingredients.size,
        "ingredient_unit_options" to snapshot.ingredientUnitOptions.size,
        "suppliers" to snapshot.suppliers.size,
        "purchase_receipts" to snapshot.purchaseReceipts.size,
        "purchase_lines" to snapshot.purchaseLines.size,
        "stock_counts" to snapshot.stockCounts.size,
        "stock_count_areas" to snapshot.stockCountAreas.size,
        "stock_count_lines" to snapshot.stockCountLines.size,
        "waste_events" to snapshot.wasteEvents.size,
        "inventory_movements" to snapshot.inventoryMovements.size,
        "inventory_balance_projections" to snapshot.inventoryBalanceProjections.size,
        "ingredient_cost_projections" to snapshot.ingredientCostProjections.size,
        "preparation_recipes" to snapshot.preparationRecipes.size,
        "preparation_recipe_components" to snapshot.preparationRecipeComponents.size,
        "production_batches" to snapshot.productionBatches.size,
        "production_batch_components" to snapshot.productionBatchComponents.size
        ,"menu_recipes" to snapshot.menuRecipes.size,"menu_recipe_components" to snapshot.menuRecipeComponents.size,
        "menus" to snapshot.menus.size,"menu_categories" to snapshot.menuCategories.size,"menu_placements" to snapshot.menuPlacements.size
    )

    for (table in expectedTables) {
        val count = counts[table] ?: 0
        tableMetadata[table] = TableMetadata(
            entryCount = count,
            isDerived = table in derivedTables
        )
    }

    return BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = java.time.Instant.now().toString(),
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0.0",
        appVersionCode = 1L,
        databaseSchemaVersion = schemaVersion,
        checksumAlgorithm = "SHA-256",
        restaurantId = restaurantId,
        restaurantName = restaurantName,
        localeTag = localeTag,
        currencyCode = currencyCode,
        includedSections = BackupFormatV1Contract.REQUIRED_SECTIONS.toList(),
        tableMetadata = tableMetadata,
        attachments = emptyList()
    )
}
