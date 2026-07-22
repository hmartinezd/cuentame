# Purchases and Suppliers - Milestone 5

## Overview

Milestone 5 implements the full purchase lifecycle and supplier management. It ensures that every purchase is tracked through a safe state machine and impacts inventory correctly.

## Lifecycle

1.  **DRAFT:** The initial state of a purchase receipt. It is fully mutable (header and lines). Drafts are persisted in Room.
2.  **POSTED:** The authoritative state. Posting creates positive `PURCHASE` inventory movements and updates balance and weighted-average-cost projections. Posted receipts are immutable.
3.  **VOIDED:** Reverses the impact of a posted receipt. Creates exact `REVERSAL` movements and rebuilds projections. Voided receipts remain in history but have no inventory impact.

## Rules and Invariants

*   **Restaurant Scoping:** All operations are strictly validated against the active restaurant ID.
*   **Precise Calculations:** All quantities and costs use `BigDecimal` with `MathContext.DECIMAL128`.
*   **Atomicity:** Posting and Voiding are wrapped in atomic Room transactions. Projections are rebuilt as part of the same transaction.
*   **Idempotency:** Repeated post or void commands detect existing valid history and return success without creating duplicate records.
*   **Malformed History Detection:** Before any state transition, the complete movement history for the receipt is validated. Inconsistencies (missing or extra movements) prevent the operation.
*   **Reference Integrity:** Drafts cannot be posted if they contain references to Master Data (Ingredients, Areas, Units, Suppliers) that have been archived since the draft was created.

## Supplier Management

*   Suppliers are managed at the restaurant level.
*   Active supplier names must be unique within a restaurant (normalized).
*   Archiving is a soft-delete operation.

## Inventory Impact

*   **Balance:** Each purchase line adds quantity to the specified area.
*   **Weighted Average Cost:**
    ```text
    newAverage = ((currentQuantity × currentAverageCost) + (purchaseQuantity × purchaseUnitCost)) / (currentQuantity + purchaseQuantity)
    ```
    *   If current quantity is ≤ 0, the new purchase established the average cost.
    *   Negative movements (Waste, Reversals) do not alter the established average cost.

## Testing Strategy

*   **JVM Unit Tests:** Logic for ViewModels, Calculators, and Normalization.
*   **Room Integration Tests:** Transaction integrity, Idempotency, and Projection rebuilding.
*   **Compose UI Tests:** E2E flows for Supplier CRUD and Purchase lifecycle.
*   **Integration Fixture:** `PurchaseIntegrationTest` verifies a complete multi-receipt scenario with chronological rebuilding and voids.
