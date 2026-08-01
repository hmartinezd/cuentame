# Preparation Recipes Domain

This document describes the implementation of restaurant preparation recipes in the Cuentame application.

## Overview

A **preparation** is an inventory ingredient that is produced in-house from other ingredients (components). Examples include sauces, stocks, doughs, or marinated meats.

### Key Principles

1.  **Unified Ingredient Model**: Preparations remain normal inventory ingredients. There is no separate "Prepared Ingredient" entity. An ingredient is considered a preparation if it has a non-archived recipe associated with it.
2.  **Recipe Lifecycle**: Recipes progress through `DRAFT`, `ACTIVE`, and `ARCHIVED` statuses.
    *   `DRAFT`: Incomplete or being edited.
    *   `ACTIVE`: The authoritative recipe for production.
    *   `ARCHIVED`: Historical record.
3.  **One Recipe Per Output**: At most one non-archived (Draft or Active) recipe can exist for a specific output ingredient at any time.
4.  **No Immediate Inventory Side Effects**: In Milestone 1, saving, editing, or archiving a recipe does not create inventory movements, update balances, or affect projections. Production batch implementation is deferred to a future milestone.

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

The database was migrated from version 2 to 3 to include these new tables. The migration preserves all existing inventory data, categories, and settings.

## Backup and Restore

The backup snapshot now includes `preparationRecipes` and `preparationRecipeComponents` arrays.
*   **Backward Compatibility**: The system can read older backups (v2) by defaulting recipe arrays to empty lists.
*   **Foreign Key Integrity**: The restore process ensures ingredients are restored before recipes, and recipes before components.

## Future Scope (Milestone 2+)

*   **Production Batches**: Recording when a preparation is actually made.
*   **Inventory Integration**: Posting movements to consume components and add prepared output to stock.
*   **Costing**: Calculating the unit cost of a preparation based on its components.
*   **UI**: Complete screens for managing recipes and recording production.
