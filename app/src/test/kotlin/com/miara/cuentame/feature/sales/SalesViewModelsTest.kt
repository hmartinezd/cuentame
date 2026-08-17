package com.miara.cuentame.feature.sales

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.domain.repository.SalesImportRepository
import com.miara.cuentame.core.database.repository.SalesConsumptionAlignment
import com.miara.cuentame.core.database.repository.SalesConsumptionImportAlignment
import com.miara.cuentame.core.database.repository.SalesConsumptionImportResult
import com.miara.cuentame.core.database.repository.SalesConsumptionPostingCoordinator
import com.miara.cuentame.core.database.repository.SalesConsumptionTransactionResult
import com.miara.cuentame.core.model.salesimport.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelsTest {
 private val dispatcher=StandardTestDispatcher();private val repository=mockk<SalesImportRepository>()
 @Before fun setup(){Dispatchers.setMain(dispatcher)}
 @After fun teardown(){Dispatchers.resetMain()}

 @Test fun `summary excludes voided amounts and counts current statuses`() {
  val detail=SalesImportDetail(anImport("CAD"),listOf(transaction("complete",ImportedSaleStatus.COMPLETED,"10.00","1.00","9.00"),transaction("void",ImportedSaleStatus.VOIDED,"50.00","2.00","48.00")))
  val summary=detail.summary()
  assertThat(summary.transactions).isEqualTo(2);assertThat(summary.completed).isEqualTo(1);assertThat(summary.voided).isEqualTo(1)
  assertThat(summary.gross.compareTo(BigDecimal("10.00"))).isEqualTo(0);assertThat(summary.discount.compareTo(BigDecimal("1.00"))).isEqualTo(0);assertThat(summary.net.compareTo(BigDecimal("9.00"))).isEqualTo(0)
  assertThat(detail.salesImport.currency).isEqualTo("CAD")
 }

 @Test fun `import detail loads successfully`()=runTest {val detail=SalesImportDetail(anImport(),listOf(transaction()));coEvery{repository.getImport("export") } returns detail;val vm=SalesImportDetailViewModel(SavedStateHandle(mapOf("exportId" to "export")),repository);runCurrent();assertThat(vm.state.value.detail).isEqualTo(detail);assertThat(vm.state.value.error).isFalse()}
 @Test fun `missing import becomes error instead of infinite loading`()=runTest {coEvery{repository.getImport("export") } returns null;val vm=SalesImportDetailViewModel(SavedStateHandle(mapOf("exportId" to "export")),repository);runCurrent();assertThat(vm.state.value.loading).isFalse();assertThat(vm.state.value.detail).isNull();assertThat(vm.state.value.error).isTrue()}
 @Test fun `import detail retries after failure`()=runTest {val detail=SalesImportDetail(anImport(),emptyList());coEvery{repository.getImport("export") } throws IllegalStateException() andThen detail;val vm=SalesImportDetailViewModel(SavedStateHandle(mapOf("exportId" to "export")),repository);runCurrent();assertThat(vm.state.value.error).isTrue();vm.retry();runCurrent();assertThat(vm.state.value.detail).isEqualTo(detail)}
 @Test fun `opening import detail only inspects and reports attention`()=runTest {
  val consumption=mockk<SalesConsumptionPostingCoordinator>();val detail=SalesImportDetail(anImport(),listOf(transaction()))
  coEvery{repository.getImport("export") } returns detail
  coEvery{consumption.inspectImport("export") } returns SalesConsumptionImportAlignment(mapOf(("terminal" to "transaction") to SalesConsumptionAlignment.NEEDS_RECONCILIATION))
  val vm=SalesImportDetailViewModel(SavedStateHandle(mapOf("exportId" to "export")),repository,consumption);runCurrent()
  assertThat(vm.state.value.inventoryImpact).isEqualTo(InventoryImpactState.NEEDS_ATTENTION)
  coVerify(exactly=1){consumption.inspectImport("export")};coVerify(exactly=0){consumption.reconcileImport(any())}
 }
 @Test fun `inventory retry reconciles then derives final state from inspection`()=runTest {
  val consumption=mockk<SalesConsumptionPostingCoordinator>();val detail=SalesImportDetail(anImport(),listOf(transaction()))
  coEvery{repository.getImport("export") } returns detail
  coEvery{consumption.inspectImport("export") } returnsMany listOf(
   SalesConsumptionImportAlignment(mapOf(("terminal" to "transaction") to SalesConsumptionAlignment.NEEDS_RECONCILIATION)),
   SalesConsumptionImportAlignment(mapOf(("terminal" to "transaction") to SalesConsumptionAlignment.ALIGNED)))
  coEvery{consumption.reconcileImport("export") } returns SalesConsumptionImportResult(mapOf(("terminal" to "transaction") to SalesConsumptionTransactionResult.Applied))
  val vm=SalesImportDetailViewModel(SavedStateHandle(mapOf("exportId" to "export")),repository,consumption);runCurrent();vm.retryInventory();runCurrent()
  assertThat(vm.state.value.inventoryImpact).isEqualTo(InventoryImpactState.UPDATED)
  coVerify(exactly=1){consumption.reconcileImport("export")};coVerify(exactly=2){consumption.inspectImport("export")}
 }
 @Test fun `missing transaction becomes error instead of infinite loading`()=runTest {coEvery{repository.getTransaction("terminal","transaction") } returns null;val vm=transactionVm();runCurrent();assertThat(vm.state.value.loading).isFalse();assertThat(vm.state.value.error).isTrue()}
 @Test fun `transaction repository failure becomes error`()=runTest {coEvery{repository.getTransaction("terminal","transaction") } throws IllegalStateException();val vm=transactionVm();runCurrent();assertThat(vm.state.value.error).isTrue()}
 @Test fun `transaction detail retries and succeeds`()=runTest {val detail=transaction();coEvery{repository.getTransaction("terminal","transaction") } returns null andThen detail;val vm=transactionVm();runCurrent();vm.retry();runCurrent();assertThat(vm.state.value.detail).isEqualTo(detail);assertThat(vm.state.value.error).isFalse()}

 private fun transactionVm()=SalesTransactionDetailViewModel(SavedStateHandle(mapOf("terminalId" to "terminal","transactionId" to "transaction")),repository)
 private fun anImport(currency:String="USD")=SalesImport("export","hash","restaurant","terminal",Instant.EPOCH,LocalDate.of(2026,8,16),"package","menu",3,currency,Instant.EPOCH)
 private fun transaction(id:String="transaction",status:ImportedSaleStatus=ImportedSaleStatus.COMPLETED,gross:String="10",discount:String="1",net:String="9"):ImportedSaleTransactionDetail {val tx=ImportedSaleTransaction("terminal",id,"restaurant","package","menu",3,LocalDate.of(2026,8,16),"USD",Instant.EPOCH,Instant.EPOCH,status,"export",Instant.EPOCH,"export",Instant.EPOCH);val line=ImportedSaleLine("terminal","line-$id",id,"item","Burger",BigDecimal.ONE,BigDecimal(gross),BigDecimal(gross),BigDecimal(discount),BigDecimal(net),1,1);return ImportedSaleTransactionDetail(tx,listOf(line))}
}
