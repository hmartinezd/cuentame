# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 8 — Dashboard and Reports - Phase 1)
- `clean`: PASSED
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (135 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED (108 tests)

### Milestone 8 Phase 1 (Home Dashboard) Corrections Applied
- **Removed duplicate top bar:** Home uses global app bar, content moved to scrollable dashboard.
- **Fixed SetupRequired:** App-start logic handles redirection; dashboard shows non-interactive explanation if needed.
- **Trend formatting:** Percentage symbols formatted correctly (no double %%).
- **Recent Activity localization:** All status enums and fallback type names converted to localized strings.
- **Data Completeness:** Preserved valued/stocked ingredient counts with authoritative coverage display.
- **Currency formatting:** Using restaurant locale with explicit invalid-code fallback (e.g. "XYZ 100.00").
- **Date/time formatting:** Using localized DateTimeFormatter with restaurant locale and system zone.
- **HomeViewModel tests:** Expanded to cover stale-flow cancellation, repository failures, and empty data scenarios.
- **Home instrumentation tests:** Enhanced with real data seeding and actual value assertions.
- **DAO ordering tests:** Added Room tests for Waste and Stock Count deterministic ordering.
- **Accessibility:** Added combined semantics for cards and activity items.

### Verification Status
- **Home ViewModel tests:** PASSED
- **Formatters tests:** PASSED
- **Home instrumentation tests:** PASSED
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


