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

### Calculations
- Use `PurchaseLineCalculator` with `BigDecimal` and `MathContext.DECIMAL128`.
- `quantityBase = quantityEntered × optionFactorToBase`.
- `unitCostBase = lineTotal ÷ quantityBase`.

### History Validation
- **POSTED:** Exactly one `PURCHASE` movement per receipt line. Matches line values numerically.
- **VOIDED:** Exactly one `PURCHASE` and one `REVERSAL` per line. Reversal must target the correct movement and have opposite quantities.

## Technical Design

### authorative Reference Validator
`PurchaseReferenceValidator` is used by both `saveLine` and `post` to ensure consistency in ownership and active-reference checks.

### Movement History Validator
`PurchaseMovementHistoryValidator` ensures the integrity of inventory movements before allowing status transitions or idempotent retries.

### Idempotency
`post()` and `void()` are idempotent. If a receipt is already in the target state, the validator checks the movement history. If it is valid, the operation returns success without duplicating records.

### UI Operation Lifecycle
Confirmation dialogs in `PurchaseDraftScreen` and `PurchaseDetailScreen` use dedicated operation states (`isPosting`, `isVoiding`, `isDeletingDraft`, `deletingLineId`). They remain open during asynchronous calls to prevent duplicate actions and only close upon confirmed success.
