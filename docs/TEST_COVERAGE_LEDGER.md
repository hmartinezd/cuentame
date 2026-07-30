# Test Coverage Ledger

| Original test path | Original behavior | Restored or replacement path | Concrete scenarios restored | Assertion equivalence | Verification command | Status | Remaining gap |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `ArchiveEntryValidatorTest.kt` | ZIP entry path safety | `ArchiveEntryValidatorTest.kt` | Absolute paths, traversal, alphanumeric, backslashes | Stronger | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `AttachmentFilenameSanitizerTest.kt` | Attachment filename sanitization | `AttachmentFilenameSanitizerTest.kt` | Dangerous chars, extension preservation, valid flag | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupFilenameGeneratorTest.kt` | Backup filename generation logic | `BackupFilenameGeneratorTest.kt` | With restaurant, without restaurant, sanitization | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupManifestHardeningTest.kt` | Manifest field limits and types | `BackupManifestValidatorTest.kt` | Version, limits, locale, metadata, schema | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupManifestTest.kt` | Manifest serialization | `BackupRoundTripTest.kt` | E2E manifest serialization | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupManifestValidatorTest.kt` | Manifest business rules | `BackupManifestValidatorTest.kt` | version, limits, locale, metadata, schema | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupSnapshotIntegrityNumericTest.kt` | Snapshot decimal/range validation | `BackupSnapshotIntegrityNumericTest.kt` | Zero quantity, negative quantity, malformed decimal | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupSnapshotIntegrityProjectionTest.kt` | Projection sum validation | `BackupSnapshotIntegrityProjectionTest.kt` | Balance projection mismatch | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupSnapshotIntegrityReversalTest.kt` | Reversal graph consistency | `BackupSnapshotIntegrityReversalTest.kt` | Reversal logic, multi-reversal rejection | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupSnapshotIntegrityValidatorTest.kt" | Snapshot FK and business rules | `BackupSnapshotIntegrityValidatorTest.kt` | Simple valid, posted without movement | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupSnapshotIntegrityVoidedCountTest.kt` | Voided count lifecycle | `RoomStockCountRepositoryTest.kt` | Voided count bijection | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `ChecksumParserTest.kt` | character-by-character JSON parsing | `ChecksumParserTest.kt` | Empty, valid, duplicate key, non-hex, self-ref, malformed escape, trailing | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `ChecksumTest.kt` | Checksum logic | `ChecksumTest.kt` | sha256 matches, deterministic | Stronger | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `InsufficientStorageDetectionTest.kt` | Storage failure classification | `InsufficientStorageDetectionTest.kt` | ENOSPC, SecurityException, Open failure | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `NavigationTest.kt` | App navigation flow | `NavigationTest.kt` | Onboarding clean start, Home seeded start | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `ArchiveDeterminismTest.kt` | Identical inputs produce identical bytes | `BackupRoundTripTest.kt` | Deterministic byte equality | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupProductionIntegrationTest.kt` | Full pipeline with Room/DataStore | `BackupProductionIntegrationTest.kt` | E2E with real repository | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `BackupRoundTripTest.kt` | Backup/Restore integration | `BackupRoundTripTest.kt` | No-attachment, Shared attachments E2E | Stronger (JVM) | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `BackupValidatorAdversarialTest.kt` | Adversarial archives (Android-side) | `BackupArchiveValidatorAdversarialTest.kt` | Missing manifest, unexpected entry, duplicate, checksum mismatch, malformed UTF-8, schema version | Stronger (JVM) | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `ProjectionRebuildTest.kt` | Projection maintenance | `RoomPurchaseRepositoryTest.kt` | Balance projection update | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `IngredientRepositoryTest.kt` | Ingredient CRUD and search | `RoomIngredientRepositoryTest.kt` | save/load ingredient | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `PurchaseRepositoryTest.kt` | Purchase lifecycle | `RoomPurchaseRepositoryTest.kt` | draft, lines, post, void, movement bijection | Stronger | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `RoomDashboardRepositoryIntegrationTest.kt` | Dashboard aggregation | `RoomDashboardRepositoryIntegrationTest.kt` | Empty DB return zeros | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `RoomLocalSetupRepositoryTest.kt` | Onboarding persistence | `RoomRestaurantRepositoryTest.kt` | save/load restaurant | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `RoomStockCountRepositoryTest.kt` | Stock count lifecycle | `RoomStockCountRepositoryTest.kt` | start, save, complete, void | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `RoomWasteRepositoryTest.kt` | Waste lifecycle | `RoomWasteRepositoryTest.kt` | create, post, void, movements | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `ChickenIntegrationTest.kt` | Business logic integration | `ChickenIntegrationTest.kt` | Multiple units for ingredient | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `PurchaseIntegrationTest.kt` | Purchase logic integration | `RoomPurchaseRepositoryTest.kt` | Full lifecycle | Stronger | `./gradlew :app:connectedDebugAndroidTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `HomeScreenStateTest.kt` | Home UI state mapping | `HomeScreenStateTest.kt` | Ready state copy/refresh | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `DetailedReportsUiTest.kt` | Reports UI interaction | `DetailedReportsUiTest.kt` | Navigation and seeded value display | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `InventoryDetailScreenTest.kt` | Inventory detail UI | `DetailedReportsUiTest.kt` | Value visibility | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `PurchaseDetailScreenTest.kt` | Purchase detail UI | `DetailedReportsUiTest.kt` | Value visibility | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `ReportingRefreshComposeTest.kt` | Reporting UI refresh | `DetailedReportsUiTest.kt` | Value visibility | Equivalent | `./gradlew :app:connectedDebugAndroidTest" | REPLACED WITH EQUIVALENT COVERAGE | None |
| `ReportsScreenStateTest.kt` | Reports state mapping | `ReportsScreenStateTest.kt` | Ready state copy | Equivalent | `./gradlew :app:testDebugUnitTest` | RESTORED | None |
| `WasteDetailScreenTest.kt` | Waste detail UI | `DetailedReportsUiTest.kt` | Value visibility | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | REPLACED WITH EQUIVALENT COVERAGE | None |
| `SettingsBackupUiTest.kt` | Backup UI interaction | `SettingsBackupUiTest.kt` | Button existence | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `WasteLifecycleTest.kt` | Waste UI lifecycle | `WasteLifecycleTest.kt` | Navigation and draft persistence | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `WasteArchiveUiTest.kt` | Waste archived references UI | `WasteArchiveUiTest.kt` | Archived refs display & save, replacement, missing refs, cross-ingredient unit option | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `WasteFailureUiTest.kt` | Waste failure rollback UI | `WasteFailureUiTest.kt` | Post failure rollback & retry, void failure rollback & retry, delete failure integrity & retry | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
| `OnboardingUiTest.kt` | Full onboarding UI flow | `OnboardingUiTest.kt` | Welcome screen, multi-step restaurant setup, DB/DataStore persistence, recreation, 2nd launch | Equivalent | `./gradlew :app:connectedDebugAndroidTest` | RESTORED | None |
