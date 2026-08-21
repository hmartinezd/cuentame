# Cuentame Architecture Specification

This document details the architectural baseline, current single-module structure, and the postponed multi-module design for the Cuentame Android application.

## 1. Current Project Structure (Single-Module Consolidation)

The project is intentionally consolidated into a single Gradle module (`:app`) to ensure behavioral stability before physical modularization. Architectural boundaries are enforced via **package-level dependency rules** verified by static analysis in `ArchitectureTest.kt`.

### Internal Package Layout

```text
com.venkoi.restaurantops.
  app/              # Application composition, root navigation, dependency injection
  core/
    common/         # Pure Kotlin helpers (IDs, time, math)
    model/          # Pure domain models (no Room, no Android)
    domain/         # Repository contracts, use cases (no Room, no Compose)
    presentation/   # Shared UI models, navigation routes, sanitization
    designsystem/   # Material 3 theme, shared UI components
    data/           # Room/DataStore implementations, backup sources
    database/       # Room entity definitions and DAOs
    backup/         # Backup creation, planning, and validation logic
  feature/          # Feature-oriented vertical slices (onboarding, inventory, etc.)
```

### Dependency Direction

1. **core.common**: No dependencies on higher layers.
2. **core.model**: Depends on `common`. No Room, Context, or R imports.
3. **core.domain**: Depends on `common` and `model`. No Room, Compose, or R imports.
4. **core.data/database**: Implements domain contracts. Depends on Room, DataStore, and `core.domain`.
5. **feature packages**: Depend on `core` layers. Direct feature-to-feature imports are prohibited.

---

## 2. Future Multi-Module Migration

Physical modularization is postponed until backup creation is fully stable, restoration is implemented, and package boundaries are verified clean.

### Migration Pre-requisites
- [x] Behavioral stabilization of backup creation.
- [ ] Completion of backup restore functionality.
- [x] Elimination of cross-feature UI component leaks.
- [x] Reliable JVM-based architecture enforcement tests.

### Planned Module Order
1. `:core:common`
2. `:core:model`
3. `:core:domain`
4. `:core:data`
5. `:core:backup`
6. Feature modules one at a time.

---

## 3. Core Architectural Rules

1. **Decimal Precision**: All inventory quantities and financial values must use `BigDecimal`. `Double` and `Float` are forbidden for business calculations.
2. **Cancellation Exception**: `CancellationException` must never be caught without re-throwing.
3. **User-Facing Errors**: Never expose raw system tracebacks, URIs, database payloads, or internal paths in user error messages. Use stable programmatic codes.
4. **Atomic UI Transitions**: ViewModel operations (like starting a backup) must be atomic and protect against concurrent requests and process death.
5. **Room Database Compatibility**: Schema Version 2 and Backup Format Version 1 are strictly preserved.
6. **No Feature-to-Feature Coupling**: Features communicate solely through domain interfaces and navigation routes. Shared UI components must reside in `core.presentation` or `core.designsystem`.
