# Cuentame - Local Restaurant Inventory

Cuentame is a local-first Android application designed for restaurant inventory management. It operates entirely offline, providing tools for tracking ingredients, purchases, waste, and physical counts.

## Current Milestone: Milestone 2 — Core Domain and Room Database

### Implemented Scope
* **Milestone 1:** Responsive UI shell, Navigation, Theme, and project structure.
* **Milestone 2:** Strongly modeled domain entities, UUID-based IDs, BigDecimal precision, complete Room v1 schema with foreign keys and indexes, DAOs, Repositories, and pure domain calculation services.

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

### Current Status (Milestone 3)
- `testDebugUnitTest`: PASSED
- `lintDebug`: PASSED
- `assembleDebug`: PASSED
- `connectedDebugAndroidTest`: PASSED (19 tests including onboarding flow and preference persistence)

### Milestone 3 Highlights
- **Persistent Onboarding:** Multi-step setup with draft auto-save and crash recovery.
- **Local Configuration:** Restaurant profile, area, and category management.
- **Theming:** Full support for Light/Dark/System themes and Material You dynamic color.
- **Localization:** English and Spanish support with in-app language switching.

### Resetting App Data
To re-run onboarding, clear the application storage via Android Settings. This will remove both the Room database and Preferences DataStore.


## Next Milestone
**Milestone 3 — Onboarding and Local Configuration**: Implementing the setup flow and local restaurant configuration.

## Project Documentation
* [Product Specs](docs/product-spec.md)
* [Implementation Plan](docs/implementation-plan.md)
* [Architecture](docs/architecture.md)
* [Project Audit](docs/project-audit.md)
