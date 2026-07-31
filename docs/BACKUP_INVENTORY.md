# Persisted Data Inventory & Backup Specification (Version 1)

This document specifies the authoritative format and strategy for Cuentame versioned backups (`.cuentame-backup`).

## Backup and Restore v1 Status

**Backup and Restore v1 supports Room database records and typed application preferences only.**

* **Room database tables**: COMPLETE (16 tables)
* **Typed preferences**: COMPLETE (Theme, Locale, Dynamic Color)
* **Backup Creation**: COMPLETE
* **Backup Inspection**: COMPLETE
* **Restore Application**: COMPLETE
* **Rollback & Recovery**: COMPLETE
* **Attachments**: UNSUPPORTED in v1.

## No-Attachment Policy

Backup and Restore v1 does not support attachment files. To ensure data safety and prevent inconsistent states:
1. **Backup Creation**: Blocked if any database records contain non-null attachment references.
2. **Restore Application**: Blocked if the selected backup archive contains any attachment metadata or files.
3. **Current Data Protection**: Restore is blocked if the current live database contains attachment references, preventing the orphaning of existing files.
4. **Zero Mutation**: No attachment files are copied, moved, staged, installed, or deleted by the backup/restore system.

## Restore Orchestration

### Mutual Exclusion
A global singleton `RestoreOperationGate` provides a `Mutex` that synchronizes:
* Startup recovery
* Backup creation
* Restore application
* Manual recovery retries

### Transactional Room Replacement
Database restoration is performed within a single `RoomDatabase.withTransaction` block:
1. Deletion occurs in child-to-parent order to satisfy foreign key constraints.
2. Insertion occurs in parent-to-child order.
3. Row counts and restaurant identity are verified before commit.
4. Final verification ensures the new database state matches the backup snapshot exactly.

### Internal Rollback Model
During restoration, the system captures a full `RestoreDatabaseRollbackSnapshot` and the current `BackupPreferencesDto`. These are persisted atomically to private storage before any mutation begins.
* The rollback snapshot preserves **raw local attachment paths**, ensuring they can be restored exactly if an operation fails.

### Atomic Journaling
A durable `RestoreJournalDto` tracks the progress of the restoration using an `AtomicFile`. 
* **Phase Ordering**: `ROLLBACK_CAPTURED` -> `MUTATION_STARTED` -> `DATABASE_APPLIED` -> `PREFERENCES_APPLIED` -> `COMPLETED`.
* **Previous Preferences**: The journal persists the exact previous preferences to ensure they can be restored during crash recovery.

### Startup Recovery
The `RestoreRecoveryBootstrapper` runs during application startup. If a previous restoration was interrupted:
1. It acquires the global operation lock.
2. It restores the database and preferences from the rollback evidence.
3. it verifies the restored state before cleaning up.
4. It blocks further operations if recovery fails and enters a `RecoveryRequired` state.

## Archive Specification

The backup is a deterministic ZIP archive. To ensure bit-for-byte identity, entries MUST be written in this exact order:
1. `data/database.json`: Logical snapshot of all 16 Room tables, sorted deterministically by primary keys.
2. `preferences/settings.json`: Typed application settings (Theme, Locale, Dynamic Color).
3. `manifest.json`: Archive metadata, table counts, and bidirectional attachment map.
4. `checksums.json`: SHA-256 hashes for all preceding entries.

## Numeric Validation Policy
All numeric fields are validated as `BigDecimal` strings. No `Double` or `Float` is used anywhere in backup business logic.

## Checksum Rules
* SHA-256 hashes are computed for every entry.
* `checksums.json` contains hashes for all entries except itself.
* Keys are sorted deterministically.

## Error Mapping Policy
Public results use `BackupRestoreFailure` and related enums. No raw system data, SQL, or archive paths are exposed to the UI.
