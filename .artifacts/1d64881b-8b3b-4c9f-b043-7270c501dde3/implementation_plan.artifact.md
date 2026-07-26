# Implementation Plan — Milestone 8 Phase 1: Local Dashboard and Reports Foundation

Implement a real, data-backed Home dashboard and a corresponding Reports overview using local data from Room.

## User Review Required

> [!IMPORTANT]
> This phase uses exclusively local data. Food cost metrics requiring sales or recipe data are intentionally deferred.

## Proposed Changes

### Domain

#### [NEW] [DashboardModels.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/domain/model/DashboardModels.kt)
- Define `DashboardDateRange`: Enum (7, 30, 90 days).
- Define `MetricComparison`: current, previous, absoluteChange, `percentageChange: BigDecimal?`.
- Define `InventoryValuationSummary`: totalValue, valuedIngredientCount, stockedIngredientCount, missingCostCount.
- Define `WasteReportItem`: ingredientId, name, quantityBase, unitSymbol, totalValue, eventCount.
- Define `DashboardActivityItem`: id, type, status, timestamp, description, optional value.
- Define `DashboardSnapshot`: Aggregated data for a specific range.

#### [NEW] [DashboardRepository.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/domain/repository/DashboardRepository.kt)
- `observeDashboard(restaurantId: RestaurantId, range: DashboardDateRange): Flow<DashboardSnapshot>`

#### Date-range calculator
- Inject `Clock` into the repository.
- Intervals: Current `[now - N, now)`, Previous `[now - 2N, now - N)`.

#### Decimal Policy
- Parse TEXT columns in Repository.
- Math using `BigDecimal` with `MathContext.DECIMAL128`.
- Round percentages to 1 decimal place.

---

### Data

#### [MODIFY] [InventoryProjectionDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/InventoryProjectionDao.kt)
- Add `observeValuationRows(restaurantId: String): Flow<List<InventoryValuationRow>>`.
- Join `inventory_balance_projection` and `ingredient_cost_projection`.

#### [MODIFY] [PurchaseDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/PurchaseDao.kt)
- Add `observeSpendRows(restaurantId: String, start: Long, end: Long): Flow<List<PurchaseSpendRow>>`.
- Filter `status = 'POSTED'` and `purchaseDate` in range.

#### [MODIFY] [InventoryMovementDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/InventoryMovementDao.kt)
- Add `observeWasteRows(restaurantId: String, start: Long, end: Long): Flow<List<WasteValueRow>>`.
- Join `inventory_movements` and `waste_events`.
- Filter `movementType = 'WASTE'` and `wasteEvent.status = 'POSTED'`.

#### [MODIFY] [StockCountDao.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/dao/StockCountDao.kt)
- Add `observeCompletedCountLines(restaurantId: String, start: Long, end: Long): Flow<List<CompletedCountLineRow>>`.
- Filter `status = 'COMPLETED'` and `completedAt` in range.

#### [NEW] [RoomDashboardRepository.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepository.kt)
- Combine flows from DAOs.
- Aggregate using `BigDecimal`.
- Implement comparison logic and top-waste ranking.

#### [MODIFY] [RepositoryModule.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/core/di/RepositoryModule.kt)
- Bind `RoomDashboardRepository`.

---

### Home

#### [MODIFY] [HomeViewModel.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/home/HomeViewModel.kt)
- Use `DashboardRepository.observeDashboard`.
- Handle `HomeScreenState`: `Loading`, `SetupRequired`, `Ready`, `Error`.
- Handle date-range switching with `flatMapLatest`.

#### [MODIFY] [HomeScreen.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/home/HomeScreen.kt)
- Scrollable layout with KPI cards.
- Add sections: Data completeness, Top Waste, Recent Activity.
- Add Quick Actions: Log Waste, New Purchase, Start Stock Count, View Reports.
- Navigation wiring in `CuentameNavHost`.

---

### Reports

#### [NEW] [ReportsViewModel.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/reports/ReportsViewModel.kt)
- Manage `DashboardDateRange` and expose `DashboardSnapshot`.

#### [NEW] [ReportsScreen.kt](file:///Users/hector/Projects/cuentame/app/src/main/kotlin/com/miara/cuentame/feature/reports/ReportsScreen.kt)
- Replace placeholder with overview based on KPI definitions.
- Date-range selector.

---

### Localization and accessibility

- **Strings**: English and Spanish for all new labels (Purchase Spend, Inventory Value, Waste, Coverage, etc.).
- **Content Descriptions**: For date-range selector and action buttons.
- **Accessibility**: Support large text, high-contrast labels.
- **Test Tags**: Authoritative tags for all KPI values and lists (e.g., `dashboard_inventory_value`).

---

### Verification

#### Repository Tests
- Unit tests for `RoomDashboardRepository` using fixed `Clock`.
- Scenarios: No data, negative balance, missing cost, voided documents exclusion, period boundaries, percentage changes.

#### ViewModel Tests
- `HomeViewModelTest` and `ReportsViewModelTest`.
- Test: Loading state, error handling, date-range switching, currency mapping.

#### Instrumentation Tests
- `HomeUiTest` and `ReportsUiTest`.
- Test: Populated vs Empty states, date switching, quick-action navigation.
- Regression check of existing features.

---

## Verification Plan

### Automated Tests
- JVM: `./gradlew testDebugUnitTest --tests "*Dashboard*" --tests "*HomeViewModelTest*" --tests "*ReportsViewModelTest*"`
- Instrumented: Run `HomeUiTest` and `ReportsUiTest` individually.
- Full Pipe: `./gradlew clean assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest`

### Manual Verification
- Verify Home and Reports alignment.
- Verify currency symbols match restaurant profile.
- Verify "New Purchase" and "Start Stock Count" open correct forms.
