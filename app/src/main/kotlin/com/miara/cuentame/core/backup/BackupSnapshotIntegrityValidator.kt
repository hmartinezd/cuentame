package com.miara.cuentame.core.backup

import com.miara.cuentame.core.common.parsePersistedEnum
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityCode.*
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.model.purchase.MatchIntegrityPolicy
import java.math.BigDecimal

import com.miara.cuentame.core.domain.service.HistoricalInventoryCostCalculationResult
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostFailure
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostCalculator
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostBoundary
import com.miara.cuentame.core.domain.service.HistoricalInventoryMovement
import com.miara.cuentame.core.domain.service.SourceDocumentIdentity
import com.miara.cuentame.core.domain.service.HistoricalInventoryCostResult

/**
 * Validates logical consistency, enum validity, document timestamps, movement graph semantics,
 * document lifecycle consistency, numeric semantics, bijection, and restaurant isolation.
 *
 * The public signature returns [Result]<[Unit]>. On failure the exception is always a
 * [BackupSnapshotIntegrityException] carrying a stable [BackupSnapshotIntegrityCode].
 * Human-readable messages must never include customer data, paths, or raw JSON.
 *
 * Sub-functions follow the naming pattern `validateXxx` and return [BackupSnapshotIntegrityException]?
 * (null = pass). The top-level [validate] function composes them with early exit.
 */
object BackupSnapshotIntegrityValidator {

    private val costCalculator = HistoricalInventoryCostCalculator()

    // ── public API ───────────────────────────────────────────────────────────────

