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
    fun createManifestForSnapshot_producesConsistentManifest() {
        val snapshot = BackupTestFixtures.createEmptySnapshotDto()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 4,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD"
        )
        
        assertThat(manifest.databaseSchemaVersion).isEqualTo(4)
        assertThat(manifest.tableMetadata.keys).containsExactlyElementsIn(BackupFormatV1Contract.expectedTablesForSchema(4))
        
        // Assert consistency returns null (success)
        val result = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(result).isNull()
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
