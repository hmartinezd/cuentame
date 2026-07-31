# Stabilization Progress — Backup and Restore v1

## Status
**COMPLETE**

Backup and Restore v1 is now production-ready for Room database records and typed application preferences. Attachments are explicitly excluded from this version.

## Key Safety Milestones
- [x] **No-Attachment Enforcement**: Creation and restoration are blocked when attachments are present.
- [x] **Transactional Restore**: Database replacement uses Room transactions with foreign key enforcement.
- [x] **Durable Rollback**: Internal rollback snapshot preserves full state including raw paths.
- [x] **Atomic Journaling**: All progress is tracked via `AtomicFile` to survive process death.
- [x] **Startup Recovery**: Automatic crash recovery runs during app startup with global locking.
- [x] **Mutual Exclusion**: Shared mutex prevents concurrent backup, restore, or recovery.
- [x] **Validated Fingerprints**: Restore reinspects archives and compares fingerprints before mutation.
- [x] **Global Recovery Gate**: `RecoveryRequired` state immediately blocks the entire application.
- [x] **Actionable Recovery**: Recovery correctly distinguishes between `COMPLETED` cleanup and partial mutation rollback.
- [x] **Strict Preference Validation**: Rejects unsupported locales and invalid themes before any mutation.
- [x] **Fail-Closed Cleanup**: Verified cleanup ensures no false positives on recovery success.

## Limitations
- Attachments are not backed up or restored.
- Restore requires the current database to have no attachment references.
- Backup requires the current database to have no attachment references.

## Verification
Final JVM suite and instrumentation tests pass. Documentation updated to reflect the final no-attachment scope.
