# Waste Instrumentation Tests — Targeted Diagnosis Plan

This plan focuses on identifying and fixing instrumentation test failures in the Waste feature through improved isolation, authoritative selectors, and better synchronization.

## User Review Required

> [!IMPORTANT]
> I will be disabling animations for all instrumented tests in `app/build.gradle.kts` to improve test reliability.
> I will also be removing the `debug_checkFormState` test as it is invalid.

## Proposed Changes

### 1. Build Configuration
- [MODIFY] [app/build.gradle.kts](file:///Users/hector/Projects/cuentame/app/build.gradle.kts): Disable animations for instrumented tests.

### 2. Test Infrastructure
- [NEW] [WasteTestHelper.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteTestHelper.kt): Implement robust helper functions for navigation, synchronization, and diagnostic tree dumping.

### 3. Waste Feature Tests
- [MODIFY] [WasteArchiveUiTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteArchiveUiTest.kt):
    - Remove `debug_checkFormState`.
    - Use authoritative `waste_item_{eventId}` tags.
    - Implement proper dropdown menu handling (closing before opening next).
    - Add corrupted reference coverage with specific order of waits.
- [MODIFY] [WasteFailureUiTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteFailureUiTest.kt):
    - Use authoritative tags.
    - Implement exact rollback assertions for balance and cost projections.
- [MODIFY] [WasteLifecycleTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteLifecycleTest.kt):
    - Use authoritative tags.
    - Replace brittle text-count assertions with stable tag assertions (`waste_detail_quantity`, etc.).
    - Fix scrolling issues for buttons in long forms/details.

## Verification Plan

### Diagnostic Sequence (One-by-One)
1. `WasteArchiveUiTest.draftWithArchivedReferences_fullFlow`
2. `WasteArchiveUiTest.missingIngredient_producesErrorState`
3. `WasteArchiveUiTest.missingArea_producesErrorState`
4. `WasteArchiveUiTest.missingUnitOption_producesErrorState`
5. `WasteArchiveUiTest.crossIngredientUnitOption_producesErrorState`
6. `WasteFailureUiTest.postFailure_provesRollback`
7. `WasteFailureUiTest.voidFailure_provesRollback`
8. `WasteFailureUiTest.deleteFailure_provesIntegrity`
9. `WasteLifecycleTest.wasteLifecycle_fullScenario`
10. `WasteLifecycleTest.wasteLifecycle_negativeBalance`

### Package Run
`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.miara.cuentame.feature.waste`

### Full Suite Run
`./gradlew connectedDebugAndroidTest`
