package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.BackupSnapshotIntegrityCode.*
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.*
import java.math.BigDecimal

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

    // ── public API ───────────────────────────────────────────────────────────────

    fun validate(dto: BackupSnapshotDto, manifest: BackupManifest): Result<Unit> {
        val manifestRestaurantId = manifest.restaurantId
            ?: return fail(RESTAURANT_ID_MISMATCH, "Manifest missing restaurantId")

        validateRestaurant(dto, manifest, manifestRestaurantId)?.let { return Result.failure(it) }

        // Build lookup maps once
        val ctx = ValidationContext(dto, manifestRestaurantId)

        validatePrimaryKeys(dto)?.let { return Result.failure(it) }
        validateIsolation(dto, manifestRestaurantId)?.let { return Result.failure(it) }
        validateForeignKeys(dto, ctx)?.let { return Result.failure(it) }
        validateNumericFields(dto)?.let { return Result.failure(it) }
        validateDocumentTimestamps(dto)?.let { return Result.failure(it) }
        validateMovementGraph(dto, ctx)?.let { return Result.failure(it) }
        validateDocumentLifecycle(dto)?.let { return Result.failure(it) }
        validateBalanceProjections(dto)?.let { return Result.failure(it) }
        validateCostProjections(dto, ctx)?.let { return Result.failure(it) }
        validateRecipes(dto, ctx)?.let { return Result.failure(it) }

        return Result.success(Unit)
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
    }

    // ── sub-validators ────────────────────────────────────────────────────────────

    private fun validateRestaurant(
        dto: BackupSnapshotDto,
        manifest: BackupManifest,
        manifestRestaurantId: String,
    ): BackupSnapshotIntegrityException? {
        if (dto.restaurants.size != 1) {
            return err(INVALID_RESTAURANT_COUNT, "Snapshot must contain exactly one restaurant; found ${dto.restaurants.size}")
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

        // Balance projection composite keys
        val balanceKeys = dto.inventoryBalanceProjections.map { Triple(it.restaurantId, it.ingredientId, it.areaId) }
        if (balanceKeys.any { it.first.isBlank() || it.second.isBlank() || it.third.isBlank() }) {
            return err(BLANK_PRIMARY_KEY, "Blank composite key field in inventory_balance_projections")
        }
        if (balanceKeys.distinct().size != balanceKeys.size) {
            return err(DUPLICATE_COMPOSITE_KEY, "Duplicate composite key in inventory_balance_projections")
        }

        // Cost projection composite keys
        val costKeys = dto.ingredientCostProjections.map { Pair(it.restaurantId, it.ingredientId) }
        if (costKeys.any { it.first.isBlank() || it.second.isBlank() }) {
            return err(BLANK_PRIMARY_KEY, "Blank composite key field in ingredient_cost_projections")
        }
        if (costKeys.distinct().size != costKeys.size) {
            return err(DUPLICATE_COMPOSITE_KEY, "Duplicate composite key in ingredient_cost_projections")
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
        if (dto.inventoryBalanceProjections.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in inventory_balance_projections")
        if (dto.ingredientCostProjections.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in ingredient_cost_projections")
        if (dto.preparationRecipes.any { it.restaurantId != restaurantId }) return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in preparation_recipes")

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

        return null
    }

    private fun validateNumericFields(dto: BackupSnapshotDto): BackupSnapshotIntegrityException? {
        // Units
        for (unit in dto.units) {
            try { UnitDimension.valueOf(unit.dimension) } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for units.dimension")
            }
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

        // Ingredients — optional reorderPointBase
        for (ing in dto.ingredients) {
            if (ing.reorderPointBase != null) {
                val r = parseDecimal(ing.reorderPointBase, "Invalid numeric format in ingredients.reorderPointBase")
                when (r) {
                    is BigDecimalResult.Err -> return err(r.code, r.msg)
                    is BigDecimalResult.Ok -> {
                        if (r.value < BigDecimal.ZERO) return err(INVALID_NUMERIC_RANGE, "ingredients.reorderPointBase must be >= 0")
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
            try { WasteReason.valueOf(waste.reason) } catch (_: IllegalArgumentException) {
                return err(INVALID_ENUM, "Invalid value for waste_events.reason")
            }
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
                        SourceDocumentType.MANUAL -> null
                    }
                    if (parentDocStatus != null
                        && parentDocStatus != DocumentStatus.VOIDED.name
                        && parentDocStatus != StockCountStatus.VOIDED.name) {
                        return err(INVALID_REVERSAL, "REVERSAL movement exists on a non-VOIDED parent document")
                    }
                }
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
            }
        }

        return null
    }

    private fun validateBalanceProjections(dto: BackupSnapshotDto): BackupSnapshotIntegrityException? {
        val computedBalances = mutableMapOf<Pair<String, String>, BigDecimal>()
        val keysWithMovements = mutableSetOf<Pair<String, String>>()

        for (move in dto.inventoryMovements) {
            val key = Pair(move.ingredientId, move.areaId)
            keysWithMovements.add(key)
            val qr = parseDecimal(move.quantityBaseSigned, "Invalid numeric format in inventory_movements.quantityBaseSigned")
            val q = when (qr) {
                is BigDecimalResult.Err -> return err(qr.code, qr.msg)
                is BigDecimalResult.Ok -> qr.value
            }
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
            val actualResult = parseDecimal(proj.quantityBase, "Invalid numeric format in inventory_balance_projections.quantityBase")
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
        for (proj in dto.ingredientCostProjections) {
            // FK to ingredient
            if (!ctx.ingById.containsKey(proj.ingredientId)) {
                return err(BROKEN_FOREIGN_KEY, "Broken FK: ingredient_cost_projection to ingredient")
            }
            // Restaurant isolation
            if (proj.restaurantId != ctx.restaurantId) {
                return err(RESTAURANT_ISOLATION_FAILURE, "Isolation error in ingredient_cost_projections")
            }
            // Optional averageUnitCostBase
            if (proj.averageUnitCostBase != null) {
                val r = parseDecimal(proj.averageUnitCostBase, "Invalid numeric format in ingredient_cost_projections.averageUnitCostBase")
                when (r) {
                    is BigDecimalResult.Err -> return err(r.code, r.msg)
                    is BigDecimalResult.Ok -> {
                        if (r.value < BigDecimal.ZERO) {
                            return err(INVALID_NUMERIC_RANGE, "ingredient_cost_projections.averageUnitCostBase must be >= 0")
                        }
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

            if (status == PreparationRecipeStatus.ACTIVE) {
                if (yieldQtyResult !is NullableDecimalResult.Ok || yieldQtyResult.value <= BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "ACTIVE recipe requires positive standardYieldQuantity")
                }
                if (yieldQtyBaseResult !is NullableDecimalResult.Ok || yieldQtyBaseResult.value <= BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "ACTIVE recipe requires positive standardYieldQuantityBase")
                }
                if (recipe.yieldUnitOptionId == null) return err(INVALID_RECIPE_STRUCTURE, "ACTIVE recipe requires yieldUnitOptionId")
                
                // Base quantity check
                val yieldOpt = ctx.optionById[recipe.yieldUnitOptionId]!!
                val factor = (parseDecimal(yieldOpt.factorToBase, "Invalid factor in yield option") as BigDecimalResult.Ok).value
                if (yieldQtyBaseResult.value.compareTo(yieldQtyResult.value.multiply(factor)) != 0) {
                    return err(INVALID_NUMERIC_RANGE, "standardYieldQuantityBase mismatch in recipe")
                }
            } else if (status == PreparationRecipeStatus.DRAFT) {
                // Draft yield validation: if quantity supplied, must be > 0
                if (yieldQtyResult is NullableDecimalResult.Ok && yieldQtyResult.value <= BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "Draft recipe standardYieldQuantity must be positive if supplied")
                }
                if (yieldQtyBaseResult is NullableDecimalResult.Ok && yieldQtyBaseResult.value <= BigDecimal.ZERO) {
                    return err(INVALID_NUMERIC_RANGE, "Draft recipe standardYieldQuantityBase must be positive if supplied")
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
                components.map { it.componentIngredientId to recipe.outputIngredientId } // component -> output
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
}
