# Implementation Plan — Milestone 8 Phase 1: Home Dashboard UI

Implement the Home Dashboard UI with real data-backed summaries, KPI cards, and recent activity tracking.

## User Review Required

> [!IMPORTANT]
> The dashboard will exclusively use local data. External integrations and food cost percentages are deferred to later phases.

## Proposed Changes

### Data Layer (Deterministic Activity Ordering)

#### [MODIFY] [PurchaseDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/PurchaseDao.kt)
- Update `observeRecentPurchaseActivity`: Change `ORDER BY pr.postedAt DESC` to `ORDER BY pr.postedAt DESC, pr.id ASC`.

#### [MODIFY] [InventoryMovementDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/InventoryMovementDao.kt)
- Update `observeRecentWasteActivity`: Change `ORDER BY timestamp DESC` to `ORDER BY timestamp DESC, we.id ASC`.

#### [MODIFY] [StockCountDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/StockCountDao.kt)
- Update `observeRecentCountActivity`: Change `ORDER BY completedAt DESC` to `ORDER BY completedAt DESC, id ASC`.

---

### UI Layer (Home Feature)

#### [NEW] [HomeUiModels.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/home/HomeUiModels.kt)
- Define `DashboardMetricUiModel` and `MetricComparisonState` (INCREASE, DECREASE, NO_CHANGE, NEW, UNAVAILABLE).
- Define `DashboardUiModel` for use in `HomeScreenState`.

#### [MODIFY] [HomeViewModel.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/home/HomeViewModel.kt)
- Replace placeholder logic with `DashboardRepository` and `RestaurantRepository`.
- Use `flatMapLatest` to switch repository flows when the date range changes.
- Map `DashboardSnapshot` to `DashboardUiModel`.
- Handle `HomeScreenState`: `Loading`, `SetupRequired`, `Ready`, `Error`.

#### [MODIFY] [HomeScreen.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/home/HomeScreen.kt)
- Implement vertically scrollable layout.
- Sections:
    - **Header**: Restaurant name, Dashboard title, selected range.
    - **Date-range Selector**: 7, 30, 90 days.
    - **KPI Section**: Current Inventory Value, Purchase Spend, Waste Value, Negative Balances.
    - **Data Completeness**: Coverage stats.
    - **Stock-count Summary**: Completed counts, adjusted lines, most recent count date.
    - **Top Waste**: Top 5 ingredients.
    - **Recent Activity**: 10 most recent finalized documents.
    - **Quick Actions**: Log Waste, New Purchase, Start Stock Count, View Reports.

#### [MODIFY] [CuentameNavHost.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/app/navigation/CuentameNavHost.kt)
- Update `HomeRoute` callbacks to handle new actions.

---

### Localization & Infrastructure

#### [MODIFY] [strings.xml](file:///Users/hector/Projects/cuentame/app/src/main/res/values/strings.xml)
- Add all required strings in English and Spanish for new dashboard labels and activity types.

#### [MODIFY] [Formatters.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/designsystem/util/Formatters.kt)
- Ensure existing formatters meet dashboard precision requirements.

---

## Verification Plan

### Automated Tests
- **HomeViewModelTest** (JVM):
    - Verify initial `Loading` and `SetupRequired` (missing restaurant).
    - Verify `Ready` state with populated and empty snapshots.
    - Verify range changes trigger new flow collection.
    - Verify currency mapping.
- **HomeUiTest** (Instrumentation):
    - Verify state rendering (Loading, Error, Populated, Empty).
    - Verify date-range switching updates UI.
    - Verify navigation to quick actions.
- **DashboardDaoTest** (Instrumentation):
    - Verify deterministic ordering of recent activity.

### Manual Verification
- Deploy to emulator.
- Toggle between 7, 30, and 90 day ranges and verify values update.
- Verify quick actions navigate to correct screens.
- Check accessibility tags with a screen reader.
