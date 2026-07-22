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

### Current Status (Milestone 3 Integrity Pass)
- `testDebugUnitTest`: PASSED
- `lintDebug`: PASSED
- `assembleDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED

### Milestone 3 Highlights
- **Persistent Onboarding:** Multi-step setup with deterministic sequential autosave and crash recovery.
- **Unified Ordering:** Combined suggested and custom items in a single, stable ordered list.
- **Robust Integrity:** Authoritative validation for restaurant details, areas, and categories.
- **Setup Recovery:** Automatic repair of incomplete database states and synchronized Preferences DataStore.
- **Localization:** Real-time English and Spanish switching with full resource-based implementation.

### Resetting App Data
To re-run onboarding, clear the application storage via Android Settings. This will remove both the Room database and Preferences DataStore.

## Next Milestone
**Milestone 4 — Ingredients and Units**: Implementing ingredient CRUD, advanced unit conversions, and packaging options.

## Project Documentation
* [Product Specs](docs/product-spec.md)
* [Implementation Plan](docs/implementation-plan.md)
* [Architecture](docs/architecture.md)
* [Project Audit](docs/project-audit.md)
