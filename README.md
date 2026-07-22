# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 4 — Functional Completion)
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED

### Milestone 4 Highlights
- **Authoritative Repository Validation:** All ingredient and unit invariants are enforced in the Room transaction layer.
- **Unit Integrity:** Derived standard-unit factors, enforced base-unit immutability, and atomic default management.
- **Advanced UI:** Search with debounce and normalization, Uncategorized/Archived filtering, and reactive detail screens.
- **Decimal Precision:** Full `BigDecimal` integration from Room entities to UI, ensuring total inventory accuracy.

### Current milestone: Milestone 4
### Next milestone: Milestone 5 — Purchases

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
