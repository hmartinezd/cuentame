# Cuentame Architecture Specification

This document details the architectural baseline, target multi-module design, layer boundaries, and dependency rules for the Cuentame Android application.

## 1. Architectural Principles

- **Unidirectional Data Flow (UDF)**: UI observes immutable state from ViewModels; user actions trigger intent methods.
- **Strict Layer Isolation**: Core domain logic has zero Android framework or database dependencies.
- **Single Responsibility**: Repositories act as thin facades delegating transactional operations, read models, and state reconciliation to dedicated coordinators.
- **Single-Source-of-Truth**: Shared system states (such as active restaurant and application locale) are reconciled centrally before state consumption or backup serialization.

---

## 2. Module Graph Architecture

```
                                 ┌──────────────┐
                                 │     :app     │
                                 └──────┬───────┘
                                        │
           ┌────────────────────────────┼───────────────────────────┐
           │                            │                           │
           ▼                            ▼                           ▼
  ┌──────────────────┐        ┌──────────────────┐        ┌──────────────────┐
  │:feature:onboarding│        │  :feature:home   │        │:feature:inventory│ ... (and other features)
  └────────┬─────────┘        └────────┬─────────┘        └────────┬─────────┘
           │                           │                           │
           └───────────────────────────┼───────────────────────────┘
                                       │
           ┌───────────────────────────┼───────────────────────────┐
           │                           │                           │
           ▼                           ▼                           ▼
  ┌──────────────────┐        ┌──────────────────┐        ┌──────────────────┐
  │:core:presentation│        │   :core:domain   │        │   :core:backup   │
  └────────┬─────────┘        └────────┬─────────┘        └────────┬─────────┘
           │                           │                           │
           └───────────────────────────┼───────────────────────────┘
                                       │
           ┌───────────────────────────┴───────────────────────────┐
           │                                                       │
           ▼                                                       ▼
  ┌──────────────────┐                                   ┌──────────────────┐
  │   :core:model    │                                   │    :core:data    │
  └────────┬─────────┘                                   └────────┬─────────┘
           │                                                       │
           └───────────────────────────┬───────────────────────────┘
                                       │
                                       ▼
                              ┌──────────────────┐
                              │  :core:common    │
                              └──────────────────┘
```

---

## 3. Layer Definitions & Allowed Dependencies

### `:core:common`
- **Responsibilities**: Pure Kotlin primitive helpers, ID generators, time providers, decimal math utilities, text normalizers, app version interfaces.
- **Allowed Dependencies**: None (Pure Kotlin module).

### `:core:model`
- **Responsibilities**: Domain entities, value objects, immutable DTOs, `SupportedAppLocale` enum, domain-level backup metadata.
- **Allowed Dependencies**: `:core:common`.
- **Forbidden Dependencies**: Room, Android framework, Compose, Android resources (`R.string`).

### `:core:domain`
- **Responsibilities**: Repository contracts, use cases, domain validators, calculation engines, workflow interfaces.
- **Allowed Dependencies**: `:core:common`, `:core:model`.
- **Forbidden Dependencies**: Android framework, Room, Compose.

### `:core:presentation`
- **Responsibilities**: Platform-neutral presentation contracts (`UiText`), shared presentation mappers, locale-agnostic UI state interfaces.
- **Allowed Dependencies**: `:core:common`, `:core:model`, `:core:domain`.

### `:core:designsystem`
- **Responsibilities**: Color palette, typography, shapes, shared Compose UI components, layout primitives, Compose-based formatters.
- **Allowed Dependencies**: `:core:common`, `:core:model`, Compose dependencies.

### `:core:backup`
- **Responsibilities**: Decomposed backup subsystem (archive creation, preflight checks, snapshot integrity validation, ZIP entry writing, checksum generation, platform document access).
- **Allowed Dependencies**: `:core:common`, `:core:model`, `:core:domain`.

### `:core:data`
- **Responsibilities**: Room database, Room entities, DAOs, Room mappers, DataStore preferences implementation, backup data source adapters.
- **Allowed Dependencies**: `:core:common`, `:core:model`, `:core:domain`, Room, DataStore, Hilt.

### `:core:testing`
- **Responsibilities**: Shared fake repositories, fake document stores, test clocks, test fixture builders.
- **Allowed Dependencies**: Core layer interfaces.

### `:feature:*` (`onboarding`, `home`, `inventory`, `purchases`, `counts`, `waste`, `reports`, `settings`)
- **Responsibilities**: Screens, ViewModels, feature navigation graphs, feature-specific UI states, feature strings.
- **Allowed Dependencies**: `:core:common`, `:core:model`, `:core:domain`, `:core:presentation`, `:core:designsystem`, `:core:data` (if binding requires), Compose, Hilt.
- **Forbidden Dependencies**: Other feature modules (`feature -> feature` imports strictly prohibited).

### `:app`
- **Responsibilities**: Application entry point, Hilt root component, root `NavHost` graph composition.
- **Allowed Dependencies**: All `:feature:*` modules and required `:core:*` modules.

---

## 4. Architectural Rules

1. **Decimal Precision**: All inventory quantities and financial values must use `BigDecimal`. `Double` and `Float` are forbidden for business calculations.
2. **Cancellation Exception**: `CancellationException` must never be caught without re-throwing.
3. **User-Facing Errors**: Never expose raw system tracebacks, URIs, database payloads, or internal paths in user error messages.
4. **Room Database Compatibility**: Schema Version 2 and Backup Format Version 1 are strictly preserved.
5. **No Feature-to-Feature Coupling**: Features communicate solely through domain interfaces and navigation routes composed at `:app`.
