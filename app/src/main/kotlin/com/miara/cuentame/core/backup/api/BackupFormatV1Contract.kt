package com.miara.cuentame.core.backup.api

object BackupFormatV1Contract {

    const val BACKUP_FORMAT_VERSION = 1
    const val DATABASE_SCHEMA_VERSION = 3

    val SUPPORTED_RESTORE_DATABASE_SCHEMA_VERSIONS = setOf(2, 3)

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
        "waste_events",
        "inventory_movements",
        "inventory_balance_projections",
        "ingredient_cost_projections",
        "preparation_recipes",
        "preparation_recipe_components"
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
        return if (schemaVersion >= 3) {
            base + setOf("preparation_recipes", "preparation_recipe_components")
        } else {
            base
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
