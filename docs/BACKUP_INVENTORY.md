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
* **Database Schema Version**: Current baseline is 2.
* **Timestamp**: Must be canonical ISO-8601 UTC (e.g., `2026-07-27T19:00:00Z`).
* **Localization**: `localeTag` must be a valid BCP 47 tag. `currencyCode` must be a valid ISO 4217 code.
* **Integrity**: Every declared table count must match the actual record count in `database.json`.
* **Attachment Metadata**: `displayName` is non-null for backup format v1.

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
* **Attachment Mapping**:
    - `expectedSet = database.json{attachmentId + type + recordId}`
    - `manifestSet = manifest.attachments{attachmentId + referencedBy{type + recordId}}`
    - Both sets must be EQUAL. No orphans, no missing files, no extra manifest metadata.

## Checksum Rules
* Verified via a custom character-by-character single-pass JSON scanner (non-regex).
* `checksums.json` MUST NOT contain a key for `checksums.json` (self-referential rejection).
* Duplicate keys (including escaped-sequence collisions) cause immediate rejection.
* Checksum key set must equal exactly `archive entries - checksums.json`.

## Streaming Safety Limits (`BackupLimits`)
* **Archive Entry Count**: Max 100 entries.
* **Attachment Count**: Max 50 attachments.
* **Entry Name Length**: Max 255 characters.
* **JSON Size**: Max 10MB per file.
* **Total Uncompressed Size**: Max 100MB (includes database, preferences, manifest, checksums, and attachments).

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
* Migration 1->2 is implemented via table recreation to preserve data. `1.json` and `2.json` Room identity hashes differ appropriately.

## Security Note
Backups contain sensitive restaurant data. The archive is **not encrypted** by the application. Users are responsible for secure storage.
