# Baseline Test Inventory (TEST_BASELINE_BEFORE_FINAL_GATE.md)

This document provides a complete, non-truncated inventory of every test file, class, `@Test` method, execution type, and subsystem.

- **Total Test Classes**: 68
- **Total Test Methods**: 257
- **Ignored / Disabled Methods**: 0

--- 

| Subsystem | Type | Class Name | Test Method | Ignored |
| --- | --- | --- | --- | --- |
| Core | JVM | `ArchitectureTest` | `enforcePackageBoundaries` | NO |
| Core | JVM | `ArchitectureTest` | `ruleModelToDatabaseViolationDetected` | NO |
| Core | JVM | `ArchitectureTest` | `rulePureBackupToDatabaseViolationDetected` | NO |
| Core | JVM | `ArchitectureTest` | `ruleDomainToComposeViolationDetected` | NO |
| Core | JVM | `ArchitectureTest` | `ruleCoreToFeatureViolationDetected` | NO |
| Core | JVM | `ArchitectureTest` | `aliasedForbiddenImportDetected` | NO |
| Core | JVM | `ExampleUnitTest` | `addition_isCorrect` | NO |
| Core: Backup | JVM | `AndroidBackupRepositoryTest` | ``full successful orchestration sequence`` | NO |
| Core: Backup | JVM | `AndroidBackupRepositoryTest` | ``cleans destination on planning failure`` | NO |
| Core: Backup | JVM | `ArchiveEntryValidatorTest` | ``isSafe rejects absolute paths`` | NO |
| Core: Backup | JVM | `ArchiveEntryValidatorTest` | ``isSafe rejects relative traversal`` | NO |
| Core: Backup | JVM | `ArchiveEntryValidatorTest` | ``isSafe accepts simple alphanumeric paths`` | NO |
| Core: Backup | JVM | `ArchiveEntryValidatorTest` | ``isSafe accepts valid attachment paths`` | NO |
| Core: Backup | JVM | `ArchiveEntryValidatorTest` | ``isSafe rejects backslashes`` | NO |
| Core: Backup | JVM | `ArchiveEntryValidatorTest` | ``isSafe rejects blank names`` | NO |
| Core: Backup | JVM | `AttachmentFilenameSanitizerTest` | ``sanitize removes dangerous characters`` | NO |
| Core: Backup | JVM | `AttachmentFilenameSanitizerTest` | ``sanitize preserves extensions`` | NO |
| Core: Backup | JVM | `AttachmentFilenameSanitizerTest` | ``isValid validates correctly`` | NO |
| Core: Backup | JVM | `BackupArchiveValidatorAdversarialTest` | ``positive control - valid archive passes`` | NO |
| Core: Backup | JVM | `BackupArchiveValidatorAdversarialTest` | ``rejects archive with missing manifest`` | NO |
| Core: Backup | JVM | `BackupArchiveValidatorAdversarialTest` | ``rejects archive with duplicate entry`` | NO |
| Core: Backup | JVM | `BackupArchiveValidatorAdversarialTest` | ``rejects archive with checksum key mismatch`` | NO |
| Core: Backup | JVM | `BackupArchiveValidatorAdversarialTest` | ``rejects archive with malformed UTF-8 manifest`` | NO |
| Core: Backup | JVM | `BackupArchiveValidatorAdversarialTest` | ``rejects archive with schema version mismatch`` | NO |
| Core: Backup | JVM | `BackupArchiveWriterTest` | ``writer releases resources and does not close underlying stream`` | NO |
| Core: Backup | JVM | `BackupArchiveWriterTest` | ``writer rejects plan with checksum map mismatch`` | NO |
| Core: Backup | JVM | `BackupArchiveWriterTest` | ``writer detects attachment growth during write`` | NO |
| Core: Backup | JVM | `BackupByteMathTest` | `addExact_success` | NO |
| Core: Backup | JVM | `BackupByteMathTest` | `addExact_overflow_throws` | NO |
| Core: Backup | JVM | `BackupByteMathTest` | `addExact_rejectsNegative` | NO |
| Core: Backup | JVM | `BackupCleanupCoordinatorTest` | ``returns Deleted when delete succeeds`` | NO |
| Core: Backup | JVM | `BackupCleanupCoordinatorTest` | ``returns Truncated when delete fails but truncate succeeds`` | NO |
| Core: Backup | JVM | `BackupCleanupCoordinatorTest` | ``returns Failed when both fail`` | NO |
| Core: Backup | JVM | `BackupCleanupCoordinatorTest` | ``returns Failed when delete throws`` | NO |
| Core: Backup | JVM | `BackupCleanupLifecycleTest` | ``preflight failure does not open destination for write`` | NO |
| Core: Backup | JVM | `BackupCleanupLifecycleTest` | ``creation failure triggers cleanup`` | NO |
| Core: Backup | JVM | `BackupCleanupLifecycleTest` | ``failed deletion falls back to truncate`` | NO |
| Core: Backup | JVM | `BackupCreationPlannerTest` | ``successful deterministic plan`` | NO |
| Core: Backup | JVM | `BackupCreationPlannerTest` | ``plan ensures defensive copies of byte arrays`` | NO |
| Core: Backup | JVM | `BackupCreationPlannerTest` | ``plan reflects attachment list from binding`` | NO |
| Core: Backup | JVM | `BackupCreationPlannerTest` | ``rejects plan if schema version mismatch`` | NO |
| Core: Backup | JVM | `BackupFilenameGeneratorTest` | ``generates valid name with restaurant`` | NO |
| Core: Backup | JVM | `BackupFilenameGeneratorTest` | ``generates valid name without restaurant`` | NO |
| Core: Backup | JVM | `BackupFilenameGeneratorTest` | ``sanitizes restaurant name`` | NO |
| Core: Backup | JVM | `BackupManifestValidatorTest` | ``valid manifest passes`` | NO |
| Core: Backup | JVM | `BackupManifestValidatorTest` | ``rejects unsupported format version`` | NO |
| Core: Backup | JVM | `BackupManifestValidatorTest` | ``rejects overlong attachment list`` | NO |
| Core: Backup | JVM | `BackupManifestValidatorTest` | ``rejects invalid locale`` | NO |
| Core: Backup | JVM | `BackupManifestValidatorTest` | ``rejects missing table metadata`` | NO |
| Core: Backup | JVM | `BackupRoundTripTest` | ``complete round trip with no attachments`` | NO |
| Core: Backup | JVM | `BackupRoundTripTest` | ``complete round trip with shared attachments`` | NO |
| Core: Backup | JVM | `BackupRoundTripTest` | ``archive determinism - identical inputs produce identical bytes`` | NO |
| Core: Backup | JVM | `BackupSnapshotIntegrityNumericTest` | ``rejects negative purchase quantity`` | NO |
| Core: Backup | JVM | `BackupSnapshotIntegrityNumericTest` | ``rejects malformed decimal`` | NO |
| Core: Backup | JVM | `BackupSnapshotIntegrityProjectionTest` | ``rejects balance projection mismatch`` | NO |
| Core: Backup | JVM | `BackupSnapshotIntegrityReversalTest` | ``rejects reversal targeting another reversal`` | NO |
| Core: Backup | JVM | `BackupSnapshotIntegrityValidatorTest` | ``validate accepts valid simple snapshot`` | NO |
| Core: Backup | JVM | `BackupSnapshotIntegrityValidatorTest` | ``validate rejects zero purchase quantity`` | NO |
| Core: Backup | JVM | `BackupSnapshotIntegrityValidatorTest` | ``validate rejects balance projection mismatch`` | NO |
| Core: Backup | JVM | `BackupSnapshotIntegrityValidatorTest` | ``validate rejects posted purchase receipt without movement`` | NO |
| Core: Backup | JVM | `ChecksumParserTest` | ``parse empty input fails`` | NO |
| Core: Backup | JVM | `ChecksumParserTest` | ``parse empty object fails`` | NO |
| Core: Backup | JVM | `ChecksumParserTest` | ``parse valid sorted object succeeds`` | NO |
| Core: Backup | JVM | `ChecksumParserTest` | ``parse duplicate key fails`` | NO |
| Core: Backup | JVM | `ChecksumParserTest` | ``parse non-hex hash fails`` | NO |
| Core: Backup | JVM | `ChecksumParserTest` | ``parse self reference fails`` | NO |
| Core: Backup | JVM | `ChecksumParserTest` | ``parse malformed escape fails`` | NO |
| Core: Backup | JVM | `ChecksumParserTest` | ``parse trailing comma fails`` | NO |
| Core: Backup | JVM | `ChecksumParserTest` | ``parse trailing content fails`` | NO |
| Core: Backup | JVM | `ChecksumTest` | ``ImmutableBackupBytes sha256 matches manual digest`` | NO |
| Core: Backup | JVM | `ChecksumTest` | ``sha256 is deterministic`` | NO |
| Core: Backup | JVM | `InsufficientStorageDetectionTest` | ``detects ENOSPC from message`` | NO |
| Core: Backup | JVM | `InsufficientStorageDetectionTest` | ``detects SecurityException as PermissionDenied`` | NO |
| Core: Backup | JVM | `InsufficientStorageDetectionTest` | ``detects open failure as DestinationUnavailable`` | NO |
| Core: Backup | JVM | `InsufficientStorageDetectionTest` | ``falls back to GenericIo for generic IOException`` | NO |
| Core: Backup | JVM | `SupportedAppLocaleTest` | ``languageTags contains all supported tags`` | NO |
| Core: Backup | JVM | `SupportedAppLocaleTest` | ``fromLanguageTag returns correct enum for valid tags`` | NO |
| Core: Backup | JVM | `SupportedAppLocaleTest` | ``fromLanguageTag returns null for invalid tags`` | NO |
| Core | JVM | `DecimalPersistenceTest` | ``toStorageString preserves plain format`` | NO |
| Core | JVM | `DecimalPersistenceTest` | ``toBigDecimalValue parses canonical string`` | NO |
| Core | JVM | `DecimalPersistenceTest` | ``toBigDecimalValue fails on invalid string`` | NO |
| Core | JVM | `IdGeneratorTest` | ``newId returns non-empty string`` | NO |
| Core | JVM | `IdGeneratorTest` | ``newId returns unique strings`` | NO |
| Core | JVM | `NameNormalizationTest` | ``normalizeName trims and collapses spaces`` | NO |
| Core | JVM | `NameNormalizationTest` | ``normalizeName lowercases root locale`` | NO |
| Core | JVM | `NameNormalizationTest` | ``normalizeName handles empty string`` | NO |
| Core | JVM | `TimeProviderTest` | ``now returns current time`` | NO |
| Core: Database | JVM | `RoomDashboardRepositoryTest` | ``empty data returns zeroed snapshot`` | NO |
| Core: Database | JVM | `RoomDashboardRepositoryTest` | ``calculate inventory valuation with multiple areas and negative quantities`` | NO |
| Core: Database | JVM | `RoomDashboardRepositoryTest` | ``invalid inventory decimal throws error`` | NO |
| Core: Database | JVM | `RoomDashboardRepositoryTest` | ``negative cost throws error`` | NO |
| Core: Database | JVM | `RoomDashboardRepositoryTest` | ``calculate purchase spend comparison correctly`` | NO |
| Core: Database | JVM | `RoomDashboardRepositoryTest` | ``waste value uses historical snapshots`` | NO |
| Core: Database | JVM | `RoomDashboardRepositoryTest` | ``null waste valuation throws error`` | NO |
| Core: Database | JVM | `RoomDashboardRepositoryTest` | ``completed count summary is independent of lines`` | NO |
| Core: Database | JVM | `RoomDashboardRepositoryTest` | ``recent activity uses structured data without fallback prose`` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `validPostedHistory_passes` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `positiveWasteQuantity_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `wrongRestaurant_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `costWithoutTotal_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `incorrectTotalEquation_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `validVoidedHistory_passes` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `reversalOfReversal_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `zeroWasteQuantity_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `wrongIngredient_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `wrongArea_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `missingReversal_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `wrongReversalTarget_throws` | NO |
| Core: Database | JVM | `WasteMovementHistoryValidatorTest` | `wrongReversalOperationId_throws` | NO |
| Core | JVM | `FormattersTest` | `formatCurrency_US_Locale` | NO |
| Core | JVM | `FormattersTest` | `formatCurrency_ES_Locale` | NO |
| Core | JVM | `FormattersTest` | `formatCurrency_invalidCode_fallbackToLiteral` | NO |
| Core | JVM | `FormattersTest` | `formatPercent_US_Locale` | NO |
| Core | JVM | `FormattersTest` | `formatPercent_ES_Locale` | NO |
| Core | JVM | `FormattersTest` | `formatQuantity_stripsZeros` | NO |
| Core | JVM | `FormattersTest` | `formatQuantity_roundsToThreePlaces` | NO |
| Core: Domain | JVM | `ChickenFixtureTest` | ``Chicken Breast fixture domain validation`` | NO |
| Core: Domain | JVM | `CountAdjustmentCalculatorTest` | ``positive adjustment`` | NO |
| Core: Domain | JVM | `CountAdjustmentCalculatorTest` | ``negative adjustment`` | NO |
| Core: Domain | JVM | `CountComparisonCalculatorTest` | ``calculateUnclassifiedUsage with simple values`` | NO |
| Core: Domain | JVM | `IngredientUnitConverterTest` | ``toBase converts case to lb`` | NO |
| Core: Domain | JVM | `IngredientUnitConverterTest` | ``fromBase converts lb to case`` | NO |
| Core: Domain | JVM | `InventoryBalanceCalculatorTest` | ``calculateBalance sums quantities`` | NO |
| Core: Domain | JVM | `InventoryBalanceCalculatorTest` | ``calculateBalance handles empty list`` | NO |
| Core: Domain | JVM | `InventoryMovementServiceTest` | ``createReversal returns negative quantity and references original`` | NO |
| Core: Domain | JVM | `InventoryMovementServiceTest` | ``cannot reverse a reversal`` | NO |
| Core: Domain | JVM | `ReportingPeriodCalculatorTest` | `calculatePeriods_7Days` | NO |
| Core: Domain | JVM | `ReportingPeriodCalculatorTest` | `calculatePeriods_30Days` | NO |
| Core: Domain | JVM | `ReportingPeriodCalculatorTest` | `calculatePeriods_90Days` | NO |
| Core: Domain | JVM | `StandardUnitConverterTest` | ``convert same units returns same value`` | NO |
| Core: Domain | JVM | `StandardUnitConverterTest` | ``convert kg to grams`` | NO |
| Core: Domain | JVM | `StandardUnitConverterTest` | ``convert lb to grams`` | NO |
| Core: Domain | JVM | `StandardUnitConverterTest` | ``convert incompatible dimensions throws`` | NO |
| Core: Domain | JVM | `WeightedAverageCostCalculatorTest` | ``calculate first purchase`` | NO |
| Core: Domain | JVM | `WeightedAverageCostCalculatorTest` | ``calculate with existing inventory`` | NO |
| Core: Domain | JVM | `WeightedAverageCostCalculatorTest` | ``calculate with zero cost purchase`` | NO |
| Core: Domain | JVM | `CompleteOnboardingUseCaseTest` | ``Success updates DataStore and clears draft`` | NO |
| Core: Domain | JVM | `CompleteOnboardingUseCaseTest` | ``AlreadyCompleted uses Room locale as authoritative`` | NO |
| Core: Domain | JVM | `PreviewWasteUseCaseTest` | ``calculates preview correctly`` | NO |
| Core: Domain | JVM | `ResolveAppStartStateUseCaseTest` | ``both incomplete returns RequiresOnboarding`` | NO |
| Core: Domain | JVM | `ResolveAppStartStateUseCaseTest` | ``both complete returns Ready`` | NO |
| Core: Domain | JVM | `ResolveAppStartStateUseCaseTest` | ``db complete but locale mismatch repairs DataStore and returns Ready`` | NO |
| Core: Domain | JVM | `ResolveAppStartStateUseCaseTest` | ``db incomplete but DataStore says complete repairs DataStore and returns RequiresOnboarding`` | NO |
| Core: Domain | JVM | `AppLocaleUseCaseTest` | ``reconciler rethrows cancellation exception`` | NO |
| Core: Domain | JVM | `AppLocaleUseCaseTest` | ``reconciler handles room failure as ordinary failure`` | NO |
| Feature: Stock Counts | JVM | `StartStockCountViewModelTest` | ``initial state loads areas`` | NO |
| Feature: Stock Counts | JVM | `StartStockCountViewModelTest` | ``area selection works`` | NO |
| Feature: Stock Counts | JVM | `StartStockCountViewModelTest` | ``start count fails with future date`` | NO |
| Feature: Stock Counts | JVM | `StartStockCountViewModelTest` | ``start count succeeds with valid input`` | NO |
| Feature: Stock Counts | JVM | `StartStockCountViewModelTest` | ``overlapping area is disabled`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelRaceTest` | ``Delete during CREATE captures generated ID and removes line`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelRaceTest` | ``Queued save and delete complete in order without deadlock`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelRaceTest` | ``Flush during CREATE does not create duplicate line`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelTest` | ``initial state loads correctly`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelTest` | ``Archived unit becomes disabled after changing away`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelTest` | ``untouched suggestions are not pending`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelTest` | ``user edit makes line pending`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelTest` | ``rapid unit selection preserves final selection`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelTest` | ``invalid input blocks completion`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelTest` | ``back navigation flushes pending saves`` | NO |
| Feature: Stock Counts | JVM | `StockCountAreaViewModelTest` | ``stale save result does not overwrite newer state`` | NO |
| Feature: Stock Counts | JVM | `StockCountDetailViewModelTest` | ``initial state loads correctly`` | NO |
| Feature: Stock Counts | JVM | `StockCountDetailViewModelTest` | ``ownership mismatch state works`` | NO |
| Feature: Stock Counts | JVM | `StockCountDetailViewModelTest` | ``not found state works`` | NO |
| Feature: Stock Counts | JVM | `StockCountDetailViewModelTest` | ``complete count resets state after success`` | NO |
| Feature: Home | JVM | `HomeScreenStateTest` | ``Ready state can be copied and updated`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``initial state sequence`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``initial repository failure triggers Error state`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``retry after initial failure resubscribes`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``coverage mapping handles edge cases`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``mapComparison handles states correctly`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``scale-independent zero handling`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``account switch resets to full Loading and hides old data`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``range refresh sequence`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``rapid range selection handles cancellation`` | NO |
| Feature: Home | JVM | `HomeViewModelTest` | ``selecting same range is no-op`` | NO |
| Feature: Ingredients | JVM | `IngredientDetailViewModelTest` | ``initial state loads ingredient`` | NO |
| Feature: Ingredients | JVM | `IngredientDetailViewModelTest` | ``options are reactive`` | NO |
| Feature: Ingredients | JVM | `IngredientDetailViewModelTest` | ``add standard option emits success event`` | NO |
| Feature: Ingredients | JVM | `IngredientDetailViewModelTest` | ``add package option emits success event`` | NO |
| Feature: Ingredients | JVM | `IngredientDetailViewModelTest` | ``update package option emits success event`` | NO |
| Feature: Ingredients | JVM | `IngredientDetailViewModelTest` | ``archive option emits success event`` | NO |
| Feature: Ingredients | JVM | `IngredientDetailViewModelTest` | ``default change emits success event`` | NO |
| Feature: Ingredients | JVM | `IngredientFormViewModelTest` | ``initial state is not loading`` | NO |
| Feature: Ingredients | JVM | `IngredientFormViewModelTest` | ``onSave fails with blank name`` | NO |
| Feature: Ingredients | JVM | `IngredientFormViewModelTest` | ``onSave fails without dimension and base unit in create mode`` | NO |
| Feature: Ingredients | JVM | `IngredientFormViewModelTest` | ``dimension selection resets base unit`` | NO |
| Feature: Ingredients | JVM | `IngredientFormViewModelTest` | ``edit mode hides unit mutation controls`` | NO |
| Feature: Ingredients | JVM | `IngredientListViewModelTest` | ``search filters ingredients with normalization`` | NO |
| Feature: Ingredients | JVM | `IngredientListViewModelTest` | ``search filters ingredients case-insensitive`` | NO |
| Feature: Ingredients | JVM | `IngredientListViewModelTest` | ``category filter filters ingredients`` | NO |
| Feature: Ingredients | JVM | `IngredientListViewModelTest` | ``archived toggle updates includeArchived flag`` | NO |
| Feature: Onboarding | JVM | `OnboardingViewModelTest` | ``initial state loads defaults when no draft exists`` | NO |
| Feature: Onboarding | JVM | `OnboardingViewModelTest` | ``autosave persists changes with debounce for name`` | NO |
| Feature: Onboarding | JVM | `OnboardingViewModelTest` | ``autosave persists selections immediately`` | NO |
| Feature: Onboarding | JVM | `OnboardingViewModelTest` | ``next step persists the NEW step immediately`` | NO |
| Feature: Onboarding | JVM | `OnboardingViewModelTest` | ``reordering updates sort order and persists immediately`` | NO |
| Feature: Onboarding | JVM | `OnboardingViewModelTest` | ``draft save failure is visible in UI state`` | NO |
| Feature: Purchases | JVM | `PurchaseDetailViewModelTest` | ``void purchase success updates state to VOIDED`` | NO |
| Feature: Purchases | JVM | `PurchaseDraftViewModelTest` | ``post purchase success emits event`` | NO |
| Feature: Purchases | JVM | `PurchaseDraftViewModelTest` | ``delete line success emits event`` | NO |
| Feature: Purchases | JVM | `PurchaseLineViewModelTest` | ``initial state is loading then ready for new line`` | NO |
| Feature: Purchases | JVM | `PurchaseLineViewModelTest` | ``ingredient selection updates options and previews`` | NO |
| Feature: Purchases | JVM | `PurchaseLineViewModelTest` | ``rapid ingredient selection cancels previous work`` | NO |
| Feature: Purchases | JVM | `PurchaseListViewModelTest` | ``list updates when repository emits new purchases`` | NO |
| Feature: Purchases | JVM | `PurchaseListViewModelTest` | ``search updates filter`` | NO |
| Feature: Reports | JVM | `ReportsScreenStateTest` | `readyStateCanBeCopied` | NO |
| Feature: Reports | JVM | `InventoryDetailViewModelTest` | ``initial state is Loading then SetupRequired when no restaurant`` | NO |
| Feature: Reports | JVM | `InventoryDetailViewModelTest` | ``Ready state refresh sequence`` | NO |
| Feature: Reports | JVM | `InventoryDetailViewModelTest` | ``account switch resets to full Loading and hides old data`` | NO |
| Feature: Reports | JVM | `InventoryDetailViewModelTest` | ``refresh failure preserves old data`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``navigation-provided 7-day range`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``navigation-provided 90-day range`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``missing range defaults to 30 days`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``malformed range defaults to 30 days`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``initial state is Loading then SetupRequired when no restaurant`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``Ready state success sequence`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``initial repository failure triggers Error state`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``initial retry resubscribes`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``range change triggers refreshing sequence with distinct periods`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``selecting same range does not trigger new request`` | NO |
| Feature: Reports | JVM | `PurchaseDetailViewModelTest` | ``account switch resets to Loading`` | NO |
| Feature: Reports | JVM | `ReportsViewModelTest` | ``initial state is Loading then SetupRequired when no restaurant`` | NO |
| Feature: Reports | JVM | `ReportsViewModelTest` | ``initial repository failure triggers Error state`` | NO |
| Feature: Reports | JVM | `ReportsViewModelTest` | ``retry after initial failure resubscribes`` | NO |
| Feature: Reports | JVM | `ReportsViewModelTest` | ``Ready state refresh sequence`` | NO |
| Feature: Reports | JVM | `ReportsViewModelTest` | ``account switch resets to full Loading and hides old data`` | NO |
| Feature: Reports | JVM | `ReportsViewModelTest` | ``mapping coverage and fields`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``navigation-provided 7-day range`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``navigation-provided 90-day range`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``missing range defaults to 30 days`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``malformed range defaults to 30 days`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``initial state sequence`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``initial error and retry`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``selecting same range is no-op`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``range change triggers refreshing sequence with distinct periods`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``refresh failure preserves old data`` | NO |
| Feature: Reports | JVM | `WasteDetailViewModelTest` | ``account switch resets identity`` | NO |
| Feature: Settings | JVM | `BackupViewModelTest` | ``initial state is Idle`` | NO |
| Feature: Settings | JVM | `BackupViewModelTest` | ``two simultaneous create requests only start one operation`` | NO |
| Feature: Settings | JVM | `BackupViewModelTest` | ``reset cancels active preparation job`` | NO |
| Feature: Settings | JVM | `BackupViewModelTest` | ``restored CREATING state shows OperationInterrupted error`` | NO |
| Feature: Settings | JVM | `BackupViewModelTest` | ``stale repository emissions are ignored after reset`` | NO |
| Core | JVM | `SupplierFormViewModelTest` | ``save supplier success emits event`` | NO |
| Core | JVM | `SupplierListViewModelTest` | ``list updates when repository emits new suppliers`` | NO |
| Feature: Waste | JVM | `WasteDetailViewModelTest` | ``loading to ready state`` | NO |
| Feature: Waste | JVM | `WasteDetailViewModelTest` | ``not found state`` | NO |
| Feature: Waste | JVM | `WasteDetailViewModelTest` | ``invalid route state`` | NO |
| Feature: Waste | JVM | `WasteDetailViewModelTest` | ``ownership mismatch state`` | NO |
| Feature: Waste | JVM | `WasteFormViewModelTest` | ``initial state is Loading then Ready`` | NO |
| Feature: Waste | JVM | `WasteFormViewModelTest` | ``selecting ingredient updates unit options and handles loading state`` | NO |
| Feature: Waste | JVM | `WasteFormViewModelTest` | ``unit option repository failure produces error and disables save`` | NO |
| Feature: Waste | JVM | `WasteFormViewModelTest` | ``attachment permission failure handles error and preserves existing`` | NO |
| Feature: Waste | JVM | `WasteFormViewModelTest` | ``preview failure handles error and clears preview`` | NO |
| Feature: Waste | JVM | `WasteListViewModelTest` | ``initial state shows events from repository`` | NO |
| Feature: Waste | JVM | `WasteRaceTest` | `ingredientRace_lastSelectionWins` | NO |
| Feature: Waste | JVM | `WasteRaceTest` | `previewCancellation_latestRequestWins` | NO |


## Complete Class Inventory

### `ArchitectureTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/ArchitectureTest.kt`
- **Subsystem**: Core
- **Type**: JVM
- **Method Count**: 6
#### Methods:
- `enforcePackageBoundaries`
- `ruleModelToDatabaseViolationDetected`
- `rulePureBackupToDatabaseViolationDetected`
- `ruleDomainToComposeViolationDetected`
- `ruleCoreToFeatureViolationDetected`
- `aliasedForbiddenImportDetected`

### `ExampleUnitTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/ExampleUnitTest.kt`
- **Subsystem**: Core
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- `addition_isCorrect`

### `AndroidBackupRepositoryTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/AndroidBackupRepositoryTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``full successful orchestration sequence``
- ``cleans destination on planning failure``

