# Domain Model

## Core Principles

*   **Immutability:** Domain models are implemented as Kotlin `data class` with `val` properties.
*   **Strong Typing:** Use of inline value classes for IDs (e.g., `IngredientId`) to prevent ID mixing.
*   **Decimal Precision:** `BigDecimal` is used for all quantities, costs, and factors.
*   **Time Abstraction:** `Instant` is used for all timestamps, managed via an injectable `TimeProvider`.

## Units and Conversions

### Unit Dimensions
*   `MASS`
*   `VOLUME`
*   `COUNT`

Conversions are only allowed between units of the same dimension.

### Ingredient Base Unit
Every ingredient has exactly one **base unit** (e.g., `lb`). All movements are recorded and calculated in this base unit for consistency.

### Unit Options
Ingredients can have multiple selectable units (e.g., `Case`, `Bag`, `Ounce`) which are converted to the base unit using a `factorToBase`.

## Inventory Logic

### Movements
The `InventoryMovement` is the atomic record of change.
*   **Inflows:** Positive quantity (Purchases, Positive adjustments).
*   **Outflows:** Negative quantity (Waste, Negative adjustments).

### Projections
Projections (`InventoryBalance`, `IngredientCost`) provide the "current state" of the inventory by aggregating movements.

### Weighted Average Cost
Cost is tracked using the weighted average method:
`new_avg = ((old_qty * old_avg) + (new_qty * new_unit_cost)) / (old_qty + new_qty)`
Only positive inflows with cost data affect the average cost.

## Validation
Domain validation rules are centralized in `ValidationError`. Repositories and Services enforce these rules before any operation is committed.
