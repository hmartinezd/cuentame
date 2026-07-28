# Cuentame Inventory

**Cuentame** is a professional inventory management tool designed specifically for restaurants. It helps you track your stock, manage purchases, and monitor waste—all from your Android device.

## What Cuentame helps you do

*   **Restaurant Profile:** Set up your restaurant details including your preferred currency and language.
*   **Inventory Areas:** Organize your stock into logical locations like "Main Freezer," "Dry Storage," or "Bar."
*   **Ingredient Management:** Create a master list of all your ingredients.
*   **Unit Options:** Configure exactly how you buy and count each item (e.g., cases, bags, or individual pounds).
*   **Purchase Tracking:** Record new stock as it arrives, track costs, and post purchases to update your inventory automatically.
*   **Stock Counts:** Perform regular physical counts to verify your actual stock levels.
*   **Waste Logging:** Record spoiled, expired, or dropped items to keep your inventory accurate and understand your losses.
*   **Dashboard & Alerts:** View real-time warnings for negative balances or missing costs.
*   **Reporting:** Monitor your total inventory value and compare your spending and waste over 7, 30, or 90 days.

## Getting Started

1.  **Restaurant Setup:** Complete the initial onboarding to set your restaurant name and currency.
2.  **Add Areas:** Go to Settings to define your inventory locations.
3.  **Create Ingredients:** Add the items you want to track. Be sure to add at least one **Unit Option** for each ingredient so you can record quantities.
4.  **Daily Workflow:**
    *   Record a **Purchase** when an order arrives.
    *   Log **Waste** as it happens throughout the shift.
    *   Perform a **Stock Count** weekly or monthly to stay accurate.
5.  **Review Reports:** Use the Dashboard or Reports screen to monitor inventory value, purchasing and Waste trends. Range changes update the report in place without leaving the screen.

## Understanding Document Statuses

*   **DRAFT:** A record that is still being worked on. It does not affect your inventory levels or show up in finalized reports.
*   **POSTED:** A finalized record. Once posted, it updates your inventory balances and is included in all reports.
*   **VOIDED:** A previously posted record that has been cancelled. It is excluded from active reporting totals.
*   **COMPLETED (Counts):** A finalized stock count that sets the authoritative balance for your ingredients.

## Local-First Data

Cuentame stores all your business data **locally on your device**. 
*   No internet connection is required for daily operation.
*   **Important:** Your data is not currently backed up to the cloud. Deleting the app or clearing its storage will permanently remove your restaurant's records.

## Languages and Accessibility

*   Available in **English** and **Español**.
*   Screen-reader support and accessible labels across key workflows.

## User Guides

Detailed instructions for every feature:
*   [English User Guide](docs/USER_GUIDE.md)
*   [Guía del Usuario en Español](docs/USER_GUIDE.es.md)

---

## Development & Status

### Current Status (Milestone 9 — Backup and Export)
*   **Milestone 8 — Dashboard and Reports:** COMPLETE
*   **Milestone 9 — Backup and Export:** IN PROGRESS
*   **Backup creation:** PARTIAL
*   **Backup restore:** NOT STARTED
*   **Customer data export:** NOT STARTED
*   **CI verification:** NOT CONFIGURED

### Automated Test Summary
- `clean`: PASSED
- `assembleDebug`: PASSED
- `testDebugUnitTest`: PASSED (211 tests)
- `lintDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED (166 tests)

### Tech Stack
- **UI:** Jetpack Compose with Material 3.
- **Architecture:** Clean Architecture with Hilt for DI and Coroutines/Flow for reactivity.
- **Persistence:** Room (SQL-based business data) and Preferences DataStore (Local settings).
- **Precision:** `BigDecimal` used for all monetary and quantity calculations to ensure financial accuracy.

### Development Setup
1. Open in Android Studio Ladybug or newer.
2. Run `./gradlew assembleDebug` to verify the build.
3. Run `./gradlew testDebugUnitTest` for JVM unit tests.
4. Run `./gradlew connectedDebugAndroidTest` for integration and E2E verification.
