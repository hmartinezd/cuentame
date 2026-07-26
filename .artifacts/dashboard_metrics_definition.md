# Dashboard Metrics Definition - Milestone 8 Phase 1

This document describes the exact formulas and data sources for the metrics implemented in the Milestone 8 Phase 1 Local Dashboard.

## 1. Current Inventory Value
- **Metric**: Total value of current inventory per ingredient.
- **Formula**: `currentInventoryValue = SUM(ingredientQuantity * current averageUnitCostBase)`
    - `ingredientQuantity = SUM(quantityBase across every area for the same restaurant and ingredient)`
- **Data Source**: `inventory_balance_projection` grouped by `ingredientId`, joined with `ingredient_cost_projection`.
- **Status Inclusion**: Current projections (latest state).
- **Negative Quantities**: Included in the sum (they reduce the total value).
- **Stocked Definition**: Unique ingredients whose total balance across all areas != 0.
- **Valued Definition**: Stocked ingredients with a usable cost projection.
- **Usable Cost**: Present in `ingredient_cost_projection`, parseable as `BigDecimal`, and non-negative.
- **Missing Cost**: Stocked ingredients without a usable cost projection.
- **Empty Data**: If `stockedIngredientCount == 0`, `coveragePercentage` is `null` (display `N/A`).

## 2. Purchase Spend
- **Metric**: Net active purchase spend by purchase date.
- **Formula**: `SUM(PurchaseLine.lineTotal)`
- **Data Source**: `purchase_receipts` joined with `purchase_lines`.
- **Status Inclusion**: `POSTED` receipts only.
- **Status Exclusion**: `DRAFT`, `VOIDED`. (A purchase that is later VOIDED is excluded from current and historical results).
- **Date Boundary**: `PurchaseReceipt.purchaseDate` in range `[startInclusive, endExclusive)`.
- **Comparison**: Compared with the immediately preceding period of equal duration.

## 3. Waste Value
- **Metric**: Total cost of posted waste events using historical snapshots.
- **Formula**: `SUM(ABS(InventoryMovement.totalValueSnapshot))`
- **Data Source**: Join `inventory_movements` and `waste_events` on `waste_events.id = inventory_movements.sourceDocumentId`.
- **Conditions**: 
    - `movement.sourceDocumentType = 'WASTE_EVENT'`
    - `movement.movementType = 'WASTE'`
    - `wasteEvent.status = 'POSTED'`
- **Status Exclusion**: `VOIDED`, `DRAFT`. `REVERSAL` movements are excluded.
- **Date Boundary**: `wasteEvent.effectiveAt` in range `[startInclusive, endExclusive)`.
- **Note**: Sum performed as `BigDecimal` in Kotlin.

## 4. Inventory Alerts
- **Negative Balances**: Count of `inventory_balance_projection` rows where `quantityBase < 0`. (Counts ingredient-area rows).
- **Missing Costs**: Count of unique stocked ingredients missing a usable cost projection.
- **No Unit Options**: Count of unique active ingredients with zero active `IngredientUnitOption` rows.

## 5. Stock-count Summary
- **Completed Counts**: Count of `stock_counts` with status `COMPLETED` and `completedAt` in range.
- **Most Recent**: `MAX(completedAt)` for `COMPLETED` counts in range.
- **Adjusted Lines**: Count of `stock_count_lines` in completed counts where `adjustmentQuantityBase != 0`.
- **Note**: Quantities from different dimensions are NOT summed together. Monetary variance is deferred.

## 6. Time Periods (Rolling Intervals)
- **Timezone**: Presentation uses device default `ZoneId`. Internal filtering uses UTC `Instant`.
- **endExclusive**: `clock.instant()`
- **startInclusive**: `endExclusive - N days`
- **previousEndExclusive**: `startInclusive`
- **previousStartInclusive**: `previousEndExclusive - N days`
- **Periods**: 7 days, 30 days (default), 90 days.
- **Range Logic**: `[startInclusive, endExclusive)`

## 7. Comparisons (Percentage Change)
- **Formula**: `percentageChange = ((current - previous) / ABS(previous)) * 100` (if `previous != 0`).
- **Rounding**: `1 decimal place` using `HALF_UP`.
- **previous == 0, current > 0**: `percentageChange = null` (Display `NEW`).
- **previous == 0, current == 0**: `percentageChange = null` (Display `NO_CHANGE`).

## 8. Precision
- **Logic**: All math performed in Kotlin using `BigDecimal`.
- **Aggregation**: Lightweight rows with TEXT decimal strings are retrieved from Room and parsed in the Repository.
