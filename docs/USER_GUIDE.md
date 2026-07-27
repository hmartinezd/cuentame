# Cuentame User Guide

Welcome to **Cuentame**, your local-first restaurant inventory manager. This guide explains how to use the app to maintain accurate stock levels and monitor your restaurant's financial health.

## 1. Initial Setup

When you first open Cuentame, you will be guided through the **Onboarding** process:
*   **Restaurant Name:** Enter the name of your establishment.
*   **Currency & Language:** Choose your primary currency (e.g., USD) and preferred app language.

## 2. Managing Your Inventory Foundation

Before you can record activity, you need to define your storage locations and items.

### Inventory Areas
Define where you keep your stock (e.g., "Walk-in Fridge," "Bar," "Dry Storage").
1.  Go to **Settings** -> **Inventory Areas**.
2.  Add a new area with a descriptive name.
3.  You can archive areas you no longer use.

### Ingredients
Create a list of every item you track.
1.  Go to the **Inventory** tab.
2.  Tap the **Add** button.
3.  Enter the name and choose a base unit (e.g., mass for lbs/kg, volume for liters/gallons).

### Unit Options
Unit options are critical. They define how you measure the item in the real world.
*   **Base Unit:** How the item is counted in your bottom-line inventory (e.g., Lb).
*   **Purchase Unit:** How you buy it (e.g., Case of 50 Lbs).
*   **Count Unit:** How you count it during a physical inventory (e.g., Bag of 5 Lbs).
*   *Requirement:* An ingredient must have at least one unit option before it can be used in records.

## 3. Daily Operations

### Purchases
Record new stock arriving from suppliers.
1.  Tap **New Purchase** on the Dashboard.
2.  Select a **Supplier** and enter the **Invoice Number** and **Date**.
3.  Add lines for each ingredient, specifying the quantity and the cost.
4.  **Important:** A purchase is a **DRAFT** until you tap **Post**. Only POSTED purchases update your stock levels and appear in reports.

### Waste
Record items that are lost, spoiled, or discarded.
1.  Tap **Log Waste** on the Dashboard.
2.  Select the ingredient, the area it was in, the quantity, and the **Reason** (e.g., Spoiled, Expired).
3.  Review the **estimated value** of the loss based on historical costs.
4.  **Post** the waste event to finalize the record.

### Stock Counts
Perform a physical inventory to verify your actual stock.
1.  Tap **Start Count** on the Dashboard.
2.  Select the **Areas** you are counting.
3.  Enter the physical quantities found for each item.
4.  Once **Completed**, the app will update your inventory balances to match what you physically counted.

## 4. Dashboard and Reports

### Home Dashboard
*   **Inventory Value:** The total monetary value of your current stock.
*   **Operational Alerts:** Immediate warnings for negative balances or missing ingredient costs.
*   **Recent Activity:** A quick view of your most recent posted documents.

### Reports Overview
Select between **7, 30, or 90-day** rolling windows.
*   **Purchase Spend:** Compare how much you spent on new stock against the previous period.
*   **Waste Value:** Review your total losses over time.
*   **Data Completeness:** See how many of your items have costs assigned.
*   **Top Waste:** Identify the 5 items causing your highest financial losses.

## 5. Document Statuses

*   **DRAFT:** Still being edited. No effect on inventory.
*   **POSTED:** Finalized and active.
*   **VOIDED:** Cancelled after posting. Excluded from active totals.

## 6. Local Data and Security

*   **Privacy:** All your business data is stored only on this device.
*   **Internet:** No internet connection is required.
*   **Backups:** There is currently no cloud backup. If you lose your device or delete the app, your data will be lost. We recommend keeping physical copies of critical invoices.

## 7. Troubleshooting

*   **Missing Costs:** If an ingredient shows "$0.00" value, ensure you have posted at least one purchase with a valid cost for that item.
*   **Negative Balances:** This happens if you log more waste or post more sales than you have recorded in purchases. Perform a **Stock Count** to reset to the correct level.
