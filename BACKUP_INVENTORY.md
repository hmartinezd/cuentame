# Backup and Restore v1 Inventory

## Overview
Status: **Production Ready**
Version: 1.0.0
Implementation Date: January 2026

## Core Components
| Component | Responsibility | Status |
| :--- | :--- | :--- |
| `BackupArchiveProcessor` | Shared ZIP streaming, checksum calculation, and limit enforcement. | DONE |
| `BackupArchiveFingerprinter` | Stable logical identity for archives (Manifest + Checksums). | DONE |
| `BackupRestoreCoordinator` | Top-level orchestration of the 24-step restore sequence. | DONE |
| `RestoreJournal` | Persistent state tracking for crash recovery. | DONE |
| `RestoreRecoveryCoordinator` | Startup logic to clean or rollback interrupted operations. | DONE |
| `RestoreDatabaseApplier` | Transactional database replacement (Room). | DONE |
| `RestoreAttachmentInstaller` | Atomic directory swaps for media files. | DONE |
| `RestorePreferencesApplier` | DataStore configuration updates. | DONE |

## Safety & Security
- **Deterministic ZIP Order**: Database -> Settings -> Attachments -> Manifest -> Checksums.
- **Fingerprint Revalidation**: Application re-scans the archive and confirms the fingerprint matches the preview before starting mutation.
- **Transactional DB**: Child-to-Parent deletion, Parent-to-Child insertion.
- **Rollback**: Captures full DB snapshot and attachment state before mutation; reverts on any error.
- **Process Death Recovery**: Journaled state allows cleaning staging or finishing rollback on app restart.
- **Limits**: Enforced max entry counts, JSON sizes, attachment sizes, and total uncompressed bytes.

## Testing Coverage
- **Unit Tests**: 442 tests passing.
- **Race Condition Tests**: Verified concurrent file selection and operation cancellation in ViewModel.
- **Integrity Tests**: Verified manifest validation, checksum mismatches, and fingerprint stability.
- **Architecture**: No violations of core/feature boundaries.

## Documentation
- Archive format contract defined in `BackupFormatV1Contract.kt`.
- Table processing order defined in `RestoreDao.kt` and `RoomRestoreDatabaseApplier.kt`.
- Failure mappings defined in `RestoreErrorMapper.kt`.
