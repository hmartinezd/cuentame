package com.venkoi.cuentame.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.BackupFormatV1Contract
import com.venkoi.cuentame.core.backup.platform.BackupManifestContractValidator
import com.venkoi.cuentame.core.model.backup.BackupRestoreFailure
import com.venkoi.cuentame.core.model.backup.TableMetadata
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManifestContractTest {

    @Test
    fun createManifestForSnapshot_populatedSchema4_producesConsistentManifest() {
        val snapshot = BackupTestFixtures.createPopulatedSchema4Snapshot()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 4,
            restaurantName = "Test Populated",
            localeTag = "en-US",
            currencyCode = "USD",
        )
        
        assertThat(manifest.databaseSchemaVersion).isEqualTo(4)
        assertThat(manifest.restaurantId).isEqualTo("r1")
        assertThat(manifest.checksumAlgorithm).isEqualTo("SHA-256")
        
        // Assert all expected tables for schema 4
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(4)
        assertThat(manifest.tableMetadata.keys).containsExactlyElementsIn(expectedTables)
        
        // Assert specific populated counts
        assertThat(manifest.tableMetadata["restaurants"]?.entryCount).isEqualTo(1)
        assertThat(manifest.tableMetadata["ingredients"]?.entryCount).isEqualTo(2)
        assertThat(manifest.tableMetadata["production_batches"]?.entryCount).isEqualTo(1)
        
        // Assert derived flags
        val derivedTables = BackupFormatV1Contract.DERIVED_TABLES
        for (table in expectedTables) {
            val meta = manifest.tableMetadata[table]!!
            assertThat(meta.isDerived).isEqualTo(table in derivedTables)
        }
        
        // Assert consistency returns null (success)
        val result = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(result).isNull()
    }

    @Test
    fun representativeSnapshot_schema2_consistent() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(com.venkoi.cuentame.core.backup.model.RestaurantBackupDto("r1", "Rest", "USD", "en-US", 0, 0, null)),
            inventoryAreas = listOf(com.venkoi.cuentame.core.backup.model.InventoryAreaBackupDto("a1", "r1", "Area", "area", 0, true, 0, 0, null)),
            units = listOf(com.venkoi.cuentame.core.backup.model.UnitBackupDto("u1", "U", "u", "MASS", "1", true, 0)),
            ingredients = listOf(com.venkoi.cuentame.core.backup.model.IngredientBackupDto("i1", "r1", "I", "i", null, "u1", "a1", null, null, null, true, 0, 0, null)),
            ingredientUnitOptions = listOf(com.venkoi.cuentame.core.backup.model.IngredientUnitOptionBackupDto("o1", "i1", "O", "o", null, "1", true, true, true, true, 0, 0, null))
        )
        val manifest = createManifestForSnapshot(snapshot, 2, "Rest", "en-US", "USD")
        
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(2)
        assertThat(manifest.tableMetadata.keys).containsExactlyElementsIn(expectedTables)
        assertThat(manifest.tableMetadata.keys).containsNoneOf("preparation_recipes", "production_batches")
        
        assertThat(manifest.tableMetadata["restaurants"]?.entryCount).isEqualTo(1)
        assertThat(manifest.tableMetadata["ingredients"]?.entryCount).isEqualTo(1)
        
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isNull()
    }

    @Test
    fun representativeSnapshot_schema3_consistent() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(com.venkoi.cuentame.core.backup.model.RestaurantBackupDto("r1", "Rest", "USD", "en-US", 0, 0, null)),
            preparationRecipes = listOf(com.venkoi.cuentame.core.backup.model.PreparationRecipeBackupDto("rec1", "r1", "i1", "R", "r", "1", "1", "o1", "ACTIVE", null, 0, 0, null)),
            preparationRecipeComponents = listOf(com.venkoi.cuentame.core.backup.model.PreparationRecipeComponentBackupDto("rc1", "rec1", "i2", "o2", "1", "1", 0, null, 0, 0))
        )
        val manifest = createManifestForSnapshot(snapshot, 3, "Rest", "en-US", "USD")
        
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(3)
        assertThat(manifest.tableMetadata.keys).containsExactlyElementsIn(expectedTables)
        assertThat(manifest.tableMetadata.keys).contains("preparation_recipes")
        assertThat(manifest.tableMetadata.keys).doesNotContain("production_batches")
        
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)).isNull()
    }

    @Test
    fun validateSnapshotConsistency_schema2_detectsUnexpectedTables() {
        // Schema 4 snapshot being claimed as schema 2
        val snapshot = BackupTestFixtures.createPopulatedSchema4Snapshot()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 2,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD",
        )
        
        // This should fail because snapshot contains recipes and batches which are NOT in schema 2
        val result = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(result).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun validateManifestStructure_detectsMissingTable() {
        val snapshot = BackupTestFixtures.createPopulatedSchema4Snapshot()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 4,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD",
        )
        
        // Remove one table from manifest
        val sabotagedMetadata = manifest.tableMetadata.toMutableMap()
        sabotagedMetadata.remove("production_batches")
        val sabotagedManifest = manifest.copy(tableMetadata = sabotagedMetadata)
        
        val result = BackupManifestContractValidator.validateManifestStructure(sabotagedManifest, emptyMap(), emptyMap())
        assertThat(result).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun validateManifestStructure_detectsUnexpectedTableInManifest() {
        val snapshot = BackupTestFixtures.createPopulatedSchema4Snapshot()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 4,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD",
        )
        
        // Add unexpected table to manifest
        val sabotagedMetadata = manifest.tableMetadata.toMutableMap()
        sabotagedMetadata["rogue_table"] = TableMetadata(entryCount = 1, isDerived = false)
        val sabotagedManifest = manifest.copy(tableMetadata = sabotagedMetadata)
        
        val result = BackupManifestContractValidator.validateManifestStructure(sabotagedManifest, emptyMap(), emptyMap())
        assertThat(result).isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun validateSnapshotConsistency_detectsCountMismatch() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 4,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD",
        )
        
        // Sabotage one count
        val sabotagedMetadata = manifest.tableMetadata.toMutableMap()
        sabotagedMetadata["ingredients"] = TableMetadata(entryCount = 99, isDerived = false)
        val sabotagedManifest = manifest.copy(tableMetadata = sabotagedMetadata)
        
        val result = BackupManifestContractValidator.validateSnapshotConsistency(sabotagedManifest, snapshot)
        assertThat(result).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun validateManifestStructure_detectsWrongDerivedFlags() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val manifest = createManifestForSnapshot(snapshot, 4, "T", "en-US", "USD")
        
        // Test ingredients = true (should be false)
        val meta1 = manifest.tableMetadata.toMutableMap()
        meta1["ingredients"] = TableMetadata(entryCount = 0, isDerived = true)
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest.copy(tableMetadata = meta1), emptyMap(), emptyMap()))
            .isEqualTo(BackupRestoreFailure.MalformedManifest)

        // Test inventory_balance_projections = false (should be true)
        val meta2 = manifest.tableMetadata.toMutableMap()
        meta2["inventory_balance_projections"] = TableMetadata(entryCount = 0, isDerived = false)
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest.copy(tableMetadata = meta2), emptyMap(), emptyMap()))
            .isEqualTo(BackupRestoreFailure.MalformedManifest)

        // Test ingredient_cost_projections = false (should be true)
        val meta3 = manifest.tableMetadata.toMutableMap()
        meta3["ingredient_cost_projections"] = TableMetadata(entryCount = 0, isDerived = false)
        assertThat(BackupManifestContractValidator.validateManifestStructure(manifest.copy(tableMetadata = meta3), emptyMap(), emptyMap()))
            .isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun validateManifestStructure_detectsBlankIdentity() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val base = createManifestForSnapshot(snapshot, 4, "T", "en-US", "USD")
        
        assertThat(BackupManifestContractValidator.validateManifestStructure(base.copy(restaurantId = ""), emptyMap(), emptyMap()))
            .isEqualTo(BackupRestoreFailure.MalformedManifest)
        assertThat(BackupManifestContractValidator.validateManifestStructure(base.copy(restaurantName = " "), emptyMap(), emptyMap()))
            .isEqualTo(BackupRestoreFailure.MalformedManifest)
        assertThat(BackupManifestContractValidator.validateManifestStructure(base.copy(localeTag = ""), emptyMap(), emptyMap()))
            .isEqualTo(BackupRestoreFailure.MalformedManifest)
        assertThat(BackupManifestContractValidator.validateManifestStructure(base.copy(currencyCode = " "), emptyMap(), emptyMap()))
            .isEqualTo(BackupRestoreFailure.MalformedManifest)
    }

    @Test
    fun validateManifestStructure_detectsUnsupportedSchema() {
        val snapshot = BackupTestFixtures.createPopulatedSchema4Snapshot()
        val validManifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 4,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD",
        )
        
        val unsupported = validManifest.copy(databaseSchemaVersion = 1)
        
        val result = BackupManifestContractValidator.validateManifestStructure(unsupported, emptyMap(), emptyMap())
        assertThat(result).isEqualTo(BackupRestoreFailure.IncompatibleSchemaVersion)
    }
}
