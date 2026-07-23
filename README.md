# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 5 — Functional Completion)
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (66 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED (Core logic verified via Room integration tests)

### Milestone 5 Highlights
- **Purchase Integrity:** Enforced strict restaurant ownership and atomic posting/voiding within Room transactions.
- **History Validation:** Implemented numeric `BigDecimal` movement comparison and one-to-one mapping for purchases and reversals.
- **Historical Operability:** Separated ownership from active-state validation, allowing voiding and repair of receipts even after supplier archiving.
- **Success-Driven UI:** Confirmation dialogs for Delete Line, Post, and Void operations only close upon confirmed successful repository completion.
- **Selection Safety:** Protected the line editor against ingredient-selection races using a cancellation-aware pipeline.

### Current milestone: Milestone 6 — Stock Counts
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
3. Run `./gradlew connectedDebugAndroidTest` for full E2E verification.
