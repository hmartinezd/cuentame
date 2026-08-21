# Preparation Recipes Domain

This document describes the implementation of restaurant preparation recipes in the Venkoi Restaurant Ops application.

## Overview

A **preparation** is an inventory ingredient that is produced in-house from other ingredients (components). Examples include sauces, stocks, doughs, or marinated meats.

### Key Principles

1.  **Unified Ingredient Model**: Preparations remain normal inventory ingredients. There is no separate "Prepared Ingredient" entity. An ingredient is considered a preparation if it has a non-archived recipe associated with it.
2.  **Recipe Lifecycle**: Recipes progress through `DRAFT`, `ACTIVE`, and `ARCHIVED` statuses.
    *   `DRAFT`: Incomplete or being edited.
    *   `ACTIVE`: The authoritative recipe for production.
    *   `ARCHIVED`: Historical record.
3.  **One Recipe Per Output**: At most one non-archived (Draft or Active) recipe can exist for a specific output ingredient at any time. Archived recipes may remain as historical records.
4.  **Strict Immutability**: Recipe definitions (components, yields, notes) are mutable ONLY while the recipe is in `DRAFT` status. Once `ACTIVE` or `ARCHIVED`, the definition is immutable. To edit an active recipe, it must first be moved back to `DRAFT`.
5.  **No Immediate Inventory Side Effects**: In Milestone 1, saving, editing, or archiving a recipe does not create inventory movements, update balances, or affect projections. Production batch implementation is deferred to a future milestone.

## Data Model

### PreparationRecipe

Represents the definition of how to produce a specific ingredient.

*   `outputIngredientId`: The ingredient being produced.
*   `standardYieldQuantity`: The quantity produced by one batch (e.g., "5").
*   `yieldUnitOptionId`: The unit of the yield (e.g., "Gallons").
*   `status`: The current lifecycle state.
*   `components`: A list of ingredients and quantities required for the recipe.

### PreparationRecipeComponent

Represents a single ingredient used in a recipe.

*   `componentIngredientId`: The ingredient being used.
*   `quantityEntered`: User-provided quantity in the selected unit.
*   `quantityBase`: The quantity converted to the component's base unit.
*   `sortOrder`: Used for deterministic UI display.

## Validation and Integrity

### Business Rules

*   A recipe cannot have its output ingredient as a component (direct self-reference).
*   Active recipes must have a positive yield and at least one valid component.
*   Ingredient units must belong to the respective ingredients.

### Cycle Prevention

The system prevents circular dependencies between preparations. For example:
*   Recipe A produces Ingredient X using Ingredient Y.
*   Recipe B produces Ingredient Y using Ingredient X.
*   This is rejected by the `PreparationRecipeValidator` before activation.

The cycle detection algorithm uses a directed graph traversal (`DFS`) on all non-archived recipes.

## Persistence

Recipes are stored in the `preparation_recipes` and `preparation_recipe_components` Room tables.

### Migration

The database was migrated from version 2 to 3 to include these new tables. The Room schema version is 3. The migration preserves all existing inventory data, categories, and settings.

## Backup and Restore

The backup snapshot now includes `preparationRecipes` and `preparationRecipeComponents` arrays.
*   **Database Schema 3**: New backups are created with database schema version 3.
*   **Backward Compatibility**: The system supports restoring Schema 2 and Schema 3 backups. For Schema 2 backups, recipe arrays are defaulted to empty lists.
*   **Foreign Key Integrity**: The restore process ensures ingredients are restored before recipes, and recipes before components.

## Integrity and Protections

### Reference Protection

To maintain historical and operational integrity:
*   **Ingredients**: Cannot be archived if they are the output or a component of a `DRAFT` or `ACTIVE` recipe.
*   **Unit Options**: Cannot be archived if they are used as a yield unit or component unit in a `DRAFT` or `ACTIVE` recipe.

### Lifecycle Transitions

Allowed transitions are strictly enforced:
*   `DRAFT` -> `ACTIVE`
*   `DRAFT` -> `ARCHIVED`
*   `ACTIVE` -> `DRAFT`
*   `ACTIVE` -> `ARCHIVED`
*   `ARCHIVED` -> `DRAFT` (only if no other non-archived recipe exists for that output)

## Future Scope (Milestone 2+)

*   **Production Batches**: Recording when a preparation is actually made.
*   **Inventory Integration**: Posting movements to consume components and add prepared output to stock.
*   **Costing**: Calculating the unit cost of a preparation based on its components.
*   **UI**: Complete screens for managing recipes and recording production.
