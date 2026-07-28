# Persisted Data Inventory & Backup Specification (Version 1)

This document specifies the authoritative format and strategy for Cuentame versioned backups (`.cuentame-backup`).

## Archive Specification

The backup is a deterministic ZIP archive. To ensure bit-for-byte identity, entries MUST be written in this exact order:
1. `data/database.json`: Logical snapshot of all 16 Room tables.
2. `preferences/settings.json`: Typed application settings (Theme, Locale, Dynamic Color).
3. `attachments/<attachmentId>/<effectiveDisplayName>`: Binary attachment files, sorted by `attachmentId` ascending.
4. `manifest.json`: Archive metadata, table counts, and bidirectional attachment map.
5. `checksums.json`: SHA-256 hashes for all preceding entries.

## Manifest Validation Rules
* **Format Version**: Must be exactly 1.
* **Database Schema Version**: Current baseline is 2.
* **Timestamp**: Must be canonical ISO-8601 UTC (e.g., `2026-07-27T19:00:00Z`).
* **Localization**: `localeTag` must be a valid BCP 47 tag. `currencyCode` must be a valid ISO 4217 code.
* **Integrity**: Every declared table count must match the actual record count in `database.json`.

## Typed Preferences (`settings.json`)
The preferences section uses a strictly typed DTO:
* `themeMode`: `LIGHT`, `DARK`, or `SYSTEM`.
* `dynamicColorEnabled`: Boolean.
* `appLocaleTag`: `en-US` or `es-US`.
* *Note: `onboardingCompleted` is excluded as it is a device-local transient state.*

## Relational Integrity Policy
The backup is rejected if the internal relational graph is broken:
* **Snaphot Integrity**: 
    - Exactly one restaurant, matching manifest ID/Name/Currency/Locale.
    - No duplicate primary keys.
    - All records must belong to the manifest's `restaurantId`.
    - All foreign keys must resolve within the backup set.
* **Attachment Mapping**:
    - `expectedSet = database.json{attachmentId + type + recordId}`
    - `manifestSet = manifest.attachments{attachmentId + referencedBy{type + recordId}}`
    - Both sets must be EQUAL. No orphans, no missing files, no extra manifest metadata.

## Checksum Rules
* `checksums.json` MUST NOT contain a checksum for itself.
* Duplicate decoded keys in `checksums.json` MUST cause rejection.
* All payload entries must be covered.

## Streaming Safety Limits
* **Archive Entry Count**: Max 1000.
* **JSON Size**: Max 10MB per file.
* **Total Uncompressed Size**: Max 500MB.
* **Attachment Count**: Max 500.

## Error Mapping Policy
Failures are mapped to structured results:
* `DatabaseSnapshotFailure`: Room transaction or query error, or preflight integrity failure.
* `PreferencesReadFailure`: DataStore error.
* `InsufficientStorage`: Platform `ENOSPC` detected via cause chain.
* `ArchiveValidationFailure`: Any integrity, limit, or relationship violation.
* `UnreadableAttachment`: SecurityException or IOException while reading source files.

## Room Migration Policy
* Baseline: Version 1 (Initial schema).
* Version 2: Removed NOT NULL from `ingredient_cost_projection.averageUnitCostBase`.
* Migration 1->2 is implemented via table recreation to preserve data.

## Security Note
Backups contain sensitive restaurant data. The archive is **not encrypted** by the application. Users are responsible for secure storage.
