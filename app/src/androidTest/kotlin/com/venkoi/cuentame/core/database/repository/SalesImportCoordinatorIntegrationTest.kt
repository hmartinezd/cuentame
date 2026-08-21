package com.venkoi.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.time.TimeProvider
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.*
import com.venkoi.cuentame.core.model.menu.CashDiscountBehavior
import com.venkoi.cuentame.core.model.salesexport.*
import com.venkoi.cuentame.core.model.salesimport.*
import com.venkoi.cuentame.core.model.inventory.*
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

    @Test fun overlappingExports_deduplicateCanonicalTransaction_andKeepBothReferences()=runBlocking<Unit> {
        val first=export().copy(transactions=listOf(transaction("transaction-1","line-1"),transaction("transaction-2","line-2")))
        val second=export("export-b","2026-08-16T19:01:00Z").copy(transactions=listOf(transaction("transaction-2","line-2"),transaction("transaction-3","line-3")))
        import(first);import(second)
        assertThat(database.salesImportDao().getTransactionsForImport("export-a").map{it.transactionId}).containsExactly("transaction-1","transaction-2")
        assertThat(database.salesImportDao().getTransactionsForImport("export-b").map{it.transactionId}).containsExactly("transaction-2","transaction-3")
    }

    @Test fun overlappingExports_reconcileSharedTransactionOnlyOnce()=runBlocking {
        val first=export().copy(transactions=listOf(transaction("transaction-1","line-1"),transaction("transaction-2","line-2")))
        val second=export("export-b","2026-08-16T19:01:00Z").copy(transactions=listOf(transaction("transaction-2","line-2"),transaction("transaction-3","line-3")))
        import(first);import(second);consumptionCoordinator.reconcileImport("export-a");consumptionCoordinator.reconcileImport("export-b")
        val shared=database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,SalesTransactionSourceIdentity.encode("terminal","transaction-2"))
        assertThat(shared).hasSize(1);assertThat(shared.single().sourceOperationId).isEqualTo(InventoryMovementOperationIds.salesConsumption("line-2","component"))
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
        val semanticallyInvalid = SalesExportJsonCodec.encode(export())
            .replace("\"terminalId\": \"terminal\"", "\"terminalId\": \"\"")
            .toByteArray()
        assertFailure(coordinator.prepare(restaurantId,semanticallyInvalid),SalesImportFailureCode.INVALID_SALES_EXPORT)
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
        val projectionBeforeInspection=projectionQuantity()
        assertThat(consumptionCoordinator.inspectImport("export-a").alignments.values.single()).isEqualTo(SalesConsumptionAlignment.NEEDS_RECONCILIATION)
        assertThat(inventoryMovementCount()).isEqualTo(0)
        assertThat(projectionQuantity()).isEqualTo(projectionBeforeInspection)
        assertThat(consumptionCoordinator.reconcileImport("export-a").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.Applied)
        val movements=database.inventoryMovementDao().getBySourceDocument("SALES_TRANSACTION",SalesTransactionSourceIdentity.encode("terminal","transaction-1"))
        with(movements.single()) {
            assertThat(movementType).isEqualTo("SALES_CONSUMPTION");assertThat(quantityBaseSigned).isEqualTo("-4")
            assertThat(ingredientId).isEqualTo("ingredient");assertThat(areaId).isEqualTo("walk-in")
            assertThat(sourceDocumentType).isEqualTo(SourceDocumentType.SALES_TRANSACTION.name)
            assertThat(sourceDocumentId).isEqualTo(SalesTransactionSourceIdentity.encode("terminal","transaction-1"))
            assertThat(sourceLineId).isEqualTo("line-1");assertThat(sourceOperationId).isEqualTo(InventoryMovementOperationIds.salesConsumption("line-1","component"))
            assertThat(effectiveAt).isEqualTo(Instant.parse("2026-08-16T18:30:00Z").toEpochMilli())
            assertThat(reversalOfMovementId).isNull()
            assertThat(unitCostBaseSnapshot).isNull();assertThat(totalValueSnapshot).isNull()
        }
        assertThat(database.inventoryProjectionDao().getBalance("ingredient","walk-in")!!.quantityBase).isEqualTo("-4")
        assertThat(consumptionCoordinator.reconcileImport("export-a").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.AlreadyAligned)
        assertThat(inventoryMovementCount()).isEqualTo(1)
    }

    @Test fun historicalCost_isSnapshottedAndProducesNegativeTotalValue()=runBlocking {
        val beforeClose=Instant.parse("2026-08-16T18:00:00Z").toEpochMilli()
        database.inventoryMovementDao().insert(InventoryMovementEntity("purchase",restaurantId.value,"ingredient","walk-in",InventoryMovementType.PURCHASE.name,"10","3","30",beforeClose,SourceDocumentType.PURCHASE_RECEIPT.name,"purchase","purchase-op","purchase-line",null,beforeClose))
        import(export());assertThat(consumptionCoordinator.reconcileImport("export-a").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.Applied)
        val sale=database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,SalesTransactionSourceIdentity.encode("terminal","transaction-1")).single()
        assertThat(BigDecimal(sale.unitCostBaseSnapshot)).isEqualTo(BigDecimal("3"));assertThat(BigDecimal(sale.totalValueSnapshot)).isEqualTo(BigDecimal("-12"))
    }

    @Test fun reconciliationUsesImmutablePublicationAreaAfterIngredientDefaultChanges()=runBlocking {
        database.inventoryAreaDao().upsert(InventoryAreaEntity("line",restaurantId.value,"Line","line",1,true,0,0,null))
        database.ingredientDao().update(database.ingredientDao().getById("ingredient")!!.copy(defaultAreaId="line"))
        import(export());consumptionCoordinator.reconcileImport("export-a")
        val sale=database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,SalesTransactionSourceIdentity.encode("terminal","transaction-1")).single()
        assertThat(sale.areaId).isEqualTo("walk-in");assertThat(database.menuPublicationDao().getComponents("package").single().inventoryAreaIdSnapshot).isEqualTo("walk-in")
    }

    @Test fun preparedIngredientIsConsumedDirectlyWithoutRawIngredientExpansion()=runBlocking {
        database.ingredientDao().insert(IngredientEntity("raw",restaurantId.value,"Raw","raw",null,"unit","walk-in",null,null,null,true,0,0,null))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("raw-option","raw","Unit","unit",null,BigDecimal.ONE,true,true,true,true,0,0,null))
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("prepared-option","ingredient","Unit","unit",null,BigDecimal.ONE,true,true,true,true,0,0,null))
        database.preparationRecipeDao().insert(PreparationRecipeEntity("prep",restaurantId.value,"ingredient","Prepared","prepared",BigDecimal.ONE,BigDecimal.ONE,"prepared-option","ACTIVE",null,0,0,null))
        database.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity("prep-component","prep","raw","raw-option",BigDecimal("3"),BigDecimal("3"),0,null,0,0))
        import(export());consumptionCoordinator.reconcileImport("export-a")
        assertThat(database.inventoryMovementDao().getByIngredient("ingredient").filter{it.movementType==InventoryMovementType.SALES_CONSUMPTION.name}).hasSize(1)
        assertThat(database.inventoryMovementDao().getByIngredient("raw").filter{it.sourceDocumentType==SourceDocumentType.SALES_TRANSACTION.name}).isEmpty()
    }

    @Test fun multipleLinesAndComponents_preserveDistinctLedgerOperations()=runBlocking {
        database.menuPublicationDao().insertComponents(listOf(MenuPublicationItemComponentEntity("component-2","item","recipe-component-2","ingredient","option","walk-in",BigDecimal.ONE,BigDecimal.ONE,1)))
        val first=transaction("transaction-many","line-1");val second=first.lines.single().copy(saleLineId="line-2",quantity="3",gross="30.00",net="29.00")
        import(export().copy(transactions=listOf(first.copy(lines=listOf(first.lines.single(),second)))))
        assertThat(consumptionCoordinator.reconcileImport("export-a").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.Applied)
        val rows=database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,SalesTransactionSourceIdentity.encode("terminal","transaction-many"))
        assertThat(rows).hasSize(4);assertThat(rows.map{it.ingredientId}.distinct()).containsExactly("ingredient")
        val quantities=rows.associate{it.sourceOperationId to BigDecimal(it.quantityBaseSigned)}
        assertThat(quantities[InventoryMovementOperationIds.salesConsumption("line-1","component")]).isEqualTo(BigDecimal("-4"))
        assertThat(quantities[InventoryMovementOperationIds.salesConsumption("line-1","component-2")]).isEqualTo(BigDecimal("-2"))
        assertThat(quantities[InventoryMovementOperationIds.salesConsumption("line-2","component")]).isEqualTo(BigDecimal("-6"))
        assertThat(quantities[InventoryMovementOperationIds.salesConsumption("line-2","component-2")]).isEqualTo(BigDecimal("-3"))
    }

    @Test fun completedThenVoided_createsOneExactReversalPerOriginal_andIsIdempotent()=runBlocking {
        import(export());assertThat(consumptionCoordinator.reconcileImport("export-a").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.Applied)
        val sourceId=SalesTransactionSourceIdentity.encode("terminal","transaction-1");val original=database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,sourceId).single()
        val voided=export("export-b","2026-08-16T19:01:00Z").copy(transactions=listOf(transaction("transaction-1","line-1").copy(status="VOIDED")))
        import(voided);assertThat(consumptionCoordinator.reconcileImport("export-b").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.Reversed)
        val rows=database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,sourceId);assertThat(rows).hasSize(2)
        val reversal=rows.single{it.movementType==InventoryMovementType.REVERSAL.name};assertThat(reversal.reversalOfMovementId).isEqualTo(original.id)
        assertThat(BigDecimal(reversal.quantityBaseSigned)).isEqualTo(BigDecimal(original.quantityBaseSigned).negate())
        assertThat(reversal.unitCostBaseSnapshot).isEqualTo(original.unitCostBaseSnapshot);assertThat(reversal.totalValueSnapshot).isEqualTo(original.totalValueSnapshot?.let{BigDecimal(it).negate().toPlainString()})
        assertThat(consumptionCoordinator.reconcileImport("export-b").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.AlreadyAligned)
        assertThat(database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,sourceId)).hasSize(2)
    }

    @Test fun initiallyVoided_hasNoInventoryEffect()=runBlocking {
        import(export().copy(transactions=listOf(transaction("transaction-1","line-1").copy(status="VOIDED"))))
        assertThat(consumptionCoordinator.reconcileImport("export-a").results.values.single()).isEqualTo(SalesConsumptionTransactionResult.NoEffect)
        assertThat(inventoryMovementCount()).isEqualTo(0)
    }

    @Test fun partialOriginalGraph_isConflictAndIsNeverHealed()=runBlocking {
        database.menuPublicationDao().insertComponents(listOf(MenuPublicationItemComponentEntity("component-2","item","recipe-component-2","ingredient","option","walk-in",BigDecimal.ONE,BigDecimal.ONE,1)))
        import(export());val sourceId=SalesTransactionSourceIdentity.encode("terminal","transaction-1");val closed=Instant.parse("2026-08-16T18:30:00Z").toEpochMilli()
        database.inventoryMovementDao().insert(InventoryMovementEntity("partial",restaurantId.value,"ingredient","walk-in",InventoryMovementType.SALES_CONSUMPTION.name,"-4",null,null,closed,SourceDocumentType.SALES_TRANSACTION.name,sourceId,InventoryMovementOperationIds.salesConsumption("line-1","component"),"line-1",null,closed))
        val beforeProjection=projectionQuantity()
        assertThat(consumptionCoordinator.inspectTransaction("terminal","transaction-1")).isEqualTo(SalesConsumptionAlignment.HISTORY_CONFLICT)
        assertThat(consumptionCoordinator.reconcileTransaction("terminal","transaction-1")).isEqualTo(SalesConsumptionTransactionResult.Failed(SalesConsumptionFailureCode.HISTORY_CONFLICT))
        assertThat(database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,sourceId)).hasSize(1);assertThat(projectionQuantity()).isEqualTo(beforeProjection)
    }

    @Test fun partialReversalGraph_isConflictAndNoAdditionalReversalIsWritten()=runBlocking {
        database.menuPublicationDao().insertComponents(listOf(MenuPublicationItemComponentEntity("component-2","item","recipe-component-2","ingredient","option","walk-in",BigDecimal.ONE,BigDecimal.ONE,1)))
        import(export());consumptionCoordinator.reconcileImport("export-a");val sourceId=SalesTransactionSourceIdentity.encode("terminal","transaction-1")
        val originals=database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,sourceId)
        val voided=export("export-b","2026-08-16T19:01:00Z").copy(transactions=listOf(transaction("transaction-1","line-1").copy(status="VOIDED")));import(voided)
        val original=originals.first();val at=original.createdAt+1
        database.inventoryMovementDao().insert(InventoryMovementEntity("partial-reversal",original.restaurantId,original.ingredientId,original.areaId,InventoryMovementType.REVERSAL.name,BigDecimal(original.quantityBaseSigned).negate().toPlainString(),original.unitCostBaseSnapshot,original.totalValueSnapshot?.let{BigDecimal(it).negate().toPlainString()},at,SourceDocumentType.SALES_TRANSACTION.name,sourceId,InventoryMovementOperationIds.reversal(original.id),original.sourceLineId,original.id,at))
        assertThat(consumptionCoordinator.inspectTransaction("terminal","transaction-1")).isEqualTo(SalesConsumptionAlignment.HISTORY_CONFLICT)
        assertThat(consumptionCoordinator.reconcileTransaction("terminal","transaction-1")).isEqualTo(SalesConsumptionTransactionResult.Failed(SalesConsumptionFailureCode.HISTORY_CONFLICT))
        assertThat(database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,sourceId).filter{it.movementType==InventoryMovementType.REVERSAL.name}).hasSize(1)
    }

    @Test fun reconcileImport_isolatesInvalidTransactionAndContinues()=runBlocking {
        val value=export().copy(transactions=listOf(transaction("t1","l1"),transaction("t2","l2"),transaction("t3","l3")));import(value)
        database.openHelper.writableDatabase.execSQL("UPDATE imported_sale_lines SET sellableItemId='missing' WHERE transactionId='t2'")
        val result=consumptionCoordinator.reconcileImport("export-a").results
        assertThat(result["terminal" to "t1"]).isEqualTo(SalesConsumptionTransactionResult.Applied)
        assertThat(result["terminal" to "t2"]).isEqualTo(SalesConsumptionTransactionResult.Failed(SalesConsumptionFailureCode.PUBLICATION_ITEM_NOT_FOUND))
        assertThat(result["terminal" to "t3"]).isEqualTo(SalesConsumptionTransactionResult.Applied)
        assertThat(database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.SALES_TRANSACTION.name,SalesTransactionSourceIdentity.encode("terminal","t2"))).isEmpty()
    }

    private suspend fun import(value:SalesExportV1){val ready=coordinator.prepare(restaurantId,bytes(value)) as SalesImportPreparationResult.Ready;assertThat(coordinator.commit(ready.prepared)).isInstanceOf(SalesImportCommitResult.Imported::class.java)}
    private fun assertFailure(value:SalesImportPreparationResult,code:SalesImportFailureCode){assertThat(value).isInstanceOf(SalesImportPreparationResult.Failure::class.java);assertThat((value as SalesImportPreparationResult.Failure).failure.code).isEqualTo(code)}
    private fun bytes(value:SalesExportV1)=SalesExportJsonCodec.encode(value).toByteArray()
    private fun inventoryMovementCount():Long=database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM inventory_movements").use{cursor->cursor.moveToFirst();cursor.getLong(0)}
    private fun projectionQuantity():String?=database.openHelper.readableDatabase.query("SELECT quantityBase FROM inventory_balance_projection WHERE ingredientId='ingredient' AND areaId='walk-in'").use{cursor->if(cursor.moveToFirst())cursor.getString(0) else null}
    private fun sha256(value:ByteArray)=MessageDigest.getInstance("SHA-256").digest(value).joinToString(""){"%02x".format(it)}
    private fun export(id:String="export-a",generatedAt:String="2026-08-16T19:00:00Z")=SalesExportV1(SALES_EXPORT_FORMAT,1,id,"terminal",restaurantId.value,generatedAt,"2026-08-16","package","menu",1,"USD",listOf(transaction("transaction-1","line-1")))
    private fun transaction(id:String,lineId:String)=SalesExportTransactionV1(id,"2026-08-16T18:00:00Z","2026-08-16T18:30:00Z","COMPLETED",listOf(SalesExportLineV1(lineId,"recipe","Burger","2","10.00","20.00","1.00","19.00",3,4)))
}
