# Persisted Data Inventory & Backup Specification (Version 1)

This document specifies the authoritative format and strategy for Cuentame versioned backups (`.cuentame-backup`).

## Archive Specification

The backup is a deterministic ZIP archive. To ensure bit-for-byte identity, entries MUST be written in this exact order:
1. `data/database.json`: Logical snapshot of all 16 Room tables, sorted deterministically by primary keys.
2. `preferences/settings.json`: Typed application settings (Theme, Locale, Dynamic Color).
3. `attachments/<attachmentId>/<effectiveDisplayName>`: Binary attachment files, sorted by `attachmentId` ascending.
4. `manifest.json`: Archive metadata, table counts, and bidirectional attachment map.
5. `checksums.json`: SHA-256 hashes for all preceding entries.

## Manifest Validation Rules
* **Format Version**: Must be exactly 1.
* **Database Schema Version**: Authoritative version is defined by `DatabaseSchema.VERSION` (currently 2).
* **Timestamp**: Must be canonical ISO-8601 UTC (e.g., `2026-07-27T19:00:00Z`).
* **Localization**: `localeTag` must be one of the supported locales in `SupportedAppLocale.languageTags` (`en-US`, `es-US`).
* **Currency**: `currencyCode` must be a valid ISO 4217 code.
* **Integrity**: Every declared table count must match the actual record count in `database.json`.
* **Attachment Metadata**: `displayName` is non-null for backup format v1.

## Locale Consistency Rules
* **Supported locales**: Only `en-US` and `es-US` are supported.
* **Single source of truth**: `SupportedAppLocale` enum is the only place the allowed locale set is defined.
* **Preferences/Manifest equality**: `preferences/settings.json#appLocaleTag` must equal `manifest.json#localeTag`.
* **Restaurant/Manifest equality**: `data/database.json#restaurant.localeTag` must equal `manifest.json#localeTag`.
* **Preflight Reconciliation**: Application and database locales are reconciled via `AppLocaleReconciler` before backup planning.

## Typed Preferences (`settings.json`)
The preferences section uses a strictly typed DTO:
* `themeMode`: `LIGHT`, `DARK`, or `SYSTEM`.
* `dynamicColorEnabled`: Boolean.
* `appLocaleTag`: `en-US` or `es-US`.

## Preflight Planning (`BackupCreationPlanner`)
Before opening the destination file for writing, the following checks MUST pass:
1. **Locale Reconciliation**: Ensures Room and DataStore are in sync.
2. **Snapshot Integrity**: Invokes `BackupSnapshotIntegrityValidator`.
3. **Attachment Inspection**: Verifies metadata, sanitized display names, and stream accessibility.
4. **Serialization Limits**: Pre-serializes JSON payloads and enforces `BackupLimits`.
5. **Attachment Limits**: Enforces `MAX_ATTACHMENT_COUNT`.

## Relational Integrity Policy
The backup is rejected if the internal relational graph is broken:
* **Snapshot Integrity**: 
    - Exactly one restaurant, matching manifest ID/Name/Currency/Locale.
    - Non-blank and unique primary keys across all 16 tables.
    - Typed composite keys verified for uniqueness in projection tables.
    - All records must belong to the manifest's `restaurantId`.
    - Foreign keys and unit option ingredient ownership verified.
* **Attachment Mapping**:
    - `expectedSet = database.json{attachmentId + type + recordId}`
    - `manifestSet = manifest.attachments{attachmentId + referencedBy{type + recordId}}`
    - Both sets must be EQUAL. No orphans, no missing files.

## Numeric Validation Policy
All numeric fields are validated as `BigDecimal` strings. Rules by field:
* `units.factorToCanonical`: > 0.
* `ingredient_unit_options.factorToBase`: > 0.
* `purchase_lines.quantityEntered/quantityBase`: > 0 (Zero rejected).
* `purchase_lines.unitCostBase/lineTotal`: >= 0.
* `waste_events.quantityEntered/quantityBase`: > 0 (Zero rejected).
* `stock_count_lines.quantityEntered/quantityBase`: >= 0.
* `stock_count_lines.adjustmentQuantityBase` (optional): May be negative.
* `stock_count_lines.expectedQuantityBaseSnapshot` (optional): May be negative.
* `inventory_movements.quantityBaseSigned`: Non-zero (sign follows direction).
* `inventory_movements.unitCostBaseSnapshot` (optional): >= 0.
* `inventory_balance_projections.quantityBase`: Must match movement sum.

No `Double` or `Float` is used anywhere in backup business logic.

## Document Lifecycle Validation
* **DRAFT** documents must have no movements.
* **POSTED** documents: exactly one movement per line (1:1 bijection). No reversals.
* **VOIDED** documents: exactly one reversal per original movement (1:1 bijection).
* **Source Lines**: No null source line is accepted where a source line is required.

## Reversal Validation Rules
REVERSAL movements must satisfy:
* Points to existing non-REVERSAL movement.
* Identity fields match original (Restaurant, Ingredient, Area, Document, Line).
* `unitCostBaseSnapshot` numeric comparison matches (ignoring scale, e.g. 1.0 == 1.00).
* `effectiveAt >= original.effectiveAt`.
* `quantityBaseSigned` and `totalValueSnapshot` are exact additive inverses of original.
* Parent document status must be `VOIDED`.

## Checksum Rules
* SHA-256 hashes are computed for every entry.
* `checksums.json` contains hashes for all entries except itself.
* Keys are sorted deterministically.

## Destination Writing Strategy
* Destination file is opened ONLY after `BackupCreationPlanner` returns success.
* **Automatic Resource Closure**: `AutoCloseOutputStream` ensures the `ParcelFileDescriptor` is closed.
* **NonCancellable Cleanup**: If creation fails or is cancelled, `BackupCleanupCoordinator` attempts deletion with truncation fallback.

## Schema Version Source
The Room schema version is defined in `DatabaseSchema.VERSION` (currently 2).
The `AndroidAppVersionProvider` and `BackupManifest` reference this constant.

## Error Mapping Policy
Public results use `BackupValidationCode` and `BackupValidationDiagnostic` enums. No raw system data or archive paths are exposed to the UI.
