| Class | Method | File |
|---|---|---|
| ExampleUnitTest | addition_isCorrect | app/src/test/kotlin/com/venkoi/cuentame/ExampleUnitTest.kt |
| ArchitectureTest | enforcePackageBoundaries | app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt |
| ArchitectureTest | ruleModelToDatabaseViolationDetected | app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt |
| M | rulePureBackupToDatabaseViolationDetected | app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt |
| V | ruleDomainToComposeViolationDetected | app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt |
| V | ruleCoreToFeatureViolationDetected | app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt |
| P | aliasedForbiddenImportDetected | app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt |
| WasteMovementHistoryValidatorTest | validPostedHistory_passes | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | positiveWasteQuantity_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | wrongRestaurant_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | costWithoutTotal_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | incorrectTotalEquation_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | validVoidedHistory_passes | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | reversalOfReversal_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | zeroWasteQuantity_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | wrongIngredient_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | wrongArea_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | missingReversal_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | wrongReversalTarget_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| WasteMovementHistoryValidatorTest | wrongReversalOperationId_throws | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt |
| RoomDashboardRepositoryTest | empty data returns zeroed snapshot | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt |
| RoomDashboardRepositoryTest | calculate inventory valuation with multiple areas and negative quantities | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt |
| RoomDashboardRepositoryTest | invalid inventory decimal throws error | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt |
| RoomDashboardRepositoryTest | negative cost throws error | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt |
| RoomDashboardRepositoryTest | calculate purchase spend comparison correctly | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt |
| RoomDashboardRepositoryTest | waste value uses historical snapshots | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt |
| RoomDashboardRepositoryTest | null waste valuation throws error | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt |
| RoomDashboardRepositoryTest | completed count summary is independent of lines | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt |
| RoomDashboardRepositoryTest | recent activity uses structured data without fallback prose | app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt |
| FormattersTest | formatCurrency_US_Locale | app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt |
| FormattersTest | formatCurrency_ES_Locale | app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt |
| FormattersTest | formatCurrency_invalidCode_fallbackToLiteral | app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt |
| FormattersTest | formatPercent_US_Locale | app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt |
| FormattersTest | formatPercent_ES_Locale | app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt |
| FormattersTest | formatQuantity_stripsZeros | app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt |
| FormattersTest | formatQuantity_roundsToThreePlaces | app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt |
| DecimalPersistenceTest | toStorageString preserves plain format | app/src/test/kotlin/com/venkoi/cuentame/core/common/decimal/DecimalPersistenceTest.kt |
| DecimalPersistenceTest | toBigDecimalValue parses canonical string | app/src/test/kotlin/com/venkoi/cuentame/core/common/decimal/DecimalPersistenceTest.kt |
| DecimalPersistenceTest | toBigDecimalValue fails on invalid string | app/src/test/kotlin/com/venkoi/cuentame/core/common/decimal/DecimalPersistenceTest.kt |
| TimeProviderTest | now returns current time | app/src/test/kotlin/com/venkoi/cuentame/core/common/time/TimeProviderTest.kt |
| IdGeneratorTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/core/common/ids/IdGeneratorTest.kt |
| IdGeneratorTest | newId returns unique strings | app/src/test/kotlin/com/venkoi/cuentame/core/common/ids/IdGeneratorTest.kt |
| NameNormalizationTest | normalizeName trims and collapses spaces | app/src/test/kotlin/com/venkoi/cuentame/core/common/text/NameNormalizationTest.kt |
| NameNormalizationTest | normalizeName lowercases root locale | app/src/test/kotlin/com/venkoi/cuentame/core/common/text/NameNormalizationTest.kt |
| NameNormalizationTest | normalizeName handles empty string | app/src/test/kotlin/com/venkoi/cuentame/core/common/text/NameNormalizationTest.kt |
| BackupByteMathTest | addExact_success | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupByteMathTest.kt |
| BackupByteMathTest | addExact_overflow_throws | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupByteMathTest.kt |
| BackupByteMathTest | addExact_rejectsNegative | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupByteMathTest.kt |
| BackupSnapshotIntegrityNumericTest | rejects negative purchase quantity | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityNumericTest.kt |
| BackupSnapshotIntegrityNumericTest | rejects malformed decimal | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityNumericTest.kt |
| InsufficientStorageDetectionTest | detects ENOSPC from message | app/src/test/kotlin/com/venkoi/cuentame/core/backup/InsufficientStorageDetectionTest.kt |
| InsufficientStorageDetectionTest | detects SecurityException as PermissionDenied | app/src/test/kotlin/com/venkoi/cuentame/core/backup/InsufficientStorageDetectionTest.kt |
| InsufficientStorageDetectionTest | detects open failure as DestinationUnavailable | app/src/test/kotlin/com/venkoi/cuentame/core/backup/InsufficientStorageDetectionTest.kt |
| InsufficientStorageDetectionTest | falls back to GenericIo for generic IOException | app/src/test/kotlin/com/venkoi/cuentame/core/backup/InsufficientStorageDetectionTest.kt |
| BackupCreationPlannerTest | successful deterministic plan | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt |
| BackupCreationPlannerTest | plan ensures defensive copies of byte arrays | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt |
| BackupCreationPlannerTest | plan reflects attachment list from binding | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt |
| BackupCreationPlannerTest | maps missing restaurant to RestaurantDisappeared | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt |
| BackupCreationPlannerTest | maps reconciliation failure to LocaleReconciliationFailed | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt |
| BackupCreationPlannerTest | rejects plan if schema version mismatch | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt |
| BackupCreationPlannerTest | rejects plan if attachment ID is invalid | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt |
| BackupCreationPlannerTest | fails with MissingAttachmentSource when binding is missing | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt |
| BackupCleanupCoordinatorTest | returns Deleted when delete succeeds | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupCoordinatorTest.kt |
| BackupCleanupCoordinatorTest | returns Truncated when delete fails but truncate succeeds | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupCoordinatorTest.kt |
| BackupCleanupCoordinatorTest | returns Failed when both fail | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupCoordinatorTest.kt |
| BackupCleanupCoordinatorTest | returns Failed when delete throws | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupCoordinatorTest.kt |
| BackupArchiveWriterTest | writer releases resources and does not close underlying stream | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupArchiveWriterTest | writer rejects plan whose calculated total exceeds configured limit during prevalidation | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupArchiveWriterTest | database payload checksum mismatch rejected | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupArchiveWriterTest | preferences payload checksum mismatch rejected | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupArchiveWriterTest | writer rejects calculated total above configured limit during prevalidation | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupArchiveWriterTest | runtime cumulative limit exceeded when attachment streams more than planned and crosses limit | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupArchiveWriterTest | exact total limit succeeds | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupArchiveWriterTest | attachment checksum map disagreement rejected | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupArchiveWriterTest | attachment disappears after planning results in failure | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupArchiveWriterTest | writer detects attachment growth during write | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt |
| BackupSnapshotIntegrityProjectionTest | rejects balance projection mismatch | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityProjectionTest.kt |
| SupportedAppLocaleTest | languageTags contains all supported tags | app/src/test/kotlin/com/venkoi/cuentame/core/backup/SupportedAppLocaleTest.kt |
| SupportedAppLocaleTest | fromLanguageTag returns correct enum for valid tags | app/src/test/kotlin/com/venkoi/cuentame/core/backup/SupportedAppLocaleTest.kt |
| SupportedAppLocaleTest | fromLanguageTag returns null for invalid tags | app/src/test/kotlin/com/venkoi/cuentame/core/backup/SupportedAppLocaleTest.kt |
| AttachmentFilenameSanitizerTest | sanitize removes dangerous characters | app/src/test/kotlin/com/venkoi/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt |
| AttachmentFilenameSanitizerTest | sanitize preserves extensions | app/src/test/kotlin/com/venkoi/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt |
| AttachmentFilenameSanitizerTest | isValid validates correctly | app/src/test/kotlin/com/venkoi/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt |
| TestTagConsistencyTest | ensure no obsolete tags are used in android tests | app/src/test/kotlin/com/venkoi/cuentame/core/backup/TestTagConsistencyTest.kt |
| TestTagConsistencyTest | ensure critical tags exist in production source | app/src/test/kotlin/com/venkoi/cuentame/core/backup/TestTagConsistencyTest.kt |
| TestTagConsistencyTest | HomeUiTest must not reference reports_view_inventory_details without navigation | app/src/test/kotlin/com/venkoi/cuentame/core/backup/TestTagConsistencyTest.kt |
| TestTagConsistencyTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/core/backup/TestTagConsistencyTest.kt |
| ChecksumParserTest | parse empty input fails | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt |
| ChecksumParserTest | parse empty object fails | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt |
| fails | parse valid sorted object succeeds | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt |
| succeeds | parse duplicate key fails | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt |
| succeeds | Unknown | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt |
| succeeds | parse self reference fails | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt |
| succeeds | parse malformed escape fails | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt |
| succeeds | parse trailing comma fails | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt |
| succeeds | parse trailing content fails | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt |
| BackupManifestValidatorTest | valid manifest passes | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt |
| BackupManifestValidatorTest | rejects unsupported format version | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt |
| BackupManifestValidatorTest | rejects overlong attachment list | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt |
| BackupManifestValidatorTest | rejects invalid locale | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt |
| BackupManifestValidatorTest | rejects missing table metadata | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt |
| BackupSnapshotIntegrityValidatorTest | validate accepts valid simple snapshot | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt |
| BackupSnapshotIntegrityValidatorTest | validate rejects zero purchase quantity | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt |
| BackupSnapshotIntegrityValidatorTest | validate rejects balance projection mismatch | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt |
| BackupSnapshotIntegrityValidatorTest | validate rejects posted purchase receipt without movement | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt |
| BackupCleanupLifecycleTest | preflight failure does not open destination for write | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupLifecycleTest.kt |
| BackupCleanupLifecycleTest | creation failure triggers cleanup | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupLifecycleTest.kt |
| BackupCleanupLifecycleTest | failed deletion falls back to truncate | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupLifecycleTest.kt |
| BackupSnapshotIntegrityReversalTest | rejects reversal targeting another reversal | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityReversalTest.kt |
| ChecksumTest | ImmutableBackupBytes sha256 matches manual digest | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumTest.kt |
| ChecksumTest | sha256 is deterministic | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumTest.kt |
| BackupPlanTest | create with valid data succeeds | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt |
| BackupPlanTest | plan is immutable and performs defensive copies | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt |
| BackupPlanTest | create rejects total size mismatch | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt |
| BackupPlanTest | rejects manifest metadata mismatch | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt |
| BackupPlanTest | rejects duplicate manifest attachment ID | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt |
| BackupPlanTest | rejects attachment checksum mismatch with expected map | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt |
| AndroidBackupRepositoryTest | full successful orchestration sequence | app/src/test/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupRepositoryTest.kt |
| AndroidBackupRepositoryTest | cleans destination on planning failure | app/src/test/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupRepositoryTest.kt |
| BackupRoundTripTest | complete round trip with no attachments | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupRoundTripTest.kt |
| BackupRoundTripTest | complete round trip with shared attachments | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupRoundTripTest.kt |
| BackupRoundTripTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupRoundTripTest.kt |
| PlannedBackupAttachmentTest | create with valid data succeeds | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects invalid attachment ID | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects invalid display name | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects invalid checksum | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects empty references | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects duplicate references | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects unsupported record type | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects negative size | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects absolute path | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects traversal path | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| PlannedBackupAttachmentTest | create rejects blank reference record ID | app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt |
| ArchiveEntryValidatorTest | isSafe rejects absolute paths | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt |
| ArchiveEntryValidatorTest | isSafe rejects relative traversal | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt |
| ArchiveEntryValidatorTest | isSafe accepts simple alphanumeric paths | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt |
| ArchiveEntryValidatorTest | isSafe accepts valid attachment paths | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt |
| ArchiveEntryValidatorTest | isSafe rejects backslashes | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt |
| ArchiveEntryValidatorTest | isSafe rejects blank names | app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt |
| BackupArchiveValidatorAdversarialTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt |
| BackupArchiveValidatorAdversarialTest | rejects archive with missing manifest | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt |
| BackupArchiveValidatorAdversarialTest | rejects archive with duplicate entry | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt |
| BackupArchiveValidatorAdversarialTest | rejects archive with checksum key mismatch | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt |
| BackupArchiveValidatorAdversarialTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt |
| BackupArchiveValidatorAdversarialTest | rejects archive with schema version mismatch | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt |
| BackupArchiveValidatorAdversarialTest | rejects archive with checksum value mismatch | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt |
| BackupArchiveValidatorAdversarialTest | rejects archive with traversal path | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt |
| BackupArchiveValidatorAdversarialTest | rejects archive with unexpected entry | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt |
| BackupFilenameGeneratorTest | generates valid name with restaurant | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupFilenameGeneratorTest.kt |
| BackupFilenameGeneratorTest | generates valid name without restaurant | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupFilenameGeneratorTest.kt |
| BackupFilenameGeneratorTest | sanitizes restaurant name | app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupFilenameGeneratorTest.kt |
| IngredientUnitConverterTest | toBase converts case to lb | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/IngredientUnitConverterTest.kt |
| IngredientUnitConverterTest | fromBase converts lb to case | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/IngredientUnitConverterTest.kt |
| ReportingPeriodCalculatorTest | calculatePeriods_7Days | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt |
| ReportingPeriodCalculatorTest | calculatePeriods_30Days | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt |
| ReportingPeriodCalculatorTest | calculatePeriods_90Days | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt |
| ChickenFixtureTest | Chicken Breast fixture domain validation | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/ChickenFixtureTest.kt |
| CountComparisonCalculatorTest | calculateUnclassifiedUsage with simple values | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/CountComparisonCalculatorTest.kt |
| InventoryMovementServiceTest | createReversal returns negative quantity and references original | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/InventoryMovementServiceTest.kt |
| InventoryMovementServiceTest | cannot reverse a reversal | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/InventoryMovementServiceTest.kt |
| WeightedAverageCostCalculatorTest | calculate first purchase | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt |
| WeightedAverageCostCalculatorTest | calculate with existing inventory | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt |
| WeightedAverageCostCalculatorTest | calculate with zero cost purchase | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt |
| CountAdjustmentCalculatorTest | positive adjustment | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/CountAdjustmentCalculatorTest.kt |
| CountAdjustmentCalculatorTest | negative adjustment | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/CountAdjustmentCalculatorTest.kt |
| InventoryBalanceCalculatorTest | calculateBalance sums quantities | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/InventoryBalanceCalculatorTest.kt |
| InventoryBalanceCalculatorTest | calculateBalance handles empty list | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/InventoryBalanceCalculatorTest.kt |
| StandardUnitConverterTest | convert same units returns same value | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/StandardUnitConverterTest.kt |
| StandardUnitConverterTest | convert kg to grams | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/StandardUnitConverterTest.kt |
| StandardUnitConverterTest | convert lb to grams | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/StandardUnitConverterTest.kt |
| StandardUnitConverterTest | convert incompatible dimensions throws | app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/StandardUnitConverterTest.kt |
| ResolveAppStartStateUseCaseTest | both incomplete returns RequiresOnboarding | app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt |
| ResolveAppStartStateUseCaseTest | both complete returns Ready | app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt |
| ResolveAppStartStateUseCaseTest | db complete but locale mismatch repairs DataStore and returns Ready | app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt |
| ResolveAppStartStateUseCaseTest | db incomplete but DataStore says complete repairs DataStore and returns RequiresOnboarding | app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt |
| CompleteOnboardingUseCaseTest | Success updates DataStore and clears draft | app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/CompleteOnboardingUseCaseTest.kt |
| CompleteOnboardingUseCaseTest | AlreadyCompleted uses Room locale as authoritative | app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/CompleteOnboardingUseCaseTest.kt |
| PreviewWasteUseCaseTest | calculates preview correctly | app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/PreviewWasteUseCaseTest.kt |
| AppLocaleUseCaseTest | reconciler rethrows cancellation exception | app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/locale/AppLocaleUseCaseTest.kt |
| AppLocaleUseCaseTest | reconciler handles room failure as ordinary failure | app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/locale/AppLocaleUseCaseTest.kt |
| BackupViewModelTest | initial state is Idle | app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt |
| BackupViewModelTest | two simultaneous create requests only start one operation | app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt |
| BackupViewModelTest | reset cancels active preparation job | app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt |
| BackupViewModelTest | restored CREATING state shows OperationInterrupted error | app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt |
| BackupViewModelTest | stale repository emissions are ignored after reset | app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt |
| BackupViewModelTest | interrupted survives second recreation | app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt |
| BackupViewModelTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt |
| BackupViewModelTest | stale file selection is rejected | app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt |
| HomeScreenStateTest | Ready state can be copied and updated | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeScreenStateTest.kt |
| HomeViewModelTest | initial state sequence | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| HomeViewModelTest | initial repository failure triggers Error state | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| HomeViewModelTest | retry after initial failure resubscribes | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| HomeViewModelTest | coverage mapping handles edge cases | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| HomeViewModelTest | mapComparison handles states correctly | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| HomeViewModelTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| HomeViewModelTest | account switch resets to full Loading and hides old data | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| HomeViewModelTest | range refresh sequence | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| HomeViewModelTest | rapid range selection handles cancellation | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| HomeViewModelTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt |
| PurchaseDetailViewModelTest | void purchase success updates state to VOIDED | app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseListViewModelTest | list updates when repository emits new purchases | app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseListViewModelTest.kt |
| PurchaseListViewModelTest | search updates filter | app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseListViewModelTest.kt |
| PurchaseLineViewModelTest | initial state is loading then ready for new line | app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt |
| PurchaseLineViewModelTest | ingredient selection updates options and previews | app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt |
| PurchaseLineViewModelTest | rapid ingredient selection cancels previous work | app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt |
| PurchaseDraftViewModelTest | post purchase success emits event | app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseDraftViewModelTest.kt |
| PurchaseDraftViewModelTest | delete line success emits event | app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseDraftViewModelTest.kt |
| SupplierListViewModelTest | list updates when repository emits new suppliers | app/src/test/kotlin/com/venkoi/cuentame/feature/suppliers/viewmodel/SupplierListViewModelTest.kt |
| SupplierFormViewModelTest | save supplier success emits event | app/src/test/kotlin/com/venkoi/cuentame/feature/suppliers/viewmodel/SupplierFormViewModelTest.kt |
| IngredientDetailViewModelTest | initial state loads ingredient | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt |
| IngredientDetailViewModelTest | options are reactive | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt |
| IngredientDetailViewModelTest | add standard option emits success event | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt |
| IngredientDetailViewModelTest | add package option emits success event | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt |
| IngredientDetailViewModelTest | update package option emits success event | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt |
| IngredientDetailViewModelTest | archive option emits success event | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt |
| IngredientDetailViewModelTest | default change emits success event | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt |
| IngredientListViewModelTest | search filters ingredients with normalization | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt |
| IngredientListViewModelTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt |
| IngredientListViewModelTest | category filter filters ingredients | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt |
| IngredientListViewModelTest | archived toggle updates includeArchived flag | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt |
| IngredientFormViewModelTest | initial state is not loading | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt |
| IngredientFormViewModelTest | onSave fails with blank name | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt |
| IngredientFormViewModelTest | onSave fails without dimension and base unit in create mode | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt |
| IngredientFormViewModelTest | dimension selection resets base unit | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt |
| IngredientFormViewModelTest | edit mode hides unit mutation controls | app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt |
| WasteListViewModelTest | initial state shows events from repository | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteListViewModelTest.kt |
| WasteDetailViewModelTest | loading to ready state | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | not found state | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | invalid route state | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | ownership mismatch state | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt |
| WasteFormViewModelTest | initial state is Loading then Ready | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt |
| WasteFormViewModelTest | selecting ingredient updates unit options and handles loading state | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt |
| WasteFormViewModelTest | unit option repository failure produces error and disables save | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt |
| WasteFormViewModelTest | attachment permission failure handles error and preserves existing | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt |
| WasteFormViewModelTest | preview failure handles error and clears preview | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt |
| WasteRaceTest | ingredientRace_lastSelectionWins | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteRaceTest.kt |
| WasteRaceTest | previewCancellation_latestRequestWins | app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteRaceTest.kt |
| ControllableStockCountRepository | Delete during CREATE captures generated ID and removes line | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt |
| ControllableStockCountRepository | Queued save and delete complete in order without deadlock | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt |
| ControllableStockCountRepository | Flush during CREATE does not create duplicate line | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt |
| StockCountAreaViewModelTest | initial state loads correctly | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt |
| StockCountAreaViewModelTest | Archived unit becomes disabled after changing away | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt |
| StockCountAreaViewModelTest | untouched suggestions are not pending | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt |
| StockCountAreaViewModelTest | user edit makes line pending | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt |
| StockCountAreaViewModelTest | rapid unit selection preserves final selection | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt |
| StockCountAreaViewModelTest | invalid input blocks completion | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt |
| StockCountAreaViewModelTest | back navigation flushes pending saves | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt |
| StockCountAreaViewModelTest | stale save result does not overwrite newer state | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt |
| StartStockCountViewModelTest | initial state loads areas | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt |
| StartStockCountViewModelTest | area selection works | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt |
| StartStockCountViewModelTest | start count fails with future date | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt |
| StartStockCountViewModelTest | start count succeeds with valid input | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt |
| StartStockCountViewModelTest | overlapping area is disabled | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt |
| StockCountDetailViewModelTest | initial state loads correctly | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt |
| StockCountDetailViewModelTest | ownership mismatch state works | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt |
| StockCountDetailViewModelTest | not found state works | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt |
| StockCountDetailViewModelTest | complete count resets state after success | app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt |
| OnboardingViewModelTest | initial state loads defaults when no draft exists | app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt |
| OnboardingViewModelTest | autosave persists changes with debounce for name | app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt |
| OnboardingViewModelTest | autosave persists selections immediately | app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt |
| OnboardingViewModelTest | next step persists the NEW step immediately | app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt |
| OnboardingViewModelTest | reordering updates sort order and persists immediately | app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt |
| OnboardingViewModelTest | draft save failure is visible in UI state | app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt |
| ReportsScreenStateTest | readyStateCanBeCopied | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/ReportsScreenStateTest.kt |
| WasteDetailViewModelTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | missing range defaults to 30 days | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | malformed range defaults to 30 days | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | initial state sequence | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | initial error and retry | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | range change triggers refreshing sequence with distinct periods | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | refresh failure preserves old data | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| WasteDetailViewModelTest | account switch resets identity | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | Unknown | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | missing range defaults to 30 days | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | malformed range defaults to 30 days | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | initial state is Loading then SetupRequired when no restaurant | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | Ready state success sequence | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | initial repository failure triggers Error state | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | initial retry resubscribes | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | range change triggers refreshing sequence with distinct periods | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | selecting same range does not trigger new request | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| PurchaseDetailViewModelTest | account switch resets to Loading | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt |
| InventoryDetailViewModelTest | initial state is Loading then SetupRequired when no restaurant | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt |
| InventoryDetailViewModelTest | Ready state refresh sequence | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt |
| InventoryDetailViewModelTest | account switch resets to full Loading and hides old data | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt |
| InventoryDetailViewModelTest | refresh failure preserves old data | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt |
| ReportsViewModelTest | initial state is Loading then SetupRequired when no restaurant | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt |
| ReportsViewModelTest | initial repository failure triggers Error state | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt |
| ReportsViewModelTest | retry after initial failure resubscribes | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt |
| ReportsViewModelTest | Ready state refresh sequence | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt |
| ReportsViewModelTest | account switch resets to full Loading and hides old data | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt |
| ReportsViewModelTest | mapping coverage and fields | app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt |
| ExampleInstrumentedTest | useAppContext | app/src/androidTest/kotlin/com/venkoi/cuentame/ExampleInstrumentedTest.kt |
| NavigationTest | app_startsOnOnboarding_whenNoRestaurant | app/src/androidTest/kotlin/com/venkoi/cuentame/NavigationTest.kt |
| NavigationTest | app_startsOnHome_whenRestaurantExists | app/src/androidTest/kotlin/com/venkoi/cuentame/NavigationTest.kt |
| NavigationTest | navigateToSettingsAndBack | app/src/androidTest/kotlin/com/venkoi/cuentame/NavigationTest.kt |
| MigrationTest | migrate1To2 | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/MigrationTest.kt |
| ParentUpdateTest | updateReceiptPreservesLines | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/ParentUpdateTest.kt |
| DatabaseTest | writeAndReadRestaurant | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/DatabaseTest.kt |
| DatabaseTest | seedUnitsExist | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/DatabaseTest.kt |
| DatabaseTest | movementIdempotency | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/DatabaseTest.kt |
| DatabaseSeedingTest | unitsAreSeededSynchronouslyOnCreate | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/DatabaseSeedingTest.kt |
| ReversalTest | onlyOneReversalAllowedPerMovement | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/ReversalTest.kt |
| BackupIsolationTest | createSnapshot_isolatesAllTablesByRestaurant | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/BackupIsolationTest.kt |
| RoomWasteRepositoryTest | fullLifecycle_create_post_void | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomWasteRepositoryTest.kt |
| RoomDetailedReportsRepositoryTest | observeInventoryDetails_aggregatesAreasAndCalculatesValue | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt |
| RoomDetailedReportsRepositoryTest | observeInventoryDetails_alertOnlyRow_aggregateZeroButNegativeArea | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt |
| RoomDetailedReportsRepositoryTest | observeInventoryDetails_excludedRow_allZeroNoNegative | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt |
| RoomDetailedReportsRepositoryTest | observeInventoryDetails_missingCost_onlyForStocked | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt |
| RoomDetailedReportsRepositoryTest | observeInventoryDetails_zeroCost_isValid | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt |
| RoomDetailedReportsRepositoryTest | observeInventoryDetails_strictDecimalValidation | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt |
| RoomDetailedReportsRepositoryTest | observePurchaseDetails_strictDecimalValidation | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt |
| RoomDetailedReportsRepositoryTest | observeWasteDetails_strictSnapshotValidation | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt |
| RoomStockCountRepositoryTest | fullLifecycle_start_save_complete_void | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomStockCountRepositoryTest.kt |
| RoomRestaurantRepositoryTest | save_newRestaurant_inserts | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomRestaurantRepositoryTest.kt |
| RoomRestaurantRepositoryTest | save_existingRestaurant_updates | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomRestaurantRepositoryTest.kt |
| RoomInventoryAreaRepositoryTest | archiveFinalArea_throwsError | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt |
| RoomInventoryAreaRepositoryTest | archiveFinalArea_scopedByRestaurant | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt |
| RoomInventoryAreaRepositoryTest | reorder_updatesSortOrderContiguously | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt |
| RoomInventoryAreaRepositoryTest | reorder_subset_throwsError | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt |
| RoomInventoryAreaRepositoryTest | reorder_otherRestaurant_throwsError | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt |
| RoomInventorySnapshotServiceTest | calculateAt_noHistory_returnsEmpty | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt |
| RoomInventorySnapshotServiceTest | calculateAt_withHistory_returnsSnapshot | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt |
| RoomInventorySnapshotServiceTest | calculateAt_reversal_cancelsOriginal | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt |
| RoomInventorySnapshotServiceTest | calculateAt_futureReversal_doesNotCancel | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt |
| RoomInventorySnapshotServiceTest | calculateAt_historyWithoutCost_returnsNullCost | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt |
| RoomDashboardRepositoryIntegrationTest | observeDashboard_emptyDatabase_returnsZeros | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryIntegrationTest.kt |
| SupplierRepositoryTest | createSupplier_succeeds | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt |
| SupplierRepositoryTest | createSupplier_failsOnDuplicateName | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt |
| SupplierRepositoryTest | updateSupplier_updatesAllowedFieldsOnly | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt |
| SupplierRepositoryTest | createSupplier_failsOnOwnershipMismatch | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt |
| SupplierRepositoryTest | archiveSupplier_removesFromActiveList | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt |
| ReportingIsolationTest | inventoryDetails_hardenedIsolation_excludesCrossRestaurantMetadata | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/ReportingIsolationTest.kt |
| ReportingIsolationTest | purchaseDetails_hardenedIsolation_excludesCrossRestaurantSuppliers | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/ReportingIsolationTest.kt |
| ReportingIsolationTest | wasteDetails_hardenedIsolation_excludesCrossRestaurantMetadata | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/ReportingIsolationTest.kt |
| ReportingIsolationTest | recentWasteActivity_hardenedIsolation_excludesCrossRestaurantMetadata | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/ReportingIsolationTest.kt |
| RoomPurchaseRepositoryTest | fullLifecycle_draft_to_posted_to_void | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomPurchaseRepositoryTest.kt |
| RoomIngredientRepositoryTest | createIngredientWithBaseOption_succeeds | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomIngredientRepositoryTest.kt |
| RoomInventorySnapshotServiceFailureTest | calculateAt_reversalWithoutTarget_throws | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceFailureTest.kt |
| RoomInventorySnapshotServiceFailureTest | calculateAt_reversalOfReversal_throws | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceFailureTest.kt |
| BackupHardeningRepositoryTest | purchasePost_failure_afterMovements_rollsBack | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/BackupHardeningRepositoryTest.kt |
| BackupHardeningRepositoryTest | purchasePost_failure_afterProjections_rollsBack | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/BackupHardeningRepositoryTest.kt |
| BackupHardeningRepositoryTest | purchasePost_failure_afterMarkPosted_rollsBack | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/BackupHardeningRepositoryTest.kt |
| BackupHardeningRepositoryTest | purchaseVoid_failure_afterReversals_rollsBack | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/BackupHardeningRepositoryTest.kt |
| BackupHardeningRepositoryTest | purchaseVoid_failure_afterProjections_rollsBack | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/BackupHardeningRepositoryTest.kt |
| BackupHardeningRepositoryTest | purchaseVoid_failure_afterMarkVoided_rollsBack | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/BackupHardeningRepositoryTest.kt |
| DashboardDaoTest | valuationRows_isolatesByRestaurant | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt |
| DashboardDaoTest | spendRows_filtersByStatusAndDate | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt |
| DashboardDaoTest | wasteValueRows_filtersByStatusAndDate | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt |
| DashboardDaoTest | stockCountSummaries_independentOfLines | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt |
| DashboardDaoTest | adjustedLineCount_providesPersistedValues | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt |
| DashboardDaoTest | recentActivity_deterministicOrdering | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt |
| BackupDaoTest | createSnapshot_includesAllTables | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/BackupDaoTest.kt |
| DetailedReportsDaoTest | inventoryValuationRows_joinsMetadata | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt |
| DetailedReportsDaoTest | inventoryValuationRows_includesNegativeBalances | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt |
| DetailedReportsDaoTest | purchaseSpendRows_filtersByStatus | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt |
| DetailedReportsDaoTest | purchaseSpendRows_filtersByDateRange | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt |
| DetailedReportsDaoTest | wasteValueRows_filtersByStatus | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt |
| RoomMigrationTest | migrate1To2_preservesData_supportsNullCost_andOpensInRoom | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/migration/RoomMigrationTest.kt |
| RoomMigrationTest | createDatabaseDirectlyAtVersion2 | app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/migration/RoomMigrationTest.kt |
| DataStoreAppPreferencesRepositoryTest | saveAndObservePreferences | app/src/androidTest/kotlin/com/venkoi/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt |
| DataStoreAppPreferencesRepositoryTest | saveAndLoadDraft | app/src/androidTest/kotlin/com/venkoi/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt |
| DataStoreAppPreferencesRepositoryTest | corruptDraft_isRemovedSynchronously | app/src/androidTest/kotlin/com/venkoi/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt |
| DataStoreAppPreferencesRepositoryTest | unsupportedVersion_isRemovedSynchronously | app/src/androidTest/kotlin/com/venkoi/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt |
| AndroidBackupDocumentStoreTest | openForWrite_validFileUri_returnsStream | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt |
| AndroidBackupDocumentStoreTest | openForRead_validFileUri_returnsStream | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt |
| AndroidBackupDocumentStoreTest | openForRead_nonExistent_throwsWrapped | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt |
| AndroidBackupDocumentStoreTest | truncate_validFile_emptiesFile | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt |
| AndroidBackupDocumentStoreTest | closeSuppressing_attachesFailure | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt |
| AndroidBackupDocumentStoreTest | closeSuppressing_rethrowsFatal | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt |
| AndroidBackupDocumentStoreTest | delete_validFile_returnsTrue | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt |
| AndroidBackupDocumentStoreTest | openStream_securityException_isPropagated | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt |
| AndroidBackupDocumentStoreTest | openStream_nullDescriptor_throwsOpenException | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt |
| AndroidBackupRepositoryTest | createBackup_successful_sequence | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupRepositoryTest.kt |
| BackupProductionIntegrationTest | fullBackupPipeline_producesValidArchive | app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/BackupProductionIntegrationTest.kt |
| PurchaseIntegrationTest | insertAndReadPurchase | app/src/androidTest/kotlin/com/venkoi/cuentame/core/domain/service/PurchaseIntegrationTest.kt |
| ChickenIntegrationTest | createChickenIngredientWithMultipleUnits | app/src/androidTest/kotlin/com/venkoi/cuentame/core/domain/service/ChickenIntegrationTest.kt |
| Unknown | Unknown | app/src/androidTest/kotlin/com/venkoi/cuentame/test/di/TestStorageModule.kt |
| SettingsBackupUiTest | createBackup_buttonExists | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/settings/ui/SettingsBackupUiTest.kt |
| HomeUiTest | dashboard_emptyState_whenRestaurantConfiguredAndNoActivity | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/home/HomeUiTest.kt |
| HomeUiTest | dashboard_fullVerification_populatedData | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/home/HomeUiTest.kt |
| HomeUiTest | dashboard_navigation_to_reports | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/home/HomeUiTest.kt |
| PurchaseFailureUiTest | purchasePost_rollback_andRetry_onFailure | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/purchases/ui/PurchaseFailureUiTest.kt |
| PurchaseUiTest | complete_purchase_lifecycle | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/purchases/ui/PurchaseUiTest.kt |
| IngredientUiTest | complete_ingredient_e2e_flow | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/ingredients/ui/IngredientUiTest.kt |
| IngredientDetailUiTest | archive_ingredient_flow | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/ingredients/ui/IngredientDetailUiTest.kt |
| WasteFailureUiTest | wastePost_failureRollback_andRetrySuccess | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteFailureUiTest.kt |
| WasteFailureUiTest | wasteVoid_failureRollback_andRetrySuccess | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteFailureUiTest.kt |
| WasteFailureUiTest | wasteDelete_failureRollback_andRetrySuccess | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteFailureUiTest.kt |
| WasteArchiveUiTest | wasteForm_existingArchivedReferences_displayedAndPreservedOnSave | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt |
| WasteArchiveUiTest | wasteForm_changingAwayFromArchivedReferences_persistsActiveValues | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt |
| WasteArchiveUiTest | wasteForm_missingIngredientReference_showsSafeErrorAndDisablesSave | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt |
| WasteArchiveUiTest | wasteForm_missingAreaReference_showsSafeError | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt |
| WasteArchiveUiTest | wasteForm_missingUnitOptionReference_showsSafeError | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt |
| WasteArchiveUiTest | wasteForm_crossIngredientUnitOption_showsSafeErrorAndPreventsSave | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt |
| WasteLifecycleTest | navigateToWasteAndBack | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteLifecycleTest.kt |
| StockCountLifecycleTest | full_lifecycle_test | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/counts/ui/StockCountLifecycleTest.kt |
| StockCountUiTest | start_count_flow | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/counts/ui/StockCountUiTest.kt |
| OnboardingUiTest | onboarding_start_navigation | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/onboarding/ui/OnboardingUiTest.kt |
| OnboardingUiTest | onboarding_fullFlow_persistsRestaurantAndNavigatesToHome | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/onboarding/ui/OnboardingUiTest.kt |
| DetailedReportsUiTest | reports_display_seeded_values | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/DetailedReportsUiTest.kt |
| PurchaseDetailScreenTest | purchaseDetail_exists | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/PurchaseDetailScreenTest.kt |
| WasteDetailScreenTest | wasteDetail_exists | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/WasteDetailScreenTest.kt |
| InventoryDetailScreenTest | inventoryDetail_exists | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/InventoryDetailScreenTest.kt |
| ReportsUiTest | reports_populatedData_verification | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt |
| ReportsUiTest | reports_rangeSwitching_7_days | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt |
| ReportsUiTest | reports_rangeSwitching_90_days | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt |
| ReportsUiTest | reports_navigation_homeToReports_andBack | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt |
| ReportsUiTest | reportsOverview_rangeRefresh_noFlicker | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt |
| ReportingRefreshComposeTest | reportsRefresh_exists | app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportingRefreshComposeTest.kt |
