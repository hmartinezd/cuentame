# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 8 — Dashboard and Reports - Phase 1)
- `clean`: PASSED
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (150 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED (117 tests)

### Milestone 8 Phase 1 (Reports Overview) Implementation
- **Reports Overview Screen:** Vertically scrollable dashboard with sections for Inventory, Purchasing, Waste, Operational Alerts, and Stock-count summary.
- **Header Summary:** Reports header now displays the selected reporting range (e.g., "Last 30 days").
- **Authoritative Metrics:** Utilizes `DashboardRepository` for all reporting formulas, ensuring data parity with the Home Dashboard.
- **BigDecimal Integrity:** All comparison logic and aggregations use scale-independent numeric comparisons.
- **Accessibility & Localization:** Full semantic descriptions for all sections and items. Native support for English and Spanish locales.
- **Deterministic Seeding:** Integration tests use a centralized seeding strategy with `Instant.now()` boundaries.
- **Verification Suite:** 100% pass rate on 150 unit tests and 117 instrumentation tests.

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


