# Implementation Plan — Milestone 8 Phase 1: Reports Overview

Implement the Reports Overview feature, providing a detailed breakdown of restaurant inventory, purchasing, and waste metrics with localized date-range filtering.

## User Review Required

> [!IMPORTANT]
> - Reports will use the existing authoritative formulas and data from `DashboardRepository`.
> - The feature will be accessible from the bottom navigation and the "View Reports" quick action on the Home screen.
> - High-precision `BigDecimal` math and localized formatting will be applied consistently across all metrics.

## Proposed Changes

### Residual Home Corrections

#### [MODIFY] [HomeScreen.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/home/HomeScreen.kt)
- Restore combined accessibility semantics for `KpiCard`.
- Ensure all trend descriptions are localized and include previous-period context.

#### [MODIFY] [HomeScreenStateTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/home/HomeScreenStateTest.kt)
- Replace `assert(retryClicked)` with `assertThat(retryClicked).isTrue()`.

#### [MODIFY] [FormattersTest.kt](file:///Users/hector/Projects/cuentame/app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt)
- Add JVM test for invalid currency code fallback behavior (e.g., "XYZ 1,234.56").

#### [MODIFY] [HomeUiTest.kt](file:///Users/hector/Projects/cuentame/app/src/androidTest/kotlin/com/miara/cuentame/feature/home/HomeUiTest.kt)
- Rename and extend `dashboard_fullVerification` with authoritative metric assertions.
- Add Home-to-Reports navigation verification.

---

### Reports Feature

#### [NEW] Reports Models
- Create `ReportsUiModels.kt` to hold structured data for the Reports screen (Comparison, Inventory, Alerts, Counts).

#### [NEW] Reports ViewModel
- Create `ReportsViewModel.kt` utilizing `RestaurantRepository` and `DashboardRepository`.
- Implement `ReportsScreenState` (Loading, SetupRequired, Ready, Error).
- Support date-range selection with cancellation of stale repository emissions.

#### [NEW] Reports UI
- Create `ReportsScreen.kt` with a vertically scrollable layout.
- Implement sections: Header, Date-Range Selector, Inventory Overview, Purchase Spend, Waste Value, Operational Alerts, Stock-Count Summary, and Top Waste Detail.
- Apply consistent localized formatting for currency, percentages, quantities, and dates.

#### [MODIFY] [CuentameNavHost.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/app/navigation/CuentameNavHost.kt)
- Replace `PlaceholderScreen(TopLevelDestination.REPORTS)` with `ReportsRoute`.

---

### Resources & Localization

#### [MODIFY] [strings.xml](file:///Users/hector/Projects/cuentame/app/src/main/res/values/strings.xml) & [strings.xml (es)](file:///Users/hector/Projects/cuentame/app/src/main/res/values-es/strings.xml)
- Add localized strings for all Reports sections, labels, and error messages.
- Ensure no hard-coded English remains in accessibility or UI prose.

## Verification Plan

### Automated Tests
1. **JVM Tests**:
   - Verify `ReportsViewModel` state transitions, range switching, and stale-flow handling.
   - Verify `Formatters` with localized and invalid inputs.
   - Command: `./gradlew testDebugUnitTest --tests "*ReportsViewModelTest*" --tests "*FormattersTest*"`
2. **Compose State Tests**:
   - Verify `ReportsScreen` rendering for all states using `ReportsScreenStateTest`.
3. **Integration Tests**:
   - Verify `ReportsUiTest` with real seeded data for all reporting sections and range switching.
   - Verify navigation flows from Home and Bottom Nav.
   - Command: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.miara.cuentame.feature.reports.ReportsUiTest`

### Manual Verification
- Deploy to emulator and verify visual layout on different device widths.
- Confirm localized formatting in both English and Spanish locales.
- Verify "Back" navigation correctly returns to the previous screen.
