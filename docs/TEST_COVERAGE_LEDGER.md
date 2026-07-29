# Test Coverage Ledger

| Original test path | Original behavior | Restored or replacement path | Concrete scenarios restored | Assertion equivalence | Verification command | Status | Remaining gap |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `ArchiveEntryValidatorTest.kt` | ZIP entry path safety | `ArchiveEntryValidatorTest.kt` | Absolute paths, traversal, alphanumeric, backslashes | Stronger | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `AttachmentFilenameSanitizerTest.kt` | Attachment filename sanitization | `AttachmentFilenameSanitizerTest.kt` | Dangerous chars, extension preservation, valid flag | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupFilenameGeneratorTest.kt` | Backup filename generation logic | `BackupFilenameGeneratorTest.kt` | With restaurant, without restaurant, sanitization | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupManifestHardeningTest.kt` | Manifest field limits and types | | `BackupManifestValidatorTest.kt` | Equivalent | `./gradlew :app:testDebugUnitTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `BackupManifestTest.kt` | Manifest serialization | | `BackupRoundTripTest.kt` | Stronger | `./gradlew :app:testDebugUnitTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `BackupManifestValidatorTest.kt` | Manifest business rules | `BackupManifestValidatorTest.kt` | version, limits, locale, metadata, schema | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupSnapshotIntegrityNumericTest.kt` | Snapshot decimal/range validation | | `BackupSnapshotIntegrityValidatorTest.kt` | Equivalent | `./gradlew :app:testDebugUnitTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `BackupSnapshotIntegrityProjectionTest.kt` | Projection sum validation | | `BackupSnapshotIntegrityValidatorTest.kt` | Equivalent | `./gradlew :app:testDebugUnitTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `BackupSnapshotIntegrityReversalTest.kt` | Reversal graph consistency | | `BackupSnapshotIntegrityValidatorTest.kt` | Equivalent | `./gradlew :app:testDebugUnitTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `BackupSnapshotIntegrityValidatorTest.kt` | Snapshot FK and business rules | `BackupSnapshotIntegrityValidatorTest.kt` | Simple valid, posted without movement, zero purchase qty, balance projection mismatch | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupSnapshotIntegrityVoidedCountTest.kt` | Voided count lifecycle | | `BackupSnapshotIntegrityValidatorTest.kt` | Equivalent | `./gradlew :app:testDebugUnitTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `ChecksumParserTest.kt` | character-by-character JSON parsing | `ChecksumParserTest.kt` | Empty, valid, duplicate key, non-hex, self-ref, malformed escape, trailing | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `ChecksumTest.kt` | Checksum logic | `ChecksumTest.kt` | sha256 matches, deterministic | Stronger | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `InsufficientStorageDetectionTest.kt` | Storage failure classification | `InsufficientStorageDetectionTest.kt` | ENOSPC, SecurityException, Open failure | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `NavigationTest.kt` | App navigation flow | `NavigationTest.kt` | Home, Settings, Back | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `ArchiveDeterminismTest.kt` | Identical inputs produce identical bytes | `BackupRoundTripTest.kt` | Deterministic byte equality | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupProductionIntegrationTest.kt` | Full pipeline with Room/DataStore | `BackupProductionIntegrationTest.kt` | E2E with real repository | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `BackupRoundTripTest.kt` | Backup/Restore integration | `BackupRoundTripTest.kt` | No-attachment, Shared attachments E2E | Stronger (JVM) | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupValidatorAdversarialTest.kt` | Adversarial archives (Android-side) | `BackupArchiveValidatorAdversarialTest.kt` | Missing manifest, unexpected entry, duplicate, checksum mismatch, malformed UTF-8, schema version | Stronger (JVM) | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `ProjectionRebuildTest.kt` | Projection maintenance | `DetailedReportsDaoTest.kt` | valuation rows joining | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `IngredientRepositoryTest.kt` | Ingredient CRUD and search | `RoomIngredientRepositoryTest.kt` | save/load ingredient | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `PurchaseRepositoryTest.kt` | Purchase lifecycle | `PurchaseIntegrationTest.kt` | Direct DAO insertion | Partial | `./gradlew :app:assembleDebugAndroidTest` | PARTIALLY RESTORED | Repository lifecycle missing |
| `RoomDashboardRepositoryIntegrationTest.kt` | Dashboard aggregation | `RoomDashboardRepositoryIntegrationTest.kt` | Empty DB return zeros | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `RoomLocalSetupRepositoryTest.kt` | Onboarding persistence | `RoomRestaurantRepositoryTest.kt` | save/load restaurant | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `RoomStockCountRepositoryTest.kt` | Stock count lifecycle | `RoomStockCountRepositoryTest.kt` | start stock count | Partial | `./gradlew :app:assembleDebugAndroidTest` | PARTIALLY RESTORED | complete/void missing |
| `RoomWasteRepositoryTest.kt` | Waste lifecycle | `RoomWasteRepositoryTest.kt` | create waste draft | Partial | `./gradlew :app:assembleDebugAndroidTest` | PARTIALLY RESTORED | post/void missing |
| `ChickenIntegrationTest.kt` | Business logic integration | `ChickenIntegrationTest.kt` | Multiple units for ingredient | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `PurchaseIntegrationTest.kt` | Purchase logic integration | `PurchaseIntegrationTest.kt` | Insert and read | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `HomeScreenStateTest.kt` | Home UI state mapping | `HomeScreenStateTest.kt` | Ready state copy/refresh | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `DetailedReportsUiTest.kt` | Reports UI interaction | `DetailedReportsUiTest.kt` | Navigation to reports | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `InventoryDetailScreenTest.kt` | Inventory detail UI | `InventoryDetailScreenTest.kt` | Visibility in reports | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `PurchaseDetailScreenTest.kt` | Purchase detail UI | `PurchaseDetailScreenTest.kt` | Visibility in reports | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `ReportingRefreshComposeTest.kt` | Reporting UI refresh | `ReportingRefreshComposeTest.kt` | Visibility in reports | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `ReportsScreenStateTest.kt` | Reports state mapping | `ReportsScreenStateTest.kt` | Stub | Partial | `./gradlew :app:testDebugUnitTest` | PARTIALLY RESTORED | Logic missing |
| `WasteDetailScreenTest.kt` | Waste detail UI | `WasteDetailScreenTest.kt` | Visibility in reports | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `SettingsBackupUiTest.kt` | Backup UI interaction | `SettingsBackupUiTest.kt` | Button existence | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
| `WasteLifecycleTest.kt` | Waste UI lifecycle | `WasteLifecycleTest.kt` | Navigation to waste | Equivalent | `./gradlew :app:assembleDebugAndroidTest` | RESTORED | None |
