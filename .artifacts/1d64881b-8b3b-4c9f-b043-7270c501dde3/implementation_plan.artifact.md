# Milestone 7 — Zero-Failure Verification Pass Implementation Plan

This plan addresses the final verification of Milestone 7, focusing on test stabilization, projection rollback proof, and complete coverage of edge cases like corrupted references.

## User Review Required

> [!IMPORTANT]
> I will be migrating several instrumented tests from `createAndroidComposeRule<MainActivity>` to `createEmptyComposeRule` to ensure that database seeding occurs **before** the Activity launches, which is a key requirement for test isolation and reliability.

## Proposed Changes

### 1. Test Isolation & Reliability
- Update all instrumented test classes to use `createEmptyComposeRule()`.
- Use `ActivityScenario.launch(MainActivity::class.java)` inside test methods or a manual setup to ensure seeding happens before launch.
- Implement a robust `clearRoomTables` and `resetDataStore` utility.
- Explicitly close `ActivityScenario` after each test.

### 2. Waste Projection Rollback (`WasteFailureUiTest.kt`)
- Extend `postFailure_provesRollback` to assert:
    - `balance projection` and `cost projection` before and after failure.
    - `event status` remains `DRAFT`.
    - `postedAt` remains null.
    - `WASTE` movement count is zero.
    - `failure boundary triggerCount == 1`.
- Extend `voidFailure_provesRollback` to assert:
    - `event status` remains `POSTED`.
    - `voidedAt` remains null.
    - `original WASTE` remains.
    - `REVERSAL` count is zero.
    - `balance projection` and `cost projection` before and after failure.
    - `failure boundary triggerCount == 1`.

### 3. Corrupted Reference Coverage (`WasteArchiveUiTest.kt`)
- Add/Refine separate tests for:
    - `missing ingredient`.
    - `missing area`.
    - `missing unit option`.
    - `unit option belonging to another ingredient`.
- Verify each shows `Error` state, hides `Save`, and prevents mutation.

### 4. Archived Reference Persistence (`WasteArchiveUiTest.kt`)
- Extend `draftWithArchivedReferences_fullFlow` to:
    1. Select active ingredient, area, unit.
    2. Save as DRAFT.
    3. Navigate away and reopen.
    4. Assert active selections persisted and are NOT labeled "Archived".
    5. Assert archived references are no longer in menus.

### 5. Stock Count Lifecycle Restoration (`StockCountLifecycleTest.kt`)
- Restore exact assertions for expected/adjustment values.
- Verify read-only behavior for `COMPLETED` and `VOIDED` states.
- Ensure snapshots (Expected/Adjustment) are visible after reopening.

### 6. Waste Lifecycle Assertions (`WasteLifecycleTest.kt`)
- Assert exact values (Chicken Breast, Main Kitchen, 3 lb, Spoiled, balance, value) after reopening DRAFT.
- Assert UI state after POST (status, snapshots visible, mutation buttons gone).
- Assert UI state after VOID (status, original data remains, mutation buttons gone).

## Verification Plan

### Automated Tests
- Run full suite: `./gradlew clean assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest`.
- Ensure `connectedDebugAndroidTest` passes with 0 failures.

### Manual Verification
- Truthfully update `README.md` with final test counts and status.
