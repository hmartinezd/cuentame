# Persisted Data Inventory & Backup Strategy

This document describes all sources of persistent data in Cuentame and their inclusion policy for the versioned backup system (`.cuentame-backup`).

## Database Version
Current Room Schema Version: 1

## Data Sources

| Persistent Data Source | Classification | Included | Reason | Restore Dependency | Sensitive Data |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `restaurants` | Canonical | YES | Core restaurant profile | None | NO |
| `inventory_areas` | Canonical | YES | User configuration of storage areas | `restaurants` | NO |
| `ingredient_categories` | Canonical | YES | User configuration of categories | `restaurants` | NO |
| `units` | Reference | YES | System and custom units | None | NO |
| `ingredients` | Canonical | YES | Master ingredient list | `restaurants`, `units`, `ingredient_categories`, `inventory_areas` | NO |
| `ingredient_unit_options` | Canonical | YES | Unit mapping for ingredients | `ingredients`, `units` | NO |
| `suppliers` | Canonical | YES | Supplier information | `restaurants` | NO |
| `purchase_receipts` | Canonical | YES | Financial purchase history | `restaurants`, `suppliers` | NO |
| `purchase_lines` | Canonical | YES | Detailed purchase data | `purchase_receipts`, `ingredients`, `inventory_areas`, `ingredient_unit_options` | NO |
| `stock_counts` | Canonical | YES | Periodic inventory snapshots | `restaurants` | NO |
| `stock_count_areas` | Canonical | YES | Progress of counts per area | `stock_counts`, `inventory_areas` | NO |
| `stock_count_lines` | Canonical | YES | Individual items counted | `stock_count_areas`, `ingredients`, `ingredient_unit_options` | NO |
| `waste_events` | Canonical | YES | Loss history | `restaurants`, `ingredients`, `inventory_areas`, `ingredient_unit_options` | NO |
| `inventory_movements` | Canonical | YES | Detailed ledger of all stock changes | `restaurants`, `ingredients`, `inventory_areas` | NO |
| `inventory_balance_projection` | Derived | YES | Instant Dashboard balance | `ingredients`, `inventory_areas` | NO |
| `ingredient_cost_projection` | Derived | YES | Instant Dashboard costing | `ingredients` | NO |
| App Preferences (DataStore) | Setting | YES | Language, theme, dynamic color | None | NO |
| Local Attachments (Files) | Attachment | YES | Scanned receipts and loss photos | `purchase_receipts`, `waste_events` | YES (User content) |
| Onboarding Draft | Temporary | NO | Incomplete setup data | None | NO |
| Room WAL/Journal files | Temporary | NO | SQLite internal state | None | NO |
| Image Cache | Temporary | NO | UI performance data | None | NO |

## Derived Data Policy
Projections are included in the backup to ensure the Dashboard is immediately accurate upon restoration without requiring a full ledger re-calculation, which may be expensive. Restored projections must be marked as derived.

## Global Units Policy
Units are included as required reference data. System units will be deduplicated based on ID during restoration in future phases.

## Attachment Portability Policy
Absolute device URIs and content provider permissions are NOT stored in the backup. Attachments are assigned a logical UUID and stored in a relative path within the archive. Database records are updated to refer to these logical IDs.
