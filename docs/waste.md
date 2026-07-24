# Waste Tracking

Waste tracking allows the restaurant to record inventory that is discarded due to expiration, spoilage, or other reasons.

## Waste Lifecycle

Waste events follow a simple lifecycle:
1. **DRAFT**: The event is being created or edited. It does not affect inventory projections.
2. **POSTED**: The event is finalized. A negative `WASTE` movement is created, and inventory projections are updated.
3. **VOIDED**: The event is reversed. A `REVERSAL` movement is created to restore the inventory quantity.

## Rules
- Only `DRAFT` events can be edited or deleted.
- `POSTED` and `VOIDED` events are immutable.
- A waste event can be voided but not deleted once posted.
- Every posted waste event creates exactly one `WASTE` movement.
- Voiding a waste event creates exactly one `REVERSAL` movement targeting the original `WASTE` movement.

## Calculations

### Unit Conversion
Quantities entered in any valid unit option for the ingredient are automatically converted to the base unit using the stored `factorToBase`.

### Historical Preview
When creating or editing a draft, the system provides a preview of the inventory status as of the event's `effectiveAt` timestamp:
- **Current Balance**: Total quantity in the selected area up to that point.
- **Remaining Balance**: Predicted balance after the waste event.
- **Estimated Value**: Based on the weighted-average cost of the ingredient at that time.

## Negative Inventory
The system allows waste events to result in a negative area balance. A prominent warning is shown to the user, but posting is not blocked, as negative inventory can be a valid signal of data discrepancies or missed receipts.

## Attachments
One optional local photo can be attached to each waste event. The system stores the local URI with persistable read permission.
