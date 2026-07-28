# Persisted Data Inventory & Backup Specification (Version 1)

This document specifies the authoritative format and strategy for Cuentame versioned backups (`.cuentame-backup`).

## Archive Specification

The backup is a deterministic ZIP archive with the following entry set and order:
1. `data/database.json`: Logical snapshot of all 16 Room tables.
2. `preferences/settings.json`: Key application settings (Theme, Locale, Dynamic Color).
3. `attachments/<attachmentId>/<sanitizedFilename>`: Binary attachment files.
4. `manifest.json`: Metadata, table counts, and attachment relationship map.
5. `checksums.json`: SHA-256 hashes for all payload entries.

## Manifest Validation Rules
* **Format Version**: Must be exactly 1.
* **Timestamp**: Must be canonical ISO-8601 UTC.
* **Metadata**: All identifiers (Application ID, Restaurant ID) and version strings must be non-blank.
* **Localization**: `localeTag` must be a valid BCP 47 tag (e.g., `en-US`). `currencyCode` must be a valid ISO 4217 code.
* **Sections**: Must contain exactly `data`, `preferences`, and `attachments`.

## Table Schema Metadata
The manifest must declare metadata for all 16 tables. `isDerived` must be true ONLY for `inventory_balance_projections` and `ingredient_cost_projections`.

## Checksum Rules
* Every file in the archive (except `checksums.json`) must have a corresponding SHA-256 entry.
* Duplicate keys in `checksums.json` are strictly forbidden and cause validation failure.
* Checksums are verified via streaming to ensure memory efficiency.

## Attachment Relationship Policy
* **Portability**: Device-specific URIs are never stored. Attachments are referenced by a logical `attachmentId`.
* **Bidirectional Integrity**: 
    - Every logical ID in `database.json` must exist in the manifest.
    - Every manifest attachment must have at least one valid record reference in `database.json`.
* **Sanitization**: Filenames are sanitized to remove path separators and control characters. Invalid names are rejected during validation.

## Streaming Safety Limits
* **Archive Entry Count**: Max 1000
* **Attachment Count**: Max 500
* **JSON Size**: Max 10MB per file
* **Total Uncompressed Size**: Max 500MB
* **Entry Name Length**: Max 255 characters

## Data Isolation & Security
* **Isolation**: All backups are strictly scoped to the active restaurant. No data from other restaurant profiles is included.
* **Confidentiality**: Backups contain sensitive operational data (prices, costs, contacts). The archive is NOT encrypted by the application. Users must store the file securely.

## Restore & Export Status
* **Restore**: NOT IMPLEMENTED (Phase 2).
* **CSV Export**: NOT IMPLEMENTED (Phase 3).
