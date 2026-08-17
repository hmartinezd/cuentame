package com.miara.cuentame.feature.sales

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.database.repository.SalesImportCoordinator
import com.miara.cuentame.core.database.repository.SalesConsumptionPostingCoordinator
import com.miara.cuentame.core.database.repository.SalesConsumptionTransactionResult
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.SalesImportRepository
import com.miara.cuentame.core.model.salesimport.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject

enum class SalesUiError { FILE_TOO_LARGE, UNREADABLE, PERMISSION_DENIED, INVALID_FILE, WRONG_RESTAURANT, UNKNOWN_MENU, MENU_MISMATCH, ITEM_MISMATCH, EXPORT_CONFLICT, HISTORY_CONFLICT, SAVE_FAILED }
data class SalesListState(val loading:Boolean=true,val imports:List<SalesImport> = emptyList(),val prepared:PreparedSalesImport?=null,val duplicate:SalesImport?=null,val busy:Boolean=false,val error:SalesUiError?=null,val success:Boolean=false,val inventoryNeedsAttention:Boolean=false,val noRestaurant:Boolean=false)

@HiltViewModel class SalesImportListViewModel @Inject constructor(private val restaurants:RestaurantRepository,private val repository:SalesImportRepository,private val coordinator:SalesImportCoordinator,private val consumption:SalesConsumptionPostingCoordinator,private val reader:SalesDocumentReader):ViewModel(){
 private val transient=MutableStateFlow(SalesListState())
 val state:StateFlow<SalesListState> = restaurants.observeRestaurant().flatMapLatest { r -> if(r==null) flowOf(transient.value.copy(loading=false,noRestaurant=true)) else repository.observeImports(r.id).map { transient.value.copy(loading=false,imports=it,noRestaurant=false) }.onStart { emit(transient.value.copy(loading=true)) } }.combine(transient){history,t->t.copy(loading=history.loading,imports=history.imports,noRestaurant=history.noRestaurant)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),SalesListState())
 fun select(uri:Uri){if(transient.value.busy)return;viewModelScope.launch{transient.value=transient.value.copy(busy=true,error=null,success=false,prepared=null,duplicate=null);val read=withContext(Dispatchers.IO){reader.read(uri)};when(read){is SalesDocumentReadResult.Success->{val restaurant=restaurants.getRestaurant();if(restaurant==null){transient.value=transient.value.copy(busy=false,noRestaurant=true);return@launch};when(val result=coordinator.prepare(restaurant.id,read.bytes)){is SalesImportPreparationResult.Ready->transient.value=transient.value.copy(busy=false,prepared=result.prepared);is SalesImportPreparationResult.Duplicate->transient.value=transient.value.copy(busy=false,duplicate=result.existing);is SalesImportPreparationResult.Failure->transient.value=transient.value.copy(busy=false,error=result.failure.code.ui())}};SalesDocumentReadResult.FileTooLarge->fail(SalesUiError.FILE_TOO_LARGE);SalesDocumentReadResult.PermissionDenied->fail(SalesUiError.PERMISSION_DENIED);SalesDocumentReadResult.Unreadable->fail(SalesUiError.UNREADABLE)}}}
 fun confirm(){val prepared=transient.value.prepared?:return;if(transient.value.busy)return;transient.value=transient.value.copy(busy=true,error=null);viewModelScope.launch{try{when(val result=coordinator.commit(prepared)){is SalesImportCommitResult.Imported->{val inventory=consumption.reconcileImport(result.detail.salesImport.exportId);transient.value=transient.value.copy(busy=false,prepared=null,success=true,inventoryNeedsAttention=inventory.results.values.any{it is SalesConsumptionTransactionResult.Failed})};is SalesImportCommitResult.Duplicate->transient.value=transient.value.copy(busy=false,prepared=null,duplicate=result.existing);is SalesImportCommitResult.Failure->transient.value=transient.value.copy(busy=false,error=result.failure.code.ui())}}catch(e:CancellationException){throw e}}}
 fun dismiss(){transient.value=transient.value.copy(prepared=null,duplicate=null,error=null)};fun consumeSuccess(){transient.value=transient.value.copy(success=false)};private fun fail(e:SalesUiError){transient.value=transient.value.copy(busy=false,error=e)}
}
private fun SalesImportFailureCode.ui()=when(this){SalesImportFailureCode.FILE_TOO_LARGE->SalesUiError.FILE_TOO_LARGE;SalesImportFailureCode.INVALID_UTF8,SalesImportFailureCode.INVALID_JSON,SalesImportFailureCode.INVALID_SALES_EXPORT->SalesUiError.INVALID_FILE;SalesImportFailureCode.WRONG_RESTAURANT->SalesUiError.WRONG_RESTAURANT;SalesImportFailureCode.UNKNOWN_MENU_PACKAGE->SalesUiError.UNKNOWN_MENU;SalesImportFailureCode.PUBLICATION_RESTAURANT_MISMATCH,SalesImportFailureCode.MENU_MISMATCH,SalesImportFailureCode.PUBLICATION_REVISION_MISMATCH,SalesImportFailureCode.CURRENCY_MISMATCH->SalesUiError.MENU_MISMATCH;SalesImportFailureCode.UNKNOWN_SELLABLE_ITEM,SalesImportFailureCode.COMMERCIAL_REVISION_MISMATCH,SalesImportFailureCode.CONSUMPTION_REVISION_MISMATCH,SalesImportFailureCode.ITEM_NAME_MISMATCH,SalesImportFailureCode.ITEM_PRICE_MISMATCH->SalesUiError.ITEM_MISMATCH;SalesImportFailureCode.EXPORT_ID_CONFLICT->SalesUiError.EXPORT_CONFLICT;SalesImportFailureCode.TRANSACTION_CONFLICT,SalesImportFailureCode.LINE_CONFLICT,SalesImportFailureCode.STALE_TRANSACTION_STATE->SalesUiError.HISTORY_CONFLICT;SalesImportFailureCode.PERSISTENCE_FAILURE->SalesUiError.SAVE_FAILED}

