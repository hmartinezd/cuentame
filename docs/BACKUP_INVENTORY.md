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
* **Localization**: `localeTag` must be one of the supported locales in `SupportedAppLocales.ALL` (`en-US`, `es-US`).
* **Currency**: `currencyCode` must be a valid ISO 4217 code.
* **Integrity**: Every declared table count must match the actual record count in `database.json`.
* **Attachment Metadata**: `displayName` is non-null for backup format v1.

## Locale Consistency Rules
* **Supported locales**: Only `en-US` and `es-US` are accepted. This is enforced by `SupportedAppLocales.ALL`.
* **Single source of truth**: `SupportedAppLocales` is the only place the allowed locale set is defined. Validation code in `BackupManifestValidator`, `AndroidBackupRepository`, and the UI all reference this constant.
* **Preferences/Manifest equality**: `preferences/settings.json#appLocaleTag` must equal `manifest.json#localeTag`. A backup created with one locale and validated with a different locale is rejected.
* **Restaurant/Manifest equality**: `data/database.json#restaurant.localeTag` must equal `manifest.json#localeTag`.

## Typed Preferences (`settings.json`)
The preferences section uses a strictly typed DTO:
* `themeMode`: `LIGHT`, `DARK`, or `SYSTEM`.
* `dynamicColorEnabled`: Boolean.
* `appLocaleTag`: `en-US` or `es-US`.
* *Note: `onboardingCompleted` is excluded as it is a device-local transient state.*

## Relational Integrity Policy
The backup is rejected if the internal relational graph is broken:
* **Snapshot Integrity**: 
    - Exactly one restaurant, matching manifest ID/Name/Currency/Locale.
    - Non-blank and unique primary keys across all 16 tables.
    - Typed composite keys (`Pair`/`Triple`) verified for uniqueness in projection tables.
    - All records must belong to the manifest's `restaurantId` (resolved directly or transitively).
    - Foreign keys and unit option ingredient ownership verified.
    - Ingredient cost projections: FK to ingredient verified, restaurant isolation enforced.
* **Attachment Mapping**:
    - `expectedSet = database.json{attachmentId + type + recordId}`
    - `manifestSet = manifest.attachments{attachmentId + referencedBy{type + recordId}}`
    - Both sets must be EQUAL. No orphans, no missing files, no extra manifest metadata.

## Numeric Validation Policy
All numeric fields are validated as `BigDecimal` strings. Rules by field:
* `units.factorToCanonical`: Must be parseable and > 0.
* `ingredient_unit_options.factorToBase`: Must be parseable and > 0.
* `ingredients.reorderPointBase` (optional): Must be parseable and >= 0.
* `purchase_lines.quantityEntered/quantityBase/unitCostBase/lineTotal`: Must be parseable and >= 0.
* `stock_count_lines.quantityEntered/quantityBase`: Must be parseable and >= 0.
* `stock_count_lines.adjustmentQuantityBase` (optional): Must be parseable (may be negative).
* `stock_count_lines.expectedQuantityBaseSnapshot` (optional): Must be parseable and >= 0.
* `waste_events.quantityEntered/quantityBase`: Must be parseable and >= 0.
* `inventory_movements.quantityBaseSigned`: Must be parseable (sign follows movement direction).
* `inventory_movements.unitCostBaseSnapshot` (optional): Must be parseable and >= 0.
* `inventory_movements.totalValueSnapshot` (optional): Must be parseable.
* `inventory_balance_projections.quantityBase`: Must be parseable.
* `ingredient_cost_projections.averageUnitCostBase` (optional): Must be parseable and >= 0.

No `Double` or `Float` is used anywhere in backup serialization or validation.

## Document Lifecycle Validation
* **DRAFT** documents must have no movements.
* **POSTED** purchase receipts: exactly one `PURCHASE` movement per line (1:1 by `sourceLineId`). No `REVERSAL` movements.
* **POSTED** waste events: exactly one `WASTE` movement (`sourceLineId == waste.id`). No `REVERSAL` movements.
* **COMPLETED** stock counts: exactly one `OPENING_BALANCE` or `COUNT_ADJUSTMENT` movement per count line. No `REVERSAL` movements.
* **VOIDED** documents: must have matching `PURCHASE`/`WASTE`/`OPENING_BALANCE`/`COUNT_ADJUSTMENT` movements AND exactly one `REVERSAL` per original movement with 1:1 coverage.

