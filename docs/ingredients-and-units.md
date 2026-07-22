# Ingredients and Units

## Ingredient Lifecycle
Ingredients are the core entities of the inventory system. They move through several states:
1.  **Draft/Creation:** Created atomically with a base unit and optional additional units.
2.  **Active:** Available for use in purchases, counts, and waste.
3.  **Archived:** Soft-deleted to preserve historical integrity while being removed from operational lists.

## Base Unit Immutability
To ensure consistency across historical records and derived unit options, an ingredient's **base unit** cannot be changed after the ingredient is created. If a different base unit is required, a new ingredient must be created.

## Measurement Dimensions
System supports three dimensions: `MASS`, `VOLUME`, and `COUNT`. Conversions are only permitted within the same dimension.
- **MASS:** gram (canonical), kilogram, ounce, pound.
- **VOLUME:** milliliter (canonical), liter, cup, gallon, etc.
- **COUNT:** each (canonical).

## Unit Options
Ingredients support two types of unit options:
1.  **Standard Units:** References to system units (e.g., adding `kg` to an ingredient with `gram` base). The conversion factor is automatically calculated based on system definitions.
2.  **Custom Packages:** User-defined packaging (e.g., "Case of 40 lb"). The user provides a name and the quantity of the base unit it contains.

## Conversion Formula
`Quantity in Option * factorToBase = Quantity in Base Unit`

## Default Units
Each ingredient can have one default unit for **Counting** and one for **Purchasing**. By default, these point to the base unit option but can be reassigned to any active option belonging to that ingredient.
