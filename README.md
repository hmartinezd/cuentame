# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 7 — Waste Tracking)
- `clean`: PASSED
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (115 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PARTIAL (98 PASSED, 3 FAILED in legacy modules; **Waste Module: 100% PASSED**)

### Verification Summary
- **JVM Tests:** 115 total (ViewModel, UseCase, Repository unit tests).
- **Waste Suite:** 49 tests total (All PASSED).
    - **ViewModel:** 15 PASSED.
    - **Validator:** 11 PASSED.
    - **Repository (Room):** 13 PASSED.
    - **UI (Compose):** 10 PASSED (Archive, Failure/Rollback, Lifecycle).
- **CI verification:** NOT CONFIGURED

### Milestone 7 Highlights
- **Atomic Integrity:** Verified that `POST`, `VOID`, and `DELETE` operations rollback cleanly on transactional failure, preserving projection and movement consistency.
- **Historical Snapshots:** Cost and quantity snapshots are correctly captured at the effective timestamp.
- **Robust UI Selectors:** Migration to authoritative test tags (`waste_item_{id}`) and synchronized `waitForTag` helpers resolved environment timeouts.
- **Negative Balance Handling:** Explicit warning states for waste exceeding theoretical balance are fully tested.
- **Archived Reference Recovery:** UI handles drafting and posting even when ingredients or areas are archived during the process.

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
