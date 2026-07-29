# Refactor Progress Tracking

## Goal
Consolidate the application into a single `:app` module while preserving Clean Architecture layers, ensuring production-grade backup integrity, and maintaining full regression test coverage.

## Phase Checklist

- [ ] **Phase 0 — Establish Baseline & Inventory** `IN PROGRESS`
- [ ] **Phase 1 — Shared Locale Definition & Reconciliation** `IN PROGRESS`
- [ ] **Phase 2 — Decompose Backup Subsystem** `IN PROGRESS`
- [ ] **Phase 3 — Correct & Decompose Snapshot Validation** `IN PROGRESS`
- [ ] **Phase 4 — Rebuild Backup Test Architecture** `IN PROGRESS`
- [ ] **Phase 5 — Lifecycle-Safe File Picker Workflow** `IN PROGRESS`
- [ ] **Phase 6 — Single-Module Consolidation** `IN PROGRESS`
- [ ] **Phase 7 — Package-Level Boundary Enforcement** `IN PROGRESS`
- [ ] **Phase 8 — Future Multi-Module Migration** `POSTPONED`

---

## Detailed Status

### Phase 0-5 — Stabilization & Corrections
- **Status**: `IN PROGRESS`
- **Actions**:
  - Refactored `AndroidBackupRepository` to use `BackupCreationPlanner`, `BackupSnapshotSource`, and `BackupDocumentStore`.
  - Implemented `SavedPickerLaunchState` in `BackupViewModel` for process-death resilience.
  - Extracted `BackupArchiveWriter` and `BackupArchiveValidator`.
  - **RECOVERY REQUIRED**: Restoring deleted test suites and strengthening defensive validations.

### Phase 6 — Single-Module Consolidation
- **Status**: `IN PROGRESS`
- **Actions**:
  - Removed empty Gradle submodule scaffolding (`core/*`, `feature/*`) and `build-logic`.
  - Consolidated all source code into `:app`.

### Phase 7 — Package-Level Boundary Enforcement
- **Status**: `IN PROGRESS`
- **Actions**:
  - Repaired `ArchitectureTest.kt` for single-module structure.
  - **NEXT**: Strengthening rules to detect cross-layer violations.
