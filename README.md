# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 8 — Dashboard and Reports - Phase 1)
- `clean`: PASSED
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (146 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED (116 tests)

### Milestone 8 Phase 1 (Reports Overview) Implementation
- **Reports Overview Screen:** Implemented as a vertically scrollable layout with sections for Inventory, Purchasing, Waste, Operational Alerts, and Stock-Count summary.
- **Data Consistency:** Utilizes authoritative `DashboardRepository` for all metrics, matching Home Dashboard formulas.
- **ViewModel Implementation:** Supports date-range filtering (7/30/90 days) with robust cancellation of stale emissions.
- **Accessibility & Localization:** Full English/Spanish support with combined semantic descriptions for KPI cards and section headers.
- **Verification Suite:** Added focused JVM, Compose, and Integration tests covering all reporting states and range switching.
- **Home Dashboard Refinement:** Corrected accessibility semantics and extended integration coverage with authoritative assertions.

### Verification Status
- **Home ViewModel tests:** PASSED
- **Reports ViewModel tests:** PASSED
- **Formatters tests:** PASSED
- **Home instrumentation tests:** PASSED
- **Reports instrumentation tests:** PASSED
- **Dashboard DAO Room tests:** PASSED
- **Full JVM suite:** PASSED
- **assembleDebug:** PASSED
- **lintDebug:** PASSED
- **Full instrumentation suite:** PASSED

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


