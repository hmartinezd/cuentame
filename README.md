# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 7 — Waste Tracking)
- `clean`: PASSED
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (112 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED (93 tests)

### Verification Summary
- **JVM Tests:** 112 passed (including Waste lifecycle, race conditions, and unit load states).
- **Instrumentation Tests:** 93 passed (Waste, Ingredients, Purchases, Stock Count flows verified).
- **CI verification:** NOT CONFIGURED

### Milestone 7 Highlights
- **Authoritative Integrity:** Verified that `POST`, `VOID`, and `DELETE` operations rollback cleanly on transactional failure, with strict `triggerCount == 1` assertions.
- **Robust UI Synchronization:** Implemented `waitForWasteStatus` and specialized test tags for confirmation dialogs and progress indicators.
- **Production Correction:** Fixed critical navigation defect where DRAFT purchases were routed to read-only detail screens.
- **Archived Reference Persistence:** Verified that archived ingredients, areas, and unit options remain usable in DRAFT states and display localized (Archived) markers.
- **Reactive State Management:** Transitioned Waste unit loading to a typed `UnitOptionsLoadState` flow to prevent races and handle authoritative empty results.
- **Historical Costing:** Quantity and value snapshots are correctly captured and preserved through state transitions.

### Current milestone: Milestone 7 — Waste Tracking (Completed)
### Next milestone: Milestone 8 — Dashboard and Reports

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
