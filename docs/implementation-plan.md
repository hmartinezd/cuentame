# Implementation Plan - Cuentame Inventory

## Planned Milestones

### Milestone 1: Project Foundation (Completed)
* **Scope:** Build system configuration (Gradle, Version Catalog), Hilt setup, Room/DataStore/Serialization dependencies, Application shell with Navigation Compose, Material 3 Theme, basic Testing foundation.

### Milestone 2: Core Domain and Database (Completed)
* **Scope:** Data models, Room Entities, DAOs, Type Converters, Repositories, initial Unit seeds, Domain services for calculations and conversions.
* **Integrity:** Enforced atomic ingredient creation, movement idempotency, and deterministic reversal handling with projection rebuilding.
* **Result:** A robust, testable persistence and business logic foundation.

### Milestone 3: Onboarding and Local Configuration (Completed)
* **Scope:** First-run experience, Restaurant setup, Area/Category configuration, DataStore-backed preferences, English/Spanish localization.
* **Integrity:** Sequential deterministic autosave with flush, authoritative validation, unified reorderable lists, restaurant-scoped protection, and reactive startup repair.
* **Result:** A hardened configuration foundation that ensures consistency between local storage and business records.

### Milestone 4: Ingredients Management (Completed)
* **Scope:** CRUD for ingredients, Unit conversions, Packaging options, Category assignment, Base unit immutability, Search and Filtering.
* **Integrity:** Atomic ingredient/unit creation, base unit immutability enforced in repository, dimension-safe conversions.
* **Result:** A fully functional ingredient catalog with precise measurement and packaging support.

### Milestone 5: Purchases (Completed)
* **Scope:** Supplier management, Purchase receipts (Draft/Posted/Void), Inventory movements, Cost averaging.
* **Integrity:** Authoritative `PurchaseLineCalculator`, transactional posting/voiding, movement history validation, numeric equivalence for decimal comparison.
* **Result:** Precise, restaurant-scoped purchase tracking with strict audit trails.

### Milestone 6: Stock Counts (Completed)
* **Scope:** Physical inventory counts, Area-based counting, Opening balances, Adjustments.
* **Integrity:** Point-in-time inventory snapshot replaying movement history, debounced autosave for counts, atomic completion, strict history validation.
* **Result:** Accurate area-based physical counts with automated opening balances and adjustments.

### Milestone 7: Waste Tracking
* **Scope:** Waste events registration, Photos, History.
* **Dependency:** Milestone 5.

### Milestone 8: Dashboard and Reports
* **Scope:** Inventory value, Negative inventory alerts, Count comparisons, Unclassified usage reports.
* **Dependency:** Milestone 6 & 7.

### Milestone 9: Backup and Export
* **Scope:** JSON/ZIP backups, CSV exports.
* **Dependency:** All previous milestones.

### Milestone 10: Polish and Finalization
* **Scope:** Tablet layouts, Accessibility, Spanish translation, UI Tests, final Build.

## Technical Risks
* **Unit Conversions:** Handling complex conversions (e.g., volume to mass for specific ingredients) requires a robust model.
* **Inventory Projections:** Ensuring the derived balance (Movements -> Projections) is always accurate and performant.
* **Concurrency:** Room transactions for document posting/voiding to ensure data integrity.

## Testing Strategy
* **Unit Tests:** Business logic (conversions, cost averaging, projections).
* **DAO Tests:** Database operations and constraints.
* **ViewModel Tests:** UI state and navigation logic.
* **Compose Tests:** User flows and adaptive layouts.

## Explicitly Excluded
* Backend/Cloud synchronization.
* POS Integrations (Clover, Square, etc.).
* Multi-user/Authentication.
* Real-time OCR.
