# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 6 — Historical Snapshot, Autosave and Completion Integrity)
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (66 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED (Core logic verified via Room and Snapshot integration tests)

### Milestone 6 Highlights
- **Historical Snapshot Hardening:** `InventorySnapshotService` is now restaurant-scoped with authoritative movement replay and reversal validation.
- **Cost Integrity:** Historical costs are tracked strictly; no "zero" cost is invented if priced history is missing.
- **Stock Count Lifecycle:** Atomic DRAFT -> COMPLETED -> VOIDED transitions with full graph re-validation during posting.
- **Autosave with Integrity:** Revision-based latest-write-wins autosave for count lines with flush-before-completion protection.
- **Candidate Suggestions:** Enhanced area-counting with suggested active items, missing candidate tracking, and archived-balance warnings.
- **UI Enhancements:** Selectable count units, effective date/time selection, and full adjustment review before posting.

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
3. Run `./gradlew connectedDebugAndroidTest` for integration and E2E verification.
