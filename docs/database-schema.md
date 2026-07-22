# Database Schema - Milestone 2

## Overview

The database uses SQLite via Room. It follows a local-first approach with UUIDs generated on the client.

## Tables

### `restaurants`
Root entity for the application data.
*   `id`: String (PK)
*   `name`: String
*   `currencyCode`: String (ISO)
*   `localeTag`: String
*   `createdAt`, `updatedAt`: Long (Timestamp)
*   `deletedAt`: Long? (Soft delete)

### `inventory_areas`
Physical locations where items are counted.
*   `restaurantId`: String (FK -> restaurants.id, CASCADE)
*   `name`: String
*   `normalizedName`: String (Normalization for unique checks)
*   `sortOrder`: Int

### `ingredient_categories`
Logical groupings for ingredients.
*   `restaurantId`: String (FK -> restaurants.id, CASCADE)
*   `name`, `normalizedName`, `sortOrder`

### `units`
System and custom units of measure.
*   `id`: String (PK)
*   `name`, `symbol`
*   `dimension`: String (MASS, VOLUME, COUNT)
*   `factorToCanonical`: String (BigDecimal)
*   `isSystem`: Boolean

### `ingredients`
The core items being tracked.
*   `categoryId`: String? (FK, SET_NULL)
*   `baseUnitId`: String (FK -> units.id, RESTRICT)
*   `defaultAreaId`: String? (FK, SET_NULL)
*   `reorderPointBase`: String? (BigDecimal)

### `ingredient_unit_options`
Conversions for specific ingredients (e.g., Case of 40lb).
*   `ingredientId`: String (FK, CASCADE)
*   `standardUnitId`: String? (FK -> units.id, RESTRICT)
*   `factorToBase`: String (BigDecimal)
*   `isBase`, `isDefaultCount`, `isDefaultPurchase`: Boolean

### `purchase_receipts` & `purchase_lines`
Records of incoming goods.
*   `supplierId`: String? (FK, SET_NULL)
*   `quantityEntered`, `quantityBase`, `lineTotal`, `unitCostBase`: String (BigDecimal)

### `stock_counts`, `stock_count_areas`, `stock_count_lines`
Periodic physical counts.
*   `adjustmentQuantityBase`: String? (BigDecimal)

### `waste_events`
Records of discarded items.

### `inventory_movements`
**Source of Truth** for all inventory changes.
*   `quantityBaseSigned`: String (BigDecimal)
*   `unitCostBaseSnapshot`: String?
*   `sourceDocumentType`, `sourceDocumentId`, `sourceOperationId`, `sourceLineId`

### `inventory_balance_projection`
Derived table for fast balance lookups.
*   Composite PK: `restaurantId`, `ingredientId`, `areaId`

### `ingredient_cost_projection`
Derived table for average costs.
*   Composite PK: `restaurantId`, `ingredientId`

## Design Decisions

*   **Idempotency:** Inventory movements use a unique index on `(sourceDocumentType, sourceDocumentId, sourceOperationId)` to prevent duplicate generation from the same document operation.
*   **Soft Deletion:** Used for master data (Ingredients, Areas, etc.) to maintain historical integrity.
*   **Decimal Storage:** All `BigDecimal` values are stored as `TEXT` in canonical format to ensure precision and locale independence.
*   **Enum Storage:** Enums are stored as `TEXT` names.
*   **Foreign Keys:** Enforced at the database level to ensure consistency.
*   **Projections:** Used for performance, but can be fully rebuilt from `inventory_movements`.
