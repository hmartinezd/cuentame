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
            currencyCode = "USD"
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
    fun validateSnapshotConsistency_schema2_detectsUnexpectedTables() {
        // Schema 4 snapshot being claimed as schema 2
        val snapshot = BackupTestFixtures.createPopulatedSchema4Snapshot()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 2,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD"
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
            currencyCode = "USD"
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
            currencyCode = "USD"
        )
        
        // Add unexpected table to manifest
        val sabotagedMetadata = manifest.tableMetadata.toMutableMap()
        sabotagedMetadata["rogue_table"] = TableMetadata(entryCount = 1, isDerived = false)
        val sabotagedManifest = manifest.copy(tableMetadata = sabotagedMetadata)
        
        val result = BackupManifestContractValidator.validateSnapshotConsistency(sabotagedManifest, snapshot)
        assertThat(result).isNotNull()
    }

    @Test
    fun createManifestForSnapshot_schema3_producesCorrectKeys() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 3,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD"
        )
        
        assertThat(manifest.databaseSchemaVersion).isEqualTo(3)
        assertThat(manifest.tableMetadata.keys).containsExactlyElementsIn(BackupFormatV1Contract.expectedTablesForSchema(3))
        assertThat(manifest.tableMetadata.keys).containsNoneOf("production_batches", "production_batch_components")
    }

    @Test
    fun validateSnapshotConsistency_detectsCountMismatch() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 4,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD"
        )
        
        // Sabotage one count
        val sabotagedMetadata = manifest.tableMetadata.toMutableMap()
        sabotagedMetadata["ingredients"] = TableMetadata(entryCount = 99, isDerived = false)
        val sabotagedManifest = manifest.copy(tableMetadata = sabotagedMetadata)
        
        val result = BackupManifestContractValidator.validateSnapshotConsistency(sabotagedManifest, snapshot)
        assertThat(result).isNotNull()
    }
}
