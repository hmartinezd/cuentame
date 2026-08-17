package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.menu.CashDiscountBehavior
import com.miara.cuentame.core.model.salesexport.*
import com.miara.cuentame.core.model.salesimport.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SalesImportCoordinatorIntegrationTest {
    @get:Rule val hiltRule=HiltAndroidRule(this)
    @Inject lateinit var database:RestaurantInventoryDatabase
    @Inject lateinit var consumptionCoordinator:SalesConsumptionPostingCoordinator
    private val restaurantId=RestaurantId("sales-restaurant")
    private val importedAt=Instant.parse("2026-08-16T20:00:00Z")
    private lateinit var coordinator:SalesImportCoordinator

    @Before fun setUp()=runBlocking {
        hiltRule.inject()
        database.clearAllTables()
        database.restaurantDao().insert(RestaurantEntity(restaurantId.value,"Sales","USD","en-US",0,0,null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity("unit", "Each", "each", "COUNT", BigDecimal.ONE, true, 0)))
        database.inventoryAreaDao().upsert(InventoryAreaEntity("walk-in",restaurantId.value,"Walk-in","walk-in",0,true,0,0,null))
        database.ingredientDao().insert(IngredientEntity("ingredient",restaurantId.value,"Ingredient","ingredient",null,"unit","walk-in",null,null,null,true,0,0,null))
        database.menuPublicationDao().insertPublication(MenuPublicationEntity("package",restaurantId.value,"menu",1,"Menu",null,BigDecimal.ZERO,"USD",1))
        database.menuPublicationDao().insertCategories(listOf(MenuPublicationCategoryEntity("category","package","source-category","Food",0)))
        database.menuPublicationDao().insertItems(listOf(MenuPublicationItemEntity("item","package","category","placement","recipe","Burger",BigDecimal("10.00"),CashDiscountBehavior.NONE,3,4,0)))
        database.menuPublicationDao().insertComponents(listOf(MenuPublicationItemComponentEntity("component","item","recipe-component","ingredient","option","walk-in",BigDecimal("2"),BigDecimal("2"),0)))
        coordinator=SalesImportCoordinator(database,database.restaurantDao(),database.menuPublicationDao(),database.salesImportDao(),object:TimeProvider{override fun now()=importedAt})
    }

    @Test fun successfulImport_persistsExactHistory_andExactReimportIsDuplicate()=runBlocking {
        val bytes=bytes(export())
        val prepared=(coordinator.prepare(restaurantId,bytes) as SalesImportPreparationResult.Ready).prepared
        val result=coordinator.commit(prepared) as SalesImportCommitResult.Imported
        val detail=result.detail
        assertThat(detail.salesImport.exportId).isEqualTo("export-a")
        assertThat(detail.salesImport.originalSha256).isEqualTo(sha256(bytes))
        assertThat(detail.salesImport.importedAt).isEqualTo(importedAt)
        assertThat(detail.transactions.single().transaction.status).isEqualTo(ImportedSaleStatus.COMPLETED)
        with(detail.transactions.single().lines.single()) {
            assertThat(sellableItemId).isEqualTo("recipe");assertThat(displayNameSnapshot).isEqualTo("Burger")
            assertThat(quantity).isEqualTo(BigDecimal("2"));assertThat(unitPrice).isEqualTo(BigDecimal("10.00"))
            assertThat(gross).isEqualTo(BigDecimal("20.00"));assertThat(discount).isEqualTo(BigDecimal("1.00"));assertThat(net).isEqualTo(BigDecimal("19.00"))
            assertThat(commercialRevision).isEqualTo(3);assertThat(consumptionRevision).isEqualTo(4)
        }
        assertThat(coordinator.prepare(restaurantId,bytes)).isInstanceOf(SalesImportPreparationResult.Duplicate::class.java)
        assertThat(database.salesImportDao().getTransactionsForImport("export-a")).hasSize(1)
        assertThat(database.salesImportDao().getLines("terminal","transaction-1")).hasSize(1)
    }

    @Test fun exportIdConflict_andTransactionConflict_doNotPartiallyWrite()=runBlocking {
        import(export())
        val exportConflict=export().copy(generatedAt="2026-08-16T19:01:00Z")
        assertFailure(coordinator.prepare(restaurantId,bytes(exportConflict)),SalesImportFailureCode.EXPORT_ID_CONFLICT)
        val transactionConflict=export("export-b").let { it.copy(transactions=listOf(it.transactions.single().copy(lines=listOf(it.transactions.single().lines.single().copy(quantity="3",gross="30",net="29"))))) }
        assertFailure(coordinator.prepare(restaurantId,bytes(transactionConflict)),SalesImportFailureCode.TRANSACTION_CONFLICT)
        assertThat(database.salesImportDao().getImport("export-b")).isNull()
    }

    @Test fun overlappingExports_deduplicateCanonicalTransaction_andKeepBothReferences()=runBlocking {
        val first=export().copy(transactions=listOf(transaction("transaction-1","line-1"),transaction("transaction-2","line-2")))
        val second=export("export-b","2026-08-16T19:01:00Z").copy(transactions=listOf(transaction("transaction-2","line-2"),transaction("transaction-3","line-3")))
        import(first);import(second)
        assertThat(database.salesImportDao().getTransactionsForImport("export-a").map{it.transactionId}).containsExactly("transaction-1","transaction-2")
        assertThat(database.salesImportDao().getTransactionsForImport("export-b").map{it.transactionId}).containsExactly("transaction-2","transaction-3")
    }

    @Test fun completedCanBecomeVoided_butCannotBecomeCompletedAgain()=runBlocking {
        import(export())
        import(export("export-b","2026-08-16T19:01:00Z").copy(transactions=listOf(transaction("transaction-1","line-1").copy(status="VOIDED"))))
        assertThat(database.salesImportDao().getTransaction("terminal","transaction-1")?.status).isEqualTo("VOIDED")
        assertFailure(coordinator.prepare(restaurantId,bytes(export("export-c","2026-08-16T19:02:00Z"))),SalesImportFailureCode.STALE_TRANSACTION_STATE)
        assertThat(database.salesImportDao().getImport("export-c")).isNull()
    }

    @Test fun boundedUtf8JsonAndSemanticFailuresPerformNoWrites()=runBlocking {
        assertFailure(coordinator.prepare(restaurantId,ByteArray(MAX_SALES_EXPORT_BYTES+1)),SalesImportFailureCode.FILE_TOO_LARGE)
        assertFailure(coordinator.prepare(restaurantId,byteArrayOf(0xC3.toByte(),0x28)),SalesImportFailureCode.INVALID_UTF8)
        assertFailure(coordinator.prepare(restaurantId,"{".toByteArray()),SalesImportFailureCode.INVALID_JSON)
        assertFailure(coordinator.prepare(restaurantId,bytes(export().copy(terminalId=""))),SalesImportFailureCode.INVALID_SALES_EXPORT)
        assertThat(database.salesImportDao().getImport("export-a")).isNull()
    }

    @Test fun commitRechecksDurableState_andSalesImportDoesNotAffectInventory()=runBlocking {
        val prepared=(coordinator.prepare(restaurantId,bytes(export())) as SalesImportPreparationResult.Ready).prepared
        database.salesImportDao().insertTransaction(ImportedSaleTransactionEntity("terminal","transaction-1",restaurantId.value,"package","menu",1,"2026-08-16","USD",0,1,"COMPLETED","prior",0,"prior",0))
        val before=inventoryMovementCount()
        val result=coordinator.commit(prepared)
        assertThat(result).isInstanceOf(SalesImportCommitResult.Failure::class.java)
        assertThat((result as SalesImportCommitResult.Failure).failure.code).isEqualTo(SalesImportFailureCode.TRANSACTION_CONFLICT)
        assertThat(database.salesImportDao().getImport("export-a")).isNull()
        assertThat(inventoryMovementCount()).isEqualTo(before)
    }

    @Test fun completedSalePostsImmutableComponentQuantity_andInspectionIsReadOnly()=runBlocking {
        import(export())
        assertThat(consumptionCoordinator.inspectImport("export-a").alignments.values.single()).isEqualTo(SalesConsumptionAlignment.NEEDS_RECONCILIATION)
        assertThat(inventoryMovementCount()).isEqualTo(0)
        assertThat(consumptionCoordinator.reconcileImport("export-a").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.Applied)
        val movements=database.inventoryMovementDao().getBySourceDocument("SALES_TRANSACTION",SalesTransactionSourceIdentity.encode("terminal","transaction-1"))
        with(movements.single()) {
            assertThat(movementType).isEqualTo("SALES_CONSUMPTION");assertThat(quantityBaseSigned).isEqualTo("-4")
            assertThat(ingredientId).isEqualTo("ingredient");assertThat(areaId).isEqualTo("walk-in")
            assertThat(sourceLineId).isEqualTo("line-1");assertThat(sourceOperationId).isEqualTo("sales-consumption:line-1:component")
            assertThat(effectiveAt).isEqualTo(Instant.parse("2026-08-16T18:30:00Z").toEpochMilli())
        }
        assertThat(consumptionCoordinator.reconcileImport("export-a").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.AlreadyAligned)
        assertThat(inventoryMovementCount()).isEqualTo(1)
    }

    private suspend fun import(value:SalesExportV1){val ready=coordinator.prepare(restaurantId,bytes(value)) as SalesImportPreparationResult.Ready;assertThat(coordinator.commit(ready.prepared)).isInstanceOf(SalesImportCommitResult.Imported::class.java)}
    private fun assertFailure(value:SalesImportPreparationResult,code:SalesImportFailureCode){assertThat(value).isInstanceOf(SalesImportPreparationResult.Failure::class.java);assertThat((value as SalesImportPreparationResult.Failure).failure.code).isEqualTo(code)}
    private fun bytes(value:SalesExportV1)=SalesExportJsonCodec.encode(value).toByteArray()
    private fun inventoryMovementCount():Long=database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM inventory_movements").use{cursor->cursor.moveToFirst();cursor.getLong(0)}
    private fun sha256(value:ByteArray)=MessageDigest.getInstance("SHA-256").digest(value).joinToString(""){"%02x".format(it)}
    private fun export(id:String="export-a",generatedAt:String="2026-08-16T19:00:00Z")=SalesExportV1(SALES_EXPORT_FORMAT,1,id,"terminal",restaurantId.value,generatedAt,"2026-08-16","package","menu",1,"USD",listOf(transaction("transaction-1","line-1")))
    private fun transaction(id:String,lineId:String)=SalesExportTransactionV1(id,"2026-08-16T18:00:00Z","2026-08-16T18:30:00Z","COMPLETED",listOf(SalesExportLineV1(lineId,"recipe","Burger","2","10.00","20.00","1.00","19.00",3,4)))
}
