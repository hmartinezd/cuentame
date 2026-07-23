# Purchases and Supplier Management

## Domain Rules

### Purchase Lifecycle
- **DRAFT:** Mutable. Can add/remove/edit lines. No inventory impact.
- **POSTED:** Immutable. Creates positive `PURCHASE` inventory movements. Triggers balance and average cost projection updates.
- **VOIDED:** Immutable. Creates exact negative `REVERSAL` movements for every original purchase movement. Restores previous balance and average cost.

### Ownership and Integrity
- All purchases must belong to the active restaurant.
- Suppliers, Ingredients, Areas, and Unit Options must belong to the same restaurant as the receipt.
- Posting revalidates all references; archived records cannot be used for new postings.
- Purchase lines cannot be moved between receipts.
- **Historical Operability:** Receipts can be voided even if their supplier has been archived. Drafts with archived suppliers can be repaired (by removing/replacing the supplier) or deleted, but cannot be posted.

### Calculations
- Use `PurchaseLineCalculator` with `BigDecimal` and `MathContext.DECIMAL128`.
- `quantityBase = quantityEntered × optionFactorToBase`.
- `unitCostBase = lineTotal ÷ quantityBase`.

### History Validation
- **POSTED:** Exactly one `PURCHASE` movement per receipt line. Matches line values numerically using `compareTo()`.
- **VOIDED:** Exactly one `PURCHASE` and one `REVERSAL` per line. Reversal must target the correct movement and have numerically opposite quantities and costs.
- **Timestamps:** Reversal `effectiveAt` and `createdAt` match the receipt's `voidedAt` time.

## Technical Design

### Reference Validator
`PurchaseReferenceValidator` separates ownership validation from active-state validation. 
- `validateReceiptOwnership`: Ensures existence and restaurant ownership.
- `validateSupplierForDraft`/`Posting`: Ensures supplier is active and belongs to the restaurant.
- `validateLineReferences`: Ensures ingredient, area, and option are valid and belong to the restaurant.

### Movement History Validator
`PurchaseMovementHistoryValidator` ensures the integrity of inventory movements using numeric comparison for all decimal values (`quantity`, `unitCost`, `totalValue`).

### Idempotency
`post()` and `void()` are idempotent. They detect valid existing history and return success without duplication, even if master data (like suppliers) has changed since the original operation.

### UI Operation Lifecycle
Confirmation dialogs use dedicated operation states and match matching events (e.g., `PurchaseDraftEvent.LineDeleted(lineId)`) to ensure they only close upon confirmed successful completion. Ingredient selection in the line editor is protected against race conditions using a cancellation-aware pipeline.
