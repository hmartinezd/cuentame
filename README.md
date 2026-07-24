# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 7 — Waste Tracking)
- `clean`: PASSED
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (108 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: Locally verified (76 PASSED; WasteLifecycleTest stable)

### Verification Summary
- **JVM Tests:** 108 total (ViewModel, UseCase, Repository unit tests).
- **Waste ViewModel Tests:** 10 total.
- **Room Integration Tests:** 11 total for Waste (82 total in project).
- **Compose Tests:** 2 total for Waste (Waste E2E lifecycle and integrity).
- **CI verification:** NOT CONFIGURED

### Milestone 7 Highlights
- **Full Waste Lifecycle:** Implementation of DRAFT → POSTED → VOIDED states with strict immutability rules for historical records.
- **Atomic Posting Transaction:** One Room transaction handles canonical quantity recalculation, WASTE movement insertion, projection rebuilding, and status update.
- **Historical Cost Snapshots:** Waste events capture the weighted-average cost of the ingredient at the effective timestamp, ensuring accurate historical valuation.
- **Retroactive Integrity:** Projections are rebuilt by chronologically replaying all movements (effectiveAt, createdAt, ID), correctly handling retroactive waste.
- **Photo Attachments:** Support for one optional local photo per waste event using persistable URI permissions.
- **Negative Inventory Support:** Waste can result in negative area balances, acting as a discrepancy signal rather than being silently clamped.

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