### `ArchiveEntryValidatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/ArchiveEntryValidatorTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 6
#### Methods:
- ``isSafe rejects absolute paths``
- ``isSafe rejects relative traversal``
- ``isSafe accepts simple alphanumeric paths``
- ``isSafe accepts valid attachment paths``
- ``isSafe rejects backslashes``
- ``isSafe rejects blank names``

### `AttachmentFilenameSanitizerTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``sanitize removes dangerous characters``
- ``sanitize preserves extensions``
- ``isValid validates correctly``

### `BackupArchiveValidatorAdversarialTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 6
#### Methods:
- ``positive control - valid archive passes``
- ``rejects archive with missing manifest``
- ``rejects archive with duplicate entry``
- ``rejects archive with checksum key mismatch``
- ``rejects archive with malformed UTF-8 manifest``
- ``rejects archive with schema version mismatch``

### `BackupArchiveWriterTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveWriterTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``writer releases resources and does not close underlying stream``
- ``writer rejects plan with checksum map mismatch``
- ``writer detects attachment growth during write``

### `BackupByteMathTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupByteMathTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- `addExact_success`
- `addExact_overflow_throws`
- `addExact_rejectsNegative`

### `BackupCleanupCoordinatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCleanupCoordinatorTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``returns Deleted when delete succeeds``
- ``returns Truncated when delete fails but truncate succeeds``
- ``returns Failed when both fail``
- ``returns Failed when delete throws``

### `BackupCleanupLifecycleTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCleanupLifecycleTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``preflight failure does not open destination for write``
- ``creation failure triggers cleanup``
- ``failed deletion falls back to truncate``

### `BackupCreationPlannerTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCreationPlannerTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``successful deterministic plan``
- ``plan ensures defensive copies of byte arrays``
- ``plan reflects attachment list from binding``
- ``rejects plan if schema version mismatch``

### `BackupFilenameGeneratorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupFilenameGeneratorTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``generates valid name with restaurant``
- ``generates valid name without restaurant``
- ``sanitizes restaurant name``

### `BackupManifestValidatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestValidatorTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 5
#### Methods:
- ``valid manifest passes``
- ``rejects unsupported format version``
- ``rejects overlong attachment list``
- ``rejects invalid locale``
- ``rejects missing table metadata``

### `BackupRoundTripTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupRoundTripTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``complete round trip with no attachments``
- ``complete round trip with shared attachments``
- ``archive determinism - identical inputs produce identical bytes``

### `BackupSnapshotIntegrityNumericTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityNumericTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``rejects negative purchase quantity``
- ``rejects malformed decimal``

### `BackupSnapshotIntegrityProjectionTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityProjectionTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``rejects balance projection mismatch``

### `BackupSnapshotIntegrityReversalTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityReversalTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``rejects reversal targeting another reversal``

### `BackupSnapshotIntegrityValidatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``validate accepts valid simple snapshot``
- ``validate rejects zero purchase quantity``
- ``validate rejects balance projection mismatch``
- ``validate rejects posted purchase receipt without movement``

### `ChecksumParserTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 9
#### Methods:
- ``parse empty input fails``
- ``parse empty object fails``
- ``parse valid sorted object succeeds``
- ``parse duplicate key fails``
- ``parse non-hex hash fails``
- ``parse self reference fails``
- ``parse malformed escape fails``
- ``parse trailing comma fails``
- ``parse trailing content fails``

### `ChecksumTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``ImmutableBackupBytes sha256 matches manual digest``
- ``sha256 is deterministic``

### `InsufficientStorageDetectionTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/InsufficientStorageDetectionTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``detects ENOSPC from message``
- ``detects SecurityException as PermissionDenied``
- ``detects open failure as DestinationUnavailable``
- ``falls back to GenericIo for generic IOException``

### `SupportedAppLocaleTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/backup/SupportedAppLocaleTest.kt`
- **Subsystem**: Core: Backup
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``languageTags contains all supported tags``
- ``fromLanguageTag returns correct enum for valid tags``
- ``fromLanguageTag returns null for invalid tags``

### `DecimalPersistenceTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/common/decimal/DecimalPersistenceTest.kt`
- **Subsystem**: Core
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``toStorageString preserves plain format``
- ``toBigDecimalValue parses canonical string``
- ``toBigDecimalValue fails on invalid string``