    fun validate(dto: BackupSnapshotDto, manifest: BackupManifest): Result<Unit> {
        val manifestRestaurantId = manifest.restaurantId
            ?: return fail(RESTAURANT_ID_MISMATCH, "Manifest missing restaurantId")

        try {
            validateRestaurant(dto, manifest, manifestRestaurantId)?.let { throw it }

            // Build lookup maps once
            val ctx = ValidationContext(dto, manifestRestaurantId)

            validatePrimaryKeys(dto)?.let { throw it }
            validateIsolation(dto, manifestRestaurantId)?.let { throw it }
            validateForeignKeys(dto, ctx)?.let { throw it }
            validateNumericFields(dto)?.let { throw it }
            validateDocumentTimestamps(dto)?.let { throw it }
            validateMovementGraph(dto, ctx)?.let { throw it }
            validateDocumentLifecycle(dto)?.let { throw it }
            validateBalanceProjections(dto)?.let { throw it }
            validateCostProjections(dto, ctx)?.let { throw it }
            validateRecipes(dto, ctx)?.let { throw it }
            validateProductionBatches(dto, ctx)?.let { throw it }
            validateOcr(dto, manifest, ctx)?.let { throw it }
            validateParseResult(dto, ctx)?.let { throw it }
            validateMappings(dto, ctx)?.let { throw it }
            validateStagedMatches(dto, ctx)?.let { throw it }
            validateMaterialization(dto, ctx)?.let { throw it }
            validateMenuRecipes(dto, ctx)?.let { throw it }
            validateMenus(dto, ctx)?.let { throw it }
            validateMenuPublications(dto, ctx)?.let { throw it }
            validateSalesImports(dto, manifestRestaurantId)?.let { throw it }

            return Result.success(Unit)
        } catch (e: BackupSnapshotIntegrityException) {
            return Result.failure(e)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private fun fail(code: BackupSnapshotIntegrityCode, msg: String): Result<Unit> =
        Result.failure(BackupSnapshotIntegrityException(code, msg))

    private fun err(code: BackupSnapshotIntegrityCode, msg: String): BackupSnapshotIntegrityException =
        BackupSnapshotIntegrityException(code, msg)

    /**
     * Parses [value] as a [BigDecimal]. Returns null when [value] is null.
     * On parse failure, returns the provided error.
     */
    private fun parseDecimal(value: String, errMsg: String): BigDecimalResult {
        return try {
            BigDecimalResult.Ok(BigDecimal(value))
        } catch (_: Exception) {
            BigDecimalResult.Err(INVALID_DECIMAL, errMsg)
        }
    }

    private fun parseNullableDecimal(value: String?, errMsg: String): NullableDecimalResult {
        if (value == null) return NullableDecimalResult.Null
        return try {
            NullableDecimalResult.Ok(BigDecimal(value))
        } catch (_: Exception) {
            NullableDecimalResult.Err(INVALID_DECIMAL, errMsg)
        }
    }

    private sealed class BigDecimalResult {
        data class Ok(val value: BigDecimal) : BigDecimalResult()
        data class Err(val code: BackupSnapshotIntegrityCode, val msg: String) : BigDecimalResult()
    }

    private sealed class NullableDecimalResult {
        object Null : NullableDecimalResult()
        data class Ok(val value: BigDecimal) : NullableDecimalResult()
        data class Err(val code: BackupSnapshotIntegrityCode, val msg: String) : NullableDecimalResult()
    }

    // ── context ──────────────────────────────────────────────────────────────────

    private class ValidationContext(dto: BackupSnapshotDto, val restaurantId: String) {
        val unitById = dto.units.associateBy { it.id }
        val areaById = dto.inventoryAreas.associateBy { it.id }
        val catById = dto.ingredientCategories.associateBy { it.id }
        val ingById = dto.ingredients.associateBy { it.id }
        val optionById = dto.ingredientUnitOptions.associateBy { it.id }
        val supplierById = dto.suppliers.associateBy { it.id }
        val receiptById = dto.purchaseReceipts.associateBy { it.id }
        val purchaseLineById = dto.purchaseLines.associateBy { it.id }
        val countById = dto.stockCounts.associateBy { it.id }
        val countAreaById = dto.stockCountAreas.associateBy { it.id }
        val countLineById = dto.stockCountLines.associateBy { it.id }
        val wasteById = dto.wasteEvents.associateBy { it.id }
        val movementById = dto.inventoryMovements.associateBy { it.id }
        val recipeById = dto.preparationRecipes.associateBy { it.id }
        val recipeComponentById = dto.preparationRecipeComponents.associateBy { it.id }
        val componentsByRecipeId = dto.preparationRecipeComponents.groupBy { it.recipeId }
        val batchById = dto.productionBatches.associateBy { it.id }
        val batchComponentById = dto.productionBatchComponents.associateBy { it.id }
        val componentsByBatchId = dto.productionBatchComponents.groupBy { it.productionBatchId }
        val ocrResultById = dto.purchaseInvoiceOcrResults.associateBy { it.id }
        val parseResultById = dto.purchaseInvoiceParseResults.associateBy { it.id }
        val mappingById = dto.supplierItemMappings.associateBy { it.id }
        val applicationById = dto.purchaseInvoiceDraftApplications.associateBy { it.id }
        val originByLineId = dto.purchaseInvoiceLineOrigins.associateBy { it.purchaseLineId }
        val menuRecipeById = dto.menuRecipes.associateBy { it.id }
        val menuRecipeComponentById = dto.menuRecipeComponents.associateBy { it.id }
        val componentsByMenuRecipeId = dto.menuRecipeComponents.groupBy { it.menuRecipeId }
        val menuById = dto.menus.associateBy { it.id }
        val menuCategoryById = dto.menuCategories.associateBy { it.id }
    }

    // ── sub-validators ────────────────────────────────────────────────────────────

    private fun validateRestaurant(
        dto: BackupSnapshotDto,
        manifest: BackupManifest,
        manifestRestaurantId: String,
    ): BackupSnapshotIntegrityException? {
        if (dto.restaurants.size != 1) {
            return err(INVALID_RESTAURANT_COUNT, "Snapshot must contain exactly one restaurant")
        }
        val r = dto.restaurants[0]
        if (r.id != manifestRestaurantId) return err(RESTAURANT_ID_MISMATCH, "Snapshot restaurant ID does not match manifest")
        if (r.name != manifest.restaurantName) return err(RESTAURANT_NAME_MISMATCH, "Snapshot restaurant name does not match manifest")
        if (r.currencyCode != manifest.currencyCode) return err(RESTAURANT_CURRENCY_MISMATCH, "Snapshot currencyCode does not match manifest")
        if (r.localeTag != manifest.localeTag) return err(RESTAURANT_LOCALE_MISMATCH, "Snapshot localeTag does not match manifest")
        return null
    }

    private fun validatePrimaryKeys(dto: BackupSnapshotDto): BackupSnapshotIntegrityException? {
        fun <T> check(list: List<T>, sel: (T) -> String, table: String): BackupSnapshotIntegrityException? {
            val ids = list.map(sel)
            if (ids.any { it.isBlank() }) return err(BLANK_PRIMARY_KEY, "Blank primary key in $table")
            if (ids.distinct().size != ids.size) return err(DUPLICATE_PRIMARY_KEY, "Duplicate primary key in $table")
            return null
        }

        check(dto.inventoryAreas, { it.id }, "inventory_areas")?.let { return it }
        check(dto.ingredientCategories, { it.id }, "ingredient_categories")?.let { return it }
        check(dto.units, { it.id }, "units")?.let { return it }
        check(dto.ingredients, { it.id }, "ingredients")?.let { return it }
        check(dto.ingredientUnitOptions, { it.id }, "ingredient_unit_options")?.let { return it }
        check(dto.suppliers, { it.id }, "suppliers")?.let { return it }
        check(dto.purchaseReceipts, { it.id }, "purchase_receipts")?.let { return it }
        check(dto.purchaseLines, { it.id }, "purchase_lines")?.let { return it }
        check(dto.stockCounts, { it.id }, "stock_counts")?.let { return it }
        check(dto.stockCountAreas, { it.id }, "stock_count_areas")?.let { return it }
        check(dto.stockCountLines, { it.id }, "stock_count_lines")?.let { return it }
        check(dto.wasteEvents, { it.id }, "waste_events")?.let { return it }
        check(dto.inventoryMovements, { it.id }, "inventory_movements")?.let { return it }
        check(dto.preparationRecipes, { it.id }, "preparation_recipes")?.let { return it }
        check(dto.preparationRecipeComponents, { it.id }, "preparation_recipe_components")?.let { return it }
        check(dto.productionBatches, { it.id }, "production_batches")?.let { return it }
        check(dto.productionBatchComponents, { it.id }, "production_batch_components")?.let { return it }
        check(dto.purchaseInvoiceOcrResults, { it.id }, "purchase_invoice_ocr_results")?.let { return it }
        check(dto.purchaseInvoiceParseResults, { it.id }, "purchase_invoice_parse_results")?.let { return it }
        check(dto.supplierItemMappings, { it.id }, "supplier_item_mappings")?.let { return it }
        check(dto.purchaseInvoiceDraftApplications, { it.id }, "purchase_invoice_draft_applications")?.let { return it }
        check(dto.purchaseInvoiceLineOrigins, { it.purchaseLineId }, "purchase_invoice_line_origins")?.let { return it }
        check(dto.menuRecipes, { it.id }, "menu_recipes")?.let { return it }
        check(dto.menuRecipeComponents, { it.id }, "menu_recipe_components")?.let { return it }
        check(dto.menus, { it.id }, "menus")?.let { return it }
        check(dto.menuCategories, { it.id }, "menu_categories")?.let { return it }
        check(dto.menuPlacements, { it.id }, "menu_placements")?.let { return it }
        check(dto.menuPublications, { it.id }, "menu_publications")?.let { return it }
        check(dto.menuPublicationCategories, { it.id }, "menu_publication_categories")?.let { return it }
        check(dto.menuPublicationItems, { it.id }, "menu_publication_items")?.let { return it }
        check(dto.menuPublicationItemComponents, { it.id }, "menu_publication_item_components")?.let { return it }
        check(dto.salesImports, { it.exportId }, "sales_imports")?.let { return it }
        val transactionKeys=dto.importedSaleTransactions.map{it.terminalId to it.transactionId}
        if(transactionKeys.any{it.first.isBlank()||it.second.isBlank()}||transactionKeys.distinct().size!=transactionKeys.size)return err(DUPLICATE_COMPOSITE_KEY,"Invalid imported sale transaction identity")
        val lineKeys=dto.importedSaleLines.map{it.terminalId to it.saleLineId}
        if(lineKeys.any{it.first.isBlank()||it.second.isBlank()}||lineKeys.distinct().size!=lineKeys.size)return err(DUPLICATE_COMPOSITE_KEY,"Invalid imported sale line identity")
        val refKeys=dto.salesImportTransactionRefs.map{Triple(it.exportId,it.terminalId,it.transactionId)}
        if(refKeys.distinct().size!=refKeys.size)return err(DUPLICATE_COMPOSITE_KEY,"Duplicate sales import transaction reference")

        // Balance projection composite keys
        val balanceKeys = dto.inventoryBalanceProjections.map { Triple(it.restaurantId, it.ingredientId, it.areaId) }
        if (balanceKeys.any { it.first.isBlank() || it.second.isBlank() || it.third.isBlank() }) {
            return err(BLANK_PRIMARY_KEY, "Blank composite key field in inventory_balance_projection")
        }
        if (balanceKeys.distinct().size != balanceKeys.size) {
            return err(DUPLICATE_COMPOSITE_KEY, "Duplicate composite key in inventory_balance_projection")
        }

        // OCR page composite keys
        val ocrPageKeys = dto.purchaseInvoiceOcrPages.map { it.ocrResultId to it.pageIndex }
        if (ocrPageKeys.distinct().size != ocrPageKeys.size) {
            return err(DUPLICATE_COMPOSITE_KEY, "Duplicate composite key in purchase_invoice_ocr_pages")
        }

        // Parsed line composite keys
        val parsedLineKeys = dto.purchaseInvoiceParsedLines.map { it.parseResultId to it.lineIndex }
        if (parsedLineKeys.distinct().size != parsedLineKeys.size) {
            return err(DUPLICATE_COMPOSITE_KEY, "Duplicate composite key in purchase_invoice_parsed_lines")
        }
        
        // Staged match composite keys
        val matchKeys = dto.purchaseInvoiceLineMatches.map { it.parseResultId to it.lineIndex }
        if (matchKeys.distinct().size != matchKeys.size) {
            return err(DUPLICATE_COMPOSITE_KEY, "Duplicate composite key in purchase_invoice_line_matches")
        }

        // Cost projection composite keys
        val costKeys = dto.ingredientCostProjections.map { Pair(it.restaurantId, it.ingredientId) }
        if (costKeys.any { it.first.isBlank() || it.second.isBlank() }) {
            return err(BLANK_PRIMARY_KEY, "Blank composite key field in ingredient_cost_projection")
        }
        if (costKeys.distinct().size != costKeys.size) {
            return err(DUPLICATE_COMPOSITE_KEY, "Duplicate composite key in ingredient_cost_projection")
        }

        return null
    }

    private fun validateSalesImports(dto: BackupSnapshotDto, restaurantId: String): BackupSnapshotIntegrityException? {
        val publications = dto.menuPublications.associateBy { it.id }
        val publicationItems = dto.menuPublicationItems.groupBy { it.publicationId }
        val imports = dto.salesImports.associateBy { it.exportId }
        val transactions = dto.importedSaleTransactions.associateBy { it.terminalId to it.transactionId }

        fun validDate(value: String) = runCatching { java.time.LocalDate.parse(value) }.isSuccess
        fun validInstant(value: Long) = runCatching { java.time.Instant.ofEpochMilli(value) }.isSuccess
        fun validCurrency(value: String) = runCatching { java.util.Currency.getInstance(value) }.isSuccess
        fun publicationMatches(publication: com.miara.cuentame.core.backup.model.MenuPublicationBackupDto, ownerRestaurantId: String, menuId: String, revision: Long, currency: String) =
            publication.restaurantId == ownerRestaurantId &&
                publication.sourceMenuId == menuId &&
                publication.publicationRevision == revision &&
                publication.currencyCodeSnapshot == currency

        for (salesImport in dto.salesImports) {
            if (salesImport.restaurantId != restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in sales_imports")
            if (salesImport.exportId.isBlank() || salesImport.restaurantId.isBlank() || salesImport.terminalId.isBlank() ||
                salesImport.menuPackageId.isBlank() || salesImport.menuId.isBlank() ||
                !salesImport.originalSha256.matches(Regex("[0-9a-f]{64}"))) {
                return err(RELATIONSHIP_MISMATCH, "Invalid sales import envelope")
            }
            if (salesImport.publicationRevision <= 0) return err(INVALID_NUMERIC_RANGE, "Invalid sales import revision")
            if (!validDate(salesImport.businessDate) || !validCurrency(salesImport.currency)) return err(INVALID_ENUM, "Invalid sales import date or currency")
            if (!validInstant(salesImport.generatedAt) || !validInstant(salesImport.importedAt)) return err(INVALID_TIMESTAMP_ORDER, "Invalid sales import timestamp")
            val publication = publications[salesImport.menuPackageId]
                ?: return err(BROKEN_FOREIGN_KEY, "Broken sales import publication FK")
            if (!publicationMatches(publication, salesImport.restaurantId, salesImport.menuId, salesImport.publicationRevision, salesImport.currency)) {
                return err(RELATIONSHIP_MISMATCH, "Sales import publication provenance mismatch")
            }
        }

        for (transaction in dto.importedSaleTransactions) {
            if (transaction.restaurantId != restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in imported transactions")
            if (transaction.terminalId.isBlank() || transaction.transactionId.isBlank() || transaction.restaurantId.isBlank() ||
                transaction.menuPackageId.isBlank() || transaction.menuId.isBlank()) return err(RELATIONSHIP_MISMATCH, "Invalid imported transaction identity")
            if (transaction.publicationRevision <= 0) return err(INVALID_NUMERIC_RANGE, "Invalid imported transaction revision")
            if (!validDate(transaction.businessDate) || !validCurrency(transaction.currency)) return err(INVALID_ENUM, "Invalid imported transaction date or currency")
            if (!validInstant(transaction.openedAt) || !validInstant(transaction.closedAt) || !validInstant(transaction.firstImportedAt) || !validInstant(transaction.lastSeenGeneratedAt) || transaction.closedAt < transaction.openedAt) {
                return err(INVALID_TIMESTAMP_ORDER, "Invalid imported transaction timestamp")
            }
            if (transaction.status !in setOf("COMPLETED", "VOIDED")) return err(INVALID_ENUM, "Invalid imported transaction status")
            val publication = publications[transaction.menuPackageId]
                ?: return err(BROKEN_FOREIGN_KEY, "Broken imported transaction publication FK")
            if (!publicationMatches(publication, transaction.restaurantId, transaction.menuId, transaction.publicationRevision, transaction.currency)) {
                return err(RELATIONSHIP_MISMATCH, "Imported transaction publication provenance mismatch")
            }
            val first = imports[transaction.firstSeenExportId]
                ?: return err(BROKEN_FOREIGN_KEY, "Broken first-seen sales import FK")
            val last = imports[transaction.lastSeenExportId]
                ?: return err(BROKEN_FOREIGN_KEY, "Broken last-seen sales import FK")
            fun importMatches(value: com.miara.cuentame.core.backup.model.SalesImportBackupDto) =
                value.terminalId == transaction.terminalId && value.restaurantId == transaction.restaurantId &&
                    value.menuPackageId == transaction.menuPackageId && value.menuId == transaction.menuId &&
                    value.publicationRevision == transaction.publicationRevision && value.businessDate == transaction.businessDate &&
                    value.currency == transaction.currency
            if (!importMatches(first) || !importMatches(last)) return err(RELATIONSHIP_MISMATCH, "Imported transaction audit provenance mismatch")
            if (transaction.firstImportedAt != first.importedAt || transaction.lastSeenGeneratedAt < first.generatedAt || transaction.lastSeenGeneratedAt != last.generatedAt) {
                return err(INVALID_TIMESTAMP_ORDER, "Imported transaction audit timestamp mismatch")
            }
        }

        for (line in dto.importedSaleLines) {
            val parent = transactions[line.terminalId to line.transactionId]
                ?: return err(BROKEN_FOREIGN_KEY, "Broken imported sale line FK")
            if (line.terminalId != parent.terminalId || line.transactionId != parent.transactionId) return err(RELATIONSHIP_MISMATCH, "Imported sale parent mismatch")
            if (line.saleLineId.isBlank() || line.sellableItemId.isBlank() || line.displayNameSnapshot.isBlank() || line.commercialRevision < 0 || line.consumptionRevision < 0) {
                return err(RELATIONSHIP_MISMATCH, "Invalid imported sale line")
            }
            val values = try { listOf(BigDecimal(line.quantity), BigDecimal(line.unitPrice), BigDecimal(line.gross), BigDecimal(line.discount), BigDecimal(line.net)) }
            catch (_: Exception) { return err(INVALID_DECIMAL, "Invalid imported sale decimal") }
            val (quantity, unitPrice, gross, discount, net) = values
            if (quantity <= BigDecimal.ZERO || unitPrice < BigDecimal.ZERO || gross < BigDecimal.ZERO || discount < BigDecimal.ZERO || net < BigDecimal.ZERO ||
                discount > gross || gross.compareTo(quantity * unitPrice) != 0 || net.compareTo(gross - discount) != 0) {
                return err(INVALID_DECIMAL, "Invalid imported sale arithmetic")
            }
            val item = publicationItems[parent.menuPackageId].orEmpty().find { it.menuRecipeId == line.sellableItemId }
                ?: return err(BROKEN_FOREIGN_KEY, "Unknown imported sale publication item")
            val itemPrice = try { BigDecimal(item.sellingPriceSnapshot) } catch (_: Exception) { return err(INVALID_DECIMAL, "Invalid publication item price") }
            if (item.displayNameSnapshot != line.displayNameSnapshot || item.commercialRevision != line.commercialRevision ||
                item.consumptionRevision != line.consumptionRevision || itemPrice.compareTo(unitPrice) != 0) {
                return err(RELATIONSHIP_MISMATCH, "Imported sale line publication provenance mismatch")
            }
        }

        for (ref in dto.salesImportTransactionRefs) {
            val salesImport = imports[ref.exportId] ?: return err(BROKEN_FOREIGN_KEY, "Broken sales import reference FK")
            val transaction = transactions[ref.terminalId to ref.transactionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken sales import reference FK")
            if (ref.terminalId != salesImport.terminalId || ref.terminalId != transaction.terminalId ||
                salesImport.restaurantId != transaction.restaurantId || salesImport.menuPackageId != transaction.menuPackageId ||
                salesImport.menuId != transaction.menuId || salesImport.publicationRevision != transaction.publicationRevision ||
                salesImport.businessDate != transaction.businessDate || salesImport.currency != transaction.currency) {
                return err(RELATIONSHIP_MISMATCH, "Sales import reference provenance mismatch")
            }
        }
        return null
    }

    private fun validateIsolation(dto: BackupSnapshotDto, restaurantId: String): BackupSnapshotIntegrityException? {
        if (dto.inventoryAreas.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in inventory_areas")
        if (dto.ingredientCategories.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in ingredient_categories")
        if (dto.ingredients.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in ingredients")
        if (dto.suppliers.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in suppliers")
        if (dto.purchaseReceipts.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in purchase_receipts")
        if (dto.stockCounts.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in stock_counts")
        if (dto.wasteEvents.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in waste_events")
        if (dto.inventoryMovements.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in inventory_movements")
        if (dto.inventoryBalanceProjections.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in inventory_balance_projection")
        if (dto.ingredientCostProjections.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in ingredient_cost_projection")
        if (dto.preparationRecipes.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in preparation_recipes")
        if (dto.productionBatches.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in production_batches")
        if (dto.supplierItemMappings.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in supplier_item_mappings")

        val receiptById = dto.purchaseReceipts.associateBy { it.id }
        if (dto.purchaseInvoiceOcrResults.any { ocr ->
                val parent = receiptById[ocr.purchaseReceiptId]
                parent == null || parent.restaurantId != restaurantId
            }) return err(RESTAURANT_ISOLATION_FAILURE, "Transitive isolation error in purchase_invoice_ocr_results")

        if (dto.purchaseInvoiceParseResults.any { parse ->
                val parent = receiptById[parse.purchaseReceiptId]
                parent == null || parent.restaurantId != restaurantId
            }) return err(RESTAURANT_ISOLATION_FAILURE, "Transitive isolation error in purchase_invoice_parse_results")

        if (dto.purchaseInvoiceDraftApplications.any { app ->
                val parent = receiptById[app.purchaseReceiptId]
                parent == null || parent.restaurantId != restaurantId
            }) return err(RESTAURANT_ISOLATION_FAILURE, "Transitive isolation error in purchase_invoice_draft_applications")
        
        if (dto.menuRecipes.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in menu_recipes")
        if (dto.menus.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in menus")

        return null
    }

    private fun validateForeignKeys(dto: BackupSnapshotDto, ctx: ValidationContext): BackupSnapshotIntegrityException? {
        // Transitive restaurant ownership
        if (dto.ingredientUnitOptions.any { opt ->
                val parent = ctx.ingById[opt.ingredientId]
                parent == null || parent.restaurantId != ctx.restaurantId
            }) return err(RESTAURANT_ISOLATION_FAILURE, "Transitive isolation error in ingredient_unit_options")

        if (dto.purchaseLines.any { line ->
                val parent = ctx.receiptById[line.purchaseReceiptId]
                parent == null || parent.restaurantId != ctx.restaurantId
            }) return err(RESTAURANT_ISOLATION_FAILURE, "Transitive isolation error in purchase_lines")

        if (dto.stockCountAreas.any { sca ->
                val parent = ctx.countById[sca.stockCountId]
                parent == null || parent.restaurantId != ctx.restaurantId
            }) return err(RESTAURANT_ISOLATION_FAILURE, "Transitive isolation error in stock_count_areas")

        if (dto.stockCountLines.any { line ->
                val sca = ctx.countAreaById[line.stockCountAreaId]
                val count = sca?.let { ctx.countById[it.stockCountId] }
                count == null || count.restaurantId != ctx.restaurantId
            }) return err(RESTAURANT_ISOLATION_FAILURE, "Transitive isolation error in stock_count_lines")

        if (dto.productionBatchComponents.any { comp ->
                val parent = ctx.batchById[comp.productionBatchId]
                parent == null || parent.restaurantId != ctx.restaurantId
            }) return err(RESTAURANT_ISOLATION_FAILURE, "Transitive isolation error in production_batch_components")

        // Direct FKs
        for (ing in dto.ingredients) {
            if (!ctx.unitById.containsKey(ing.baseUnitId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: ingredient to unit")
            if (ing.categoryId != null && !ctx.catById.containsKey(ing.categoryId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: ingredient to category")
            if (ing.defaultAreaId != null && !ctx.areaById.containsKey(ing.defaultAreaId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: ingredient to area")
        }

        for (opt in dto.ingredientUnitOptions) {
            if (!ctx.ingById.containsKey(opt.ingredientId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: unit_option to ingredient")
            if (opt.standardUnitId != null && !ctx.unitById.containsKey(opt.standardUnitId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: unit_option to unit")
        }

        for (receipt in dto.purchaseReceipts) {
            if (receipt.supplierId != null && !ctx.supplierById.containsKey(receipt.supplierId)) {
                return err(BROKEN_FOREIGN_KEY, "Broken FK: purchase_receipt to supplier")
            }
        }

        for (line in dto.purchaseLines) {
            if (!ctx.receiptById.containsKey(line.purchaseReceiptId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: purchase_line to receipt")
            if (!ctx.ingById.containsKey(line.ingredientId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: purchase_line to ingredient")
            if (!ctx.areaById.containsKey(line.areaId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: purchase_line to area")
            val option = ctx.optionById[line.ingredientUnitOptionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: purchase_line to unit_option")
            if (option.ingredientId != line.ingredientId) {
                return err(RELATIONSHIP_MISMATCH, "Unit option mismatch in purchase line")
            }
        }

        for (sca in dto.stockCountAreas) {
            if (!ctx.countById.containsKey(sca.stockCountId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: stock_count_area to stock_count")
            if (!ctx.areaById.containsKey(sca.areaId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: stock_count_area to area")
        }

        for (scl in dto.stockCountLines) {
            if (!ctx.countAreaById.containsKey(scl.stockCountAreaId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: stock_count_line to stock_count_area")
            if (!ctx.ingById.containsKey(scl.ingredientId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: stock_count_line to ingredient")
            val option = ctx.optionById[scl.ingredientUnitOptionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: stock_count_line to unit_option")
            if (option.ingredientId != scl.ingredientId) {
                return err(RELATIONSHIP_MISMATCH, "Unit option mismatch in stock count line")
            }
        }

        for (waste in dto.wasteEvents) {
            if (!ctx.ingById.containsKey(waste.ingredientId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: waste_event to ingredient")
            if (!ctx.areaById.containsKey(waste.areaId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: waste_event to area")
            val option = ctx.optionById[waste.ingredientUnitOptionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: waste_event to unit_option")
            if (option.ingredientId != waste.ingredientId) {
                return err(RELATIONSHIP_MISMATCH, "Unit option mismatch in waste event")
            }
        }

        // Preparation recipes orphans
        for (comp in dto.preparationRecipeComponents) {
            if (!ctx.recipeById.containsKey(comp.recipeId)) {
                return err(BROKEN_FOREIGN_KEY, "Broken FK: preparation recipe component to recipe")
            }
        }

        // Materialization
        for (app in dto.purchaseInvoiceDraftApplications) {
            if (!ctx.receiptById.containsKey(app.purchaseReceiptId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: materialization_app to receipt")
            if (!ctx.parseResultById.containsKey(app.parseResultId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: materialization_app to parse")
            
            val parseResult = ctx.parseResultById[app.parseResultId]!!
            if (parseResult.purchaseReceiptId != app.purchaseReceiptId) return err(RELATIONSHIP_MISMATCH, "Materialization app receipt/parse result mismatch")
        }

        for (origin in dto.purchaseInvoiceLineOrigins) {
            if (!ctx.purchaseLineById.containsKey(origin.purchaseLineId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: line_origin to purchase_line")
            if (!ctx.applicationById.containsKey(origin.applicationId)) return err(BROKEN_FOREIGN_KEY, "Broken FK: line_origin to materialization_app")
            
            val app = ctx.applicationById[origin.applicationId]!!
            val line = ctx.purchaseLineById[origin.purchaseLineId]!!
            if (line.purchaseReceiptId != app.purchaseReceiptId) return err(RELATIONSHIP_MISMATCH, "Line origin receipt mismatch via application")
        }

        // Menu recipes orphans
        for (comp in dto.menuRecipeComponents) {
            if (!ctx.menuRecipeById.containsKey(comp.menuRecipeId)) {
                return err(BROKEN_FOREIGN_KEY, "Broken FK: menu recipe component to recipe")
            }
        }

        return null
    }

    private fun validateNumericFields(dto: BackupSnapshotDto): BackupSnapshotIntegrityException? {
        // Units
        for (unit in dto.units) {
            val dim = try { UnitDimension.valueOf(unit.dimension) } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for units.dimension")
            }
            if (dim == UnitDimension.UNKNOWN) return err(INVALID_ENUM, "units.dimension cannot be UNKNOWN")
            val r = parseDecimal(unit.factorToCanonical, "Invalid numeric format in units.factorToCanonical")
            when (r) {
                is BigDecimalResult.Err -> return err(r.code, r.msg)
                is BigDecimalResult.Ok -> {
                    if (r.value <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "units.factorToCanonical must be > 0")
                }
            }
        }

        // Ingredient unit options
        for (opt in dto.ingredientUnitOptions) {
            val r = parseDecimal(opt.factorToBase, "Invalid numeric format in ingredient_unit_options.factorToBase")
            when (r) {
                is BigDecimalResult.Err -> return err(r.code, r.msg)
                is BigDecimalResult.Ok -> {
                    if (r.value <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "ingredient_unit_options.factorToBase must be > 0")
                }
            }
        }

        // Ingredients — canonical reorder configuration
        for (ing in dto.ingredients) {
            var par: BigDecimal? = null
            if (ing.parLevelBase != null) {
                when (val r = parseDecimal(ing.parLevelBase, "Invalid numeric format in ingredients.parLevelBase")) {
                    is BigDecimalResult.Err -> return err(r.code, r.msg)
                    is BigDecimalResult.Ok -> {
                        if (r.value < BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "ingredients.parLevelBase must be >= 0")
                        par = r.value
                    }
                }
            }
            if (ing.reorderPointBase != null) {
                val r = parseDecimal(ing.reorderPointBase, "Invalid numeric format in ingredients.reorderPointBase")
                when (r) {
                    is BigDecimalResult.Err -> return err(r.code, r.msg)
                    is BigDecimalResult.Ok -> {
                        if (r.value < BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "ingredients.reorderPointBase must be >= 0")
                        if (par != null && r.value > par) return err(INVALID_NUMERIC_RANGE, "ingredients.reorderPointBase must be <= parLevelBase")
                    }
                }
            }
        }

        // Purchase lines
        for (line in dto.purchaseLines) {
            for ((value, field) in listOf(
                line.quantityEntered to "purchase_lines.quantityEntered",
                line.quantityBase to "purchase_lines.quantityBase",
            )) {
                val r = parseDecimal(value, "Invalid numeric format in $field")
                when (r) {
                    is BigDecimalResult.Err -> return err(r.code, r.msg)
                    is BigDecimalResult.Ok -> {
                        if (r.value <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "$field must be > 0")
                    }
                }
            }
            for ((value, field) in listOf(
                line.unitCostBase to "purchase_lines.unitCostBase",
                line.lineTotal to "purchase_lines.lineTotal",
            )) {
                val r = parseDecimal(value, "Invalid numeric format in $field")
                when (r) {
                    is BigDecimalResult.Err -> return err(r.code, r.msg)
                    is BigDecimalResult.Ok -> {
                        if (r.value < BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "$field must be >= 0")
                    }
                }
            }
        }

        // Stock count lines
        for (scl in dto.stockCountLines) {
            for ((value, field) in listOf(
                scl.quantityEntered to "stock_count_lines.quantityEntered",
                scl.quantityBase to "stock_count_lines.quantityBase",
            )) {
                val r = parseDecimal(value, "Invalid numeric format in $field")
                when (r) {
                    is BigDecimalResult.Err -> return err(r.code, r.msg)
                    is BigDecimalResult.Ok -> {
                        if (r.value < BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "$field must be >= 0")
                    }
                }
            }
            // optional adjustmentQuantityBase may be negative (net-adjustment)
            if (scl.adjustmentQuantityBase != null) {
                val r = parseDecimal(scl.adjustmentQuantityBase, "Invalid numeric format in stock_count_lines.adjustmentQuantityBase")
                if (r is BigDecimalResult.Err) return err(r.code, r.msg)
            }
            if (scl.expectedQuantityBaseSnapshot != null) {
                val r = parseDecimal(scl.expectedQuantityBaseSnapshot, "Invalid numeric format in stock_count_lines.expectedQuantityBaseSnapshot")
                if (r is BigDecimalResult.Err) return err(r.code, r.msg)
            }
        }

        // Waste events
        for (waste in dto.wasteEvents) {
            val reason = try { WasteReason.valueOf(waste.reason) } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for waste_events.reason")
            }
            if (reason == WasteReason.UNKNOWN) return err(INVALID_ENUM, "waste_events.reason cannot be UNKNOWN")
            for ((value, field) in listOf(
                waste.quantityEntered to "waste_events.quantityEntered",
                waste.quantityBase to "waste_events.quantityBase",
            )) {
                val r = parseDecimal(value, "Invalid numeric format in $field")
                when (r) {
                    is BigDecimalResult.Err -> return err(r.code, r.msg)
                    is BigDecimalResult.Ok -> {
                        if (r.value <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "$field must be > 0")
                    }
                }
            }
        }

        // Inventory movements — quantityBaseSigned may be any non-zero value
        for (move in dto.inventoryMovements) {
            val qr = parseDecimal(move.quantityBaseSigned, "Invalid numeric format in inventory_movements.quantityBaseSigned")
            if (qr is BigDecimalResult.Err) return err(qr.code, qr.msg)

            val ucr = parseNullableDecimal(move.unitCostBaseSnapshot, "Invalid numeric format in inventory_movements.unitCostBaseSnapshot")
            if (ucr is NullableDecimalResult.Err) return err(ucr.code, ucr.msg)
            if (ucr is NullableDecimalResult.Ok && ucr.value < BigDecimal.ZERO) {
                return err(INVALID_NUMERIC_RANGE, "inventory_movements.unitCostBaseSnapshot must be >= 0")
            }

            val tvr = parseNullableDecimal(move.totalValueSnapshot, "Invalid numeric format in inventory_movements.totalValueSnapshot")
            if (tvr is NullableDecimalResult.Err) return err(tvr.code, tvr.msg)
            // totalValueSnapshot sign follows the movement direction; no range restriction here
        }

        // Production batches
        for (batch in dto.productionBatches) {
            val fields = listOf(
                batch.batchMultiplier to "batchMultiplier",
                batch.recipeStandardYieldQuantitySnapshot to "recipeStandardYieldQuantitySnapshot",
                batch.recipeStandardYieldBaseSnapshot to "recipeStandardYieldBaseSnapshot",
                batch.expectedOutputQuantityEntered to "expectedOutputQuantityEntered",
                batch.expectedOutputQuantityBase to "expectedOutputQuantityBase",
                batch.actualOutputQuantityEntered to "actualOutputQuantityEntered",
                batch.actualOutputQuantityBase to "actualOutputQuantityBase"
            )
            for ((value, field) in fields) {
                val r = parseDecimal(value, "Invalid numeric format in production_batches.$field")
                if (r is BigDecimalResult.Err) return err(r.code, r.msg)
            }
            val costFields = listOf(
                batch.totalComponentCostSnapshot to "totalComponentCostSnapshot",
                batch.outputUnitCostBaseSnapshot to "outputUnitCostBaseSnapshot"
            )
            for ((value, field) in costFields) {
                val r = parseNullableDecimal(value, "Invalid numeric format in production_batches.$field")
                if (r is NullableDecimalResult.Err) return err(r.code, r.msg)
            }
        }

        // Production batch components
        for (comp in dto.productionBatchComponents) {
            val fields = listOf(
                comp.recipeQuantityEnteredSnapshot to "recipeQuantityEnteredSnapshot",
                comp.recipeQuantityBaseSnapshot to "recipeQuantityBaseSnapshot",
                comp.expectedQuantityEntered to "expectedQuantityEntered",
                comp.expectedQuantityBase to "expectedQuantityBase",
                comp.actualQuantityEntered to "actualQuantityEntered",
                comp.actualQuantityBase to "actualQuantityBase"
            )
            for ((value, field) in fields) {
                val r = parseDecimal(value, "Invalid numeric format in production_batch_components.$field")
                if (r is BigDecimalResult.Err) return err(r.code, r.msg)
            }
            val costFields = listOf(
                comp.unitCostBaseSnapshot to "unitCostBaseSnapshot",
                comp.totalCostSnapshot to "totalCostSnapshot"
            )
            for ((value, field) in costFields) {
                val r = parseNullableDecimal(value, "Invalid numeric format in production_batch_components.$field")
                if (r is NullableDecimalResult.Err) return err(r.code, r.msg)
            }
        }

        return null
    }

    private fun validateDocumentTimestamps(dto: BackupSnapshotDto): BackupSnapshotIntegrityException? {
        // Purchase receipts
        for (receipt in dto.purchaseReceipts) {
            val status = try {
                DocumentStatus.valueOf(receipt.status)
            } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for purchase_receipts.status")
            }
            if (receipt.createdAt > receipt.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "purchase_receipts: createdAt must be <= updatedAt")
            when (status) {
                DocumentStatus.DRAFT -> {
                    if (receipt.postedAt != null || receipt.voidedAt != null) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "DRAFT purchase receipt must not have postedAt or voidedAt")
                    }
                }
                DocumentStatus.POSTED -> {
                    if (receipt.postedAt == null) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED purchase receipt requires postedAt")
                    if (receipt.voidedAt != null) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED purchase receipt must not have voidedAt")
                }
                DocumentStatus.VOIDED -> {
                    if (receipt.postedAt == null || receipt.voidedAt == null) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED purchase receipt requires both postedAt and voidedAt")
                    }
                    if (receipt.postedAt > receipt.voidedAt) {
                        return err(INVALID_TIMESTAMP_ORDER, "purchase_receipts: postedAt must be <= voidedAt")
                    }
                }
                DocumentStatus.UNKNOWN -> return err(INVALID_DOCUMENT_LIFECYCLE, "Purchase receipt status cannot be UNKNOWN")
            }
        }

        // Waste events
        for (waste in dto.wasteEvents) {
            val status = try {
                DocumentStatus.valueOf(waste.status)
            } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for waste_events.status")
            }
            if (waste.createdAt > waste.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "waste_events: createdAt must be <= updatedAt")
            when (status) {
                DocumentStatus.DRAFT -> {
                    if (waste.postedAt != null || waste.voidedAt != null) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "DRAFT waste event must not have postedAt or voidedAt")
                    }
                }
                DocumentStatus.POSTED -> {
                    if (waste.postedAt == null) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED waste event requires postedAt")
                    if (waste.voidedAt != null) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED waste event must not have voidedAt")
                }
                DocumentStatus.VOIDED -> {
                    if (waste.postedAt == null || waste.voidedAt == null) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED waste event requires both postedAt and voidedAt")
                    }
                    if (waste.postedAt > waste.voidedAt) {
                        return err(INVALID_TIMESTAMP_ORDER, "waste_events: postedAt must be <= voidedAt")
                    }
                }
                DocumentStatus.UNKNOWN -> return err(INVALID_DOCUMENT_LIFECYCLE, "Waste event status cannot be UNKNOWN")
            }
        }

        // Stock counts
        for (count in dto.stockCounts) {
            val status = try {
                StockCountStatus.valueOf(count.status)
            } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for stock_counts.status")
            }
            if (count.createdAt > count.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "stock_counts: createdAt must be <= updatedAt")
            when (status) {
                StockCountStatus.DRAFT -> {
                    if (count.completedAt != null || count.voidedAt != null) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "DRAFT stock count must not have completedAt or voidedAt")
                    }
                }
                StockCountStatus.COMPLETED -> {
                    if (count.completedAt == null) return err(INVALID_DOCUMENT_LIFECYCLE, "COMPLETED stock count requires completedAt")
                    if (count.voidedAt != null) return err(INVALID_DOCUMENT_LIFECYCLE, "COMPLETED stock count must not have voidedAt")
                }
                StockCountStatus.VOIDED -> {
                    if (count.completedAt == null || count.voidedAt == null) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED stock count requires both completedAt and voidedAt")
                    }
                    if (count.completedAt > count.voidedAt) {
                        return err(INVALID_TIMESTAMP_ORDER, "stock_counts: completedAt must be <= voidedAt")
                    }
                }
                StockCountStatus.UNKNOWN -> return err(INVALID_DOCUMENT_LIFECYCLE, "Stock count status cannot be UNKNOWN")
            }
        }

        // Stock count areas
        for (sca in dto.stockCountAreas) {
            val status = try {
                CountAreaStatus.valueOf(sca.status)
            } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for stock_count_areas.status")
            }
            when (status) {
                CountAreaStatus.NOT_STARTED, CountAreaStatus.IN_PROGRESS -> {
                    if (sca.completedAt != null) return err(INVALID_DOCUMENT_LIFECYCLE, "Non-COMPLETED stock count area must not have completedAt")
                }
                CountAreaStatus.COMPLETED -> {
                    if (sca.completedAt == null) return err(INVALID_DOCUMENT_LIFECYCLE, "COMPLETED stock count area requires completedAt")
                }
                CountAreaStatus.UNKNOWN -> return err(INVALID_DOCUMENT_LIFECYCLE, "Stock count area status cannot be UNKNOWN")
            }
        }

        return null
    }

    private fun validateMovementGraph(dto: BackupSnapshotDto, ctx: ValidationContext): BackupSnapshotIntegrityException? {
        // Unique source-operation key
        val moveKeys = dto.inventoryMovements.map {
            Triple(it.sourceDocumentType, it.sourceDocumentId, it.sourceOperationId)
        }
        if (moveKeys.distinct().size != moveKeys.size) {
            return err(INVALID_MOVEMENT_GRAPH, "Duplicate source operation key in inventory_movements")
        }

        val reversedMovementIds = mutableSetOf<String>()

        for (move in dto.inventoryMovements) {
            val moveType = try {
                InventoryMovementType.valueOf(move.movementType)
            } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for inventory_movements.movementType")
            }

            val docType = try {
                SourceDocumentType.valueOf(move.sourceDocumentType)
            } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for inventory_movements.sourceDocumentType")
            }
            if (docType == SourceDocumentType.UNKNOWN) return err(INVALID_ENUM, "inventory_movements.sourceDocumentType cannot be UNKNOWN")

            if (move.sourceDocumentId.isBlank()) return err(BLANK_PRIMARY_KEY, "Blank sourceDocumentId in inventory_movements")
            if (move.sourceOperationId.isBlank()) return err(BLANK_PRIMARY_KEY, "Blank sourceOperationId in inventory_movements")

            val ing = ctx.ingById[move.ingredientId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: movement to ingredient")
            val area = ctx.areaById[move.areaId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: movement to area")
            if (ing.restaurantId != ctx.restaurantId || area.restaurantId != ctx.restaurantId) {
                return err(RESTAURANT_ISOLATION_FAILURE, "Restaurant mismatch on movement ingredient/area")
            }

            val moveQtyResult = parseDecimal(move.quantityBaseSigned, "Invalid numeric format in inventory_movements.quantityBaseSigned")
            val moveQty = when (moveQtyResult) {
                is BigDecimalResult.Err -> return err(moveQtyResult.code, moveQtyResult.msg)
                is BigDecimalResult.Ok -> moveQtyResult.value
            }

            when (moveType) {
                InventoryMovementType.PURCHASE -> {
                    if (docType != SourceDocumentType.PURCHASE_RECEIPT) {
                        return err(INVALID_MOVEMENT_GRAPH, "PURCHASE movement must use PURCHASE_RECEIPT source document type")
                    }
                    if (moveQty <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "PURCHASE movement quantityBaseSigned must be > 0")
                    val receipt = ctx.receiptById[move.sourceDocumentId]
                        ?: return err(BROKEN_FOREIGN_KEY, "PURCHASE movement sourceDocumentId not found in purchase_receipts")
                    val lineId = move.sourceLineId ?: return err(INVALID_MOVEMENT_GRAPH, "PURCHASE movement requires non-null sourceLineId")
                    val line = ctx.purchaseLineById[lineId]
                        ?: return err(BROKEN_FOREIGN_KEY, "PURCHASE movement sourceLineId not found in purchase_lines")
                    if (line.purchaseReceiptId != receipt.id) {
                        return err(RELATIONSHIP_MISMATCH, "PURCHASE movement sourceLineId does not belong to source purchase receipt")
                    }
                    if (line.ingredientId != move.ingredientId || line.areaId != move.areaId) {
                        return err(RELATIONSHIP_MISMATCH, "PURCHASE movement ingredient/area does not match purchase line")
                    }
                }
                InventoryMovementType.WASTE -> {
                    if (docType != SourceDocumentType.WASTE_EVENT) {
                        return err(INVALID_MOVEMENT_GRAPH, "WASTE movement must use WASTE_EVENT source document type")
                    }
                    if (moveQty >= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "WASTE movement quantityBaseSigned must be < 0")
                    val waste = ctx.wasteById[move.sourceDocumentId]
                        ?: return err(BROKEN_FOREIGN_KEY, "WASTE movement sourceDocumentId not found in waste_events")
                    if (move.sourceLineId != waste.id) {
                        return err(RELATIONSHIP_MISMATCH, "WASTE movement sourceLineId must equal waste event ID")
                    }
                    if (waste.ingredientId != move.ingredientId || waste.areaId != move.areaId) {
                        return err(RELATIONSHIP_MISMATCH, "WASTE movement ingredient/area does not match waste event")
                    }
                }
                InventoryMovementType.OPENING_BALANCE, InventoryMovementType.COUNT_ADJUSTMENT -> {
                    if (docType != SourceDocumentType.STOCK_COUNT) {
                        return err(INVALID_MOVEMENT_GRAPH, "${moveType.name} movement must use STOCK_COUNT source document type")
                    }
                    val count = ctx.countById[move.sourceDocumentId]
                        ?: return err(BROKEN_FOREIGN_KEY, "${moveType.name} movement sourceDocumentId not found in stock_counts")
                    val lineId = move.sourceLineId ?: return err(INVALID_MOVEMENT_GRAPH, "${moveType.name} movement requires non-null sourceLineId")
                    val line = ctx.countLineById[lineId]
                        ?: return err(BROKEN_FOREIGN_KEY, "${moveType.name} movement sourceLineId not found in stock_count_lines")
                    val sca = ctx.countAreaById[line.stockCountAreaId]
                        ?: return err(BROKEN_FOREIGN_KEY, "Stock count line parent area not found")
                    if (sca.stockCountId != count.id) {
                        return err(RELATIONSHIP_MISMATCH, "${moveType.name} movement line does not belong to source stock count")
                    }
                    if (line.ingredientId != move.ingredientId || sca.areaId != move.areaId) {
                        return err(RELATIONSHIP_MISMATCH, "${moveType.name} movement ingredient/area does not match stock count line")
                    }
                }
                InventoryMovementType.MANUAL_ADJUSTMENT -> {
                    if (docType != SourceDocumentType.MANUAL) {
                        return err(INVALID_MOVEMENT_GRAPH, "MANUAL_ADJUSTMENT movement must use MANUAL source document type")
                    }
                }
                InventoryMovementType.PRODUCTION_CONSUMPTION -> {
                    if (docType != SourceDocumentType.PRODUCTION_BATCH) {
                        return err(INVALID_MOVEMENT_GRAPH, "PRODUCTION_CONSUMPTION movement must use PRODUCTION_BATCH source document type")
                    }
                    if (moveQty >= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "PRODUCTION_CONSUMPTION quantity must be < 0")
                    val batch = ctx.batchById[move.sourceDocumentId]
                        ?: return err(BROKEN_FOREIGN_KEY, "PRODUCTION_CONSUMPTION batch not found")
                    val lineId = move.sourceLineId ?: return err(INVALID_MOVEMENT_GRAPH, "PRODUCTION_CONSUMPTION requires sourceLineId")
                    val component = ctx.batchComponentById[lineId]
                        ?: return err(BROKEN_FOREIGN_KEY, "PRODUCTION_CONSUMPTION component not found")
                    if (component.productionBatchId != batch.id) return err(RELATIONSHIP_MISMATCH, "Component mismatch in production movement")
                    if (component.componentIngredientId != move.ingredientId || component.sourceAreaId != move.areaId) {
                        return err(RELATIONSHIP_MISMATCH, "Ingredient/area mismatch in production consumption movement")
                    }
                }
                InventoryMovementType.PRODUCTION_OUTPUT -> {
                    if (docType != SourceDocumentType.PRODUCTION_BATCH) {
                        return err(INVALID_MOVEMENT_GRAPH, "PRODUCTION_OUTPUT movement must use PRODUCTION_BATCH source document type")
                    }
                    if (moveQty <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "PRODUCTION_OUTPUT quantity must be > 0")
                    val batch = ctx.batchById[move.sourceDocumentId]
                        ?: return err(BROKEN_FOREIGN_KEY, "PRODUCTION_OUTPUT batch not found")
                    if (move.sourceLineId != batch.id) return err(INVALID_MOVEMENT_GRAPH, "PRODUCTION_OUTPUT sourceLineId must match batch ID")
                    if (batch.outputIngredientId != move.ingredientId || batch.outputAreaId != move.areaId) {
                        return err(RELATIONSHIP_MISMATCH, "Ingredient/area mismatch in production output movement")
                    }
                }
                InventoryMovementType.REVERSAL -> {
                    val targetId = move.reversalOfMovementId
                        ?: return err(INVALID_REVERSAL, "REVERSAL movement requires non-null reversalOfMovementId")
                    if (targetId == move.id) return err(INVALID_REVERSAL, "REVERSAL movement cannot reverse itself")
                    val original = ctx.movementById[targetId]
                        ?: return err(INVALID_REVERSAL, "REVERSAL movement target not found in inventory_movements")
                    if (original.movementType == InventoryMovementType.REVERSAL.name) {
                        return err(INVALID_REVERSAL, "REVERSAL movement cannot point to another REVERSAL movement")
                    }
                    if (reversedMovementIds.contains(targetId)) {
                        return err(INVALID_REVERSAL, "Multiple REVERSAL movements pointing to the same original movement")
                    }
                    reversedMovementIds.add(targetId)

                    // Identity field matching
                    if (move.restaurantId != original.restaurantId ||
                        move.ingredientId != original.ingredientId ||
                        move.areaId != original.areaId ||
                        move.sourceDocumentType != original.sourceDocumentType ||
                        move.sourceDocumentId != original.sourceDocumentId ||
                        move.sourceLineId != original.sourceLineId) {
                        return err(INVALID_REVERSAL, "REVERSAL movement identity fields do not match original movement")
                    }

                    val revCostResult = parseNullableDecimal(move.unitCostBaseSnapshot, "Invalid numeric format in REVERSAL unit cost")
                    val origCostResult = parseNullableDecimal(original.unitCostBaseSnapshot, "Invalid numeric format in original unit cost")
                    
                    if (revCostResult is NullableDecimalResult.Err) return err(revCostResult.code, revCostResult.msg)
                    if (origCostResult is NullableDecimalResult.Err) return err(origCostResult.code, origCostResult.msg)
                    
                    val costsMatch = when {
                        revCostResult is NullableDecimalResult.Null && origCostResult is NullableDecimalResult.Null -> true
                        revCostResult is NullableDecimalResult.Ok && origCostResult is NullableDecimalResult.Ok -> 
                            revCostResult.value.compareTo(origCostResult.value) == 0
                        else -> false
                    }
                    if (!costsMatch) {
                        return err(INVALID_REVERSAL, "REVERSAL and original movement unit cost snapshots do not match")
                    }

                    if (move.effectiveAt < original.effectiveAt) {
                        return err(INVALID_TIMESTAMP_ORDER, "REVERSAL movement effectiveAt must be >= original movement effectiveAt")
                    }

                    // Quantity must be exact negation
                    val origQtyResult = parseDecimal(original.quantityBaseSigned, "Invalid numeric format in original movement quantityBaseSigned")
                    val origQty = when (origQtyResult) {
                        is BigDecimalResult.Err -> return err(origQtyResult.code, origQtyResult.msg)
                        is BigDecimalResult.Ok -> origQtyResult.value
                    }
                    if (moveQty.add(origQty).compareTo(BigDecimal.ZERO) != 0) {
                        return err(INVALID_REVERSAL, "REVERSAL movement quantityBaseSigned must be exact negation of original")
                    }

                    // totalValueSnapshot null-symmetry + negation
                    val revHasValue = move.totalValueSnapshot != null
                    val origHasValue = original.totalValueSnapshot != null
                    if (revHasValue != origHasValue) {
                        return err(INVALID_REVERSAL, "REVERSAL and original movement must have matching totalValueSnapshot nullability")
                    }
                    if (revHasValue) {
                        val revValResult = parseDecimal(move.totalValueSnapshot!!, "Invalid numeric format in REVERSAL movement totalValueSnapshot")
                        val origValResult = parseDecimal(original.totalValueSnapshot!!, "Invalid numeric format in original movement totalValueSnapshot")
                        val revVal = when (revValResult) {
                            is BigDecimalResult.Err -> return err(revValResult.code, revValResult.msg)
                            is BigDecimalResult.Ok -> revValResult.value
                        }
                        val origVal = when (origValResult) {
                            is BigDecimalResult.Err -> return err(origValResult.code, origValResult.msg)
                            is BigDecimalResult.Ok -> origValResult.value
                        }
                        if (revVal.add(origVal).compareTo(BigDecimal.ZERO) != 0) {
                            return err(INVALID_REVERSAL, "REVERSAL movement totalValueSnapshot must be exact negation of original")
                        }
                    }

                    // Parent document must be VOIDED
                    val parentDocStatus = when (SourceDocumentType.valueOf(original.sourceDocumentType)) {
                        SourceDocumentType.PURCHASE_RECEIPT -> ctx.receiptById[original.sourceDocumentId]?.status
                        SourceDocumentType.WASTE_EVENT -> ctx.wasteById[original.sourceDocumentId]?.status
                        SourceDocumentType.STOCK_COUNT -> ctx.countById[original.sourceDocumentId]?.status
                        SourceDocumentType.PRODUCTION_BATCH -> ctx.batchById[original.sourceDocumentId]?.status
                        SourceDocumentType.MANUAL -> null
                        SourceDocumentType.UNKNOWN -> null
                    }
                    if (parentDocStatus != null
                        && parentDocStatus != DocumentStatus.VOIDED.name
                        && parentDocStatus != StockCountStatus.VOIDED.name) {
                        return err(INVALID_REVERSAL, "REVERSAL movement exists on a non-VOIDED parent document")
                    }
                }
                InventoryMovementType.UNKNOWN -> return err(INVALID_ENUM, "Inventory movement type cannot be UNKNOWN")
            }
        }

        return null
    }

    private fun validateDocumentLifecycle(dto: BackupSnapshotDto): BackupSnapshotIntegrityException? {
        // Purchases
        for (receipt in dto.purchaseReceipts) {
            val status = DocumentStatus.valueOf(receipt.status) // already enum-checked
            val lines = dto.purchaseLines.filter { it.purchaseReceiptId == receipt.id }
            val lineIds = lines.map { it.id }.toSet()
            val movements = dto.inventoryMovements.filter {
                it.sourceDocumentType == SourceDocumentType.PURCHASE_RECEIPT.name && it.sourceDocumentId == receipt.id
            }
            when (status) {
                DocumentStatus.DRAFT -> {
                    if (movements.isNotEmpty()) return err(INVALID_DOCUMENT_LIFECYCLE, "DRAFT purchase receipt must not have movements")
                }
                DocumentStatus.POSTED -> {
                    val purchaseMoves = movements.filter { it.movementType == InventoryMovementType.PURCHASE.name }
                    if (purchaseMoves.size != lines.size) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED purchase receipt must have exactly one PURCHASE movement per line")
                    }
                    if (purchaseMoves.mapNotNull { it.sourceLineId }.toSet() != lineIds) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED purchase receipt line IDs must match movement sourceLineIds 1-to-1")
                    }
                    if (purchaseMoves.mapNotNull { it.sourceLineId }.size != purchaseMoves.size) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED purchase receipt contains movements with null sourceLineId")
                    }
                    if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED purchase receipt must not have REVERSAL movements")
                    }
                }
                DocumentStatus.VOIDED -> {
                    val purchaseMoves = movements.filter { it.movementType == InventoryMovementType.PURCHASE.name }
                    val reversalMoves = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }
                    if (purchaseMoves.size != lines.size || reversalMoves.size != lines.size) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED purchase receipt must have matching PURCHASE and REVERSAL movements per line")
                    }
                    if (purchaseMoves.mapNotNull { it.sourceLineId }.toSet() != lineIds) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED purchase receipt line IDs must match movement sourceLineIds 1-to-1")
                    }
                    if (purchaseMoves.mapNotNull { it.sourceLineId }.size != purchaseMoves.size) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED purchase receipt contains movements with null sourceLineId")
                    }
                    val reversedTargetIds = reversalMoves.mapNotNull { it.reversalOfMovementId }.toSet()
                    val origMoveIds = purchaseMoves.map { it.id }.toSet()
                    if (reversedTargetIds != origMoveIds) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED purchase receipt REVERSAL movements must cover all original PURCHASE movements 1-to-1")
                    }
                }
                DocumentStatus.UNKNOWN -> return err(INVALID_DOCUMENT_LIFECYCLE, "Purchase receipt status cannot be UNKNOWN")
            }
        }

        // Waste events
        for (waste in dto.wasteEvents) {
            val status = DocumentStatus.valueOf(waste.status)
            val movements = dto.inventoryMovements.filter {
                it.sourceDocumentType == SourceDocumentType.WASTE_EVENT.name && it.sourceDocumentId == waste.id
            }
            when (status) {
                DocumentStatus.DRAFT -> {
                    if (movements.isNotEmpty()) return err(INVALID_DOCUMENT_LIFECYCLE, "DRAFT waste event must not have movements")
                }
                DocumentStatus.POSTED -> {
                    val wasteMoves = movements.filter { it.movementType == InventoryMovementType.WASTE.name }
                    if (wasteMoves.size != 1) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED waste event must have exactly one WASTE movement")
                    if (wasteMoves[0].sourceLineId != waste.id) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "WASTE movement sourceLineId must equal waste event ID")
                    }
                    if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED waste event must not have REVERSAL movements")
                    }
                }
                DocumentStatus.VOIDED -> {
                    val wasteMoves = movements.filter { it.movementType == InventoryMovementType.WASTE.name }
                    val reversalMoves = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }
                    if (wasteMoves.size != 1 || reversalMoves.size != 1) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED waste event must have exactly one WASTE movement and one REVERSAL")
                    }
                    if (reversalMoves[0].reversalOfMovementId != wasteMoves[0].id) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED waste event REVERSAL must point to original WASTE movement")
                    }
                }
                DocumentStatus.UNKNOWN -> return err(INVALID_DOCUMENT_LIFECYCLE, "Waste event status cannot be UNKNOWN")
            }
        }

        // Stock counts
        for (count in dto.stockCounts) {
            val status = StockCountStatus.valueOf(count.status)
            val scas = dto.stockCountAreas.filter { it.stockCountId == count.id }
            val countLines = dto.stockCountLines.filter { line -> scas.any { it.id == line.stockCountAreaId } }
            val lineIds = countLines.map { it.id }.toSet()
            val movements = dto.inventoryMovements.filter {
                it.sourceDocumentType == SourceDocumentType.STOCK_COUNT.name && it.sourceDocumentId == count.id
            }
            when (status) {
                StockCountStatus.DRAFT -> {
                    if (movements.isNotEmpty()) return err(INVALID_DOCUMENT_LIFECYCLE, "DRAFT stock count must not have movements")
                }
                StockCountStatus.COMPLETED -> {
                    val origMoves = movements.filter {
                        it.movementType == InventoryMovementType.OPENING_BALANCE.name ||
                        it.movementType == InventoryMovementType.COUNT_ADJUSTMENT.name
                    }
                    if (origMoves.size != countLines.size) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "COMPLETED stock count must have exactly one movement per count line")
                    }
                    if (origMoves.mapNotNull { it.sourceLineId }.toSet() != lineIds) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "COMPLETED stock count line IDs must match movement sourceLineIds 1-to-1")
                    }
                    if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "COMPLETED stock count must not have REVERSAL movements")
                    }
                }
                StockCountStatus.VOIDED -> {
                    val origMoves = movements.filter {
                        it.movementType == InventoryMovementType.OPENING_BALANCE.name ||
                        it.movementType == InventoryMovementType.COUNT_ADJUSTMENT.name
                    }
                    val reversalMoves = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }
                    // Exact cardinality: one reversal per original movement
                    if (origMoves.size != countLines.size) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED stock count must have one original movement per count line")
                    }
                    if (origMoves.mapNotNull { it.sourceLineId }.toSet() != lineIds) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED stock count line IDs must match movement sourceLineIds 1-to-1")
                    }
                    if (origMoves.mapNotNull { it.sourceLineId }.size != origMoves.size) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED stock count contains movements with null sourceLineId")
                    }
                    if (reversalMoves.size != origMoves.size) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED stock count must have a REVERSAL for each original movement")
                    }
                    val reversedTargetIds = reversalMoves.mapNotNull { it.reversalOfMovementId }.toSet()
                    val origMoveIds = origMoves.map { it.id }.toSet()
                    if (reversedTargetIds != origMoveIds) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED stock count REVERSAL movements must cover all original movements 1-to-1")
                    }
                }
                StockCountStatus.UNKNOWN -> return err(INVALID_DOCUMENT_LIFECYCLE, "Stock count status cannot be UNKNOWN")
            }
        }

        // Production Batches
        for (batch in dto.productionBatches) {
            val status = try {
                DocumentStatus.valueOf(batch.status)
            } catch (_: Exception) {
                return err(INVALID_ENUM, "Invalid status in production_batches")
            }
            val components = dto.productionBatchComponents.filter { it.productionBatchId == batch.id }
            val componentIds = components.map { it.id }.toSet()
            val movements = dto.inventoryMovements.filter {
                it.sourceDocumentType == SourceDocumentType.PRODUCTION_BATCH.name && it.sourceDocumentId == batch.id
            }

            when (status) {
                DocumentStatus.DRAFT -> {
                    if (movements.isNotEmpty()) return err(INVALID_DOCUMENT_LIFECYCLE, "DRAFT batch must not have movements")
                }
                DocumentStatus.POSTED -> {
                    val consumptionMoves = movements.filter { it.movementType == InventoryMovementType.PRODUCTION_CONSUMPTION.name }
                    val outputMoves = movements.filter { it.movementType == InventoryMovementType.PRODUCTION_OUTPUT.name }
                    if (consumptionMoves.size != components.size) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED batch: consumption count mismatch")
                    if (outputMoves.size != 1) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED batch must have exactly one output movement")
                    if (consumptionMoves.mapNotNull { it.sourceLineId }.toSet() != componentIds) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED batch: component ID mismatch")
                    if (movements.any { it.movementType == InventoryMovementType.REVERSAL.name }) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED batch must not have REVERSAL movements")
                    
                    // Exact movement content validation
                    validateProductionOriginalMovements(batch, components, movements)?.let { return it }
                }
                DocumentStatus.VOIDED -> {
                    val originalMoves = movements.filter { it.movementType != InventoryMovementType.REVERSAL.name }
                    val reversals = movements.filter { it.movementType == InventoryMovementType.REVERSAL.name }
                    if (originalMoves.size != (components.size + 1) || reversals.size != originalMoves.size) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED batch: reversal count mismatch")
                    }
                    val reversedTargetIds = reversals.mapNotNull { it.reversalOfMovementId }.toSet()
                    val origMoveIds = originalMoves.map { it.id }.toSet()
                    if (reversedTargetIds != origMoveIds) return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED batch: reversals must cover all original moves")
                    
                    // Exact movement content validation
                    validateProductionOriginalMovements(batch, components, originalMoves)?.let { return it }
                    validateProductionReversals(batch, originalMoves, reversals)?.let { return it }
                }
                DocumentStatus.UNKNOWN -> return err(INVALID_DOCUMENT_LIFECYCLE, "Production batch status cannot be UNKNOWN")
            }
        }

        return null
    }

    private fun validateBalanceProjections(dto: BackupSnapshotDto): BackupSnapshotIntegrityException? {
        val reversedOriginalIds = dto.inventoryMovements
            .mapNotNull { it.reversalOfMovementId }
            .toSet()

        val effectiveMovements = dto.inventoryMovements.filter {
            it.movementType != InventoryMovementType.REVERSAL.name &&
            it.id !in reversedOriginalIds
        }

        val computedBalances = mutableMapOf<Pair<String, String>, BigDecimal>()
        val keysWithMovements = mutableSetOf<Pair<String, String>>()

        for (move in effectiveMovements) {
            val key = Pair(move.ingredientId, move.areaId)
            keysWithMovements.add(key)
            val q = BigDecimal(move.quantityBaseSigned)
            computedBalances[key] = computedBalances.getOrDefault(key, BigDecimal.ZERO).add(q)
        }

        val projectionKeys = dto.inventoryBalanceProjections.map { Pair(it.ingredientId, it.areaId) }.toSet()

        val missing = keysWithMovements - projectionKeys
        if (missing.isNotEmpty()) {
            return err(INVALID_BALANCE_PROJECTION, "Missing balance projection for ingredient/area combination with movements")
        }
        val extra = projectionKeys - keysWithMovements
        if (extra.isNotEmpty()) {
            return err(INVALID_BALANCE_PROJECTION, "Extra balance projection found for ingredient/area with no movement history")
        }

        for (proj in dto.inventoryBalanceProjections) {
            val key = Pair(proj.ingredientId, proj.areaId)
            val expected = computedBalances.getOrDefault(key, BigDecimal.ZERO)
            val actualResult = parseDecimal(proj.quantityBase, "Invalid numeric format in inventory_balance_projection.quantityBase")
            val actual = when (actualResult) {
                is BigDecimalResult.Err -> return err(actualResult.code, actualResult.msg)
                is BigDecimalResult.Ok -> actualResult.value
            }
            if (expected.compareTo(actual) != 0) {
                return err(INVALID_BALANCE_PROJECTION, "Inventory balance projection value does not match movement sum")
            }
        }

        return null
    }

    private fun validateCostProjections(dto: BackupSnapshotDto, ctx: ValidationContext): BackupSnapshotIntegrityException? {
        val reversedOriginalIds = dto.inventoryMovements
            .mapNotNull { it.reversalOfMovementId }
            .toSet()

        val movementsByIngredient = dto.inventoryMovements
            .filter { it.id !in reversedOriginalIds && it.movementType != InventoryMovementType.REVERSAL.name }
            .groupBy { it.ingredientId }

        val costProjByIng = dto.ingredientCostProjections.associateBy { it.ingredientId }

        for (ing in dto.ingredients) {
            val moves = (movementsByIngredient[ing.id] ?: emptyList()).map { move ->
                HistoricalInventoryMovement(
                    id = move.id,
                    movementType = parsePersistedEnum(move.movementType, InventoryMovementType.UNKNOWN),
                    quantityBaseSigned = BigDecimal(move.quantityBaseSigned),
                    unitCostBaseSnapshot = move.unitCostBaseSnapshot?.let { BigDecimal(it) },
                    sourceDocumentType = parsePersistedEnum(move.sourceDocumentType, SourceDocumentType.UNKNOWN),
                    sourceDocumentId = move.sourceDocumentId,
                    effectiveAt = move.effectiveAt,
                    createdAt = move.createdAt,
                    reversalOfMovementId = move.reversalOfMovementId
                )
            }

            val calculationResult = costCalculator.calculate(moves)
            val result = when (calculationResult) {
                is HistoricalInventoryCostCalculationResult.Success -> calculationResult.value
                is HistoricalInventoryCostCalculationResult.Failure -> {
                    return err(INVALID_REVERSAL, "Malformed reversal in ingredient cost history")
                }
            }
            val proj = costProjByIng[ing.id]

            if (result.hasEstablishedCost) {
                if (proj == null) return err(INVALID_COST_PROJECTION, "Missing cost projection for ingredient with established cost history")
                if (proj.averageUnitCostBase == null) return err(INVALID_COST_PROJECTION, "Cost projection must have value when history exists")
                val actual = BigDecimal(proj.averageUnitCostBase)
                if (actual.compareTo(result.averageUnitCostBase!!) != 0) {
                    return err(INVALID_COST_PROJECTION, "Cost projection value mismatch")
                }
            } else {
                if (proj != null) {
                    // Contract: Row must be absent or averageUnitCostBase must be null
                    if (proj.averageUnitCostBase != null) {
                        return err(INVALID_COST_PROJECTION, "Cost projection must be null when no cost history exists")
                    }
                }
            }
        }

        return null
    }

    private fun validateRecipes(dto: BackupSnapshotDto, ctx: ValidationContext): BackupSnapshotIntegrityException? {
        val nonArchivedByOutput = mutableMapOf<String, String>() // outputIngredientId -> recipeId

        for (recipe in dto.preparationRecipes) {
            val status = try {
                PreparationRecipeStatus.valueOf(recipe.status)
            } catch (_: Exception) {
                return err(INVALID_ENUM, "Invalid status in preparation_recipes")
            }
            if (status == PreparationRecipeStatus.UNKNOWN) return err(INVALID_ENUM, "preparation_recipes.status cannot be UNKNOWN")

            // Timestamps
            if (recipe.createdAt > recipe.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "recipe.createdAt must be <= recipe.updatedAt")

            // Isolation
            val outputIng = ctx.ingById[recipe.outputIngredientId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: recipe to output ingredient")
            if (outputIng.restaurantId != ctx.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in recipe output ingredient")

            // Lifecycle
            if (status == PreparationRecipeStatus.ARCHIVED) {
                if (recipe.archivedAt == null) return err(INVALID_RECIPE_STATUS, "ARCHIVED recipe must have archivedAt")
                if (recipe.updatedAt > recipe.archivedAt) return err(INVALID_TIMESTAMP_ORDER, "recipe.updatedAt must be <= recipe.archivedAt")
            } else {
                if (recipe.archivedAt != null) return err(INVALID_RECIPE_STATUS, "Non-ARCHIVED recipe must not have archivedAt")

                // Active-reference validation for non-archived
                if (outputIng.deletedAt != null || !outputIng.isActive) {
                    return err(INVALID_RECIPE_STRUCTURE, "Non-archived recipe output ingredient must be active and not deleted")
                }
                
                // Uniqueness: at most one non-archived recipe per output ingredient
                val existing = nonArchivedByOutput[recipe.outputIngredientId]
                if (existing != null) return err(INVALID_RECIPE_STRUCTURE, "Multiple non-archived recipes for one output ingredient")
                nonArchivedByOutput[recipe.outputIngredientId] = recipe.id
            }

            // Name validation
            if (recipe.name.isBlank()) return err(INVALID_RECIPE_STRUCTURE, "Recipe name must not be blank")
            if (recipe.normalizedName.isBlank()) return err(INVALID_RECIPE_STRUCTURE, "Recipe normalizedName must not be blank")
            if (recipe.normalizedName != recipe.name.normalizeName()) {
                return err(INVALID_RECIPE_STRUCTURE, "Recipe normalizedName mismatch")
            }

            // Yield
            if (recipe.yieldUnitOptionId != null) {
                val yieldOpt = ctx.optionById[recipe.yieldUnitOptionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: recipe to yield unit option")
                if (yieldOpt.ingredientId != recipe.outputIngredientId) return err(RELATIONSHIP_MISMATCH, "Yield unit option mismatch in recipe")
                if (status != PreparationRecipeStatus.ARCHIVED) {
                    if (yieldOpt.deletedAt != null || !yieldOpt.isActive) {
                        return err(INVALID_RECIPE_STRUCTURE, "Non-archived recipe yield unit option must be active and not deleted")
                    }
                }
            }

            val yieldQtyResult = parseNullableDecimal(recipe.standardYieldQuantity, "Invalid standardYieldQuantity")
            val yieldQtyBaseResult = parseNullableDecimal(recipe.standardYieldQuantityBase, "Invalid standardYieldQuantityBase")

            if (yieldQtyResult is NullableDecimalResult.Err) return err(yieldQtyResult.code, yieldQtyResult.msg)
            if (yieldQtyBaseResult is NullableDecimalResult.Err) return err(yieldQtyBaseResult.code, yieldQtyBaseResult.msg)

            val hasEntered = yieldQtyResult is NullableDecimalResult.Ok
            val hasBase = yieldQtyBaseResult is NullableDecimalResult.Ok
            val hasOption = recipe.yieldUnitOptionId != null

            if (status == PreparationRecipeStatus.ACTIVE) {
                if (yieldQtyResult !is NullableDecimalResult.Ok || yieldQtyResult.value <= BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "ACTIVE recipe requires positive standardYieldQuantity")
                }
                if (yieldQtyBaseResult !is NullableDecimalResult.Ok || yieldQtyBaseResult.value <= BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "ACTIVE recipe requires positive standardYieldQuantityBase")
                }
                if (!hasOption) return err(INVALID_RECIPE_STRUCTURE, "ACTIVE recipe requires yieldUnitOptionId")

                // Base quantity check
                val yieldOpt = ctx.optionById[recipe.yieldUnitOptionId]!!
                val factor = (parseDecimal(yieldOpt.factorToBase, "Invalid factor in yield option") as BigDecimalResult.Ok).value
                if (yieldQtyBaseResult.value.compareTo(yieldQtyResult.value.multiply(factor)) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "standardYieldQuantityBase mismatch in recipe")
                }
            } else {
                // DRAFT or ARCHIVED
                if (yieldQtyResult is NullableDecimalResult.Ok && yieldQtyResult.value <= BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "$status recipe standardYieldQuantity must be positive if supplied")
                }
                if (yieldQtyBaseResult is NullableDecimalResult.Ok && yieldQtyBaseResult.value <= BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "$status recipe standardYieldQuantityBase must be positive if supplied")
                }

                if (hasBase && !hasEntered) {
                    return err(INVALID_RECIPE_STRUCTURE, "$status recipe has base yield but no entered yield")
                }
                if (hasBase && !hasOption) {
                    return err(INVALID_RECIPE_STRUCTURE, "$status recipe has base yield but no unit option")
                }

                if (hasEntered && hasBase) {
                    val yieldOpt = ctx.optionById[recipe.yieldUnitOptionId]!!
                    val factor = (parseDecimal(yieldOpt.factorToBase, "Invalid factor in yield option") as BigDecimalResult.Ok).value
                    if ((yieldQtyBaseResult as NullableDecimalResult.Ok).value.compareTo((yieldQtyResult as NullableDecimalResult.Ok).value.multiply(factor)) != 0) {
                        return err(INVALID_NUMERIC_RANGE, "$status recipe standardYieldQuantityBase mismatch")
                    }
                }

                if (status == PreparationRecipeStatus.DRAFT && hasEntered && !hasBase && hasOption) {
                    return err(INVALID_RECIPE_STRUCTURE, "Draft recipe has entered yield and option but no base yield")
                }
            }

            // Components
            val components = ctx.componentsByRecipeId[recipe.id] ?: emptyList()
            if (status == PreparationRecipeStatus.ACTIVE && components.isEmpty()) {
                return err(INVALID_RECIPE_STRUCTURE, "ACTIVE recipe must have components")
            }

            val seenIngredientsInRecipe = mutableSetOf<String>()
            for (comp in components) {
                if (comp.recipeId != recipe.id) return err(RELATIONSHIP_MISMATCH, "Component recipeId mismatch")
                if (!seenIngredientsInRecipe.add(comp.componentIngredientId)) return err(INVALID_RECIPE_STRUCTURE, "Duplicate component ingredient in recipe")
                if (comp.componentIngredientId == recipe.outputIngredientId) return err(INVALID_RECIPE_STRUCTURE, "Output ingredient cannot be a component of itself")

                // Timestamps
                if (comp.createdAt > comp.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "component.createdAt must be <= component.updatedAt")

                val compIng = ctx.ingById[comp.componentIngredientId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: component to ingredient")
                if (compIng.restaurantId != ctx.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in recipe component ingredient")

                val compOpt = ctx.optionById[comp.unitOptionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: component to unit option")
                if (compOpt.ingredientId != comp.componentIngredientId) return err(RELATIONSHIP_MISMATCH, "Component unit option mismatch")

                if (status != PreparationRecipeStatus.ARCHIVED) {
                    if (compIng.deletedAt != null || !compIng.isActive) {
                        return err(INVALID_RECIPE_STRUCTURE, "Non-archived recipe component ingredient must be active and not deleted")
                    }
                    if (compOpt.deletedAt != null || !compOpt.isActive) {
                        return err(INVALID_RECIPE_STRUCTURE, "Non-archived recipe component unit option must be active and not deleted")
                    }
                }

                val qtyResult = parseDecimal(comp.quantityEntered, "Invalid quantityEntered in component")
                val qtyBaseResult = parseDecimal(comp.quantityBase, "Invalid quantityBase in component")

                if (qtyResult is BigDecimalResult.Err) return err(qtyResult.code, qtyResult.msg)
                if (qtyBaseResult is BigDecimalResult.Err) return err(qtyBaseResult.code, qtyBaseResult.msg)

                val qty = (qtyResult as BigDecimalResult.Ok).value
                val qtyBase = (qtyBaseResult as BigDecimalResult.Ok).value

                if (qty <= BigDecimal.ZERO || qtyBase <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "Component quantity must be positive")

                val factor = (parseDecimal(compOpt.factorToBase, "Invalid factor in component option") as BigDecimalResult.Ok).value
                if (qtyBase.compareTo(qty.multiply(factor)) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "quantityBase mismatch in recipe component")
                }
            }
        }

        // Graph validation (cycle detection)
        validateRecipeGraph(dto, ctx)?.let { return it }

        return null
    }

    private fun validateRecipeGraph(dto: BackupSnapshotDto, ctx: ValidationContext): BackupSnapshotIntegrityException? {
        val edges = dto.preparationRecipes
            .filter { it.status != PreparationRecipeStatus.ARCHIVED.name }
            .flatMap { recipe ->
                val components = ctx.componentsByRecipeId[recipe.id] ?: emptyList()
                components.map { recipe.outputIngredientId to it.componentIngredientId } // output -> component
            }

        val adj = edges.groupBy({ it.first }, { it.second })
        val visited = mutableSetOf<String>()
        val path = mutableSetOf<String>()

        fun hasCycle(u: String): Boolean {
            visited.add(u)
            path.add(u)
            for (v in adj[u] ?: emptyList()) {
                if (v in path) return true
                if (v !in visited && hasCycle(v)) return true
            }
            path.remove(u)
            return false
        }

        for (u in adj.keys) {
            if (u !in visited && hasCycle(u)) return err(INVALID_RECIPE_GRAPH, "Cycle detected in recipe dependency graph")
        }

        return null
    }

    private fun validateProductionBatches(dto: BackupSnapshotDto, ctx: ValidationContext): BackupSnapshotIntegrityException? {
        val movementsByIngredient = dto.inventoryMovements.groupBy { it.ingredientId }

        for (batch in dto.productionBatches) {
            val status = try {
                DocumentStatus.valueOf(batch.status)
            } catch (_: Exception) {
                return err(INVALID_ENUM, "Invalid status in production_batches")
            }

            // Timestamps
            if (batch.createdAt > batch.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "batch.createdAt must be <= batch.updatedAt")
            when (status) {
                DocumentStatus.DRAFT -> {
                    if (batch.postedAt != null || batch.voidedAt != null) return err(INVALID_DOCUMENT_LIFECYCLE, "DRAFT batch must not have postedAt/voidedAt")
                }
                DocumentStatus.POSTED -> {
                    if (batch.postedAt == null || batch.voidedAt != null) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED batch lifecycle error")
                    if (batch.createdAt > batch.postedAt) return err(INVALID_TIMESTAMP_ORDER, "batch.createdAt must be <= batch.postedAt")
                    if (batch.postedAt > batch.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "batch.postedAt must be <= batch.updatedAt")
                }
                DocumentStatus.VOIDED -> {
                    if (batch.postedAt == null || batch.voidedAt == null) return err(INVALID_DOCUMENT_LIFECYCLE, "VOIDED batch lifecycle error")
                    if (batch.postedAt > batch.voidedAt) return err(INVALID_TIMESTAMP_ORDER, "batch.postedAt must be <= voidedAt")
                    if (batch.voidedAt > batch.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "batch.voidedAt must be <= batch.updatedAt")
                }
                DocumentStatus.UNKNOWN -> return err(INVALID_DOCUMENT_LIFECYCLE, "Production batch status cannot be UNKNOWN")
            }

            // Isolation & FKs
            val recipe = ctx.recipeById[batch.recipeId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: batch to recipe")
            if (recipe.restaurantId != ctx.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Recipe restaurant mismatch in batch")
            if (recipe.outputIngredientId != batch.outputIngredientId) return err(RELATIONSHIP_MISMATCH, "Recipe output ingredient mismatch")

            val outputIng = ctx.ingById[batch.outputIngredientId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: batch to output ingredient")
            val outputArea = ctx.areaById[batch.outputAreaId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: batch to output area")
            val outputOpt = ctx.optionById[batch.outputUnitOptionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: batch to output option")

            if (outputIng.restaurantId != ctx.restaurantId || outputArea.restaurantId != ctx.restaurantId) {
                return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in production batch")
            }
            if (outputOpt.ingredientId != batch.outputIngredientId) return err(RELATIONSHIP_MISMATCH, "Output option mismatch in batch")

            val yieldOptSnapshot = ctx.optionById[batch.recipeYieldUnitOptionIdSnapshot] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: batch to yield option snapshot")
            if (yieldOptSnapshot.ingredientId != batch.outputIngredientId) return err(RELATIONSHIP_MISMATCH, "Yield option snapshot mismatch")

            // Draft-specific live reference validation
            if (status == DocumentStatus.DRAFT) {
                if (recipe.status != PreparationRecipeStatus.ACTIVE.name) return err(INVALID_RECIPE_STATUS, "Draft batch must reference ACTIVE recipe")
                if (outputIng.deletedAt != null || !outputIng.isActive) return err(RELATIONSHIP_MISMATCH, "Draft batch output ingredient must be active")
                if (outputArea.deletedAt != null || !outputArea.isActive) return err(RELATIONSHIP_MISMATCH, "Draft batch output area must be active")
                if (outputOpt.deletedAt != null || !outputOpt.isActive) return err(RELATIONSHIP_MISMATCH, "Draft batch output option must be active")
            }

            // Numeric semantics
            val multiplier = BigDecimal(batch.batchMultiplier)
            val rsYieldEntered = BigDecimal(batch.recipeStandardYieldQuantitySnapshot)
            val rsYieldBase = BigDecimal(batch.recipeStandardYieldBaseSnapshot)
            val expOutEntered = BigDecimal(batch.expectedOutputQuantityEntered)
            val expOutBase = BigDecimal(batch.expectedOutputQuantityBase)
            val actOutEntered = BigDecimal(batch.actualOutputQuantityEntered)
            val actOutBase = BigDecimal(batch.actualOutputQuantityBase)

            if (multiplier <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "batchMultiplier must be > 0")
            if (rsYieldEntered <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "recipe yield entered must be > 0")
            if (rsYieldBase <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "recipe yield base must be > 0")
            if (expOutEntered <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "expected output entered must be > 0")
            if (expOutBase <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "expected output base must be > 0")
            if (actOutEntered <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "actual output entered must be > 0")
            if (actOutBase <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "actual output base must be > 0")

            // Conversion validation
            if (rsYieldBase.compareTo(rsYieldEntered.multiply(BigDecimal(yieldOptSnapshot.factorToBase))) != 0) {
                return err(INVALID_NUMERIC_RANGE, "Recipe yield conversion mismatch in batch snapshot")
            }
            if (expOutEntered.compareTo(rsYieldEntered.multiply(multiplier)) != 0) {
                return err(INVALID_NUMERIC_RANGE, "Expected output entered scaling mismatch")
            }
            if (expOutBase.compareTo(rsYieldBase.multiply(multiplier)) != 0) {
                return err(INVALID_NUMERIC_RANGE, "Expected output base scaling mismatch")
            }
            if (actOutBase.compareTo(actOutEntered.multiply(BigDecimal(outputOpt.factorToBase))) != 0) {
                return err(INVALID_NUMERIC_RANGE, "Actual output conversion mismatch")
            }

            // Costs
            if (status == DocumentStatus.DRAFT) {
                if (batch.totalComponentCostSnapshot != null || batch.outputUnitCostBaseSnapshot != null) {
                    return err(INVALID_DOCUMENT_LIFECYCLE, "DRAFT batch must not have cost snapshots")
                }
            } else {
                if (batch.totalComponentCostSnapshot == null || batch.outputUnitCostBaseSnapshot == null) {
                    return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED/VOIDED batch must have cost snapshots")
                }
                val totalCost = BigDecimal(batch.totalComponentCostSnapshot)
                val unitCost = BigDecimal(batch.outputUnitCostBaseSnapshot)
                if (totalCost < BigDecimal.ZERO || unitCost < BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "Production costs must be >= 0")
                }
                
                val expectedUnitCost = totalCost.divide(actOutBase, java.math.MathContext.DECIMAL128)
                if (unitCost.compareTo(expectedUnitCost) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "outputUnitCostBaseSnapshot mismatch")
                }
            }

            // Components
            val components = ctx.componentsByBatchId[batch.id] ?: emptyList()
            if (components.isEmpty()) return err(INVALID_RECIPE_STRUCTURE, "Batch must have components")

            val seenIngredientsInBatch = mutableSetOf<String>()
            var computedTotalCost = BigDecimal.ZERO
            for (comp in components) {
                if (comp.productionBatchId != batch.id) return err(RELATIONSHIP_MISMATCH, "Component parent mismatch")
                if (!seenIngredientsInBatch.add(comp.componentIngredientId)) return err(DUPLICATE_PRODUCTION_COMPONENT, "Duplicate component ingredient in batch")
                if (comp.sourceRecipeComponentIdSnapshot.isBlank()) return err(BLANK_PRIMARY_KEY, "Blank sourceRecipeComponentIdSnapshot")

                // Timestamps
                if (comp.createdAt > comp.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "component.createdAt must be <= component.updatedAt")

                val compIng = ctx.ingById[comp.componentIngredientId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: component to ingredient")
                val compOpt = ctx.optionById[comp.unitOptionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: component to option")
                val rUnitOpt = ctx.optionById[comp.recipeUnitOptionIdSnapshot] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: component to recipe option snapshot")

                if (compIng.restaurantId != ctx.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in batch component")
                if (compOpt.ingredientId != comp.componentIngredientId) return err(RELATIONSHIP_MISMATCH, "Component option mismatch")
                if (rUnitOpt.ingredientId != comp.componentIngredientId) return err(RELATIONSHIP_MISMATCH, "Recipe unit option snapshot mismatch")

                if (comp.sourceAreaId != null) {
                    val srcArea = ctx.areaById[comp.sourceAreaId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: component to source area")
                    if (srcArea.restaurantId != ctx.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in component source area")
                    if (status == DocumentStatus.DRAFT) {
                        if (srcArea.deletedAt != null || !srcArea.isActive) return err(RELATIONSHIP_MISMATCH, "Draft batch component area must be active")
                    }
                } else {
                    if (status != DocumentStatus.DRAFT) return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED/VOIDED batch component must have sourceAreaId")
                }

                if (status == DocumentStatus.DRAFT) {
                    if (compIng.deletedAt != null || !compIng.isActive) return err(RELATIONSHIP_MISMATCH, "Draft batch component ingredient must be active")
                    if (compOpt.deletedAt != null || !compOpt.isActive) return err(RELATIONSHIP_MISMATCH, "Draft batch component option must be active")
                }

                val rQtyEntered = BigDecimal(comp.recipeQuantityEnteredSnapshot)
                val rQtyBase = BigDecimal(comp.recipeQuantityBaseSnapshot)
                val eQtyEntered = BigDecimal(comp.expectedQuantityEntered)
                val eQtyBase = BigDecimal(comp.expectedQuantityBase)
                val aQtyEntered = BigDecimal(comp.actualQuantityEntered)
                val aQtyBase = BigDecimal(comp.actualQuantityBase)

                if (rQtyEntered <= BigDecimal.ZERO || rQtyBase <= BigDecimal.ZERO ||
                    eQtyEntered <= BigDecimal.ZERO || eQtyBase <= BigDecimal.ZERO ||
                    aQtyEntered <= BigDecimal.ZERO || aQtyBase <= BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "Component quantities must be > 0")
                }

                if (rQtyBase.compareTo(rQtyEntered.multiply(BigDecimal(rUnitOpt.factorToBase))) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "Component recipe conversion mismatch")
                }
                if (eQtyEntered.compareTo(rQtyEntered.multiply(multiplier)) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "Component expected scaling mismatch")
                }
                if (eQtyBase.compareTo(rQtyBase.multiply(multiplier)) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "Component expected base scaling mismatch")
                }
                if (aQtyBase.compareTo(aQtyEntered.multiply(BigDecimal(compOpt.factorToBase))) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "Component actual conversion mismatch")
                }

                if (status != DocumentStatus.DRAFT) {
                    if (comp.unitCostBaseSnapshot == null || comp.totalCostSnapshot == null) {
                        return err(INVALID_DOCUMENT_LIFECYCLE, "POSTED/VOIDED component must have cost snapshots")
                    }
                    val uCost = BigDecimal(comp.unitCostBaseSnapshot)
                    val tCost = BigDecimal(comp.totalCostSnapshot)
                    if (uCost < BigDecimal.ZERO || tCost < BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "Component costs must be >= 0")
                    if (tCost.compareTo(aQtyBase.multiply(uCost)) != 0) return err(INVALID_NUMERIC_RANGE, "Component total cost mismatch")

                    // Historical cost validation
                    val moves = (movementsByIngredient[comp.componentIngredientId] ?: emptyList()).map { move ->
                        HistoricalInventoryMovement(
                            id = move.id,
                            movementType = parsePersistedEnum(move.movementType, InventoryMovementType.UNKNOWN),
                            quantityBaseSigned = BigDecimal(move.quantityBaseSigned),
                            unitCostBaseSnapshot = move.unitCostBaseSnapshot?.let { BigDecimal(it) },
                            sourceDocumentType = parsePersistedEnum(move.sourceDocumentType, SourceDocumentType.UNKNOWN),
                            sourceDocumentId = move.sourceDocumentId,
                            effectiveAt = move.effectiveAt,
                            createdAt = move.createdAt,
                            reversalOfMovementId = move.reversalOfMovementId
                        )
                    }
                    val calculationResult = costCalculator.calculate(
                        moves,
                        boundary = HistoricalInventoryCostBoundary(
                            effectiveAtInclusive = batch.effectiveAt,
                            createdAtInclusive = batch.postedAt!!
                        ),
                        excludedSourceDocument = SourceDocumentIdentity(SourceDocumentType.PRODUCTION_BATCH, batch.id)
                    )
                    val histResult = when (calculationResult) {
                        is HistoricalInventoryCostCalculationResult.Success -> calculationResult.value
                        is HistoricalInventoryCostCalculationResult.Failure -> {
                            return err(INVALID_REVERSAL, "Malformed reversal in production cost history")
                        }
                    }
                    if (!histResult.hasEstablishedCost) {
                        return err(INVALID_PRODUCTION_COST_HISTORY, "Production component cost unavailable in history")
                    }
                    if (uCost.compareTo(histResult.averageUnitCostBase!!) != 0) {
                        return err(INVALID_PRODUCTION_COST_HISTORY, "Production component cost does not match historical average")
                    }

                    computedTotalCost = computedTotalCost.add(tCost)
                }
            }

            if (status != DocumentStatus.DRAFT) {
                val totalSnapshot = BigDecimal(batch.totalComponentCostSnapshot!!)
                if (totalSnapshot.compareTo(computedTotalCost) != 0) return err(INVALID_NUMERIC_RANGE, "Batch total cost does not match components sum")
                
                // Value conservation
                val consumptionValueSum = components.map { BigDecimal(it.totalCostSnapshot!!).negate() }
                    .fold(BigDecimal.ZERO) { acc, d -> acc.add(d) }
                if (consumptionValueSum.add(totalSnapshot).compareTo(BigDecimal.ZERO) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "Production batch value conservation failure")
                }
            }
        }
        return null
    }

    private fun validateProductionOriginalMovements(
        batch: com.miara.cuentame.core.backup.model.ProductionBatchBackupDto,
        components: List<com.miara.cuentame.core.backup.model.ProductionBatchComponentBackupDto>,
        originalMovements: List<com.miara.cuentame.core.backup.model.InventoryMovementBackupDto>
    ): BackupSnapshotIntegrityException? {
        val consumptionMoves = originalMovements.filter { it.movementType == InventoryMovementType.PRODUCTION_CONSUMPTION.name }
        val outputMoves = originalMovements.filter { it.movementType == InventoryMovementType.PRODUCTION_OUTPUT.name }

        if (consumptionMoves.size != components.size) return err(INVALID_MOVEMENT_GRAPH, "Production movement count mismatch")
        if (outputMoves.size != 1) return err(INVALID_MOVEMENT_GRAPH, "Production output movement count mismatch")

        val consumptionLineIds = consumptionMoves.mapNotNull { it.sourceLineId }
        if (consumptionLineIds.size != consumptionMoves.size) return err(INVALID_MOVEMENT_GRAPH, "Production consumption missing sourceLineId")
        if (consumptionLineIds.distinct().size != consumptionLineIds.size) return err(INVALID_MOVEMENT_GRAPH, "Duplicate production consumption sourceLineId")
        if (consumptionLineIds.toSet() != components.map { it.id }.toSet()) return err(INVALID_MOVEMENT_GRAPH, "Production consumption sourceLineId set mismatch")

        val consumptionByLineId = consumptionMoves.associateBy { it.sourceLineId }
        for (component in components) {
            val move = consumptionByLineId[component.id]!!
            
            if (move.restaurantId != batch.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Production movement restaurant mismatch")
            if (move.ingredientId != component.componentIngredientId) return err(RELATIONSHIP_MISMATCH, "Production movement ingredient mismatch")
            if (move.areaId != component.sourceAreaId) return err(RELATIONSHIP_MISMATCH, "Production movement area mismatch")
            if (move.sourceDocumentType != SourceDocumentType.PRODUCTION_BATCH.name) return err(INVALID_MOVEMENT_GRAPH, "Production movement document type mismatch")
            if (move.sourceDocumentId != batch.id) return err(INVALID_MOVEMENT_GRAPH, "Production movement document ID mismatch")
            if (move.sourceOperationId != InventoryMovementOperationIds.productionConsumption(batch.id, component.id)) return err(INVALID_MOVEMENT_GRAPH, "Production movement operation identifier mismatch")
            
            val qty = BigDecimal(move.quantityBaseSigned)
            val expectedQty = BigDecimal(component.actualQuantityBase).negate()
            if (qty.compareTo(expectedQty) != 0) return err(INVALID_NUMERIC_RANGE, "Production consumption quantity mismatch")

            if (!isNumericallyEquivalent(move.unitCostBaseSnapshot, component.unitCostBaseSnapshot)) {
                return err(INVALID_NUMERIC_RANGE, "Production consumption unit cost mismatch")
            }
            
            val expectedTotalValue = BigDecimal(component.totalCostSnapshot!!).negate()
            if (!isNumericallyEquivalent(move.totalValueSnapshot, expectedTotalValue.toPlainString())) {
                return err(INVALID_NUMERIC_RANGE, "Production consumption total value mismatch")
            }
            
            if (move.effectiveAt != batch.effectiveAt) return err(INVALID_TIMESTAMP_ORDER, "Production consumption effectiveAt mismatch")
            if (move.reversalOfMovementId != null) return err(INVALID_REVERSAL, "Original production movement must not have reversal ID")
        }

        val outMove = outputMoves.first()
        if (outMove.restaurantId != batch.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Production output restaurant mismatch")
        if (outMove.ingredientId != batch.outputIngredientId) return err(RELATIONSHIP_MISMATCH, "Production output ingredient mismatch")
        if (outMove.areaId != batch.outputAreaId) return err(RELATIONSHIP_MISMATCH, "Production output area mismatch")
        if (outMove.sourceDocumentType != SourceDocumentType.PRODUCTION_BATCH.name) return err(INVALID_MOVEMENT_GRAPH, "Production output document type mismatch")
        if (outMove.sourceDocumentId != batch.id) return err(INVALID_MOVEMENT_GRAPH, "Production output document ID mismatch")
        if (outMove.sourceOperationId != InventoryMovementOperationIds.productionOutput(batch.id)) return err(INVALID_MOVEMENT_GRAPH, "Production movement operation identifier mismatch")
        if (outMove.sourceLineId != batch.id) return err(INVALID_MOVEMENT_GRAPH, "Production output source line mismatch")

        val outQty = BigDecimal(outMove.quantityBaseSigned)
        if (outQty.compareTo(BigDecimal(batch.actualOutputQuantityBase)) != 0) return err(INVALID_NUMERIC_RANGE, "Production output quantity mismatch")

        if (!isNumericallyEquivalent(outMove.unitCostBaseSnapshot, batch.outputUnitCostBaseSnapshot)) {
            return err(INVALID_NUMERIC_RANGE, "Production output unit cost mismatch")
        }
        
        if (!isNumericallyEquivalent(outMove.totalValueSnapshot, batch.totalComponentCostSnapshot)) {
            return err(INVALID_NUMERIC_RANGE, "Production output total value mismatch")
        }

        if (outMove.effectiveAt != batch.effectiveAt) return err(INVALID_TIMESTAMP_ORDER, "Production output effectiveAt mismatch")
        if (outMove.reversalOfMovementId != null) return err(INVALID_REVERSAL, "Original production movement must not have reversal ID")

        return null
    }

    private fun validateProductionReversals(
        batch: com.miara.cuentame.core.backup.model.ProductionBatchBackupDto,
        originalMovements: List<com.miara.cuentame.core.backup.model.InventoryMovementBackupDto>,
        reversals: List<com.miara.cuentame.core.backup.model.InventoryMovementBackupDto>
    ): BackupSnapshotIntegrityException? {
        val reversalTargetIds = reversals.mapNotNull { it.reversalOfMovementId }
        if (reversalTargetIds.size != reversals.size) return err(INVALID_REVERSAL, "Production reversal missing target ID")
        if (reversalTargetIds.distinct().size != reversalTargetIds.size) return err(INVALID_REVERSAL, "Duplicate production reversal target")
        if (reversalTargetIds.toSet() != originalMovements.map { it.id }.toSet()) return err(INVALID_REVERSAL, "Production reversal target set mismatch")

        val reversalsByTargetId = reversals.associateBy { it.reversalOfMovementId }
        for (original in originalMovements) {
            val reversal = reversalsByTargetId[original.id]!!
            if (reversal.movementType != InventoryMovementType.REVERSAL.name) return err(INVALID_REVERSAL, "Invalid reversal movement type")
            if (reversal.restaurantId != original.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Reversal restaurant mismatch")
            if (reversal.ingredientId != original.ingredientId) return err(RELATIONSHIP_MISMATCH, "Reversal ingredient mismatch")
            if (reversal.areaId != original.areaId) return err(RELATIONSHIP_MISMATCH, "Reversal area mismatch")
            if (reversal.sourceDocumentType != SourceDocumentType.PRODUCTION_BATCH.name) return err(INVALID_MOVEMENT_GRAPH, "Reversal document type mismatch")
            if (reversal.sourceDocumentId != batch.id) return err(INVALID_MOVEMENT_GRAPH, "Reversal document ID mismatch")
            if (reversal.sourceOperationId != InventoryMovementOperationIds.reversal(original.id)) return err(INVALID_MOVEMENT_GRAPH, "Production movement operation identifier mismatch")
            if (reversal.sourceLineId != original.sourceLineId) return err(INVALID_MOVEMENT_GRAPH, "Reversal source line mismatch")
            
            val revQtyResult = parseDecimal(reversal.quantityBaseSigned, "Invalid reversal quantity")
            val origQtyResult = parseDecimal(original.quantityBaseSigned, "Invalid original quantity")
            
            if (revQtyResult is BigDecimalResult.Err) return err(revQtyResult.code, revQtyResult.msg)
            if (origQtyResult is BigDecimalResult.Err) return err(origQtyResult.code, origQtyResult.msg)
            
            val revQty = (revQtyResult as BigDecimalResult.Ok).value
            val origQty = (origQtyResult as BigDecimalResult.Ok).value
            
            if (revQty.compareTo(origQty.negate()) != 0) return err(INVALID_NUMERIC_RANGE, "Reversal quantity mismatch")
            
            if (!isNumericallyEquivalent(reversal.unitCostBaseSnapshot, original.unitCostBaseSnapshot)) {
                return err(INVALID_NUMERIC_RANGE, "Reversal unit cost mismatch")
            }
            
            val revTotalResult = parseNullableDecimal(reversal.totalValueSnapshot, "Invalid reversal total value")
            val origTotalResult = parseNullableDecimal(original.totalValueSnapshot, "Invalid original total value")
            
            if (revTotalResult is NullableDecimalResult.Err) return err(revTotalResult.code, revTotalResult.msg)
            if (origTotalResult is NullableDecimalResult.Err) return err(origTotalResult.code, origTotalResult.msg)
            
            val revTotal = if (revTotalResult is NullableDecimalResult.Ok) revTotalResult.value else null
            val origTotal = if (origTotalResult is NullableDecimalResult.Ok) origTotalResult.value else null
            
            if ((revTotal == null) != (origTotal == null)) return err(INVALID_NUMERIC_RANGE, "Reversal total value nullability mismatch")
            if (revTotal != null && origTotal != null) {
                if (revTotal.compareTo(origTotal.negate()) != 0) return err(INVALID_NUMERIC_RANGE, "Reversal total value mismatch")
            }
            
            if (reversal.effectiveAt != batch.voidedAt) return err(INVALID_TIMESTAMP_ORDER, "Reversal effectiveAt mismatch")
            if (reversal.createdAt != batch.voidedAt) return err(INVALID_TIMESTAMP_ORDER, "Reversal createdAt mismatch")
        }

        return null
    }

    private fun validateOcr(
        dto: BackupSnapshotDto,
        manifest: BackupManifest,
        ctx: ValidationContext
    ): BackupSnapshotIntegrityException? {
        val manifestChecksums = manifest.attachments.associate { it.attachmentId to it.checksumSha256 }

        for (result in dto.purchaseInvoiceOcrResults) {
            val receipt = ctx.receiptById[result.purchaseReceiptId]
                ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: OCR result to purchase receipt")
            
            if (receipt.attachmentId == null) {
                return err(RELATIONSHIP_MISMATCH, "OCR result exists for purchase without attachment")
            }

            val expectedChecksum = manifestChecksums[receipt.attachmentId]
                ?: return err(RELATIONSHIP_MISMATCH, "OCR attachment not found in manifest")

            if (result.sourceDocumentSha256 != expectedChecksum) {
                return err(RELATIONSHIP_MISMATCH, "OCR checksum mismatch with manifest attachment")
            }

            val pages = dto.purchaseInvoiceOcrPages.filter { it.ocrResultId == result.id }
            if (pages.size != result.pageCount) {
                return err(RELATIONSHIP_MISMATCH, "OCR page count mismatch")
            }

            val pageIndexes = pages.map { it.pageIndex }.sorted()
            if (pageIndexes != (0 until result.pageCount).toList()) {
                return err(RELATIONSHIP_MISMATCH, "OCR page indexes must be contiguous 0..N-1")
            }
        }

        for (page in dto.purchaseInvoiceOcrPages) {
            if (!ctx.ocrResultById.containsKey(page.ocrResultId)) {
                return err(BROKEN_FOREIGN_KEY, "Broken FK: OCR page to result")
            }
        }

        return null
    }

    private fun validateParseResult(
        dto: BackupSnapshotDto,
        ctx: ValidationContext
    ): BackupSnapshotIntegrityException? {
        for (parse in dto.purchaseInvoiceParseResults) {
            if (!ctx.receiptById.containsKey(parse.purchaseReceiptId)) {
                return err(BROKEN_FOREIGN_KEY, "Broken FK: Parse result to purchase receipt")
            }

            val ocr = ctx.ocrResultById[parse.ocrResultId]
                ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: Parse result to OCR result")

            if (ocr.purchaseReceiptId != parse.purchaseReceiptId) {
                return err(RELATIONSHIP_MISMATCH, "Parse result and OCR result belong to different receipts")
            }

            if (ocr.sourceDocumentSha256 != parse.sourceDocumentSha256) {
                return err(RELATIONSHIP_MISMATCH, "Parse result and OCR result have different source document SHA-256")
            }

            // Version support
            if (ocr.evidenceSchemaVersion != 1) {
                return err(UNSUPPORTED_VERSION, "Unsupported OCR evidence schema version")
            }
            if (parse.parserSchemaVersion !in listOf(1, 2)) {
                return err(UNSUPPORTED_VERSION, "Unsupported parser schema version")
            }
        }

        for (line in dto.purchaseInvoiceParsedLines) {
            if (!ctx.parseResultById.containsKey(line.parseResultId)) {
                return err(BROKEN_FOREIGN_KEY, "Broken FK: Parsed line to parse result")
            }
        }

        // Uniqueness check for parsed lines
        val lineKeys = dto.purchaseInvoiceParsedLines.map { it.parseResultId to it.lineIndex }
        if (lineKeys.distinct().size != lineKeys.size) {
            return err(DUPLICATE_COMPOSITE_KEY, "Duplicate parsed line index for same result")
        }

        return null
    }

    private fun validateMappings(
        dto: BackupSnapshotDto,
        ctx: ValidationContext
    ): BackupSnapshotIntegrityException? {
        for (mapping in dto.supplierItemMappings) {
            val supplier = ctx.supplierById[mapping.supplierId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: mapping to supplier")
            val ingredient = ctx.ingById[mapping.ingredientId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: mapping to ingredient")
            
            if (supplier.restaurantId != mapping.restaurantId || ingredient.restaurantId != mapping.restaurantId) {
                return err(RESTAURANT_ISOLATION_FAILURE, "Restaurant mismatch in mapping")
            }

            if (mapping.unitOptionId != null) {
                val opt = ctx.optionById[mapping.unitOptionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: mapping to unit option")
                if (opt.ingredientId != mapping.ingredientId) return err(RELATIONSHIP_MISMATCH, "Unit option mismatch in mapping")
            }

            if (mapping.inventoryAreaId != null) {
                val area = ctx.areaById[mapping.inventoryAreaId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: mapping to area")
                if (area.restaurantId != mapping.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Area restaurant mismatch in mapping")
            }
            
            // Uniqueness check for mapping key
            val key = Triple(mapping.supplierId, mapping.keyType, mapping.normalizedKey)
            if (dto.supplierItemMappings.count { Triple(it.supplierId, it.keyType, it.normalizedKey) == key } > 1) {
                return err(DUPLICATE_COMPOSITE_KEY, "Duplicate supplier item mapping key")
            }
        }
        return null
    }

    private fun validateStagedMatches(
        dto: BackupSnapshotDto,
        ctx: ValidationContext
    ): BackupSnapshotIntegrityException? {
        val parsedLineKeys = dto.purchaseInvoiceParsedLines.map { it.parseResultId to it.lineIndex }.toSet()

        if (dto.purchaseInvoiceLineMatches.isNotEmpty() && dto.purchaseInvoiceParsedLines.isEmpty()) {
        }

        for (match in dto.purchaseInvoiceLineMatches) {
            if (match.parseResultId to match.lineIndex !in parsedLineKeys) {
                return err(RELATIONSHIP_MISMATCH, "Staged match references non-existent parsed line")
            }

            val parse = ctx.parseResultById[match.parseResultId]
                ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: match to parse result")
            
            val receipt = ctx.receiptById[parse.purchaseReceiptId]!!
            
            if (match.supplierId != null) {
                val supplier = ctx.supplierById[match.supplierId]
                    ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: match to supplier")
                if (supplier.restaurantId != receipt.restaurantId) {
                    return err(RESTAURANT_ISOLATION_FAILURE, "Supplier restaurant mismatch in match")
                }
            }
            
            if (match.ingredientId != null) {
                val ing = ctx.ingById[match.ingredientId]
                    ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: match to ingredient")
                if (ing.restaurantId != receipt.restaurantId) {
                    return err(RESTAURANT_ISOLATION_FAILURE, "Ingredient restaurant mismatch in match")
                }

                if (match.unitOptionId != null) {
                    val opt = ctx.optionById[match.unitOptionId]
                        ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: match to unit option")
                    if (opt.ingredientId != match.ingredientId) {
                        return err(RELATIONSHIP_MISMATCH, "Unit option mismatch in match")
                    }
                }
            } else {
                if (match.unitOptionId != null) return err(RELATIONSHIP_MISMATCH, "Unit option without ingredient in match")
            }

            if (match.inventoryAreaId != null) {
                val area = ctx.areaById[match.inventoryAreaId]
                    ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: match to area")
                if (area.restaurantId != receipt.restaurantId) {
                    return err(RESTAURANT_ISOLATION_FAILURE, "Area restaurant mismatch in match")
                }
            }
            
            if (match.mappingId != null) {
                val mapping = ctx.mappingById[match.mappingId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: match to reusable mapping")
                
                val compatibilityError = MatchIntegrityPolicy.isMappingCompatible(
                    matchIngredientId = match.ingredientId,
                    matchUnitOptionId = match.unitOptionId,
                    matchAreaId = match.inventoryAreaId,
                    mappingIngredientId = mapping.ingredientId,
                    mappingUnitOptionId = mapping.unitOptionId,
                    mappingAreaId = mapping.inventoryAreaId
                )
                if (compatibilityError != null) {
                    return err(RELATIONSHIP_MISMATCH, compatibilityError)
                }
            }

            // Status invariants
            val status = try {
                com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus.valueOf(match.status)
            } catch (_: Exception) {
                return err(INVALID_ENUM, "Invalid match status")
            }

            val invariantError = MatchIntegrityPolicy.validateInvariants(
                status = status,
                ingredientId = match.ingredientId,
                unitOptionId = match.unitOptionId,
                inventoryAreaId = match.inventoryAreaId,
                confirmedAt = match.confirmedAt,
                mappingId = match.mappingId
            )
            if (invariantError != null) {
                return err(INVALID_MATCH_STATUS, invariantError)
            }
        }
        
        // Uniqueness check for staged matches
        val matchKeys = dto.purchaseInvoiceLineMatches.map { it.parseResultId to it.lineIndex }
        if (matchKeys.distinct().size != matchKeys.size) {
            return err(DUPLICATE_COMPOSITE_KEY, "Duplicate staged match index for same result")
        }
        
        return null
    }

    private fun validateMaterialization(
        dto: BackupSnapshotDto,
        ctx: ValidationContext
    ): BackupSnapshotIntegrityException? {
        // Unique logical origin check: (applicationId, sourceLineIndex)
        val logicalOrigins = mutableSetOf<Pair<String, Int>>()
        for (origin in dto.purchaseInvoiceLineOrigins) {
            val key = origin.applicationId to origin.sourceLineIndex
            if (!logicalOrigins.add(key)) {
                return err(DUPLICATE_COMPOSITE_KEY, "Duplicate logical source origin for application ${origin.applicationId} line ${origin.sourceLineIndex}")
            }
        }

        return null
    }

    private fun validateMenuRecipes(dto: BackupSnapshotDto, ctx: ValidationContext): BackupSnapshotIntegrityException? {
        for (recipe in dto.menuRecipes) {
            if (recipe.commercialRevision < 0 || recipe.consumptionRevision < 0) return err(INVALID_NUMERIC_RANGE, "Menu recipe revisions must be non-negative")
            if (runCatching { com.miara.cuentame.core.model.menu.CashDiscountBehavior.valueOf(recipe.cashDiscountBehavior) }.isFailure) return err(INVALID_MENU_STRUCTURE, "Invalid cash discount behavior")
            // Timestamps
            if (recipe.createdAt > recipe.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "menu_recipes: createdAt must be <= updatedAt")
            
            // Numeric
            if (recipe.sellingPrice != null) {
                val r = parseDecimal(recipe.sellingPrice, "Invalid sellingPrice")
                if (r is BigDecimalResult.Err) return err(r.code, r.msg)
                if ((r as BigDecimalResult.Ok).value < BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "sellingPrice must be >= 0")
            }

            val components = ctx.componentsByMenuRecipeId[recipe.id] ?: emptyList()
            val seenIngredients = mutableSetOf<String>()
            
            for (comp in components) {
                if (comp.menuRecipeId != recipe.id) return err(RELATIONSHIP_MISMATCH, "Component menuRecipeId mismatch")
                if (!seenIngredients.add(comp.ingredientId)) return err(INVALID_MENU_STRUCTURE, "Duplicate ingredient in menu recipe")
                
                // FKs
                val ing = ctx.ingById[comp.ingredientId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: menu component to ingredient")
                if (ing.restaurantId != ctx.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in menu component ingredient")
                
                val opt = ctx.optionById[comp.ingredientUnitOptionId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: menu component to unit option")
                if (opt.ingredientId != comp.ingredientId) return err(RELATIONSHIP_MISMATCH, "Unit option mismatch in menu component")
                
                // Numeric
                val qe = parseDecimal(comp.quantityEntered, "Invalid quantityEntered")
                val qb = parseDecimal(comp.quantityBase, "Invalid quantityBase")
                
                if (qe is BigDecimalResult.Err) return err(qe.code, qe.msg)
                if (qb is BigDecimalResult.Err) return err(qb.code, qb.msg)
                
                val qty = (qe as BigDecimalResult.Ok).value
                val qtyBase = (qb as BigDecimalResult.Ok).value
                
                if (qty <= BigDecimal.ZERO || qtyBase <= BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "Menu component quantity must be positive")
                
                val factor = (parseDecimal(opt.factorToBase, "Invalid factor") as BigDecimalResult.Ok).value
                if (qtyBase.compareTo(qty.multiply(factor)) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "quantityBase mismatch in menu component")
                }
            }
        }
        return null
    }

    private fun validateMenus(dto: BackupSnapshotDto, ctx: ValidationContext): BackupSnapshotIntegrityException? {
        val activeMenuNames = mutableSetOf<Pair<String, String>>()
        val categoryNames = mutableSetOf<Pair<String,String>>()
        dto.menus.forEach { menu ->
            if (menu.restaurantId != ctx.restaurantId) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in menus")
            val canonicalName = menu.name.normalizeName()
            if (canonicalName.isBlank()) return err(INVALID_MENU_STRUCTURE, "Menu name must not be blank")
            if (menu.normalizedName != canonicalName) return err(INVALID_MENU_STRUCTURE, "Menu normalizedName mismatch")
            if (menu.archivedAt == null && !activeMenuNames.add(menu.restaurantId to menu.normalizedName)) {
                return err(INVALID_MENU_STRUCTURE, "Duplicate active menu name")
            }
            if (menu.publicationRevision < 0) return err(INVALID_NUMERIC_RANGE, "Menu publication revision must be non-negative")
            val discount = runCatching { BigDecimal(menu.defaultCashDiscountPercent) }.getOrNull()
                ?: return err(INVALID_NUMERIC_RANGE, "Invalid menu discount")
            if (discount < BigDecimal.ZERO || discount >= BigDecimal("100")) return err(INVALID_NUMERIC_RANGE, "Invalid menu discount")
            if (menu.createdAt > menu.updatedAt) return err(INVALID_TIMESTAMP_ORDER, "menu.createdAt must be <= menu.updatedAt")
        }
        dto.menuCategories.forEach { category ->
            if (ctx.menuById[category.menuId] == null) return err(BROKEN_FOREIGN_KEY, "Broken FK: menu category to menu")
            val canonicalName = category.name.normalizeName()
            if (canonicalName.isBlank()) return err(INVALID_MENU_STRUCTURE, "Menu category name must not be blank")
            if (category.normalizedName != canonicalName) return err(INVALID_MENU_STRUCTURE, "Menu category normalizedName mismatch")
            if (category.sortOrder < 0) return err(INVALID_NUMERIC_RANGE, "Menu category sort order must be non-negative")
            if (!categoryNames.add(category.menuId to category.normalizedName)) return err(INVALID_MENU_STRUCTURE, "Duplicate category name")
        }
        val placements = mutableSetOf<Pair<String,String>>()
        dto.menuPlacements.forEach { placement ->
            val menu = ctx.menuById[placement.menuId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: placement to menu")
            val category = ctx.menuCategoryById[placement.categoryId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: placement to category")
            val recipe = ctx.menuRecipeById[placement.menuRecipeId] ?: return err(BROKEN_FOREIGN_KEY, "Broken FK: placement to menu recipe")
            if (category.menuId != menu.id || recipe.restaurantId != menu.restaurantId) return err(RELATIONSHIP_MISMATCH, "Invalid menu placement ownership")
            if (placement.sortOrder < 0) return err(INVALID_NUMERIC_RANGE, "Menu placement sort order must be non-negative")
            if (!placements.add(menu.id to recipe.id)) return err(INVALID_MENU_STRUCTURE, "Duplicate menu recipe placement")
        }
        return null
    }

    private fun validateMenuPublications(dto:BackupSnapshotDto,ctx:ValidationContext):BackupSnapshotIntegrityException? {
        val publications=dto.menuPublications.associateBy{it.id};val categories=dto.menuPublicationCategories.associateBy{it.id};val items=dto.menuPublicationItems.associateBy{it.id}
        val revisions=mutableSetOf<Pair<String,Long>>();val sourceCategories=mutableSetOf<Pair<String,String>>();val sourcePlacements=mutableSetOf<Pair<String,String>>();val recipes=mutableSetOf<Pair<String,String>>();val sourceComponents=mutableSetOf<Pair<String,String>>()
        dto.menuPublications.forEach{p->
            if(p.restaurantId!=ctx.restaurantId)return err(RESTAURANT_ISOLATION_FAILURE,"Isolation error in menu publications")
            if(p.sourceMenuId.isBlank()||p.publicationRevision<=0||p.menuNameSnapshot.isBlank()||p.publishedAt<=0)return err(INVALID_MENU_STRUCTURE,"Invalid menu publication")
            val discount=runCatching{BigDecimal(p.defaultCashDiscountPercentSnapshot)}.getOrNull()?:return err(INVALID_NUMERIC_RANGE,"Invalid publication discount")
            if(discount<BigDecimal.ZERO||discount>=BigDecimal("100"))return err(INVALID_NUMERIC_RANGE,"Invalid publication discount")
            if(runCatching{java.util.Currency.getInstance(p.currencyCodeSnapshot)}.isFailure)return err(INVALID_MENU_STRUCTURE,"Invalid publication currency")
            if(!revisions.add(p.sourceMenuId to p.publicationRevision))return err(INVALID_MENU_STRUCTURE,"Duplicate menu publication revision")
        }
        dto.menuPublicationCategories.forEach{c->if(publications[c.publicationId]==null||c.sourceMenuCategoryId.isBlank()||c.nameSnapshot.isBlank())return err(BROKEN_FOREIGN_KEY,"Invalid publication category");if(c.sortOrder<0)return err(INVALID_NUMERIC_RANGE,"Invalid publication category order");if(!sourceCategories.add(c.publicationId to c.sourceMenuCategoryId))return err(INVALID_MENU_STRUCTURE,"Duplicate publication category")}
        dto.menuPublicationItems.forEach{i->val p=publications[i.publicationId]?:return err(BROKEN_FOREIGN_KEY,"Broken publication item parent");val c=categories[i.publicationCategoryId]?:return err(BROKEN_FOREIGN_KEY,"Broken publication item category");if(c.publicationId!=p.id)return err(RELATIONSHIP_MISMATCH,"Publication item category mismatch");if(i.sourceMenuPlacementId.isBlank()||i.menuRecipeId.isBlank()||i.displayNameSnapshot.isBlank())return err(INVALID_MENU_STRUCTURE,"Invalid publication item");val price=runCatching{BigDecimal(i.sellingPriceSnapshot)}.getOrNull()?:return err(INVALID_NUMERIC_RANGE,"Invalid publication item price");if(price<BigDecimal.ZERO||i.commercialRevision<0||i.consumptionRevision<0||i.sortOrder<0)return err(INVALID_NUMERIC_RANGE,"Invalid publication item numeric value");if(runCatching{com.miara.cuentame.core.model.menu.CashDiscountBehavior.valueOf(i.cashDiscountBehaviorSnapshot)}.isFailure)return err(INVALID_MENU_STRUCTURE,"Invalid publication cash behavior");if(!sourcePlacements.add(i.publicationId to i.sourceMenuPlacementId)||!recipes.add(i.publicationId to i.menuRecipeId))return err(INVALID_MENU_STRUCTURE,"Duplicate publication item")}
        dto.menuPublicationItemComponents.forEach{c->if(items[c.publicationItemId]==null||c.sourceMenuRecipeComponentId.isBlank()||c.ingredientId.isBlank()||c.ingredientUnitOptionId.isBlank())return err(BROKEN_FOREIGN_KEY,"Invalid publication component");val entered=runCatching{BigDecimal(c.quantityEnteredSnapshot)}.getOrNull();val base=runCatching{BigDecimal(c.quantityBaseSnapshot)}.getOrNull();if(entered==null||base==null||entered<=BigDecimal.ZERO||base<=BigDecimal.ZERO||c.sortOrder<0)return err(INVALID_NUMERIC_RANGE,"Invalid publication component quantity");if(!sourceComponents.add(c.publicationItemId to c.sourceMenuRecipeComponentId))return err(INVALID_MENU_STRUCTURE,"Duplicate publication component")}
        return null
    }

    private fun isNumericallyEquivalent(a: String?, b: String?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return try {
            BigDecimal(a).compareTo(BigDecimal(b)) == 0
        } catch (_: Exception) {
            false
        }
    }
}
