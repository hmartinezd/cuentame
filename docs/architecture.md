# Architecture - Cuentame

## Architectural Pattern

The project follows a Clean Architecture approach with a clear separation of concerns.

### Dependency Direction

```text
Compose UI (Activity/Fragment/Composable)
    ↓
ViewModel (State management, UI logic)
    ↓
Use Cases (Domain/Business logic)
    ↓
Repository Interfaces (Data contracts)
    ↓
Repository Implementations (Data orchestration)
    ↓
Room DAOs / DataStore (Data source access)
    ↓
Room Database / Preferences (Persistence)
```

## Layers

* **UI Layer:** Jetpack Compose, Material 3, ViewModels.
* **Domain Layer:** Business models, Use Cases, Repository interfaces. (Pure Kotlin, no Android dependencies).
* **Data Layer:** Room entities, DAOs, Repository implementations, DataStore.

## Milestone 2 Implementation

In Milestone 2, we established the **Local Data Foundation**:
* **Strongly Typed Domain:** Value classes for IDs, `BigDecimal` for precision, and `Instant` for time.
* **Room Database:** Complete schema for v1, including projections and movements.
* **Mappers:** Explicit boundaries between Room Entities and Domain Models.
* **Repositories:** Room-backed implementations of domain repositories.
* **Domain Services:** Conversion and calculation logic for units, cost, and balances.
