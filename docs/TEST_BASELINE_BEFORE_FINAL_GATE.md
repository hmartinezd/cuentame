# Current Test Inventory

| Source path | Class | Method | JVM/Inst | Result |
| :--- | :--- | :--- | :--- | :--- |
| `app/src/androidTest/kotlin/com/miara/cuentame/ExampleInstrumentedTest.kt` | ExampleInstrumentedTest | useAppContext | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/NavigationTest.kt` | NavigationTest | app_startsOnHome_whenRestaurantExists | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/NavigationTest.kt` | NavigationTest | app_startsOnOnboarding_whenNoRestaurant | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/NavigationTest.kt` | NavigationTest | navigateToSettingsAndBack | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | delete_validFile_returnsTrue | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | openForRead_nonExistent_throwsWrapped | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | openForRead_validFileUri_returnsStream | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | openForWrite_validFileUri_returnsStream | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | truncate_validFile_emptiesFile | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/AndroidBackupRepositoryTest.kt` | AndroidBackupRepositoryTest | createBackup_successful_sequence | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/backup/BackupProductionIntegrationTest.kt` | BackupProductionIntegrationTest | fullBackupPipeline_producesValidArchive | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/DatabaseSeedingTest.kt` | DatabaseSeedingTest | unitsAreSeededSynchronouslyOnCreate | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/DatabaseTest.kt` | DatabaseTest | movementIdempotency | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/DatabaseTest.kt` | DatabaseTest | seedUnitsExist | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/DatabaseTest.kt` | DatabaseTest | writeAndReadRestaurant | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/MigrationTest.kt` | MigrationTest | migrate1To2 | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/ParentUpdateTest.kt` | ParentUpdateTest | updateReceiptPreservesLines | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/ReversalTest.kt` | ReversalTest | onlyOneReversalAllowedPerMovement | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/BackupDaoTest.kt` | BackupDaoTest | createSnapshot_includesAllTables | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | adjustedLineCount_providesPersistedValues | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | recentActivity_deterministicOrdering | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | spendRows_filtersByStatusAndDate | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | stockCountSummaries_independentOfLines | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | valuationRows_isolatesByRestaurant | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | wasteValueRows_filtersByStatusAndDate | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | inventoryValuationRows_includesNegativeBalances | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | inventoryValuationRows_joinsMetadata | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | purchaseSpendRows_filtersByDateRange | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | purchaseSpendRows_filtersByStatus | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | wasteValueRows_filtersByStatus | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/migration/RoomMigrationTest.kt` | RoomMigrationTest | createDatabaseDirectlyAtVersion2 | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/migration/RoomMigrationTest.kt` | RoomMigrationTest | migrate1To2_preservesData_supportsNullCost_andOpensInRoom | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/BackupIsolationTest.kt` | BackupIsolationTest | createSnapshot_isolatesAllTablesByRestaurant | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/ReportingIsolationTest.kt` | ReportingIsolationTest | inventoryDetails_hardenedIsolation_excludesCrossRestaurantMetadata | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/ReportingIsolationTest.kt` | ReportingIsolationTest | purchaseDetails_hardenedIsolation_excludesCrossRestaurantSuppliers | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/ReportingIsolationTest.kt` | ReportingIsolationTest | recentWasteActivity_hardenedIsolation_excludesCrossRestaurantMetadata | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/ReportingIsolationTest.kt` | ReportingIsolationTest | wasteDetails_hardenedIsolation_excludesCrossRestaurantMetadata | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryIntegrationTest.kt` | FixedTimeProvider | observeDashboard_emptyDatabase_returnsZeros | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_aggregatesAreasAndCalculatesValue | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_alertOnlyRow_aggregateZeroButNegativeArea | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_excludedRow_allZeroNoNegative | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_missingCost_onlyForStocked | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_strictDecimalValidation | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_zeroCost_isValid | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observePurchaseDetails_strictDecimalValidation | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeWasteDetails_strictSnapshotValidation | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomIngredientRepositoryTest.kt` | RoomIngredientRepositoryTest | createIngredientWithBaseOption_succeeds | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | archiveFinalArea_scopedByRestaurant | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | archiveFinalArea_throwsError | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | reorder_otherRestaurant_throwsError | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | reorder_subset_throwsError | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | reorder_updatesSortOrderContiguously | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventorySnapshotServiceFailureTest.kt` | RoomInventorySnapshotServiceFailureTest | calculateAt_reversalOfReversal_throws | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventorySnapshotServiceFailureTest.kt` | RoomInventorySnapshotServiceFailureTest | calculateAt_reversalWithoutTarget_throws | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_futureReversal_doesNotCancel | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_historyWithoutCost_returnsNullCost | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_noHistory_returnsEmpty | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_reversal_cancelsOriginal | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_withHistory_returnsSnapshot | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomPurchaseRepositoryTest.kt` | RoomPurchaseRepositoryTest | fullLifecycle_draft_to_posted_to_void | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomRestaurantRepositoryTest.kt` | RoomRestaurantRepositoryTest | save_existingRestaurant_updates | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomRestaurantRepositoryTest.kt` | RoomRestaurantRepositoryTest | save_newRestaurant_inserts | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomStockCountRepositoryTest.kt` | RoomStockCountRepositoryTest | fullLifecycle_start_save_complete_void | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/RoomWasteRepositoryTest.kt` | RoomWasteRepositoryTest | fullLifecycle_create_post_void | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | archiveSupplier_removesFromActiveList | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | createSupplier_failsOnDuplicateName | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | createSupplier_failsOnOwnershipMismatch | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | createSupplier_succeeds | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | updateSupplier_updatesAllowedFieldsOnly | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/domain/service/ChickenIntegrationTest.kt` | ChickenIntegrationTest | createChickenIngredientWithMultipleUnits | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/domain/service/PurchaseIntegrationTest.kt` | PurchaseIntegrationTest | insertAndReadPurchase | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt` | DataStoreAppPreferencesRepositoryTest | corruptDraft_isRemovedSynchronously | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt` | DataStoreAppPreferencesRepositoryTest | saveAndLoadDraft | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt` | DataStoreAppPreferencesRepositoryTest | saveAndObservePreferences | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt` | DataStoreAppPreferencesRepositoryTest | unsupportedVersion_isRemovedSynchronously | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/counts/ui/StockCountLifecycleTest.kt` | StockCountLifecycleTest | full_lifecycle_test | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/counts/ui/StockCountUiTest.kt` | StockCountUiTest | start_count_flow | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/home/HomeUiTest.kt` | HomeUiTest | app_routesToOnboarding_whenSetupIsIncomplete | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/home/HomeUiTest.kt` | HomeUiTest | dashboard_emptyState_whenNoActivity | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/home/HomeUiTest.kt` | HomeUiTest | dashboard_fullVerification_populatedData | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/ingredients/ui/IngredientDetailUiTest.kt` | IngredientDetailUiTest | archive_ingredient_flow | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/ingredients/ui/IngredientUiTest.kt` | IngredientUiTest | complete_ingredient_e2e_flow | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/onboarding/ui/OnboardingUiTest.kt` | OnboardingUiTest | onboarding_fullFlow_persistsRestaurantAndNavigatesToHome | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/onboarding/ui/OnboardingUiTest.kt` | OnboardingUiTest | onboarding_start_navigation | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/purchases/ui/PurchaseFailureUiTest.kt` | PurchaseFailureUiTest | post_failure_preserves_dialog_and_shows_snackbar | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/purchases/ui/PurchaseUiTest.kt` | PurchaseUiTest | complete_purchase_lifecycle | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/DetailedReportsUiTest.kt` | DetailedReportsUiTest | reports_display_seeded_values | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/InventoryDetailScreenTest.kt` | InventoryDetailScreenTest | inventoryDetail_exists | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/PurchaseDetailScreenTest.kt` | PurchaseDetailScreenTest | purchaseDetail_exists | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/ReportingRefreshComposeTest.kt` | ReportingRefreshComposeTest | reportsRefresh_exists | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reportsOverview_rangeRefresh_noFlicker | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reports_navigation_homeToReports_andBack | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reports_populatedData_verification | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reports_rangeSwitching_7_days | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reports_rangeSwitching_90_days | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/reports/WasteDetailScreenTest.kt` | WasteDetailScreenTest | wasteDetail_exists | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/settings/ui/SettingsBackupUiTest.kt` | SettingsBackupUiTest | createBackup_buttonExists | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_changingAwayFromArchivedReferences_persistsActiveValues | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_crossIngredientUnitOption_showsSafeErrorAndPreventsSave | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_existingArchivedReferences_displayedAndPreservedOnSave | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_missingAreaReference_showsSafeError | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_missingIngredientReference_showsSafeErrorAndDisablesSave | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_missingUnitOptionReference_showsSafeError | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteFailureUiTest.kt` | WasteFailureUiTest | wastePost_failureRollback_andRetrySuccess | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteFailureUiTest.kt` | WasteFailureUiTest | wasteVoid_failureRollback_andRetrySuccess | Inst | NOT_EXECUTED |
| `app/src/androidTest/kotlin/com/miara/cuentame/feature/waste/ui/WasteLifecycleTest.kt` | WasteLifecycleTest | navigateToWasteAndBack | Inst | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/ArchitectureTest.kt` | ArchitectureTest | aliasedForbiddenImportDetected | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/ArchitectureTest.kt` | ArchitectureTest | enforcePackageBoundaries | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/ArchitectureTest.kt` | ArchitectureTest | ruleCoreToFeatureViolationDetected | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/ArchitectureTest.kt` | ArchitectureTest | ruleDomainToComposeViolationDetected | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/ArchitectureTest.kt` | ArchitectureTest | ruleModelToDatabaseViolationDetected | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/ArchitectureTest.kt` | ArchitectureTest | rulePureBackupToDatabaseViolationDetected | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/ExampleUnitTest.kt` | ExampleUnitTest | addition_isCorrect | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/AndroidBackupRepositoryTest.kt` | AndroidBackupRepositoryTest | cleans destination on planning failure | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/AndroidBackupRepositoryTest.kt` | AndroidBackupRepositoryTest | full successful orchestration sequence | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe accepts simple alphanumeric paths | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe accepts valid attachment paths | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe rejects absolute paths | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe rejects backslashes | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe rejects blank names | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe rejects relative traversal | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt` | AttachmentFilenameSanitizerTest | isValid validates correctly | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt` | AttachmentFilenameSanitizerTest | sanitize preserves extensions | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt` | AttachmentFilenameSanitizerTest | sanitize removes dangerous characters | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | positive control - valid archive passes | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with checksum key mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with checksum value mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with duplicate entry | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with malformed UTF-8 manifest | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with missing manifest | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with schema version mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with traversal path | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with unexpected entry | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer detects attachment growth during write | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer detects database payload checksum mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer detects total limit exceeded during write | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer rejects plan with checksum map mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer releases resources and does not close underlying stream | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupByteMathTest.kt` | BackupByteMathTest | addExact_overflow_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupByteMathTest.kt` | BackupByteMathTest | addExact_rejectsNegative | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupByteMathTest.kt` | BackupByteMathTest | addExact_success | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCleanupCoordinatorTest.kt` | BackupCleanupCoordinatorTest | returns Deleted when delete succeeds | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCleanupCoordinatorTest.kt` | BackupCleanupCoordinatorTest | returns Failed when both fail | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCleanupCoordinatorTest.kt` | BackupCleanupCoordinatorTest | returns Failed when delete throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCleanupCoordinatorTest.kt` | BackupCleanupCoordinatorTest | returns Truncated when delete fails but truncate succeeds | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCleanupLifecycleTest.kt` | BackupCleanupLifecycleTest | creation failure triggers cleanup | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCleanupLifecycleTest.kt` | BackupCleanupLifecycleTest | failed deletion falls back to truncate | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCleanupLifecycleTest.kt` | BackupCleanupLifecycleTest | preflight failure does not open destination for write | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | fails with MissingAttachmentSource when binding is missing | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | maps missing restaurant to RestaurantDisappeared | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | maps reconciliation failure to LocaleReconciliationFailed | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | plan ensures defensive copies of byte arrays | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | plan reflects attachment list from binding | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | rejects plan if attachment ID is invalid | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | rejects plan if schema version mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | successful deterministic plan | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupFilenameGeneratorTest.kt` | BackupFilenameGeneratorTest | generates valid name with restaurant | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupFilenameGeneratorTest.kt` | BackupFilenameGeneratorTest | generates valid name without restaurant | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupFilenameGeneratorTest.kt` | BackupFilenameGeneratorTest | sanitizes restaurant name | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | rejects invalid locale | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | rejects missing table metadata | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | rejects overlong attachment list | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | rejects unsupported format version | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | valid manifest passes | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | create rejects total size mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | create with valid data succeeds | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | plan is immutable and performs defensive copies | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | rejects duplicate manifest attachment ID | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | rejects manifest metadata mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupRoundTripTest.kt` | BackupRoundTripTest | archive determinism - identical inputs produce identical bytes | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupRoundTripTest.kt` | BackupRoundTripTest | complete round trip with no attachments | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupRoundTripTest.kt` | BackupRoundTripTest | complete round trip with shared attachments | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityNumericTest.kt` | BackupSnapshotIntegrityNumericTest | rejects malformed decimal | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityNumericTest.kt` | BackupSnapshotIntegrityNumericTest | rejects negative purchase quantity | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityProjectionTest.kt` | BackupSnapshotIntegrityProjectionTest | rejects balance projection mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityReversalTest.kt` | BackupSnapshotIntegrityReversalTest | rejects reversal targeting another reversal | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | BackupSnapshotIntegrityValidatorTest | validate accepts valid simple snapshot | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | BackupSnapshotIntegrityValidatorTest | validate rejects balance projection mismatch | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | BackupSnapshotIntegrityValidatorTest | validate rejects posted purchase receipt without movement | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | BackupSnapshotIntegrityValidatorTest | validate rejects zero purchase quantity | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse duplicate key fails | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse empty input fails | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse empty object fails | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse malformed escape fails | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse non-hex hash fails | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse self reference fails | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse trailing comma fails | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse trailing content fails | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse valid sorted object succeeds | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumTest.kt` | ChecksumTest | ImmutableBackupBytes sha256 matches manual digest | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/ChecksumTest.kt` | ChecksumTest | sha256 is deterministic | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | InsufficientStorageDetectionTest | detects ENOSPC from message | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | InsufficientStorageDetectionTest | detects SecurityException as PermissionDenied | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | InsufficientStorageDetectionTest | detects open failure as DestinationUnavailable | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | InsufficientStorageDetectionTest | falls back to GenericIo for generic IOException | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects duplicate references | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects empty references | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects invalid attachment ID | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects invalid checksum | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects invalid display name | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects non-canonical archive path | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects unsafe archive path | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects unsupported record type | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create with valid data succeeds | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/SupportedAppLocaleTest.kt` | SupportedAppLocaleTest | fromLanguageTag returns correct enum for valid tags | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/SupportedAppLocaleTest.kt` | SupportedAppLocaleTest | fromLanguageTag returns null for invalid tags | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/backup/SupportedAppLocaleTest.kt` | SupportedAppLocaleTest | languageTags contains all supported tags | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/common/decimal/DecimalPersistenceTest.kt` | DecimalPersistenceTest | toBigDecimalValue fails on invalid string | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/common/decimal/DecimalPersistenceTest.kt` | DecimalPersistenceTest | toBigDecimalValue parses canonical string | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/common/decimal/DecimalPersistenceTest.kt` | DecimalPersistenceTest | toStorageString preserves plain format | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/common/ids/IdGeneratorTest.kt` | IdGeneratorTest | newId returns non-empty string | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/common/ids/IdGeneratorTest.kt` | IdGeneratorTest | newId returns unique strings | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/common/text/NameNormalizationTest.kt` | NameNormalizationTest | normalizeName handles empty string | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/common/text/NameNormalizationTest.kt` | NameNormalizationTest | normalizeName lowercases root locale | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/common/text/NameNormalizationTest.kt` | NameNormalizationTest | normalizeName trims and collapses spaces | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/common/time/TimeProviderTest.kt` | TimeProviderTest | now returns current time | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | calculate inventory valuation with multiple areas and negative quantities | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | calculate purchase spend comparison correctly | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | completed count summary is independent of lines | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | empty data returns zeroed snapshot | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | invalid inventory decimal throws error | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | negative cost throws error | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | null waste valuation throws error | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | recent activity uses structured data without fallback prose | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | waste value uses historical snapshots | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | costWithoutTotal_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | incorrectTotalEquation_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | missingReversal_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | positiveWasteQuantity_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | reversalOfReversal_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | validPostedHistory_passes | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | validVoidedHistory_passes | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongArea_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongIngredient_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongRestaurant_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongReversalOperationId_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongReversalTarget_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | zeroWasteQuantity_throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatCurrency_ES_Locale | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatCurrency_US_Locale | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatCurrency_invalidCode_fallbackToLiteral | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatPercent_ES_Locale | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatPercent_US_Locale | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatQuantity_roundsToThreePlaces | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatQuantity_stripsZeros | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/ChickenFixtureTest.kt` | ChickenFixtureTest | Chicken Breast fixture domain validation | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/CountAdjustmentCalculatorTest.kt` | CountAdjustmentCalculatorTest | negative adjustment | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/CountAdjustmentCalculatorTest.kt` | CountAdjustmentCalculatorTest | positive adjustment | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/CountComparisonCalculatorTest.kt` | CountComparisonCalculatorTest | calculateUnclassifiedUsage with simple values | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/IngredientUnitConverterTest.kt` | IngredientUnitConverterTest | fromBase converts lb to case | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/IngredientUnitConverterTest.kt` | IngredientUnitConverterTest | toBase converts case to lb | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/InventoryBalanceCalculatorTest.kt` | InventoryBalanceCalculatorTest | calculateBalance handles empty list | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/InventoryBalanceCalculatorTest.kt` | InventoryBalanceCalculatorTest | calculateBalance sums quantities | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/InventoryMovementServiceTest.kt` | InventoryMovementServiceTest | cannot reverse a reversal | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/InventoryMovementServiceTest.kt` | InventoryMovementServiceTest | createReversal returns negative quantity and references original | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt` | ReportingPeriodCalculatorTest | calculatePeriods_30Days | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt` | ReportingPeriodCalculatorTest | calculatePeriods_7Days | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt` | ReportingPeriodCalculatorTest | calculatePeriods_90Days | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/StandardUnitConverterTest.kt` | StandardUnitConverterTest | convert incompatible dimensions throws | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/StandardUnitConverterTest.kt` | StandardUnitConverterTest | convert kg to grams | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/StandardUnitConverterTest.kt` | StandardUnitConverterTest | convert lb to grams | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/StandardUnitConverterTest.kt` | StandardUnitConverterTest | convert same units returns same value | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt` | WeightedAverageCostCalculatorTest | calculate first purchase | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt` | WeightedAverageCostCalculatorTest | calculate with existing inventory | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt` | WeightedAverageCostCalculatorTest | calculate with zero cost purchase | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/CompleteOnboardingUseCaseTest.kt` | CompleteOnboardingUseCaseTest | AlreadyCompleted uses Room locale as authoritative | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/CompleteOnboardingUseCaseTest.kt` | CompleteOnboardingUseCaseTest | Success updates DataStore and clears draft | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/PreviewWasteUseCaseTest.kt` | PreviewWasteUseCaseTest | calculates preview correctly | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt` | ResolveAppStartStateUseCaseTest | both complete returns Ready | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt` | ResolveAppStartStateUseCaseTest | both incomplete returns RequiresOnboarding | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt` | ResolveAppStartStateUseCaseTest | db complete but locale mismatch repairs DataStore and returns Ready | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt` | ResolveAppStartStateUseCaseTest | db incomplete but DataStore says complete repairs DataStore and returns RequiresOnboarding | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/locale/AppLocaleUseCaseTest.kt` | AppLocaleUseCaseTest | reconciler handles room failure as ordinary failure | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/core/domain/usecase/locale/AppLocaleUseCaseTest.kt` | AppLocaleUseCaseTest | reconciler rethrows cancellation exception | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | area selection works | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | initial state loads areas | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | overlapping area is disabled | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | start count fails with future date | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | start count succeeds with valid input | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt` | StockCountAreaViewModelRaceTest | Delete during CREATE captures generated ID and removes line | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt` | StockCountAreaViewModelRaceTest | Flush during CREATE does not create duplicate line | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt` | StockCountAreaViewModelRaceTest | Queued save and delete complete in order without deadlock | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | Archived unit becomes disabled after changing away | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | back navigation flushes pending saves | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | initial state loads correctly | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | invalid input blocks completion | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | rapid unit selection preserves final selection | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | stale save result does not overwrite newer state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | untouched suggestions are not pending | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | user edit makes line pending | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt` | StockCountDetailViewModelTest | complete count resets state after success | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt` | StockCountDetailViewModelTest | initial state loads correctly | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt` | StockCountDetailViewModelTest | not found state works | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt` | StockCountDetailViewModelTest | ownership mismatch state works | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeScreenStateTest.kt` | HomeScreenStateTest | Ready state can be copied and updated | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | account switch resets to full Loading and hides old data | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | coverage mapping handles edge cases | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | initial repository failure triggers Error state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | initial state sequence | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | mapComparison handles states correctly | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | range refresh sequence | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | rapid range selection handles cancellation | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | retry after initial failure resubscribes | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | scale-independent zero handling | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | selecting same range is no-op | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | add package option emits success event | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | add standard option emits success event | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | archive option emits success event | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | default change emits success event | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | initial state loads ingredient | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | options are reactive | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | update package option emits success event | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | dimension selection resets base unit | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | edit mode hides unit mutation controls | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | initial state is not loading | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | onSave fails with blank name | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | onSave fails without dimension and base unit in create mode | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt` | IngredientListViewModelTest | archived toggle updates includeArchived flag | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt` | IngredientListViewModelTest | category filter filters ingredients | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt` | IngredientListViewModelTest | search filters ingredients case-insensitive | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt` | IngredientListViewModelTest | search filters ingredients with normalization | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | autosave persists changes with debounce for name | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | autosave persists selections immediately | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | draft save failure is visible in UI state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | initial state loads defaults when no draft exists | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | next step persists the NEW step immediately | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | reordering updates sort order and persists immediately | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | void purchase success updates state to VOIDED | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseDraftViewModelTest.kt` | PurchaseDraftViewModelTest | delete line success emits event | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseDraftViewModelTest.kt` | PurchaseDraftViewModelTest | post purchase success emits event | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt` | PurchaseLineViewModelTest | ingredient selection updates options and previews | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt` | PurchaseLineViewModelTest | initial state is loading then ready for new line | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt` | PurchaseLineViewModelTest | rapid ingredient selection cancels previous work | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseListViewModelTest.kt` | PurchaseListViewModelTest | list updates when repository emits new purchases | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/purchases/viewmodel/PurchaseListViewModelTest.kt` | PurchaseListViewModelTest | search updates filter | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/ReportsScreenStateTest.kt` | ReportsScreenStateTest | readyStateCanBeCopied | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt` | InventoryDetailViewModelTest | Ready state refresh sequence | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt` | InventoryDetailViewModelTest | account switch resets to full Loading and hides old data | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt` | InventoryDetailViewModelTest | initial state is Loading then SetupRequired when no restaurant | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt` | InventoryDetailViewModelTest | refresh failure preserves old data | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | Ready state success sequence | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | account switch resets to Loading | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | initial repository failure triggers Error state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | initial retry resubscribes | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | initial state is Loading then SetupRequired when no restaurant | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | malformed range defaults to 30 days | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | missing range defaults to 30 days | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | navigation-provided 7-day range | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | navigation-provided 90-day range | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | range change triggers refreshing sequence with distinct periods | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | selecting same range does not trigger new request | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | Ready state refresh sequence | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | account switch resets to full Loading and hides old data | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | initial repository failure triggers Error state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | initial state is Loading then SetupRequired when no restaurant | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | mapping coverage and fields | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | retry after initial failure resubscribes | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | account switch resets identity | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | initial error and retry | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | initial state sequence | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | malformed range defaults to 30 days | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | missing range defaults to 30 days | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | navigation-provided 7-day range | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | navigation-provided 90-day range | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | range change triggers refreshing sequence with distinct periods | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | refresh failure preserves old data | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | selecting same range is no-op | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | initial state is Idle | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | interrupted survives second recreation | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | malformed WAITING state with active ID -1 becomes Idle | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | reset cancels active preparation job | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | restored CREATING state shows OperationInterrupted error | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | stale file selection is rejected | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | stale repository emissions are ignored after reset | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | two simultaneous create requests only start one operation | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/suppliers/viewmodel/SupplierFormViewModelTest.kt` | SupplierFormViewModelTest | save supplier success emits event | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/suppliers/viewmodel/SupplierListViewModelTest.kt` | SupplierListViewModelTest | list updates when repository emits new suppliers | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | invalid route state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | loading to ready state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | not found state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | ownership mismatch state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | attachment permission failure handles error and preserves existing | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | initial state is Loading then Ready | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | preview failure handles error and clears preview | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | selecting ingredient updates unit options and handles loading state | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | unit option repository failure produces error and disables save | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteListViewModelTest.kt` | WasteListViewModelTest | initial state shows events from repository | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteRaceTest.kt` | WasteRaceTest | ingredientRace_lastSelectionWins | JVM | NOT_EXECUTED |
| `app/src/test/kotlin/com/miara/cuentame/feature/waste/viewmodel/WasteRaceTest.kt` | WasteRaceTest | previewCancellation_latestRequestWins | JVM | NOT_EXECUTED |
