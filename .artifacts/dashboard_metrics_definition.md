# Dashboard Metrics Definition - Milestone 8 Phase 1 (Revised)

This document describes the exact formulas and data sources for the metrics implemented in the Milestone 8 Phase 1 Local Dashboard.

## 1. Current Inventory Value
- **Metric**: Total value of current inventory aggregated per ingredient.
- **Formula**: `currentInventoryValue = SUM(ingredientValue for every ingredient with a usable cost)`
    - `ingredientValue = ingredientQuantity * averageUnitCostBase`
    - `ingredientQuantity = SUM(quantityBase across every area for the same restaurant and ingredient)`
- **Data Source**: `inventory_balance_projection` grouped by `ingredientId`, joined with `ingredient_cost_projection` on `restaurantId` and `ingredientId`.
- **Status Inclusion**: Current projections (latest state).
- **Negative Quantities**: Included at ingredient level (reduce total value).
- **Stocked Definition**: Unique ingredients whose total balance across all areas != 0.
- **Valued Definition**: Stocked ingredients with a usable cost projection.
- **Usable Cost**: Present in `ingredient_cost_projection`, non-negative.
- **Missing Cost**: Stocked ingredients without a usable cost projection.
- **Empty Data**: If `stockedIngredientCount == 0`, return `coveragePercentage = null` (display `N/A`).

## 2. Purchase Spend
- **Metric**: Net active purchase spend by purchase date.
- **Formula**: `SUM(PurchaseLine.lineTotal)`
- **Data Source**: `purchase_receipts` joined with `purchase_lines`.
- **Status Inclusion**: `POSTED` receipts only.
- **Status Exclusion**: `DRAFT`, `VOIDED`.
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

## 4. Inventory Alerts
- **Negative Balances**: Count of `inventory_balance_projection` rows where `quantityBase < 0`. (Counts ingredient-area rows).
- **Missing Costs**: Count of unique stocked ingredients missing a usable cost projection.
- **No Unit Options**: Count of unique active ingredients with zero active `IngredientUnitOption` rows.

## 5. Stock-count Summary
- **Completed Counts**: Count of `stock_counts` with status `COMPLETED` and `completedAt` in range.
- **Most Recent**: `MAX(completedAt)` for `COMPLETED` counts in range.
- **Adjusted Lines**: Count of `stock_count_lines` in completed counts where `adjustmentQuantityBase` is non-null and not zero.
- **Note**: Quantities from different dimensions are NOT summed together. Monetary variance is deferred.

## 6. Time Periods (Rolling Intervals)
- **Timezone**: Presentation uses restaurant/app locale context. Internal filtering uses UTC `Instant`.
- **Boundaries**: Derived from a single `timeProvider.now()` call.
- **Current**: `[now - N days, now)`
- **Previous**: `[now - 2N days, now - N days)`
- **Periods**: 7 days, 30 days (default), 90 days.

## 7. Comparisons (Percentage Change)
- **Formula**: `percentageChange = ((current - previous) / ABS(previous)) * 100` (if `previous != 0`).
- **Rounding**: `1 decimal place` using `HALF_UP`.
- **previous == 0, current > 0**: Display `NEW`.
- **previous == 0, current == 0**: Display `NO_CHANGE`.

## 8. Precision & Integrity
- **Logic**: All math performed in Kotlin using `BigDecimal` with `MathContext.DECIMAL128`.
- **Aggregation**: Lightweight rows with TEXT decimal strings are retrieved from Room.
- **Integrity**: Invalid or missing required decimals in `POSTED` documents trigger a reporting error instead of silent zeroing.
