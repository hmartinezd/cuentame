# Implementation Plan — Milestone 8 Phase 1: Reports Overview

Implement the Reports Overview feature, providing a detailed breakdown of restaurant inventory, purchasing, and waste metrics with localized date-range filtering.

## User Review Required

> [!IMPORTANT]
> - Reports will use the existing authoritative formulas and data from `DashboardRepository`.
> - The feature will be accessible from the bottom navigation and the "View Reports" quick action on the Home screen.
> - High-precision `BigDecimal` math (scale-independent) and localized formatting will be applied consistently across all metrics.
> - Full accessibility semantics are implemented for all reporting sections to support screen readers.

## Proposed Changes

### Residual Home Corrections

#### [MODIFY] [HomeScreen.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/home/HomeScreen.kt)
- Restore combined accessibility semantics for `KpiCard`.
- Ensure all trend descriptions are localized and include previous-period context.

#### [MODIFY] [HomeScreenStateTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/home/HomeScreenStateTest.kt)
- Standardize assertions using Google Truth (`assertThat`).

#### [MODIFY] [FormattersTest.kt](file:///Users/hector/Projects/cuentame/app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt)
- Add JVM test for invalid currency code fallback behavior (e.g., "XYZ 1,234.56").

#### [MODIFY] [HomeUiTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/home/HomeUiTest.kt)
- Rename and extend `dashboard_fullVerification` with authoritative metric assertions.
- Add Home-to-Reports navigation verification.

---

### Reports Feature

#### [NEW] [ReportsUiModels.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/reports/ui/ReportsUiModels.kt)
- Structured data for Inventory, Comparisons, Alerts, and Counts.

#### [NEW] [ReportsViewModel.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/reports/viewmodel/ReportsViewModel.kt)
- utilize `RestaurantRepository` and `DashboardRepository`.
- Implement `ReportsScreenState` (Loading, SetupRequired, Ready, Error).
- Support date-range selection with cancellation of stale repository emissions.
- Use scale-independent `BigDecimal` comparisons for trend mapping.

#### [NEW] [ReportsScreen.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/reports/ui/ReportsScreen.kt)
- Vertically scrollable layout with sections: Header (with range label), Range Selector, Inventory, Purchases, Waste, Alerts, Stock Counts, and Top Waste.
- Add combined accessibility semantics for all reporting sections.
- Add stable test tags for exact numeric verification in integration tests.

#### [MODIFY] [CuentameNavHost.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/app/navigation/CuentameNavHost.kt)
- Replace `PlaceholderScreen(TopLevelDestination.REPORTS)` with `ReportsRoute`.

---

### Resources & Localization

#### [MODIFY] [strings.xml](file:///Users/hector/Projects/cuentame/app/src/main/res/values/strings.xml) & [strings.xml (es)](file:///Users/hector/Projects/cuentame/app/src/main/res/values-es/strings.xml)
- Add localized strings for all Reports sections, range labels, and semantics patterns.

## Verification Plan

### Automated Tests
1. **JVM Tests**:
   - Verify `ReportsViewModel` state transitions, range switching, and scale-independent comparisons.
   - Command: `./gradlew testDebugUnitTest --tests "*ReportsViewModelTest*" --tests "*FormattersTest*"`
2. **Compose State Tests**:
   - Verify `ReportsScreen` rendering for all states (Loading, Error, Ready empty/populated).
3. **Integration Tests**:
   - Verify `ReportsUiTest` with real seeded data for all sections and range updates.
   - Verify Home-to-Reports and Back navigation.
   - Command: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.miara.cuentame.feature.reports.ReportsUiTest`

### Manual Verification
- Deploy to emulator and verify visual layout and "Last 30 days" header.
- Confirm localized formatting and screen-reader announcements.