enum class InventoryImpactState { UPDATING, UPDATED, NO_EFFECT, NEEDS_ATTENTION }
data class SalesImportDetailState(val loading:Boolean=true,val detail:SalesImportDetail?=null,val summary:SalesSummary?=null,val inventoryImpact:InventoryImpactState=InventoryImpactState.UPDATING,val error:Boolean=false)
data class SalesSummary(val transactions:Int,val completed:Int,val voided:Int,val gross:BigDecimal,val discount:BigDecimal,val net:BigDecimal)
fun SalesImportDetail.summary():SalesSummary{val completed=transactions.filter{it.transaction.status==ImportedSaleStatus.COMPLETED};return SalesSummary(transactions.size,completed.size,transactions.count{it.transaction.status==ImportedSaleStatus.VOIDED},completed.flatMap{it.lines}.fold(BigDecimal.ZERO){a,x->a+x.gross},completed.flatMap{it.lines}.fold(BigDecimal.ZERO){a,x->a+x.discount},completed.flatMap{it.lines}.fold(BigDecimal.ZERO){a,x->a+x.net})}
@HiltViewModel class SalesImportDetailViewModel @Inject constructor(saved:SavedStateHandle,private val repository:SalesImportRepository,private val consumption:SalesConsumptionPostingCoordinator?):ViewModel(){
 constructor(saved:SavedStateHandle,repository:SalesImportRepository):this(saved,repository,null)
 private val id=requireNotNull(saved.get<String>("exportId"));private val _state=MutableStateFlow(SalesImportDetailState());val state:StateFlow<SalesImportDetailState> = _state.asStateFlow()
 init{load()};fun retry()=load();fun retryInventory(){viewModelScope.launch{_state.value=_state.value.copy(inventoryImpact=InventoryImpactState.UPDATING);_state.value=_state.value.copy(inventoryImpact=reconcileImpact())}};private suspend fun reconcileImpact():InventoryImpactState{val coordinator=consumption?:return InventoryImpactState.UPDATED;val r=coordinator.reconcileImport(id);return when{r.results.values.any{it is SalesConsumptionTransactionResult.Failed}->InventoryImpactState.NEEDS_ATTENTION;r.results.values.all{it is SalesConsumptionTransactionResult.NoEffect}->InventoryImpactState.NO_EFFECT;else->InventoryImpactState.UPDATED}};private fun load(){viewModelScope.launch{_state.value=SalesImportDetailState();_state.value=try{val d=repository.getImport(id);if(d==null)SalesImportDetailState(false,error=true) else SalesImportDetailState(false,d,d.summary(),reconcileImpact())}catch(e:CancellationException){throw e}catch(_:Exception){SalesImportDetailState(false,error=true)}}}
}
data class SalesTransactionDetailState(val loading:Boolean=true,val detail:ImportedSaleTransactionDetail?=null,val error:Boolean=false)
@HiltViewModel class SalesTransactionDetailViewModel @Inject constructor(saved:SavedStateHandle,private val repository:SalesImportRepository):ViewModel(){
 private val terminal=requireNotNull(saved.get<String>("terminalId"));private val id=requireNotNull(saved.get<String>("transactionId"));private val _state=MutableStateFlow(SalesTransactionDetailState());val state:StateFlow<SalesTransactionDetailState> = _state.asStateFlow()
 init{load()};fun retry()=load();private fun load(){viewModelScope.launch{_state.value=SalesTransactionDetailState();_state.value=try{val d=repository.getTransaction(terminal,id);if(d==null)SalesTransactionDetailState(false,error=true) else SalesTransactionDetailState(false,d)}catch(e:CancellationException){throw e}catch(_:Exception){SalesTransactionDetailState(false,error=true)}}}
}