### `IdGeneratorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/common/ids/IdGeneratorTest.kt`
- **Subsystem**: Core
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``newId returns non-empty string``
- ``newId returns unique strings``

### `NameNormalizationTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/common/text/NameNormalizationTest.kt`
- **Subsystem**: Core
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``normalizeName trims and collapses spaces``
- ``normalizeName lowercases root locale``
- ``normalizeName handles empty string``

### `TimeProviderTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/common/time/TimeProviderTest.kt`
- **Subsystem**: Core
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``now returns current time``

### `RoomDashboardRepositoryTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt`
- **Subsystem**: Core: Database
- **Type**: JVM
- **Method Count**: 9
#### Methods:
- ``empty data returns zeroed snapshot``
- ``calculate inventory valuation with multiple areas and negative quantities``
- ``invalid inventory decimal throws error``
- ``negative cost throws error``
- ``calculate purchase spend comparison correctly``
- ``waste value uses historical snapshots``
- ``null waste valuation throws error``
- ``completed count summary is independent of lines``
- ``recent activity uses structured data without fallback prose``

### `WasteMovementHistoryValidatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt`
- **Subsystem**: Core: Database
- **Type**: JVM
- **Method Count**: 13
#### Methods:
- `validPostedHistory_passes`
- `positiveWasteQuantity_throws`
- `wrongRestaurant_throws`
- `costWithoutTotal_throws`
- `incorrectTotalEquation_throws`
- `validVoidedHistory_passes`
- `reversalOfReversal_throws`
- `zeroWasteQuantity_throws`
- `wrongIngredient_throws`
- `wrongArea_throws`
- `missingReversal_throws`
- `wrongReversalTarget_throws`
- `wrongReversalOperationId_throws`

