package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

class SalesImportBackupIntegrityTest {
    private val manifest = BackupManifest(1, "2026-08-16T00:00:00Z", "com.miara.cuentame", "1", 1, 5,
        "restaurant", "Restaurant", "en-US", "USD", emptyMap(), emptyList(), listOf("data"), "SHA-256")

    @Test fun `valid historical sales graph passes`() = assertThat(validate(snapshot()).isSuccess).isTrue()

    @Test fun `sales import must match immutable publication`() {
        val base = snapshot(); val value = base.salesImports.single()
        listOf(value.copy(menuId="other"), value.copy(publicationRevision=2), value.copy(currency="EUR")).forEach {
            assertFailure(base.copy(salesImports=listOf(it)), BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH)
        }
    }

    @Test fun `transaction must match publication and audit exports`() {
        val base=snapshot(); val transaction=base.importedSaleTransactions.single()
        listOf(transaction.copy(menuId="other"),transaction.copy(publicationRevision=2),transaction.copy(currency="EUR")).forEach {
            assertFailure(base.copy(importedSaleTransactions=listOf(it)), BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH)
        }
        assertFailure(base.copy(importedSaleTransactions=listOf(transaction.copy(firstSeenExportId="missing"))),BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
        assertFailure(base.copy(importedSaleTransactions=listOf(transaction.copy(lastSeenExportId="missing"))),BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
    }

    @Test fun `reference cannot cross-link unrelated import history`() {
        val base=snapshot(); val value=base.salesImports.single()
        assertFailure(base.copy(salesImports=listOf(value.copy(restaurantId="other"))),BackupSnapshotIntegrityCode.RESTAURANT_ISOLATION_FAILURE)
        assertFailure(base.copy(salesImports=listOf(value.copy(menuPackageId="other"))),BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
    }

    @Test fun `line must match publication item provenance and arithmetic`() {
        val base=snapshot(); val line=base.importedSaleLines.single()
        val mutations=listOf(
            line.copy(sellableItemId="missing"), line.copy(commercialRevision=2), line.copy(consumptionRevision=2),
            line.copy(displayNameSnapshot="Other"), line.copy(unitPrice="11"), line.copy(gross="11")
        )
        mutations.forEach { assertThat(validate(base.copy(importedSaleLines=listOf(it))).isFailure).isTrue() }
    }

    @Test fun `canonical and reference composite identities are unique`() {
        val base=snapshot()
        assertFailure(base.copy(importedSaleTransactions=base.importedSaleTransactions+base.importedSaleTransactions.single()),BackupSnapshotIntegrityCode.DUPLICATE_COMPOSITE_KEY)
        assertFailure(base.copy(importedSaleLines=base.importedSaleLines+base.importedSaleLines.single()),BackupSnapshotIntegrityCode.DUPLICATE_COMPOSITE_KEY)
        assertFailure(base.copy(salesImportTransactionRefs=base.salesImportTransactionRefs+base.salesImportTransactionRefs.single()),BackupSnapshotIntegrityCode.DUPLICATE_COMPOSITE_KEY)
    }

    private fun snapshot():BackupSnapshotDto {
        val importedAt=1_700_000_100_000
        val generatedAt=1_700_000_000_000
        return BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants=listOf(RestaurantBackupDto("restaurant","Restaurant","USD","en-US",1,1,null)),
            menuPublications=listOf(MenuPublicationBackupDto("package","restaurant","menu",1,"Menu",null,"0","USD",1)),
            menuPublicationCategories=listOf(MenuPublicationCategoryBackupDto("category","package","source-category","Food",0)),
            menuPublicationItems=listOf(MenuPublicationItemBackupDto("item","package","category","placement","recipe","Burger","10.00","NONE",1,1,0)),
            salesImports=listOf(SalesImportBackupDto("export","a".repeat(64),"restaurant","terminal",generatedAt,"2026-08-16","package","menu",1,"USD",importedAt)),
            importedSaleTransactions=listOf(ImportedSaleTransactionBackupDto("terminal","transaction","restaurant","package","menu",1,"2026-08-16","USD",generatedAt-1000,generatedAt,"COMPLETED","export",importedAt,"export",generatedAt)),
            importedSaleLines=listOf(ImportedSaleLineBackupDto("terminal","line","transaction","recipe","Burger","2","10.0","20.00","1","19",1,1)),
            salesImportTransactionRefs=listOf(SalesImportTransactionRefBackupDto("export","terminal","transaction"))
        )
    }

    private fun validate(value:BackupSnapshotDto)=BackupSnapshotIntegrityValidator.validate(value,manifest)
    private fun assertFailure(value:BackupSnapshotDto,code:BackupSnapshotIntegrityCode){
        val result=validate(value)
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as BackupSnapshotIntegrityException).code).isEqualTo(code)
    }
}
