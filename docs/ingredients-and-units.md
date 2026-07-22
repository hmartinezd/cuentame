# Ingredients and Units

## Ingredient Lifecycle
Ingredients move through several states:
1.  **Creation:** Created atomically with a base unit and optional additional units in a single transaction.
2.  **Active:** Available for use in inventory operations.
3.  **Archived:** Soft-deleted to preserve history while removing from active selection.

## Invariants and Protection
- **Base Unit Immutability:** An ingredient's base unit cannot be changed after creation.
- **Base Option Protection:** The unit option representing the base unit cannot be archived, degraded, or renamed to an incompatible unit.
- **Default Units:** Every ingredient must have exactly one active default unit for counting and one for purchasing.
- **Atomic Graphs:** Ingredient creation validates the entire option set and defaults before committing to Room.
- **Restaurant Scoping:** All operations are scoped by restaurant to ensure data isolation.
- **ID Integrity:** Unique IDs are validated before creation to prevent accidental overwrites.

## Standard Units vs Packages
- **Standard Units:** Derived from system measurement definitions (e.g., `lb` to `oz`). The conversion factor is automatically calculated in trusted code using `StandardUnitConverter` and cannot be manually overridden. Standard units must reference existing system units.
- **Custom Packages:** Ingredient-specific packaging (e.g., "Case of 40 lb"). The conversion factor is user-defined but must be positive.

## Conversion Formula
`quantity in option * factorToBase = quantity in ingredient base unit`

All calculations use `BigDecimal` with `DECIMAL128` precision to ensure zero-loss accuracy.

## Search and Filtering
- **Search:** Case-insensitive and whitespace-normalized search by ingredient name. Search is debounced (300ms) for performance.
- **Filtering:** Filter by category (including an explicit "Uncategorized" option) and archive status.
- **Deterministic Ordering:** Results are ordered alphabetically by normalized name and ID.

## UI Consistency and Reactivity
- **Reactive Detail:** The ingredient detail screen re-observes data automatically upon edits or unit operations.
- **Edit Scope:** The Edit form is restricted to name and category. Measurement configuration is immutable after creation and unit options are managed via specialized detail actions.
- **Success-Driven Dialogs:** Unit operation dialogs remain open during processing and only close upon confirmed success, preserving input during failures.
