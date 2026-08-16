package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.common.database.DatabaseSchema

object BackupFormatV1Contract {

    const val BACKUP_FORMAT_VERSION = 1
    val DATABASE_SCHEMA_VERSION = DatabaseSchema.VERSION

    val SUPPORTED_RESTORE_DATABASE_SCHEMA_VERSIONS = setOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)

    const val DATABASE_ENTRY = "data/database.json"
    const val PREFERENCES_ENTRY = "preferences/settings.json"
    const val MANIFEST_ENTRY = "manifest.json"
    const val CHECKSUMS_ENTRY = "checksums.json"

    val CORE_ENTRIES: Set<String> = setOf(
        DATABASE_ENTRY,
        PREFERENCES_ENTRY,
        MANIFEST_ENTRY,
        CHECKSUMS_ENTRY
    )

    const val CHECKSUM_ALGORITHM = "SHA-256"

    val REQUIRED_SECTIONS = setOf(
        "data",
        "preferences",
        "attachments"
    )

    val SUPPORTED_ATTACHMENT_RECORD_TYPES = setOf(
        "PURCHASE_RECEIPT",
        "WASTE_EVENT"
    )

    val EXPECTED_TABLES = setOf(
        "restaurants",
        "inventory_areas",
        "ingredient_categories",
        "units",
        "ingredients",
        "ingredient_unit_options",
        "suppliers",
        "purchase_receipts",
        "purchase_lines",
        "stock_counts",
        "stock_count_areas",
        "stock_count_lines",
        "stock_count_item_order",
        "waste_events",
        "inventory_movements",
        "inventory_balance_projections",
        "ingredient_cost_projections",
        "preparation_recipes",
        "preparation_recipe_components",
        "production_batches",
        "production_batch_components",
        "purchase_invoice_ocr_results",
        "purchase_invoice_ocr_pages",
        "purchase_invoice_parse_results",
        "purchase_invoice_parsed_lines",
        "supplier_item_mappings",
        "purchase_invoice_line_matches",
        "purchase_invoice_draft_applications",
        "purchase_invoice_line_origins",
        "menu_recipes",
        "menu_recipe_components",
        "menus",
        "menu_categories",
        "menu_placements"
    )

    fun expectedTablesForSchema(schemaVersion: Int): Set<String> {
        val base = setOf(
            "restaurants",
            "inventory_areas",
            "ingredient_categories",
            "units",
            "ingredients",
            "ingredient_unit_options",
            "suppliers",
            "purchase_receipts",
            "purchase_lines",
            "stock_counts",
            "stock_count_areas",
            "stock_count_lines",
            "waste_events",
            "inventory_movements",
            "inventory_balance_projections",
            "ingredient_cost_projections"
        )
        return when (schemaVersion) {
            2 -> base
            3 -> base + setOf("preparation_recipes", "preparation_recipe_components")
            4 -> base + setOf(
                "preparation_recipes",
                "preparation_recipe_components",
                "production_batches",
                "production_batch_components"
            )
            5 -> base + setOf(
                "preparation_recipes",
                "preparation_recipe_components",
                "production_batches",
                "production_batch_components"
            )
            6 -> base + setOf(
                "preparation_recipes",
                "preparation_recipe_components",
                "production_batches",
                "production_batch_components",
                "purchase_invoice_ocr_results",
                "purchase_invoice_ocr_pages"
            )
            7 -> base + setOf(
                "preparation_recipes",
                "preparation_recipe_components",
                "production_batches",
                "production_batch_components",
                "purchase_invoice_ocr_results",
                "purchase_invoice_ocr_pages",
                "purchase_invoice_parse_results",
                "purchase_invoice_parsed_lines"
            )
            8 -> base + setOf(
                "preparation_recipes",
                "preparation_recipe_components",
                "production_batches",
                "production_batch_components",
                "purchase_invoice_ocr_results",
                "purchase_invoice_ocr_pages",
                "purchase_invoice_parse_results",
                "purchase_invoice_parsed_lines",
                "supplier_item_mappings",
                "purchase_invoice_line_matches"
            )
            9, 10 -> base + setOf(
                "preparation_recipes",
                "preparation_recipe_components",
                "production_batches",
                "production_batch_components",
                "purchase_invoice_ocr_results",
                "purchase_invoice_ocr_pages",
                "purchase_invoice_parse_results",
                "purchase_invoice_parsed_lines",
                "supplier_item_mappings",
                "purchase_invoice_line_matches",
                "purchase_invoice_draft_applications",
                "purchase_invoice_line_origins"
            )
            11 -> expectedTablesForSchema(10) + "stock_count_item_order"
            12, 13 -> expectedTablesForSchema(10) + setOf("stock_count_item_order", "menu_recipes", "menu_recipe_components")
            14 -> expectedTablesForSchema(13) + setOf("menus", "menu_categories", "menu_placements")
            else -> throw IllegalArgumentException("Unsupported schema version: $schemaVersion")
        }
    }

    val DERIVED_TABLES = setOf(
        "inventory_balance_projections",
        "ingredient_cost_projections"
    )

    private val attachmentIdRegex = Regex("^[0-9a-f]{16}$")
    private val checksumRegex = Regex("^[0-9a-f]{64}$")

    fun isValidAttachmentId(value: String): Boolean =
        attachmentIdRegex.matches(value)

    fun isValidChecksum(value: String): Boolean =
        checksumRegex.matches(value)

    fun attachmentArchivePath(attachmentId: String, displayName: String): String =
        "attachments/$attachmentId/$displayName"
}

data class AttachmentReferenceKey(
    val attachmentId: String,
    val recordType: String,
    val recordId: String
)
