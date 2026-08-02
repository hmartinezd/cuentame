# Production Batches Domain

Production Batches record the actual preparation work where ingredients are transformed into a prepared output based on an active recipe.

## Lifecycle
- **DRAFT**: Editable. No inventory movements created.
- **POSTED**: Immutable. Creates consumption movements for components and an output movement for the prepared ingredient. Calculates production cost.
- **VOIDED**: Immutable. Creates reversal movements for all original production movements. Restores previous inventory state.

## Recipe Snapshots
When a Production Batch is created, it captures a snapshot of the recipe at that moment:
- Recipe name
- Standard yield and unit
- Multiplier
- Component quantities and units

Changes to the original recipe after a batch is created do not affect the batch.

## Batch Multiplier
The multiplier scales the expected output and component quantities.
- Actual output quantity can be overridden manually.
- Component actual quantities are reset to newly calculated expected quantities only if they have never been manually overridden.

## Production Cost Calculation
Posting a batch triggers a cost calculation:
1. Obtain the weighted average cost of each component at the batch's effective time using `InventorySnapshotService`.
2. `componentTotalCost = actualQuantityBase × componentAverageUnitCostBase`
3. `totalProductionCost = sum(componentTotalCost)`
4. `outputUnitCostBase = totalProductionCost ÷ actualOutputQuantityBase`

This ensures that yield loss (lower actual output) increases the unit cost of the prepared ingredient.

## Inventory Movements
Posting a batch creates:
- `PRODUCTION_CONSUMPTION`: One per component, reducing inventory at current average cost.
- `PRODUCTION_OUTPUT`: One for the output ingredient, increasing inventory and establishing/updating its weighted average cost.

## Nested Preparations
A production batch can consume ingredients that were themselves produced by another batch. The system uses the current weighted average cost of the consumed prepared ingredient, ensuring costs flow through the dependency graph without recursive explosion during posting.

## Posting Idempotency
- Posting a batch that is already `POSTED` validates its movement history and returns success.
- Voiding a batch that is already `VOIDED` validates its reversal history and returns success.

## Backup Schema 4
New backups (Schema 4) include full production batch and component data. Restoration supports Schema 2 (no recipes/production), Schema 3 (recipes but no production), and Schema 4.

## UI Implementation Status
Production batch domain, persistence, and posting are implemented.
Production management UI is not implemented yet.
