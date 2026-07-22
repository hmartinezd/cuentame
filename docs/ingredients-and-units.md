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

## Standard Units vs Packages
- **Standard Units:** Derived from system measurement definitions (e.g., `lb` to `oz`). The conversion factor is automatically calculated in trusted code using `StandardUnitConverter` and cannot be manually overridden.
- **Custom Packages:** Ingredient-specific packaging (e.g., "Case of 40 lb"). The conversion factor is user-defined but must be positive.

## Conversion Formula
`quantity in option * factorToBase = quantity in ingredient base unit`

All calculations use `BigDecimal` with `DECIMAL128` precision to ensure zero-loss accuracy.

## Search and Filtering
- **Search:** Case-insensitive and whitespace-normalized search by ingredient name.
- **Filtering:** Filter by category (including Uncategorized) and archive status.
- **Deterministic Ordering:** Results are ordered alphabetically by normalized name.