### `FormattersTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt`
- **Subsystem**: Core
- **Type**: JVM
- **Method Count**: 7
#### Methods:
- `formatCurrency_US_Locale`
- `formatCurrency_ES_Locale`
- `formatCurrency_invalidCode_fallbackToLiteral`
- `formatPercent_US_Locale`
- `formatPercent_ES_Locale`
- `formatQuantity_stripsZeros`
- `formatQuantity_roundsToThreePlaces`

### `ChickenFixtureTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/service/ChickenFixtureTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``Chicken Breast fixture domain validation``

### `CountAdjustmentCalculatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/service/CountAdjustmentCalculatorTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``positive adjustment``
- ``negative adjustment``

### `CountComparisonCalculatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/service/CountComparisonCalculatorTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``calculateUnclassifiedUsage with simple values``

### `IngredientUnitConverterTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/service/IngredientUnitConverterTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``toBase converts case to lb``
- ``fromBase converts lb to case``

### `InventoryBalanceCalculatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/service/InventoryBalanceCalculatorTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``calculateBalance sums quantities``
- ``calculateBalance handles empty list``

### `InventoryMovementServiceTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/service/InventoryMovementServiceTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``createReversal returns negative quantity and references original``
- ``cannot reverse a reversal``

### `ReportingPeriodCalculatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- `calculatePeriods_7Days`
- `calculatePeriods_30Days`
- `calculatePeriods_90Days`

