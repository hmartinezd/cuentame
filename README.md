# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 8 — Dashboard and Reports - Phase 1)
- `clean`: PASSED
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (154 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED (119 tests)

### Milestone 8 Phase 1 (Home Dashboard and Reports Overview) Closure
- **Inventory Semantics:** Accessibility strings now include current value, valued/stocked ratio, coverage percentage, and missing-cost count.
- **BigDecimal Comparison:** Fixed comparison logic to be scale-independent using `compareTo()`, ensuring `0.00` is equivalent to `0` for trend mapping.
- **Reports Header:** Displays the active reporting range summary (e.g., "Last 30 days").
- **Seeding & Fixtures:** Expanded `ReportsUiTest` with exhaustive Room seeding for Inventory, Purchases, Waste (historical snapshot validation), and Stock Counts.
- **Navigation:** Verified reliable navigation from Home Dashboard to Reports and correct system Back behavior.
- **Range Switching:** Authoritative verification of metric updates when switching between 7, 30, and 90-day periods.

### Verification Status
- **Current milestone:** Milestone 8 — Dashboard and Reports
- **Phase 1 status:** PASSED
- **Home Dashboard:** PASSED
- **Reports Overview:** PASSED
- **Next phase:** NOT STARTED — awaiting definition
- **CI verification:** NOT CONFIGURED

### Milestone 7 Highlights (Completed)
- **Authoritative Integrity:** Verified that `POST`, `VOID`, and `DELETE` operations rollback cleanly on transactional failure, with strict `triggerCount == 1` assertions.
- **Robust UI Synchronization:** Implemented `waitForWasteStatus` and specialized test tags for confirmation dialogs and progress indicators.
- **Production Correction:** Fixed critical navigation defect where DRAFT purchases were routed to read-only detail screens.
- **Archived Reference Persistence:** Verified that archived ingredients, areas, and unit options remain usable in DRAFT states and display localized (Archived) markers.
- **Reactive State Management:** Transitioned Waste unit loading to a typed `UnitOptionsLoadState` flow to prevent races and handle authoritative empty results.
- **Historical Costing:** Quantity and value snapshots are correctly captured and preserved through state transitions.

### Next Steps
- Complete compilation and test verification
- Begin Milestone 8 Phase 2 (Reports Overview)
- Implement Reports ViewModel and UI
- Complete Reports integration and testing

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


