# Cuentame

**Cuentame** is a professional, native Android inventory management application designed for restaurants. It provides a local-first, auditable, and high-precision solution for tracking ingredients, purchases, waste, and production.

## Key Features

- **Ingredients & Units:** Comprehensive management of ingredient catalog with complex unit conversions.
- **Inventory Areas:** Organize stock across multiple storage locations.
- **Purchases:** Auditable purchase history with auditable source traceability.
- **Waste Tracking:** Log and categorize inventory loss with reason codes.
- **Stock Counts:** Perform physical counts by area with automatic adjustment generation.
- **Preparation Recipes:** Define yields and components for in-house prepared items.
- **Production Batches:** Track the conversion of raw ingredients into prepared outputs.
- **Inventory Activity:** Unified auditable timeline of all inventory movements with full source traceability.
- **Reports:** Operational visibility into inventory value, usage, and variance.
- **Data Safety:** Local-first persistence with auditable, immutable movements and Backup/Restore support.

## Development Status (Milestone 4: Unified Activity)

| Capability | Status | Notes |
| :--- | :--- | :--- |
| Core Inventory Logic | Implemented | High-precision BigDecimal calculations. |
| Unified Activity Feed | Fully Verified | Auditable source traceability implemented. |
| Production & Recipes | Fully Verified | Functional dependency tracking. |
| Backup & Restore | Fully Verified | Schema version 4, Backup Format v1. |
| Instrumentation Tests | Fully Verified | Green build on connected devices. |
| Smart Invoice Capture | **Planned** | OCR and AI-assisted entry (Milestone 5). |

## Architecture

- **Native Android:** Kotlin-first implementation.
- **UI:** Jetpack Compose with Material 3.
- **Pattern:** MVVM (Model-View-ViewModel) with StateFlow.
- **DI:** Hilt.
- **Persistence:** Room (Local SQLite) with schema version 4.
- **Precision:** `BigDecimal` with `MathContext.DECIMAL128` for all financial and quantity logic.
- **Architecture:** Single-module package-oriented structure.

## Development Setup

1. Open in Android Studio Ladybug or newer.
2. Verify the build: `./gradlew :app:assembleDebug`
3. Run JVM unit tests: `./gradlew :app:testDebugUnitTest`
4. Run static analysis: `./gradlew :app:lintDebug`
5. Run connected tests: `./gradlew :app:connectedDebugAndroidTest` (Requires a connected emulator or physical device).

## Project Ownership

This repository is publicly viewable, but Cuentame is a privately developed project. External contributions, unsolicited pull requests, and feature submissions are not currently accepted. Public repository visibility should not be interpreted as an open contribution process.

---

Milestone 4 Unified Inventory Activity & Source Traceability is functionally complete and fully verified.