### `StandardUnitConverterTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/service/StandardUnitConverterTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``convert same units returns same value``
- ``convert kg to grams``
- ``convert lb to grams``
- ``convert incompatible dimensions throws``

### `WeightedAverageCostCalculatorTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``calculate first purchase``
- ``calculate with existing inventory``
- ``calculate with zero cost purchase``

### `CompleteOnboardingUseCaseTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/CompleteOnboardingUseCaseTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``Success updates DataStore and clears draft``
- ``AlreadyCompleted uses Room locale as authoritative``

### `PreviewWasteUseCaseTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/PreviewWasteUseCaseTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``calculates preview correctly``

### `ResolveAppStartStateUseCaseTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``both incomplete returns RequiresOnboarding``
- ``both complete returns Ready``
- ``db complete but locale mismatch repairs DataStore and returns Ready``
- ``db incomplete but DataStore says complete repairs DataStore and returns RequiresOnboarding``

### `AppLocaleUseCaseTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/locale/AppLocaleUseCaseTest.kt`
- **Subsystem**: Core: Domain
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``reconciler rethrows cancellation exception``
- ``reconciler handles room failure as ordinary failure``

### `StartStockCountViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt`
- **Subsystem**: Feature: Stock Counts
- **Type**: JVM
- **Method Count**: 5
#### Methods:
- ``initial state loads areas``
- ``area selection works``
- ``start count fails with future date``
- ``start count succeeds with valid input``
- ``overlapping area is disabled``

### `StockCountAreaViewModelRaceTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt`
- **Subsystem**: Feature: Stock Counts
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``Delete during CREATE captures generated ID and removes line``
- ``Queued save and delete complete in order without deadlock``
- ``Flush during CREATE does not create duplicate line``

### `StockCountAreaViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt`
- **Subsystem**: Feature: Stock Counts
- **Type**: JVM
- **Method Count**: 8
#### Methods:
- ``initial state loads correctly``
- ``Archived unit becomes disabled after changing away``
- ``untouched suggestions are not pending``
- ``user edit makes line pending``
- ``rapid unit selection preserves final selection``
- ``invalid input blocks completion``
- ``back navigation flushes pending saves``
- ``stale save result does not overwrite newer state``

### `StockCountDetailViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt`
- **Subsystem**: Feature: Stock Counts
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``initial state loads correctly``
- ``ownership mismatch state works``
- ``not found state works``
- ``complete count resets state after success``

### `HomeScreenStateTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeScreenStateTest.kt`
- **Subsystem**: Feature: Home
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``Ready state can be copied and updated``

### `HomeViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt`
- **Subsystem**: Feature: Home
- **Type**: JVM
- **Method Count**: 10
#### Methods:
- ``initial state sequence``
- ``initial repository failure triggers Error state``
- ``retry after initial failure resubscribes``
- ``coverage mapping handles edge cases``
- ``mapComparison handles states correctly``
- ``scale-independent zero handling``
- ``account switch resets to full Loading and hides old data``
- ``range refresh sequence``
- ``rapid range selection handles cancellation``
- ``selecting same range is no-op``

### `IngredientDetailViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt`
- **Subsystem**: Feature: Ingredients
- **Type**: JVM
- **Method Count**: 7
#### Methods:
- ``initial state loads ingredient``
- ``options are reactive``
- ``add standard option emits success event``
- ``add package option emits success event``
- ``update package option emits success event``
- ``archive option emits success event``
- ``default change emits success event``

