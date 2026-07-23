# Stock Counts and Opening Balances

Stock counts allow a restaurant to perform physical inventory counts by area.

## Lifecycle
`DRAFT` → `COMPLETED` → `VOIDED`

### Draft
* Mutable: name, notes, and effective time (under restrictions) can be changed.
* Areas can be completed and reopened while the count is DRAFT.
* Lines (counted quantities) can be added, edited, or deleted.
* Autosave is implemented for line entries with debounce.

### Completed
* Immutable and auditable.
* Calculations for expected inventory and adjustments are performed as of the `effectiveAt` timestamp.
* Creates `OPENING_BALANCE` movements when no prior area history exists.
* Creates `COUNT_ADJUSTMENT` movements when prior history exists.
* Atomic transaction updates line snapshots, creates movements, and rebuilds projections.

### Voided
* Immutable.
* Creates deterministic `REVERSAL` movements for each original count movement.
* Projections are rebuilt to restore prior inventory state.

## Inventory Snapshot Service
Calculates expected inventory by replaying movements chronologically up to the count's effective timestamp.
* Excludes reversal movements and the movements they reverse.
* Calculates weighted average cost globally for the ingredient.

## Movement Mapping
* `OPENING_BALANCE`: Used for the first physical count of an ingredient in an area. Sets the base for future adjustments.
* `COUNT_ADJUSTMENT`: Used when prior history exists. Does not alter weighted average cost.

## Projection Rebuilding
Movements remain the source of truth. Projections are rebuilt from scratch by replaying all non-reversed movements in deterministic order: `effectiveAt`, `createdAt`, `id`.

## UI Flows
1. **Home**: List of historical and active counts.
2. **Start Count**: Select name, date, and storage areas.
3. **Area Counting**: Enter physical quantities. Suggested items include those with default area or existing balance. Manual addition supported.
4. **Detail/Review**: Review adjustments before completion. Read-only view for COMPLETED and VOIDED counts.
