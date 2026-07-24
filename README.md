# Cuentame Inventory

A local-first restaurant inventory application built with modern Android practices.

## Current Status (Milestone 6 — Final Integrity Pass)
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (80 tests)
- `connectedDebugAndroidTest`: Locally verified (Core logic and lifecycle verified via Room integration tests; some UI flakiness in emulator)

### Milestone 6 Highlights
- **Historical Snapshot Hardening:** `InventorySnapshotService` is restaurant-scoped with authoritative movement replay and reversal validation.
- **Cost Integrity:** Historical costs are tracked strictly; no "zero" cost is invented if priced history is missing.
- **Stock Count Lifecycle:** Atomic DRAFT -> COMPLETED -> VOIDED transitions with full graph re-validation during posting.
- **Autosave with Integrity:** Revision-based latest-write-wins autosave for count lines with flush-before-completion and back-navigation protection.
- **Candidate Suggestions:** Enhanced area-counting with suggested items separated from dirty lines, and persistent missing candidate tracking.
- **UI Enhancements:** Selectable count units, UTC-safe date selection, and success-driven dialog lifecycles for completion, void, and deletion.
- **Auditability:** Authoritative historical displays for completed and voided areas using persisted snapshots instead of recomputed ledger views.

### Current milestone: Milestone 6 — Stock Counts (Completed)
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
