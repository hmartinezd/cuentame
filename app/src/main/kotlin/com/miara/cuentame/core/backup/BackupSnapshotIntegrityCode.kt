package com.miara.cuentame.core.backup

/**
 * Stable programmatic error codes for [BackupSnapshotIntegrityValidator].
 * Tests must assert on these codes, not on human-readable messages.
 */
enum class BackupSnapshotIntegrityCode {
    // Restaurant consistency
    INVALID_RESTAURANT_COUNT,
    RESTAURANT_ID_MISMATCH,
    RESTAURANT_NAME_MISMATCH,
    RESTAURANT_LOCALE_MISMATCH,
    RESTAURANT_CURRENCY_MISMATCH,

    // Primary key integrity
    BLANK_PRIMARY_KEY,
    DUPLICATE_PRIMARY_KEY,
    DUPLICATE_COMPOSITE_KEY,

    // Isolation
    RESTAURANT_ISOLATION_FAILURE,

    // Relational integrity
    BROKEN_FOREIGN_KEY,
    RELATIONSHIP_MISMATCH,

    // Enum and numeric validation
    INVALID_ENUM,
    INVALID_DECIMAL,
    INVALID_NUMERIC_RANGE,
    INVALID_TIMESTAMP_ORDER,

    // Document lifecycle
    INVALID_DOCUMENT_LIFECYCLE,

    // Movement graph
    INVALID_MOVEMENT_GRAPH,

    // Reversals
    INVALID_REVERSAL,

    // Projections
    INVALID_BALANCE_PROJECTION,
    INVALID_COST_PROJECTION,

    // Recipes
    INVALID_RECIPE_STATUS,
    INVALID_RECIPE_STRUCTURE,
    INVALID_RECIPE_GRAPH,

    // Production
    INVALID_PRODUCTION_COST_HISTORY,
    DUPLICATE_PRODUCTION_COMPONENT,

    // Versioning
    UNSUPPORTED_VERSION,

    // Matching and mappings
    INVALID_MATCH_STATUS,

    // Menu costing
    INVALID_MENU_STRUCTURE,
}
