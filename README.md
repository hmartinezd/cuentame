# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 5 — Functional Completion)
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED

### Milestone 5 Highlights
- **Purchase Integrity:** Enforced restaurant ownership and atomic posting/voiding within Room transactions.
- **History Validation:** Implemented strict one-to-one movement mapping with deterministic operation IDs.
- **Success-Driven UI:** Confirmation dialogs for Post, Void, and Delete operations remain open until background processing succeeds.
- **Centralized Calculation:** Shared `PurchaseLineCalculator` ensures consistent `BigDecimal` math between repository and UI previews.

### Current milestone: Milestone 5
### Next milestone: Milestone 6 — Stock Counts

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
3. Run `./gradlew connectedDebugAndroidTest` for full E2E verification.
