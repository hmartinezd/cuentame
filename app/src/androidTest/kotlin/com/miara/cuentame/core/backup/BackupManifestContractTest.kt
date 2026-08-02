package com.miara.cuentame.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupFormatV1Contract
import com.miara.cuentame.core.backup.platform.BackupManifestContractValidator
import com.miara.cuentame.core.model.backup.TableMetadata
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
            restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto("r1", "Rest", "USD", "en-US", 0, 0, null)),
            inventoryAreas = listOf(com.miara.cuentame.core.backup.model.InventoryAreaBackupDto("a1", "r1", "Area", "area", 0, true, 0, 0, null)),
            units = listOf(com.miara.cuentame.core.backup.model.UnitBackupDto("u1", "U", "u", "MASS", "1", true, 0)),
            ingredients = listOf(com.miara.cuentame.core.backup.model.IngredientBackupDto("i1", "r1", "I", "i", null, "u1", "a1", null, null, null, true, 0, 0, null)),
            ingredientUnitOptions = listOf(com.miara.cuentame.core.backup.model.IngredientUnitOptionBackupDto("o1", "i1", "O", "o", null, "1", true, true, true, true, 0, 0, null))
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
            restaurants = listOf(com.miara.cuentame.core.backup.model.RestaurantBackupDto("r1", "Rest", "USD", "en-US", 0, 0, null)),
            preparationRecipes = listOf(com.miara.cuentame.core.backup.model.PreparationRecipeBackupDto("rec1", "r1", "i1", "R", "r", "1", "1", "o1", "ACTIVE", null, 0, 0, null)),
            preparationRecipeComponents = listOf(com.miara.cuentame.core.backup.model.PreparationRecipeComponentBackupDto("rc1", "rec1", "i2", "o2", "1", "1", 0, null, 0, 0))
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
        assertThat(result).isNotNull()
    }

    @Test
    fun validateSnapshotConsistency_detectsMissingTable() {
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
        
        val result = BackupManifestContractValidator.validateSnapshotConsistency(sabotagedManifest, snapshot)
        assertThat(result).isNotNull()
    }

    @Test
    fun validateSnapshotConsistency_detectsUnexpectedTableInManifest() {
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
        
        val result = BackupManifestContractValidator.validateSnapshotConsistency(sabotagedManifest, snapshot)
        assertThat(result).isNotNull()
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
        assertThat(result).isNotNull()
    }

    @Test
    fun validateSnapshotConsistency_detectsWrongDerivedFlag() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val manifest = createManifestForSnapshot(snapshot, 4, "T", "en-US", "USD")
        
        // Sabotage derived flag for non-derived table
        val sabotagedMetadata = manifest.tableMetadata.toMutableMap()
        sabotagedMetadata["ingredients"] = TableMetadata(entryCount = 0, isDerived = true)
        val sabotagedManifest = manifest.copy(tableMetadata = sabotagedMetadata)
        
        val result = BackupManifestContractValidator.validateSnapshotConsistency(sabotagedManifest, snapshot)
        assertThat(result).isNotNull()
    }

    @Test
    fun validateSnapshotConsistency_detectsBlankRestaurantId() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val manifest = createManifestForSnapshot(snapshot, 4, "T", "en-US", "USD").copy(restaurantId = " ")
        
        val result = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(result).isNotNull()
    }

    @Test
    fun validateSnapshotConsistency_detectsUnsupportedSchema() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val manifest = createManifestForSnapshot(snapshot, 1, "T", "en-US", "USD")
        
        val result = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(result).isNotNull()
    }
}
