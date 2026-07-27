# Implementation Plan — Milestone 8 Phase 1: Home Dashboard Corrections

Perform a focused correction pass on the Home Dashboard to ensure UI integrity, localization accuracy, and robust verification.

## User Review Required

> [!IMPORTANT]
> - Removed duplicate TopAppBar from HomeScreen; header is now part of scrollable content.
> - Removed interactive SetupRequired button as app-start logic handles redirection.
> - Trends and Coverage now use authoritative BigDecimal calculations and localized formatters.

## Proposed Changes

### Data & Domain Layer

#### [MODIFY] [DashboardModels.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/model/dashboard/DashboardModels.kt)
- Update `InventoryValuationSummary` to include `stockedIngredientCount` and `valuedIngredientCount`.
- Ensure `DashboardActivityItem` has clear fields for localization.

#### [MODIFY] [ReportingPeriodCalculator.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/domain/service/ReportingPeriodCalculator.kt)
- Atomize period calculation to use a single `timeProvider.now()` call.

#### [MODIFY] [PurchaseDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/PurchaseDao.kt)
#### [MODIFY] [InventoryMovementDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/InventoryMovementDao.kt)
#### [MODIFY] [StockCountDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/StockCountDao.kt)
- Ensure deterministic ordering (`DESC timestamp, ASC id`) in activity queries.

---

### Home Feature

#### [MODIFY] [HomeViewModel.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/home/HomeViewModel.kt)
- Update mapping logic for `DashboardUiModel` to preserve raw counts.
- Implement strict decimal integrity parsing.
- Refine `Ready` and `Error` states to include the restaurant's locale.

#### [MODIFY] [HomeScreen.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/home/HomeScreen.kt)
- **Remove second Scaffold/TopAppBar**.
- Move Header (Name, "Dashboard", Selected Range) into `LazyColumn` item.
- Update `MetricTrend` to fix double `%` issue.
- Localize Activity status and type fallbacks.
- Complete Data Completeness section with `X / Y` counts and percentage.

#### [MODIFY] [strings.xml](file:///Users/hector/Projects/cuentame/app/src/main/res/values/strings.xml)
- Correct `trend_increase`/`trend_decrease` patterns (remove `%%`).
- Remove "check connection" from dashboard error message.
- Add activity status and type strings.

#### [MODIFY] [Formatters.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/designsystem/util/Formatters.kt)
- Update `formatCurrency` and `formatPercent` to use a provided `Locale` and `ZoneId`.

---

### Verification

#### JVM Tests
- **[MODIFY] [HomeViewModelTest.kt](file:///Users/hector/Projects/cuentame/app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt)**: Add stale-flow cancellation, retry behavior, and empty data scenarios.
- **[NEW] [FormattersTest.kt](file:///Users/hector/Projects/cuentame/app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt)**: Verify localized currency and percentage output.

#### Instrumentation Tests
- **[MODIFY] [HomeUiTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/home/HomeUiTest.kt)**:
    - Real data seeding for populated state.
    - Assert specific values and trends.
    - Verify navigation to quick actions (authoritative tags).
    - Verify range-switch updates values, not just selection.
- **[MODIFY] [DashboardDaoTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DashboardDaoTest.kt)**: Add deterministic ordering checks for all activity types.

## Verification Plan

### Automated Tests
1. **Targeted JVM**: `./gradlew testDebugUnitTest --tests "*HomeViewModelTest*" --tests "*FormattersTest*" --tests "*Dashboard*"`
2. **Targeted Room**: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.miara.cuentame.core.database.dao.DashboardDaoTest`
3. **Home UI**: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.miara.cuentame.feature.home.HomeUiTest`
4. **Regression Run**: `./gradlew connectedDebugAndroidTest` (Full suite)

### Manual Verification
- Deploy to emulator.
- Verify no duplicate top bar.
- Verify status labels are localized (Posted/Publicado) in activity feed.
- Check 0/0 and N/A behavior for coverage.