### `IngredientFormViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt`
- **Subsystem**: Feature: Ingredients
- **Type**: JVM
- **Method Count**: 5
#### Methods:
- ``initial state is not loading``
- ``onSave fails with blank name``
- ``onSave fails without dimension and base unit in create mode``
- ``dimension selection resets base unit``
- ``edit mode hides unit mutation controls``

### `IngredientListViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt`
- **Subsystem**: Feature: Ingredients
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``search filters ingredients with normalization``
- ``search filters ingredients case-insensitive``
- ``category filter filters ingredients``
- ``archived toggle updates includeArchived flag``

### `OnboardingViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt`
- **Subsystem**: Feature: Onboarding
- **Type**: JVM
- **Method Count**: 6
#### Methods:
- ``initial state loads defaults when no draft exists``
- ``autosave persists changes with debounce for name``
- ``autosave persists selections immediately``
- ``next step persists the NEW step immediately``
- ``reordering updates sort order and persists immediately``
- ``draft save failure is visible in UI state``

### `PurchaseDetailViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseDetailViewModelTest.kt`
- **Subsystem**: Feature: Purchases
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``void purchase success updates state to VOIDED``

### `PurchaseDraftViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseDraftViewModelTest.kt`
- **Subsystem**: Feature: Purchases
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``post purchase success emits event``
- ``delete line success emits event``

### `PurchaseLineViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt`
- **Subsystem**: Feature: Purchases
- **Type**: JVM
- **Method Count**: 3
#### Methods:
- ``initial state is loading then ready for new line``
- ``ingredient selection updates options and previews``
- ``rapid ingredient selection cancels previous work``

### `PurchaseListViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseListViewModelTest.kt`
- **Subsystem**: Feature: Purchases
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- ``list updates when repository emits new purchases``
- ``search updates filter``

### `ReportsScreenStateTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/reports/ReportsScreenStateTest.kt`
- **Subsystem**: Feature: Reports
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- `readyStateCanBeCopied`

### `InventoryDetailViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt`
- **Subsystem**: Feature: Reports
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``initial state is Loading then SetupRequired when no restaurant``
- ``Ready state refresh sequence``
- ``account switch resets to full Loading and hides old data``
- ``refresh failure preserves old data``

### `PurchaseDetailViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt`
- **Subsystem**: Feature: Reports
- **Type**: JVM
- **Method Count**: 11
#### Methods:
- ``navigation-provided 7-day range``
- ``navigation-provided 90-day range``
- ``missing range defaults to 30 days``
- ``malformed range defaults to 30 days``
- ``initial state is Loading then SetupRequired when no restaurant``
- ``Ready state success sequence``
- ``initial repository failure triggers Error state``
- ``initial retry resubscribes``
- ``range change triggers refreshing sequence with distinct periods``
- ``selecting same range does not trigger new request``
- ``account switch resets to Loading``

### `ReportsViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt`
- **Subsystem**: Feature: Reports
- **Type**: JVM
- **Method Count**: 6
#### Methods:
- ``initial state is Loading then SetupRequired when no restaurant``
- ``initial repository failure triggers Error state``
- ``retry after initial failure resubscribes``
- ``Ready state refresh sequence``
- ``account switch resets to full Loading and hides old data``
- ``mapping coverage and fields``

### `WasteDetailViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt`
- **Subsystem**: Feature: Reports
- **Type**: JVM
- **Method Count**: 10
#### Methods:
- ``navigation-provided 7-day range``
- ``navigation-provided 90-day range``
- ``missing range defaults to 30 days``
- ``malformed range defaults to 30 days``
- ``initial state sequence``
- ``initial error and retry``
- ``selecting same range is no-op``
- ``range change triggers refreshing sequence with distinct periods``
- ``refresh failure preserves old data``
- ``account switch resets identity``

### `BackupViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt`
- **Subsystem**: Feature: Settings
- **Type**: JVM
- **Method Count**: 5
#### Methods:
- ``initial state is Idle``
- ``two simultaneous create requests only start one operation``
- ``reset cancels active preparation job``
- ``restored CREATING state shows OperationInterrupted error``
- ``stale repository emissions are ignored after reset``

### `SupplierFormViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/suppliers/viewmodel/SupplierFormViewModelTest.kt`
- **Subsystem**: Core
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``save supplier success emits event``

### `SupplierListViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/suppliers/viewmodel/SupplierListViewModelTest.kt`
- **Subsystem**: Core
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``list updates when repository emits new suppliers``

### `WasteDetailViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt`
- **Subsystem**: Feature: Waste
- **Type**: JVM
- **Method Count**: 4
#### Methods:
- ``loading to ready state``
- ``not found state``
- ``invalid route state``
- ``ownership mismatch state``

### `WasteFormViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt`
- **Subsystem**: Feature: Waste
- **Type**: JVM
- **Method Count**: 5
#### Methods:
- ``initial state is Loading then Ready``
- ``selecting ingredient updates unit options and handles loading state``
- ``unit option repository failure produces error and disables save``
- ``attachment permission failure handles error and preserves existing``
- ``preview failure handles error and clears preview``

### `WasteListViewModelTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteListViewModelTest.kt`
- **Subsystem**: Feature: Waste
- **Type**: JVM
- **Method Count**: 1
#### Methods:
- ``initial state shows events from repository``

### `WasteRaceTest`
- **Source Path**: `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteRaceTest.kt`
- **Subsystem**: Feature: Waste
- **Type**: JVM
- **Method Count**: 2
#### Methods:
- `ingredientRace_lastSelectionWins`
- `previewCancellation_latestRequestWins`

