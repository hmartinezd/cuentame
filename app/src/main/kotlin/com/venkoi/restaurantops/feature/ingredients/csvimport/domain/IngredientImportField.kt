package com.venkoi.restaurantops.feature.ingredients.csvimport.domain

enum class IngredientImportField(
    val canonicalHeader: String,
    val required: Boolean = false,
    val aliases: Set<String> = emptySet()
) {
    INGREDIENT_NAME("ingredient_name", true, setOf("name", "ingredient", "item", "item_name", "product", "product_name")),
    SKU("sku"),
    CATEGORY("category", aliases = setOf("department", "group")),
    BASE_UNIT("base_unit", true, setOf("unit", "uom", "base_uom", "unit_of_measure")),
    COUNT_UNIT("count_unit", aliases = setOf("count_uom", "inventory_unit")),
    PURCHASE_PACKAGE("purchase_package", aliases = setOf("package", "pack")),
    PACKAGE_CONVERSION_FACTOR("package_conversion_factor", aliases = setOf("conversion", "pack_qty", "pack_size", "case_qty", "units_per_case")),
    DEFAULT_AREA("default_area", aliases = setOf("area", "storage", "location")),
    SUPPLIER("supplier", aliases = setOf("vendor", "distributor")),
    VENDOR_ITEM_CODE("vendor_item_code", aliases = setOf("vendor_code", "supplier_item_code", "item_code", "item_number")),
    CURRENT_COST_PER_BASE_UNIT("current_cost_per_base_unit", aliases = setOf("current_cost", "unit_cost", "cost")),
    REORDER_POINT_BASE("reorder_point_base", aliases = setOf("reorder_point", "min_stock"));

    val acceptedHeaders: Set<String> get() = aliases + canonicalHeader
}

data class CsvSourceColumn(val index: Int, val header: String)

data class CsvSourceTable(
    val columns: List<CsvSourceColumn>,
    val rows: List<List<String>>
) {
    fun samples(columnIndex: Int, limit: Int = 3): List<String> = rows.asSequence()
        .map { it.getOrElse(columnIndex) { "" }.trim() }
        .filter(String::isNotEmpty)
        .distinct()
        .take(limit)
        .toList()
}

data class IngredientColumnMapping(val sourceToTarget: Map<Int, IngredientImportField?>) {
    val missingRequiredFields: Set<IngredientImportField>
        get() = IngredientImportField.entries.filterTo(linkedSetOf()) { it.required && it !in sourceToTarget.values }

    val hasDuplicateTargets: Boolean
        get() = sourceToTarget.values.filterNotNull().groupingBy { it }.eachCount().any { it.value > 1 }

    val isValid: Boolean get() = missingRequiredFields.isEmpty() && !hasDuplicateTargets
}

object IngredientColumnMapper {
    fun suggest(table: CsvSourceTable): IngredientColumnMapping {
        val claimed = mutableSetOf<IngredientImportField>()
        val mappings = table.columns.associate { column ->
            val normalized = normalizeHeader(column.header)
            val target = IngredientImportField.entries.firstOrNull {
                normalized in it.acceptedHeaders && it !in claimed
            }
            if (target != null) claimed += target
            column.index to target
        }
        return IngredientColumnMapping(mappings)
    }

    fun update(mapping: IngredientColumnMapping, sourceIndex: Int, target: IngredientImportField?): IngredientColumnMapping {
        val cleared = if (target == null) mapping.sourceToTarget else mapping.sourceToTarget.mapValues { (index, value) ->
            if (index != sourceIndex && value == target) null else value
        }
        return IngredientColumnMapping(cleared + (sourceIndex to target))
    }

    fun toCanonicalRows(table: CsvSourceTable, mapping: IngredientColumnMapping): List<Map<String, String>> {
        require(mapping.isValid) { "Required fields must be mapped and targets must be unique" }
        return table.rows.map { sourceRow ->
            IngredientImportField.entries.associate { field ->
                val sourceIndex = mapping.sourceToTarget.entries.firstOrNull { it.value == field }?.key
                field.canonicalHeader to if (sourceIndex == null) "" else sourceRow.getOrElse(sourceIndex) { "" }
            }
        }
    }

    internal fun normalizeHeader(value: String): String = value.trim().lowercase().replace("#", " number ")
        .replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
