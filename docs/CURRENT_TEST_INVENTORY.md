# Current Test Inventory

| Source path | Class | Method | JVM/Inst | Result |
| :--- | :--- | :--- | :--- | :--- |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/ExampleInstrumentedTest.kt` | ExampleInstrumentedTest | useAppContext | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/NavigationTest.kt` | NavigationTest | app_startsOnHome_whenRestaurantExists | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/NavigationTest.kt` | NavigationTest | app_startsOnOnboarding_whenNoRestaurant | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/NavigationTest.kt` | NavigationTest | navigateToSettingsAndBack | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | delete_validFile_returnsTrue | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | openForRead_nonExistent_throwsWrapped | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | openForRead_validFileUri_returnsStream | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | openForWrite_validFileUri_returnsStream | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupDocumentStoreTest.kt` | AndroidBackupDocumentStoreTest | truncate_validFile_emptiesFile | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupRepositoryTest.kt` | AndroidBackupRepositoryTest | createBackup_successful_sequence | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/backup/BackupProductionIntegrationTest.kt` | BackupProductionIntegrationTest | fullBackupPipeline_producesValidArchive | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/DatabaseSeedingTest.kt` | DatabaseSeedingTest | unitsAreSeededSynchronouslyOnCreate | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/DatabaseTest.kt` | DatabaseTest | movementIdempotency | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/DatabaseTest.kt` | DatabaseTest | seedUnitsExist | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/DatabaseTest.kt` | DatabaseTest | writeAndReadRestaurant | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/MigrationTest.kt` | MigrationTest | migrate1To2 | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/ParentUpdateTest.kt` | ParentUpdateTest | updateReceiptPreservesLines | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/ReversalTest.kt` | ReversalTest | onlyOneReversalAllowedPerMovement | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/BackupDaoTest.kt` | BackupDaoTest | createSnapshot_includesAllTables | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | adjustedLineCount_providesPersistedValues | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | recentActivity_deterministicOrdering | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | spendRows_filtersByStatusAndDate | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | stockCountSummaries_independentOfLines | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | valuationRows_isolatesByRestaurant | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DashboardDaoTest.kt` | DashboardDaoTest | wasteValueRows_filtersByStatusAndDate | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | inventoryValuationRows_includesNegativeBalances | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | inventoryValuationRows_joinsMetadata | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | purchaseSpendRows_filtersByDateRange | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | purchaseSpendRows_filtersByStatus | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/dao/DetailedReportsDaoTest.kt` | DetailedReportsDaoTest | wasteValueRows_filtersByStatus | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/migration/RoomMigrationTest.kt` | RoomMigrationTest | createDatabaseDirectlyAtVersion2 | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/migration/RoomMigrationTest.kt` | RoomMigrationTest | migrate1To2_preservesData_supportsNullCost_andOpensInRoom | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/BackupIsolationTest.kt` | BackupIsolationTest | createSnapshot_isolatesAllTablesByRestaurant | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/ReportingIsolationTest.kt` | ReportingIsolationTest | inventoryDetails_hardenedIsolation_excludesCrossRestaurantMetadata | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/ReportingIsolationTest.kt` | ReportingIsolationTest | purchaseDetails_hardenedIsolation_excludesCrossRestaurantSuppliers | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/ReportingIsolationTest.kt` | ReportingIsolationTest | recentWasteActivity_hardenedIsolation_excludesCrossRestaurantMetadata | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/ReportingIsolationTest.kt` | ReportingIsolationTest | wasteDetails_hardenedIsolation_excludesCrossRestaurantMetadata | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryIntegrationTest.kt` | FixedTimeProvider | observeDashboard_emptyDatabase_returnsZeros | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_aggregatesAreasAndCalculatesValue | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_alertOnlyRow_aggregateZeroButNegativeArea | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_excludedRow_allZeroNoNegative | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_missingCost_onlyForStocked | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_strictDecimalValidation | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeInventoryDetails_zeroCost_isValid | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observePurchaseDetails_strictDecimalValidation | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomDetailedReportsRepositoryTest.kt` | RoomDetailedReportsRepositoryTest | observeWasteDetails_strictSnapshotValidation | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomIngredientRepositoryTest.kt` | RoomIngredientRepositoryTest | createIngredientWithBaseOption_succeeds | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | archiveFinalArea_scopedByRestaurant | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | archiveFinalArea_throwsError | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | reorder_otherRestaurant_throwsError | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | reorder_subset_throwsError | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventoryAreaRepositoryTest.kt` | RoomInventoryAreaRepositoryTest | reorder_updatesSortOrderContiguously | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceFailureTest.kt` | RoomInventorySnapshotServiceFailureTest | calculateAt_reversalOfReversal_throws | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceFailureTest.kt` | RoomInventorySnapshotServiceFailureTest | calculateAt_reversalWithoutTarget_throws | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_futureReversal_doesNotCancel | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_historyWithoutCost_returnsNullCost | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_noHistory_returnsEmpty | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_reversal_cancelsOriginal | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomInventorySnapshotServiceTest.kt` | RoomInventorySnapshotServiceTest | calculateAt_withHistory_returnsSnapshot | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomPurchaseRepositoryTest.kt` | RoomPurchaseRepositoryTest | fullLifecycle_draft_to_posted_to_void | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomRestaurantRepositoryTest.kt` | RoomRestaurantRepositoryTest | save_existingRestaurant_updates | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomRestaurantRepositoryTest.kt` | RoomRestaurantRepositoryTest | save_newRestaurant_inserts | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomStockCountRepositoryTest.kt` | RoomStockCountRepositoryTest | fullLifecycle_start_save_complete_void | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/RoomWasteRepositoryTest.kt` | RoomWasteRepositoryTest | fullLifecycle_create_post_void | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | archiveSupplier_removesFromActiveList | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | createSupplier_failsOnDuplicateName | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | createSupplier_failsOnOwnershipMismatch | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | createSupplier_succeeds | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/database/repository/SupplierRepositoryTest.kt` | SupplierRepositoryTest | updateSupplier_updatesAllowedFieldsOnly | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/domain/service/ChickenIntegrationTest.kt` | ChickenIntegrationTest | createChickenIngredientWithMultipleUnits | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/domain/service/PurchaseIntegrationTest.kt` | PurchaseIntegrationTest | insertAndReadPurchase | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt` | DataStoreAppPreferencesRepositoryTest | corruptDraft_isRemovedSynchronously | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt` | DataStoreAppPreferencesRepositoryTest | saveAndLoadDraft | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt` | DataStoreAppPreferencesRepositoryTest | saveAndObservePreferences | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/core/preferences/datastore/DataStoreAppPreferencesRepositoryTest.kt` | DataStoreAppPreferencesRepositoryTest | unsupportedVersion_isRemovedSynchronously | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/counts/ui/StockCountLifecycleTest.kt` | StockCountLifecycleTest | full_lifecycle_test | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/counts/ui/StockCountUiTest.kt` | StockCountUiTest | start_count_flow | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/home/HomeUiTest.kt` | HomeUiTest | dashboard_fullVerification_populatedData | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/home/HomeUiTest.kt` | HomeUiTest | dashboard_navigation_to_reports | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/ingredients/ui/IngredientDetailUiTest.kt` | IngredientDetailUiTest | archive_ingredient_flow | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/ingredients/ui/IngredientUiTest.kt` | IngredientUiTest | complete_ingredient_e2e_flow | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/onboarding/ui/OnboardingUiTest.kt` | OnboardingUiTest | onboarding_fullFlow_persistsRestaurantAndNavigatesToHome | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/onboarding/ui/OnboardingUiTest.kt` | OnboardingUiTest | onboarding_start_navigation | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/purchases/ui/PurchaseFailureUiTest.kt` | PurchaseFailureUiTest | purchasePost_rollback_onFailure | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/purchases/ui/PurchaseUiTest.kt` | PurchaseUiTest | complete_purchase_lifecycle | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/DetailedReportsUiTest.kt` | DetailedReportsUiTest | reports_display_seeded_values | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/InventoryDetailScreenTest.kt` | InventoryDetailScreenTest | inventoryDetail_exists | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/PurchaseDetailScreenTest.kt` | PurchaseDetailScreenTest | purchaseDetail_exists | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportingRefreshComposeTest.kt` | ReportingRefreshComposeTest | reportsRefresh_exists | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reportsOverview_rangeRefresh_noFlicker | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reports_navigation_homeToReports_andBack | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reports_populatedData_verification | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reports_rangeSwitching_7_days | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/ReportsUiTest.kt` | ReportsUiTest | reports_rangeSwitching_90_days | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/reports/WasteDetailScreenTest.kt` | WasteDetailScreenTest | wasteDetail_exists | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/settings/ui/SettingsBackupUiTest.kt` | SettingsBackupUiTest | createBackup_buttonExists | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_changingAwayFromArchivedReferences_persistsActiveValues | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_crossIngredientUnitOption_showsSafeErrorAndPreventsSave | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_existingArchivedReferences_displayedAndPreservedOnSave | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_missingAreaReference_showsSafeError | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_missingIngredientReference_showsSafeErrorAndDisablesSave | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteArchiveUiTest.kt` | WasteArchiveUiTest | wasteForm_missingUnitOptionReference_showsSafeError | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteFailureUiTest.kt` | WasteFailureUiTest | wasteDelete_failureRollback_andRetrySuccess | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteFailureUiTest.kt` | WasteFailureUiTest | wastePost_failureRollback_andRetrySuccess | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteFailureUiTest.kt` | WasteFailureUiTest | wasteVoid_failureRollback_andRetrySuccess | Inst | PASS |
| `app/src/androidTest/kotlin/com/venkoi/cuentame/feature/waste/ui/WasteLifecycleTest.kt` | WasteLifecycleTest | navigateToWasteAndBack | Inst | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt` | ArchitectureTest | aliasedForbiddenImportDetected | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt` | ArchitectureTest | enforcePackageBoundaries | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt` | ArchitectureTest | ruleCoreToFeatureViolationDetected | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt` | ArchitectureTest | ruleDomainToComposeViolationDetected | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt` | ArchitectureTest | ruleModelToDatabaseViolationDetected | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/ArchitectureTest.kt` | ArchitectureTest | rulePureBackupToDatabaseViolationDetected | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/ExampleUnitTest.kt` | ExampleUnitTest | addition_isCorrect | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupRepositoryTest.kt` | AndroidBackupRepositoryTest | cleans destination on planning failure | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/AndroidBackupRepositoryTest.kt` | AndroidBackupRepositoryTest | full successful orchestration sequence | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe accepts simple alphanumeric paths | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe accepts valid attachment paths | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe rejects absolute paths | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe rejects backslashes | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe rejects blank names | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ArchiveEntryValidatorTest.kt` | ArchiveEntryValidatorTest | isSafe rejects relative traversal | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt` | AttachmentFilenameSanitizerTest | isValid validates correctly | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt` | AttachmentFilenameSanitizerTest | sanitize preserves extensions | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/AttachmentFilenameSanitizerTest.kt` | AttachmentFilenameSanitizerTest | sanitize removes dangerous characters | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | positive control - valid archive passes | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with checksum key mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with checksum value mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with duplicate entry | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with malformed UTF-8 manifest | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with missing manifest | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with schema version mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with traversal path | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveValidatorAdversarialTest.kt` | BackupArchiveValidatorAdversarialTest | rejects archive with unexpected entry | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer detects attachment growth during write | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer detects database payload checksum mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer detects total limit exceeded during write using memory-safe override | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer rejects plan with checksum map mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupArchiveWriterTest.kt` | BackupArchiveWriterTest | writer releases resources and does not close underlying stream | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupByteMathTest.kt` | BackupByteMathTest | addExact_overflow_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupByteMathTest.kt` | BackupByteMathTest | addExact_rejectsNegative | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupByteMathTest.kt` | BackupByteMathTest | addExact_success | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupCoordinatorTest.kt` | BackupCleanupCoordinatorTest | returns Deleted when delete succeeds | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupCoordinatorTest.kt` | BackupCleanupCoordinatorTest | returns Failed when both fail | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupCoordinatorTest.kt` | BackupCleanupCoordinatorTest | returns Failed when delete throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupCoordinatorTest.kt` | BackupCleanupCoordinatorTest | returns Truncated when delete fails but truncate succeeds | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupLifecycleTest.kt` | BackupCleanupLifecycleTest | creation failure triggers cleanup | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupLifecycleTest.kt` | BackupCleanupLifecycleTest | failed deletion falls back to truncate | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCleanupLifecycleTest.kt` | BackupCleanupLifecycleTest | preflight failure does not open destination for write | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | fails with MissingAttachmentSource when binding is missing | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | maps missing restaurant to RestaurantDisappeared | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | maps reconciliation failure to LocaleReconciliationFailed | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | plan ensures defensive copies of byte arrays | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | plan reflects attachment list from binding | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | rejects plan if attachment ID is invalid | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | rejects plan if schema version mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupCreationPlannerTest.kt` | BackupCreationPlannerTest | successful deterministic plan | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupFilenameGeneratorTest.kt` | BackupFilenameGeneratorTest | generates valid name with restaurant | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupFilenameGeneratorTest.kt` | BackupFilenameGeneratorTest | generates valid name without restaurant | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupFilenameGeneratorTest.kt` | BackupFilenameGeneratorTest | sanitizes restaurant name | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | rejects invalid locale | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | rejects missing table metadata | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | rejects overlong attachment list | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | rejects unsupported format version | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupManifestValidatorTest.kt` | BackupManifestValidatorTest | valid manifest passes | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | create rejects total size mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | create with valid data succeeds | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | plan is immutable and performs defensive copies | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | rejects duplicate manifest attachment ID | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupPlanTest.kt` | BackupPlanTest | rejects manifest metadata mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupRoundTripTest.kt` | BackupRoundTripTest | archive determinism - identical inputs produce identical bytes | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupRoundTripTest.kt` | BackupRoundTripTest | complete round trip with no attachments | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupRoundTripTest.kt` | BackupRoundTripTest | complete round trip with shared attachments | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityNumericTest.kt` | BackupSnapshotIntegrityNumericTest | rejects malformed decimal | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityNumericTest.kt` | BackupSnapshotIntegrityNumericTest | rejects negative purchase quantity | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityProjectionTest.kt` | BackupSnapshotIntegrityProjectionTest | rejects balance projection mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityReversalTest.kt` | BackupSnapshotIntegrityReversalTest | rejects reversal targeting another reversal | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | BackupSnapshotIntegrityValidatorTest | validate accepts valid simple snapshot | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | BackupSnapshotIntegrityValidatorTest | validate rejects balance projection mismatch | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | BackupSnapshotIntegrityValidatorTest | validate rejects posted purchase receipt without movement | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/BackupSnapshotIntegrityValidatorTest.kt` | BackupSnapshotIntegrityValidatorTest | validate rejects zero purchase quantity | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse duplicate key fails | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse empty input fails | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse empty object fails | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse malformed escape fails | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse non-hex hash fails | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse self reference fails | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse trailing comma fails | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse trailing content fails | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumParserTest.kt` | ChecksumParserTest | parse valid sorted object succeeds | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumTest.kt` | ChecksumTest | ImmutableBackupBytes sha256 matches manual digest | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/ChecksumTest.kt` | ChecksumTest | sha256 is deterministic | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | InsufficientStorageDetectionTest | detects ENOSPC from message | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | InsufficientStorageDetectionTest | detects SecurityException as PermissionDenied | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | InsufficientStorageDetectionTest | detects open failure as DestinationUnavailable | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/InsufficientStorageDetectionTest.kt` | InsufficientStorageDetectionTest | falls back to GenericIo for generic IOException | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects duplicate references | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects empty references | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects invalid attachment ID | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects invalid checksum | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects invalid display name | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects non-canonical archive path | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects unsafe archive path | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create rejects unsupported record type | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/PlannedBackupAttachmentTest.kt` | PlannedBackupAttachmentTest | create with valid data succeeds | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/SupportedAppLocaleTest.kt` | SupportedAppLocaleTest | fromLanguageTag returns correct enum for valid tags | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/SupportedAppLocaleTest.kt` | SupportedAppLocaleTest | fromLanguageTag returns null for invalid tags | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/backup/SupportedAppLocaleTest.kt` | SupportedAppLocaleTest | languageTags contains all supported tags | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/common/decimal/DecimalPersistenceTest.kt` | DecimalPersistenceTest | toBigDecimalValue fails on invalid string | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/common/decimal/DecimalPersistenceTest.kt` | DecimalPersistenceTest | toBigDecimalValue parses canonical string | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/common/decimal/DecimalPersistenceTest.kt` | DecimalPersistenceTest | toStorageString preserves plain format | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/common/ids/IdGeneratorTest.kt` | IdGeneratorTest | newId returns non-empty string | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/common/ids/IdGeneratorTest.kt` | IdGeneratorTest | newId returns unique strings | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/common/text/NameNormalizationTest.kt` | NameNormalizationTest | normalizeName handles empty string | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/common/text/NameNormalizationTest.kt` | NameNormalizationTest | normalizeName lowercases root locale | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/common/text/NameNormalizationTest.kt` | NameNormalizationTest | normalizeName trims and collapses spaces | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/common/time/TimeProviderTest.kt` | TimeProviderTest | now returns current time | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | calculate inventory valuation with multiple areas and negative quantities | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | calculate purchase spend comparison correctly | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | completed count summary is independent of lines | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | empty data returns zeroed snapshot | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | invalid inventory decimal throws error | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | negative cost throws error | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | null waste valuation throws error | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | recent activity uses structured data without fallback prose | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/RoomDashboardRepositoryTest.kt` | RoomDashboardRepositoryTest | waste value uses historical snapshots | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | costWithoutTotal_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | incorrectTotalEquation_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | missingReversal_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | positiveWasteQuantity_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | reversalOfReversal_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | validPostedHistory_passes | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | validVoidedHistory_passes | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongArea_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongIngredient_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongRestaurant_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongReversalOperationId_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | wrongReversalTarget_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/database/repository/WasteMovementHistoryValidatorTest.kt` | WasteMovementHistoryValidatorTest | zeroWasteQuantity_throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatCurrency_ES_Locale | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatCurrency_US_Locale | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatCurrency_invalidCode_fallbackToLiteral | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatPercent_ES_Locale | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatPercent_US_Locale | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatQuantity_roundsToThreePlaces | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/designsystem/util/FormattersTest.kt` | FormattersTest | formatQuantity_stripsZeros | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/ChickenFixtureTest.kt` | ChickenFixtureTest | Chicken Breast fixture domain validation | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/CountAdjustmentCalculatorTest.kt` | CountAdjustmentCalculatorTest | negative adjustment | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/CountAdjustmentCalculatorTest.kt` | CountAdjustmentCalculatorTest | positive adjustment | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/CountComparisonCalculatorTest.kt` | CountComparisonCalculatorTest | calculateUnclassifiedUsage with simple values | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/IngredientUnitConverterTest.kt` | IngredientUnitConverterTest | fromBase converts lb to case | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/IngredientUnitConverterTest.kt` | IngredientUnitConverterTest | toBase converts case to lb | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/InventoryBalanceCalculatorTest.kt` | InventoryBalanceCalculatorTest | calculateBalance handles empty list | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/InventoryBalanceCalculatorTest.kt` | InventoryBalanceCalculatorTest | calculateBalance sums quantities | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/InventoryMovementServiceTest.kt` | InventoryMovementServiceTest | cannot reverse a reversal | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/InventoryMovementServiceTest.kt` | InventoryMovementServiceTest | createReversal returns negative quantity and references original | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt` | ReportingPeriodCalculatorTest | calculatePeriods_30Days | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt` | ReportingPeriodCalculatorTest | calculatePeriods_7Days | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/ReportingPeriodCalculatorTest.kt` | ReportingPeriodCalculatorTest | calculatePeriods_90Days | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/StandardUnitConverterTest.kt` | StandardUnitConverterTest | convert incompatible dimensions throws | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/StandardUnitConverterTest.kt` | StandardUnitConverterTest | convert kg to grams | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/StandardUnitConverterTest.kt` | StandardUnitConverterTest | convert lb to grams | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/StandardUnitConverterTest.kt` | StandardUnitConverterTest | convert same units returns same value | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt` | WeightedAverageCostCalculatorTest | calculate first purchase | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt` | WeightedAverageCostCalculatorTest | calculate with existing inventory | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/service/WeightedAverageCostCalculatorTest.kt` | WeightedAverageCostCalculatorTest | calculate with zero cost purchase | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/CompleteOnboardingUseCaseTest.kt` | CompleteOnboardingUseCaseTest | AlreadyCompleted uses Room locale as authoritative | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/CompleteOnboardingUseCaseTest.kt` | CompleteOnboardingUseCaseTest | Success updates DataStore and clears draft | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/PreviewWasteUseCaseTest.kt` | PreviewWasteUseCaseTest | calculates preview correctly | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt` | ResolveAppStartStateUseCaseTest | both complete returns Ready | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt` | ResolveAppStartStateUseCaseTest | both incomplete returns RequiresOnboarding | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt` | ResolveAppStartStateUseCaseTest | db complete but locale mismatch repairs DataStore and returns Ready | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/ResolveAppStartStateUseCaseTest.kt` | ResolveAppStartStateUseCaseTest | db incomplete but DataStore says complete repairs DataStore and returns RequiresOnboarding | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/locale/AppLocaleUseCaseTest.kt` | AppLocaleUseCaseTest | reconciler handles room failure as ordinary failure | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/core/domain/usecase/locale/AppLocaleUseCaseTest.kt` | AppLocaleUseCaseTest | reconciler rethrows cancellation exception | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | area selection works | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | initial state loads areas | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | overlapping area is disabled | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | start count fails with future date | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StartStockCountViewModelTest.kt` | StartStockCountViewModelTest | start count succeeds with valid input | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt` | StockCountAreaViewModelRaceTest | Delete during CREATE captures generated ID and removes line | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt` | StockCountAreaViewModelRaceTest | Flush during CREATE does not create duplicate line | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelRaceTest.kt` | StockCountAreaViewModelRaceTest | Queued save and delete complete in order without deadlock | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | Archived unit becomes disabled after changing away | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | back navigation flushes pending saves | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | initial state loads correctly | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | invalid input blocks completion | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | rapid unit selection preserves final selection | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | stale save result does not overwrite newer state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | untouched suggestions are not pending | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountAreaViewModelTest.kt` | StockCountAreaViewModelTest | user edit makes line pending | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt` | StockCountDetailViewModelTest | complete count resets state after success | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt` | StockCountDetailViewModelTest | initial state loads correctly | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt` | StockCountDetailViewModelTest | not found state works | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/counts/viewmodel/StockCountDetailViewModelTest.kt` | StockCountDetailViewModelTest | ownership mismatch state works | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeScreenStateTest.kt` | HomeScreenStateTest | Ready state can be copied and updated | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | account switch resets to full Loading and hides old data | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | coverage mapping handles edge cases | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | initial repository failure triggers Error state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | initial state sequence | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | mapComparison handles states correctly | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | range refresh sequence | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | rapid range selection handles cancellation | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | retry after initial failure resubscribes | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | scale-independent zero handling | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/home/HomeViewModelTest.kt` | HomeViewModelTest | selecting same range is no-op | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | add package option emits success event | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | add standard option emits success event | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | archive option emits success event | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | default change emits success event | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | initial state loads ingredient | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | options are reactive | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientDetailViewModelTest.kt` | IngredientDetailViewModelTest | update package option emits success event | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | dimension selection resets base unit | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | edit mode hides unit mutation controls | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | initial state is not loading | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | onSave fails with blank name | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientFormViewModelTest.kt` | IngredientFormViewModelTest | onSave fails without dimension and base unit in create mode | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt` | IngredientListViewModelTest | archived toggle updates includeArchived flag | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt` | IngredientListViewModelTest | category filter filters ingredients | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt` | IngredientListViewModelTest | search filters ingredients case-insensitive | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/ingredients/viewmodel/IngredientListViewModelTest.kt` | IngredientListViewModelTest | search filters ingredients with normalization | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | autosave persists changes with debounce for name | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | autosave persists selections immediately | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | draft save failure is visible in UI state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | initial state loads defaults when no draft exists | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | next step persists the NEW step immediately | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/onboarding/viewmodel/OnboardingViewModelTest.kt` | OnboardingViewModelTest | reordering updates sort order and persists immediately | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | void purchase success updates state to VOIDED | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseDraftViewModelTest.kt` | PurchaseDraftViewModelTest | delete line success emits event | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseDraftViewModelTest.kt` | PurchaseDraftViewModelTest | post purchase success emits event | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt` | PurchaseLineViewModelTest | ingredient selection updates options and previews | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt` | PurchaseLineViewModelTest | initial state is loading then ready for new line | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseLineViewModelTest.kt` | PurchaseLineViewModelTest | rapid ingredient selection cancels previous work | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseListViewModelTest.kt` | PurchaseListViewModelTest | list updates when repository emits new purchases | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/purchases/viewmodel/PurchaseListViewModelTest.kt` | PurchaseListViewModelTest | search updates filter | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/ReportsScreenStateTest.kt` | ReportsScreenStateTest | readyStateCanBeCopied | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt` | InventoryDetailViewModelTest | Ready state refresh sequence | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt` | InventoryDetailViewModelTest | account switch resets to full Loading and hides old data | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt` | InventoryDetailViewModelTest | initial state is Loading then SetupRequired when no restaurant | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/InventoryDetailViewModelTest.kt` | InventoryDetailViewModelTest | refresh failure preserves old data | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | Ready state success sequence | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | account switch resets to Loading | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | initial repository failure triggers Error state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | initial retry resubscribes | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | initial state is Loading then SetupRequired when no restaurant | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | malformed range defaults to 30 days | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | missing range defaults to 30 days | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | navigation-provided 7-day range | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | navigation-provided 90-day range | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | range change triggers refreshing sequence with distinct periods | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/PurchaseDetailViewModelTest.kt` | PurchaseDetailViewModelTest | selecting same range does not trigger new request | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | Ready state refresh sequence | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | account switch resets to full Loading and hides old data | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | initial repository failure triggers Error state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | initial state is Loading then SetupRequired when no restaurant | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | mapping coverage and fields | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/ReportsViewModelTest.kt` | ReportsViewModelTest | retry after initial failure resubscribes | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | account switch resets identity | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | initial error and retry | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | initial state sequence | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | malformed range defaults to 30 days | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | missing range defaults to 30 days | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | navigation-provided 7-day range | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | navigation-provided 90-day range | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | range change triggers refreshing sequence with distinct periods | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | refresh failure preserves old data | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/reports/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | selecting same range is no-op | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | initial state is Idle | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | interrupted survives second recreation | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | malformed WAITING state with active ID -1 becomes Idle | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | reset cancels active preparation job | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | restored CREATING state shows OperationInterrupted error | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | stale file selection is rejected | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | stale repository emissions are ignored after reset | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/settings/viewmodel/BackupViewModelTest.kt` | BackupViewModelTest | two simultaneous create requests only start one operation | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/suppliers/viewmodel/SupplierFormViewModelTest.kt` | SupplierFormViewModelTest | save supplier success emits event | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/suppliers/viewmodel/SupplierListViewModelTest.kt` | SupplierListViewModelTest | list updates when repository emits new suppliers | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | invalid route state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | loading to ready state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | not found state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteDetailViewModelTest.kt` | WasteDetailViewModelTest | ownership mismatch state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | attachment permission failure handles error and preserves existing | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | initial state is Loading then Ready | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | preview failure handles error and clears preview | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | selecting ingredient updates unit options and handles loading state | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteFormViewModelTest.kt` | WasteFormViewModelTest | unit option repository failure produces error and disables save | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteListViewModelTest.kt` | WasteListViewModelTest | initial state shows events from repository | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteRaceTest.kt` | WasteRaceTest | ingredientRace_lastSelectionWins | JVM | PASS |
| `app/src/test/kotlin/com/venkoi/cuentame/feature/waste/viewmodel/WasteRaceTest.kt` | WasteRaceTest | previewCancellation_latestRequestWins | JVM | PASS |
