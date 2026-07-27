# Implementation Plan — Milestone 8 Phase 1 closure

Perform final truthfulness pass on integration coverage and establish customer-oriented documentation.

## User Review Required

> [!IMPORTANT]
> - Integration tests will be updated to authoritatively prove document status exclusion rules (e.g., DRAFT/VOIDED excluded from reporting).
> - Customer documentation will be established as the primary entry point for the repository.
> - A permanent rule for maintaining documentation synchronization will be added to the project.

## Proposed Changes

### Integration Coverage

#### [MODIFY] [ReportsUiTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/ReportsUiTest.kt)
- Expand `seedPopulatedData` with:
    - DRAFT and VOIDED purchases (proving exclusion).
    - VOIDED waste and historical cost mismatch (proving historical valuation).
    - DRAFT counts and zero/null adjustments (proving count/adjustment logic).
- Strengthen assertions to verify exact totals within specific UI sections.

---

### Documentation

#### [MODIFY] [README.md](file:///Users/hector/Projects/cuentame/README.md)
- Reorganize to be customer-first.
- Add clear sections on product capability, getting started, and local-first data retention.
- Remove obsolete Milestone 8 "Next Steps".

#### [NEW] [USER_GUIDE.md](file:///Users/hector/Projects/cuentame/docs/USER_GUIDE.md)
- Comprehensive English guide for restaurant staff.

#### [NEW] [USER_GUIDE.es.md](file:///Users/hector/Projects/cuentame/docs/USER_GUIDE.es.md)
- Comprehensive Spanish guide for restaurant staff.

#### [NEW] [CONTRIBUTING.md](file:///Users/hector/Projects/cuentame/CONTRIBUTING.md)
- Define the documentation-maintenance policy.

## Verification Plan

### Automated Tests
- Run updated `ReportsUiTest` to verify exhaustive seeding and status rules.
- Run full suite to ensure no regressions.
- Command: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.miara.cuentame.feature.reports.ReportsUiTest`

### Manual Verification
- Review generated markdown files for clarity and natural language (especially Spanish).
