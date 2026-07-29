# Test Coverage Ledger

This document tracks the restoration and adaptation of tests that were deleted or modified during the backup subsystem refactor and single-module consolidation.

| Original test path | Original behavior covered | Restored test path | Replacement test path, if moved | Assertions Equivalent/Stronger | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ZIP entry path safety | `app/src/test/kotlin/com/miara/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | | Stronger | RESTORED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt` | Attachment filename sanitization | `app/src/test/kotlin/com/miara/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt` | | Equivalent | RESTORED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupFilenameGeneratorTest.kt` | Backup filename generation logic | `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupFilenameGeneratorTest.kt` | | Equivalent | RESTORED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestHardeningTest.kt` | Manifest field limits and types | | `BackupManifestValidatorTest.kt` | Equivalent | REPLACED WITH EQUIVALENT COVERAGE |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestTest.kt` | Manifest serialization | | `BackupRoundTripTest.kt` | Stronger | REPLACED WITH EQUIVALENT COVERAGE |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestValidatorTest.kt` | Manifest business rules | `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestValidatorTest.kt` | | Equivalent | RESTORED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityNumericTest.kt` | Snapshot decimal/range validation | | `BackupSnapshotIntegrityValidatorTest.kt` | Equivalent | REPLACED WITH EQUIVALENT COVERAGE |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityProjectionTest.kt` | Projection sum validation | | `BackupSnapshotIntegrityValidatorTest.kt` | Equivalent | REPLACED WITH EQUIVALENT COVERAGE |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityReversalTest.kt` | Reversal graph consistency | | `BackupSnapshotIntegrityValidatorTest.kt` | Equivalent | REPLACED WITH EQUIVALENT COVERAGE |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | Snapshot FK and business rules | `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | | Equivalent | RESTORED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityVoidedCountTest.kt` | Voided count lifecycle | | `BackupSnapshotIntegrityValidatorTest.kt` | Equivalent | REPLACED WITH EQUIVALENT COVERAGE |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | character-by-character JSON parsing | `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | | Equivalent | RESTORED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumTest.kt` | Checksum logic | `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumTest.kt` | | Stronger | RESTORED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | Storage failure classification | `app/src/test/kotlin/com/miara/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | | Equivalent | RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/NavigationTest.kt` | App navigation flow | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/ArchiveDeterminismTest.kt` | Identical inputs produce identical bytes | `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupRoundTripTest.kt` | | Equivalent | RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/BackupProductionIntegrationTest.kt` | Full pipeline with Room/DataStore | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/BackupRoundTripTest.kt` | Backup/Restore integration | `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupRoundTripTest.kt` | | Stronger (JVM) | RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/BackupValidatorAdversarialTest.kt` | Adversarial archives (Android-side) | `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | | Stronger (JVM) | RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/ProjectionRebuildTest.kt` | Projection maintenance | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/IngredientRepositoryTest.kt` | Ingredient CRUD and search | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/PurchaseRepositoryTest.kt` | Purchase lifecycle | `app/src/androidTest/kotlin/com/miara/cuentame/core/domain/service/PurchaseIntegrationTest.kt` | | Partial | RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryIntegrationTest.kt` | Dashboard aggregation | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomLocalSetupRepositoryTest.kt` | Onboarding persistence | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomStockCountRepositoryTest.kt` | Stock count lifecycle | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomWasteRepositoryTest.kt` | Waste lifecycle | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/domain/service/ChickenIntegrationTest.kt` | Business logic integration | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/domain/service/PurchaseIntegrationTest.kt` | Purchase logic integration | `app/src/androidTest/kotlin/com/miara/cuentame/core/domain/service/PurchaseIntegrationTest.kt` | | Equivalent | RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/home/HomeScreenStateTest.kt` | Home UI state mapping | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/DetailedReportsUiTest.kt` | Reports UI interaction | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/InventoryDetailScreenTest.kt` | Inventory detail UI | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/PurchaseDetailScreenTest.kt` | Purchase detail UI | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/ReportingRefreshComposeTest.kt` | Reporting UI refresh | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/ReportsScreenStateTest.kt` | Reports state mapping | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/WasteDetailScreenTest.kt` | Waste detail UI | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/settings/ui/SettingsBackupUiTest.kt` | Backup UI interaction | | | | NOT RESTORED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteLifecycleTest.kt` | Waste UI lifecycle | | | | NOT RESTORED |
