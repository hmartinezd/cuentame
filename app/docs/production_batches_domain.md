# Production Batches Domain

Production Batches record the actual preparation work where ingredients are transformed into a prepared output based on an active recipe.

## Database Schema 4
The Production Batch domain is implemented in Room Database Schema version 4, which introduces the `production_batches` and `production_batch_components` tables.

## Lifecycle
- **DRAFT**: Editable state. Does not create inventory effects. Referenced ingredients and areas are protected from archiving.
- **POSTED**: Immutable state. Marks the preparation as completed. Triggers cost calculation and inventory movements.
- **VOIDED**: Immutable state. Reverses all inventory effects of a previously posted batch.

## Recipe Snapshots and Conversions
When a Production Batch is created from an active recipe, it captures historical snapshots of recipe quantities and units.
- **Recipe Yield Option**: The unit in which the recipe's standard yield is expressed.
- **Selected Output Option**: The unit in which the specific batch's actual output is expressed. These may differ.
- **Canonical Quantities**: Both preview and posting calculate quantities from entered values using the currently selected unit option's factor to base, ensuring internal consistency even if stored base values are stale.

## Multiplier and Recalculation
The `batchMultiplier` scales recipe quantities to expected batch quantities.
- Changing the multiplier recalculates `expectedOutputQuantityBase` and `expectedQuantityBase` for all components.
- **Actual Output Recalculation**: When there is no manual output override, actual output is recalculated using the *selected batch output unit*, not just by copying recipe units.
- **Component Recalculation**: Non-overridden component actual quantities are reset to the new expected quantities when the multiplier changes.

## Historical Costing and Conservation
Posting calculates costs based on the batch's `effectiveAt` time using historical movement history.
- **Cost-bearing Inflows**: `PURCHASE`, `OPENING_BALANCE`, and `PRODUCTION_OUTPUT` are the only movements that establish or change an ingredient's weighted average cost.
- **Unavailable Cost**: If no cost-bearing history exists for an ingredient, its cost is represented as `null` (row absent or `null` in DB), never a numeric zero.
- **Movement Exclusion**: To ensure internal consistency, a batch's own output movement is excluded from the historical cost calculation of its own components, even if they share the same `effectiveAt` timestamp.
- **Nested Preparations**: A prepared ingredient produced by one batch establishes a historical cost that can be consumed by a subsequent batch. Canonical ordering (`effectiveAt`, `createdAt`, `id`) ensures deterministic results.
- **Precision**: All cost calculations use `MathContext.DECIMAL128` to prevent drift and ensure conservation.
- **Cost Conservation**: For every posted batch, the sum of consumption movement total values plus the output movement total value equals zero (numerically equivalent via `BigDecimal`).

## Transaction and Idempotency
- **Atomic Posting**: Posting is performed in a single Room transaction that validates the draft, canonicalizes quantities, calculates costs, persists snapshots, inserts movements, and updates projections.
- **Failure Boundaries**: Integrated failure points ensure the database rolls back to the `DRAFT` state (on post) or `POSTED` state (on void) if any step fails.
- **Runtime Integrity**: Projections and snapshots are guarded by strict movement-history validation. Any corruption in movements or reversals results in a typed `MovementHistoryConflict` failure.
- **Idempotency**: Reposting a `POSTED` batch or revoiding a `VOIDED` batch performs a full integrity check of existing movements/reversals and returns success if they match the document state exactly.

## Backup and Restore (Format v1)
The Backup Format v1 supports Database Schemas 2, 3, and 4.
- **Plural Identifiers**: The backup manifest uses historical plural keys for projections (`inventory_balance_projections`, `ingredient_cost_projections`) to maintain compatibility, while Room tables remain singular.
- **Schema 2**: Legacy set, no recipe or production data.
- **Schema 3**: Includes preparation recipes but no production batches.
- **Schema 4**: Includes preparation recipes and production batches.
- **Payload Boundaries**: Manifest metadata and DTO payloads are strictly enforced by schema. For example, a Schema 3 backup must not contain production records.
- **Integrity Validation**: Backups verify production numeric fields, foreign keys, lifecycle timestamps, and exact movement coverage. 
- **Temporal Historical Validation**: Stored production component costs are validated against history available at the batch's original posting boundary (`effectiveAt` and `postedAt`). Future movements, later-created backdated movements, and future reversals do not alter an existing batch's historical cost.
- **Exact Reversal Verification**: For `VOIDED` batches, every reversal movement is validated for exact field-level identity and negation against the original production movement.
- **Projection Reconstruction**: Current balance and cost projections are reconstructed from the complete current effective movement history using the shared `HistoricalInventoryCostCalculator`.

## Implementation Status
Production Batches domain, historical costing, posting, voiding and Backup/Restore are complete, internally consistent and verified. The foundation is ready for Production UI.
