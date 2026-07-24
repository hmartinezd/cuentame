# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 6 — Final Integrity Pass)
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (80 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: Locally verified (64 PASSED, 3 unrelated flaky UI tests; StockCountLifecycleTest and integration logic PASSED)

### Verification Summary
- **JVM Tests:** 80 total (ViewModel, UseCase, Repository unit tests).
- **ViewModel Tests:** 24 total (Area, Detail, Start flows with race/failure coverage).
- **Room Integration Tests:** 32 total (Snapshot, Repository transitions, Rollback validation).
- **Compose Tests:** 8 total (Start, Lifecycle, Success-driven deletion).

### Milestone 6 Highlights
- **Success-Driven Deletion:** Deletion from areas is serialized with autosave and tracks explicit operation state to prevent stale data restoration.
- **Autosave & Flush:** Revision-based serialization ensuring rapid edits create exactly one line and `flushPendingSaves()` awaits active operations.
- **Route Ownership:** All routes validate active-restaurant ownership, rejecting cross-restaurant or malformed count/area links.
- **Authoritative History:** Completed and Voided states use immutable snapshots; decimal parsing is strictly wrapped and validated against `MalformedStockCountMovementHistory`.
- **Reversal Integrity:** Full structural validation for reversals including source-line parity, negative quantity/total matching, and chronological consistency.
- **UI Integrity:** Selectable unit options exclude archived choices after change; Adjustment Review includes missing active item and archived balance warnings.
- **UTC-Safe Dates:** Material DatePicker results are handled via UTC to prevent timezone-based date shifts in historical records.

### Current milestone: Milestone 6 — Stock Counts (Completed)
### Next milestone: Milestone 7 — Waste Tracking

## Tech Stack
- **UI:** Jetpack Compose with Material 3.
- **Architecture:** Clean Architecture with Hilt for DI and Coroutines/Flow for reactivity.
- **Persistence:** Room (Business Data) and Preferences DataStore (User settings/drafts).
- **ID Strategy:** Client-generated UUIDs (@JvmInline value classes).
- **Precision:** `BigDecimal` for all quantities and costs.

## Resetting App Data
To re-run onboarding, clear the application storage via Android Settings.

## Development Setup
1. Open in Android Studio Ladybug or newer.
2. Run `./gradlew assembleDebug` to verify build.
3. Run `./gradlew testDebugUnitTest` for JVM tests.
4. Run `./gradlew connectedDebugAndroidTest` for integration and E2E verification.
