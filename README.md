# Cuentame - Local Restaurant Inventory

Cuentame is a local-first Android application designed for restaurant inventory management. It operates entirely offline, providing tools for tracking ingredients, purchases, waste, and physical counts.

## Current Milestone: Milestone 3 — Onboarding and Local Configuration

### Implemented Scope
* **Milestone 1:** Responsive UI shell, Navigation, Theme, and project structure.
* **Milestone 2:** Strongly modeled domain entities, UUID-based IDs, BigDecimal precision, complete Room v1 schema with foreign keys and indexes, DAOs, Repositories, and pure domain calculation services.
* **Milestone 3:** Comprehensive onboarding with persistent draft, localized restaurant configuration, area and category management, and robust startup recovery.

### Technical Stack
* **Language:** Kotlin
* **UI:** Jetpack Compose with Material 3
* **DI:** Hilt
* **Database:** Room (SQLite)
* **Architecture:** Clean Architecture / MVVM
* **Testing:** JUnit, Mockito/Turbine, Truth, Room Testing

## Verification Commands
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
```

### Current Status (Milestone 4 — Ingredients and Units)
- `testDebugUnitTest`: PASSED
- `lintDebug`: PASSED
- `assembleDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED

### Milestone 4 Highlights
- **Ingredient Catalog:** Full CRUD operations with search, filtering, and category organization.
- **Unit Integrity:** Enforced base unit immutability and atomic ingredient/unit creation.
- **Flexible Packaging:** Support for standard measurement conversions and custom ingredient-specific packages.
- **Precision:** All calculations powered by `BigDecimal` for zero-loss financial and inventory accuracy.

### Current milestone: Milestone 4
### Next milestone: Milestone 5 — Purchases

### Resetting App Data
To re-run onboarding, clear the application storage via Android Settings. This will remove both the Room database and Preferences DataStore.

## Next Milestone
**Milestone 5 — Purchases**: Implementing supplier management and purchase receipts.

## Project Documentation
* [Product Specs](docs/product-spec.md)
* [Implementation Plan](docs/implementation-plan.md)
* [Architecture](docs/architecture.md)
* [Project Audit](docs/project-audit.md)
