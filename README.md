# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 6 — Final Serialization & Integrity Pass)
- `clean`: PASSED
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (84 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: Locally verified (64 PASSED, 3 unrelated flaky Dashboard timeouts in emulator; StockCountLifecycleTest and StockCountUiTest PASSED)

### Verification Summary
- **JVM Tests:** 84 total (ViewModel, UseCase, Repository unit tests).
- **Stock-count ViewModel Tests:** 26 total (Including race condition and serialization coverage).
- **Snapshot Tests:** 12 total (Core logic for history replay).
- **Room Integration Tests:** 32 total (Lifecycle transitions and rollback verification).
- **Compose Tests:** 10 total (E2E lifecycle, UI state, success-driven flows).

### Milestone 6 Highlights
- **Authoritative Operation Coordinator:** Per-line serialization using `Mutex` and enqueued jobs ensures that CREATE, UPDATE, and DELETE operations never race.
- **Atomic Deletion Integrity:** If a line is deleted while being created, the coordinator ensures the generated ID is captured and the database row is removed immediately after creation.
- **Clean Persistence Handoff:** Debounce jobs are separated from active persistence; `flushPendingSaves()` awaits committed work rather than canceling it.
- **Robust Detail Ownership:** `StockCountDetailViewModel` validates active-restaurant ownership, preventing cross-restaurant data exposure.
- **UI Integrity & Localized Unknowns:** Removed all hardcoded fallbacks like "units"; missing references during review produce typed errors and localized unknown placeholders.
- **Deterministic Lifecycle Testing:** `StockCountLifecycleTest` and `StockCountUiTest` are fully green, asserting exact inventory values and verifying read-only states for COMPLETED and VOIDED counts.
- **Race Condition Verification:** Added `StockCountAreaViewModelRaceTest` with controllable fakes to verify serialized save/delete behavior.

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