## Reversal Validation Rules
REVERSAL movements must satisfy all of the following:
* `reversalOfMovementId` is non-null and points to an existing non-REVERSAL movement.
* `reversalOfMovementId != id` (no self-reversal).
* No two REVERSAL movements may point to the same original.
* Identity fields `restaurantId`, `ingredientId`, `areaId`, `sourceDocumentType`, `sourceDocumentId`, `sourceLineId`, `unitCostBaseSnapshot` must match the original.
* `effectiveAt >= original.effectiveAt`.
* `quantityBaseSigned` must be the exact additive inverse of the original.
* `totalValueSnapshot` nullability must match the original; if both are non-null, the sum must be exactly zero.
* Parent document must be `VOIDED` (not `POSTED`/`COMPLETED`/`DRAFT`).

## Typed Error Codes
Integrity failures are reported via `BackupSnapshotIntegrityException` with a `BackupSnapshotIntegrityCode` enum. Human-readable messages must never include customer data, URIs, or raw JSON. Tests assert on codes, not messages.

## Checksum Rules
* Verified via a custom character-by-character single-pass JSON scanner (non-regex).
* `checksums.json` MUST NOT contain a key for `checksums.json` (self-referential rejection).
* Duplicate keys (including escaped-sequence collisions) cause immediate rejection.
* Checksum key set must equal exactly `archive entries - checksums.json`.

## Streaming Safety Limits (`BackupLimits`)
* **Archive Entry Count**: Max 100 entries.
* **Attachment Count**: Max 50 attachments.
* **Entry Name Length**: Max 255 bytes (UTF-8).
* **JSON Size per file**:
  - `data/database.json`: Max 20MB
  - `manifest.json`: Max 1MB
  - `preferences/settings.json`: Max 100KB
  - `checksums.json`: Max 500KB
* **Total Uncompressed Size**: Max 100MB (includes database, preferences, manifest, checksums, and attachments).

## Destination Writing Strategy
* The destination file is opened via `ContentResolver.openFileDescriptor(uri, "w")` before snapshot loading.
* This means partial bytes are visible at the destination before backup completion.
* **Best-effort cleanup on failure**:
  1. `DocumentsContract.deleteDocument(contentResolver, uri)` — preferred, removes the file entirely.
  2. `contentResolver.openFileDescriptor(uri, "wt")` truncation — fallback if delete is unsupported by the picker.
  3. Both failures are silently swallowed; the original error is always preserved.
* `CancellationException` is never swallowed. Cleanup always runs before re-throwing.

## Insufficient Storage Detection
`isInsufficientStorage` traverses the full cause chain using `generateSequence`. Recognized patterns:
* `IOException.message` contains `"ENOSPC"` (case-insensitive).
* `IOException.message` contains `"No space left on device"` (case-insensitive).
* Cause class is `ErrnoException` with `errno == 28` (POSIX ENOSPC).

## Schema Version Source
The Room schema version is defined in `DatabaseSchema.VERSION` (currently 2).
Both `@Database(version = DatabaseSchema.VERSION)` in `RestaurantInventoryDatabase` and `databaseSchemaVersion` in `AndroidAppVersionProvider` reference this constant. This prevents silent drift.

## Error Mapping Policy
Failures are mapped to structured results:
* `DatabaseSnapshotFailure`: Room transaction or query error, or preflight integrity failure.
* `PreferencesReadFailure`: DataStore error.
* `InsufficientStorage`: Reserved for platform physical storage errors (`ENOSPC` / platform disk errors).
* `ArchiveValidationFailure`: Any archive limit, schema, checksum, or relational integrity violation.
* `UnreadableAttachment`: SecurityException or IOException while reading source files.

## Room Migration Policy
* Baseline: Version 1 (Initial schema).
* Version 2: Removed NOT NULL from `ingredient_cost_projection.averageUnitCostBase`.
* Migration 1→2 is implemented via table recreation to preserve data. `1.json` and `2.json` Room identity hashes differ appropriately.

## Security Note
Backups contain sensitive restaurant data. The archive is **not encrypted** by the application. Users are responsible for secure storage.

## Restore
Restore (reading backup files and importing them into the Room database) is **not yet implemented**. This document describes creation and validation only.
