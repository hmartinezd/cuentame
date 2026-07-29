# Implementation Plan - Regression Recovery and Backup Pipeline Correctness

This plan outlines the steps to recover deleted test coverage, fix remaining production issues in the backup subsystem, and ensure a robust verification gate for the single-module application.

## User Review Required

> [!IMPORTANT]
> This task involves restoring and adapting a large number of tests. Some tests previously in `androidTest` may be moved to JVM `test` if they are platform-independent to improve execution speed.

> [!WARNING]
> Production code in the backup subsystem will be modified to ensure immutability and correct resource management, which might affect existing callers if any (though currently only `AndroidBackupRepository` uses them).

## Proposed Changes

### Documentation and Status
- Update `README.md`, `docs/STABILIZATION_PROGRESS.md`, and `docs/REFACTOR_PROGRESS.md` with "IN PROGRESS" status.
- Create `docs/TEST_COVERAGE_LEDGER.md` to track test restoration.

### Test Fixtures Cleanup
- Remove `app/src/debug/kotlin/com/miara/cuentame/core/backup/BackupTestFixtures.kt`.
- Create separate `BackupTestFixtures.kt` in `app/src/test/kotlin` and `app/src/androidTest/kotlin`.

### Production Code Fixes
#### Immutable Backup Plan
- Create `ImmutableBackupBytes` to wrap `ByteArray` with defensive copies.
- Update `BackupPlan` to use `ImmutableBackupBytes` and ensure all collections are defensively copied during construction.

#### BackupArchiveWriter Refinement
- Implement `NonClosingOutputStream` to wrap `OutputStream`.
- Ensure `ZipOutputStream` and deflater resources are always released.
- Add defense-in-depth validation for entry limits and checksums during writing.

#### BackupCleanupCoordinator Corrections
- Update `cleanup` logic to attempt both deletion and truncation independently.

#### AndroidBackupDocumentStore Wrapping
- Wrap `openFileDescriptor` failures with `BackupDocumentOpenException` and ensure `ParcelFileDescriptor` is properly managed.

#### Checksum and Attachment Validation
- Enhance `ChecksumParser` to distinguish between parse failures and key-set mismatches.
- Use `AttachmentReferenceKey` (data class) for reference comparisons instead of string concatenation.
- Enforce strict 16-character hex ID format.

### Test Restoration and Expansion
- **JVM Backup Suite**: Restore and adapt all 14 deleted test files, ensuring they work with the new abstractions.
- **Android Product Suite**: Restore or replace all 22 mentioned integration tests, updating them for the single-module structure.
- **Archive Test Builder**: Build a new `valid-by-default` builder that supports ordered entries and duplicate detection.
- **Backup Round Trip**: Add a comprehensive JVM integration test for the full pipeline (Planner -> Writer -> Validator).
- **ViewModel Lifecycle**: Expand tests to cover all `SavedStateHandle` restoration and concurrency scenarios.

## Verification Plan

### Automated Tests
- Run all JVM unit tests: `./gradlew :app:testDebugUnitTest`
- Run all Android instrumentation tests (compilation/assembly only if no device): `./gradlew :app:assembleDebugAndroidTest`
- Run the specific new integration suites.
- Verify lint: `./gradlew :app:lintDebug`

### Manual Verification
- Verify that identical inputs produce bit-identical archives.
- Check that CI workflow successfully runs all steps and fails on violations.
