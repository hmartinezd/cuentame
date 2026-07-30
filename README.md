# Cuentame Inventory

**Cuentame** is a professional inventory management tool designed specifically for restaurants. It helps you track your stock, manage purchases, and monitor waste—all from your Android device.

## Development & Status

### Current Status (Stabilization: COMPLETE)
*   **Single-module Gradle consolidation:** COMPLETE
*   **Package-level architecture:** COMPLETE
*   **Backup production hardening:** COMPLETE
*   **Backup creation verification:** COMPLETE
*   **Critical regression recovery:** COMPLETE
*   **Android instrumentation verification:** COMPLETE
*   **CI verification:** COMPLETE
*   **Backup restore:** NOT STARTED
*   **Customer export:** NOT STARTED
*   **Multi-module migration:** POSTPONED

### Tech Stack
- **UI:** Jetpack Compose with Material 3.
- **Architecture:** Clean Architecture with Hilt for DI and Coroutines/Flow for reactivity.
- **Persistence:** Room (SQL-based business data) and Preferences DataStore (Local settings).
- **Precision:** `BigDecimal` used for all monetary and quantity calculations to ensure financial accuracy.

### Development Setup
1. Open in Android Studio Ladybug or newer.
2. Run `./gradlew assembleDebug` to verify the build.
3. Run `./gradlew :app:testDebugUnitTest` for JVM unit tests.
4. Run `./gradlew :app:lintDebug` for static analysis.
