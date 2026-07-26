# Milestone 7 — Final Test Integrity and Remaining-Failures Pass

This plan outlines the final verification pass for Milestone 7 (Waste Tracking). It focuses on making instrumentation tests authoritative, ensuring complete archived-reference coverage, and resolving the legacy suite regressions identified during suite integration.

## Autonomous Push (Unattended)
The system will now proceed with execution without further user prompts to complete the following:
- Fix production layout conflicts (nested scrolling in Purchases) that break test clickability.
- Synchronize all legacy tests with updated Milestone 7 components.
- Perform a clean-room full suite verification.

## Proposed Changes
### Component: feature-purchases (UI & Navigation)
#### [MODIFY] [PurchaseDraftScreen.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/purchases/ui/PurchaseDraftScreen.kt)
- Convert the screen to a single `LazyColumn`.
- Move `PurchaseHeaderSection` and the "Post" button into `item` blocks within the `LazyColumn`.
- REASON: Fixes the `performClick()` failures in `PurchaseUiTest` caused by nested scrolling conflicts between the root `verticalScroll` and the lines `LazyColumn`.

### Component: feature-waste (ViewModel & State)
#### [MODIFY] [WasteFormViewModel.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteFormViewModel.kt)
- Finalize `UnitOptionsLoadState` integration.
- Remove all production diagnostic logs.

### Component: feature-waste (Instrumentation Tests)
#### [MODIFY] [WasteTestHelper.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteTestHelper.kt)
- Robust `waitForWasteStatus` and `waitForTag` with reliable tree-dump fallback.

#### [MODIFY] [WasteFailureUiTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteFailureUiTest.kt)
- Authoritative trigger counts and dialog closure checks.

#### [MODIFY] [WasteArchiveUiTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteArchiveUiTest.kt)
- Comprehensive archived marker verification.
- Selection filtering proofs.

## Verification Plan
1. **Full Build & Verification Pipe**:
   ```bash
   ./gradlew clean assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest
   ```
2. **Final Report**: Update README.md with the 100% green status.

