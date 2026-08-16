package com.miara.cuentame.feature.sales
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.core.model.salesimport.*
import java.time.ZoneId
import java.time.format.*
import java.util.Locale

private val dateFmt get()=DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dateTimeFmt get()=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT).withZone(ZoneId.systemDefault())
private val timeFmt get()=DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class) @Composable fun SalesRoute(onBack:()->Unit,onImport:(String)->Unit,vm:SalesImportListViewModel=hiltViewModel()){
 val s by vm.state.collectAsStateWithLifecycle();val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::select)};val pick={picker.launch(arrayOf("application/json","text/json","text/plain"))}
 Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.sales_title))},navigationIcon={TextButton(onClick=onBack){Text(stringResource(R.string.action_back))}},actions={TextButton(onClick=pick,enabled=!s.busy){Text(stringResource(R.string.sales_import))}})}) { p ->
  Box(Modifier.fillMaxSize().padding(p)) {
   when {
    s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
    s.noRestaurant -> Text(stringResource(R.string.sales_load_error),Modifier.align(Alignment.Center))
    s.imports.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Text(stringResource(R.string.sales_empty),style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.sales_empty_body));Button(onClick=pick){Text(stringResource(R.string.sales_import))}}
    else -> LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text(stringResource(R.string.sales_history),style=MaterialTheme.typography.titleLarge)};items(s.imports,key={it.exportId}){x->Card(Modifier.fillMaxWidth().clickable{onImport(x.exportId)}){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(dateFmt.format(x.businessDate),style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.sales_terminal,x.terminalId));Text(stringResource(R.string.sales_revision,x.publicationRevision));Text(stringResource(R.string.sales_imported,dateTimeFmt.format(x.importedAt)))}}}}
   }
   if(s.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
  }
 }
 s.prepared?.let{x->AlertDialog(onDismissRequest=vm::dismiss,title={Text(stringResource(R.string.sales_preview_title))},text={Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Label(R.string.sales_business_date,dateFmt.format(x.businessDate));Label(R.string.sales_terminal_label,x.terminalId);Label(R.string.sales_generated,dateTimeFmt.format(x.generatedAt));Label(R.string.sales_revision_label,x.publicationRevision.toString());Label(R.string.sales_currency,x.currency);HorizontalDivider();Text(stringResource(R.string.sales_transactions_summary,x.completedCount,x.voidedCount));Text(stringResource(R.string.sales_line_count,x.lineCount));Money(R.string.sales_gross,x.completedGross,x.currency);Money(R.string.sales_discounts,x.completedDiscount,x.currency);Money(R.string.sales_net,x.completedNet,x.currency)}},confirmButton={Button(onClick=vm::confirm,enabled=!s.busy){Text(stringResource(R.string.sales_import_action))}},dismissButton={TextButton(onClick=vm::dismiss,enabled=!s.busy){Text(stringResource(R.string.action_cancel))}})}
 s.duplicate?.let{x->AlertDialog(onDismissRequest=vm::dismiss,title={Text(stringResource(R.string.sales_duplicate_title))},text={Text(stringResource(R.string.sales_duplicate_body,dateFmt.format(x.businessDate),dateTimeFmt.format(x.importedAt)))},confirmButton={Button(onClick={vm.dismiss();onImport(x.exportId)}){Text(stringResource(R.string.sales_view_import))}},dismissButton={TextButton(onClick=vm::dismiss){Text(stringResource(R.string.action_cancel))}})}
 s.error?.let{AlertDialog(onDismissRequest=vm::dismiss,confirmButton={TextButton(onClick=vm::dismiss){Text(stringResource(android.R.string.ok))}},text={Text(stringResource(it.message()))})}
 if(s.success){LaunchedEffect(Unit){vm.consumeSuccess()};Snackbar{Text(stringResource(R.string.sales_import_success))}}
}
@Composable private fun Label(label:Int,value:String){Text("${stringResource(label)}: $value")}
@Composable private fun Money(label:Int,value:java.math.BigDecimal,currency:String){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(stringResource(label));Text(Formatters.formatCurrency(value,currency))}}

@OptIn(ExperimentalMaterial3Api::class) @Composable fun SalesImportDetailRoute(onBack:()->Unit,onTransaction:(String,String)->Unit,vm:SalesImportDetailViewModel=hiltViewModel()){val s by vm.state.collectAsStateWithLifecycle();Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.sales_import_detail))},navigationIcon={TextButton(onClick=onBack){Text(stringResource(R.string.action_back))}})}){p->if(s.loading)Box(Modifier.fillMaxSize().padding(p),contentAlignment=Alignment.Center){CircularProgressIndicator()}else s.detail?.let{d->LazyColumn(Modifier.padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{ImportHeader(d.salesImport,s.summary!!)};item{Text(stringResource(R.string.sales_transactions),style=MaterialTheme.typography.titleLarge)};items(d.transactions){x->TransactionRow(x){onTransaction(x.transaction.terminalId,x.transaction.transactionId)}};item{Audit(d.salesImport)}}}?:Box(Modifier.fillMaxSize().padding(p),contentAlignment=Alignment.Center){Text(stringResource(R.string.sales_load_error))}}}
@Composable private fun ImportHeader(x:SalesImport,s:SalesSummary){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Label(R.string.sales_business_date,dateFmt.format(x.businessDate));Label(R.string.sales_terminal_label,x.terminalId);Label(R.string.sales_generated,dateTimeFmt.format(x.generatedAt));Label(R.string.sales_imported_label,dateTimeFmt.format(x.importedAt));Label(R.string.sales_currency,x.currency);Label(R.string.sales_revision_label,x.publicationRevision.toString());Text(stringResource(R.string.sales_counts,s.transactions,s.completed,s.voided));Money(R.string.sales_gross,s.gross,x.currency);Money(R.string.sales_discounts,s.discount,x.currency);Money(R.string.sales_net,s.net,x.currency)}}}
@Composable private fun TransactionRow(d:ImportedSaleTransactionDetail,onClick:()->Unit){val x=d.transaction;ListItem(headlineContent={Text(timeFmt.format(x.openedAt))},supportingContent={Text(stringResource(R.string.sales_transaction_row,status(x.status),d.lines.size))},trailingContent={Text(Formatters.formatCurrency(d.lines.fold(java.math.BigDecimal.ZERO){a,l->a+l.net},x.currency))},modifier=Modifier.clickable(onClick=onClick));HorizontalDivider()}
@Composable private fun Audit(x:SalesImport){var open by remember{mutableStateOf(false)};OutlinedButton(onClick={open=!open}){Text(stringResource(R.string.sales_audit))};if(open)Column{Text("Export ID: ${x.exportId}");Text("SHA-256: ${x.originalSha256}");Text("Menu ID: ${x.menuId}")}}
@OptIn(ExperimentalMaterial3Api::class) @Composable fun SalesTransactionDetailRoute(onBack:()->Unit,vm:SalesTransactionDetailViewModel=hiltViewModel()){val d by vm.state.collectAsStateWithLifecycle();Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.sales_transaction))},navigationIcon={TextButton(onClick=onBack){Text(stringResource(R.string.action_back))}})}){p->d?.let{detail->val x=detail.transaction;LazyColumn(Modifier.padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Label(R.string.sales_current_status,status(x.status));Label(R.string.sales_business_date,dateFmt.format(x.businessDate));Label(R.string.sales_opened,dateTimeFmt.format(x.openedAt));Label(R.string.sales_closed,dateTimeFmt.format(x.closedAt));Label(R.string.sales_terminal_label,x.terminalId);Label(R.string.sales_revision_label,x.publicationRevision.toString())};items(detail.lines){l->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(l.displayNameSnapshot,style=MaterialTheme.typography.titleMedium);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${Formatters.formatQuantity(l.quantity)} × ${Formatters.formatCurrency(l.unitPrice,x.currency)}");Text(Formatters.formatCurrency(l.net,x.currency))};Money(R.string.sales_gross,l.gross,x.currency);Money(R.string.sales_discounts,l.discount,x.currency)}}}}}?:Box(Modifier.fillMaxSize().padding(p),contentAlignment=Alignment.Center){CircularProgressIndicator()}}}
@Composable private fun status(s:ImportedSaleStatus)=stringResource(if(s==ImportedSaleStatus.COMPLETED)R.string.sales_completed else R.string.sales_voided)
private fun SalesUiError.message()=when(this){SalesUiError.FILE_TOO_LARGE->R.string.sales_error_too_large;SalesUiError.UNREADABLE->R.string.sales_error_unreadable;SalesUiError.PERMISSION_DENIED->R.string.sales_error_permission;SalesUiError.INVALID_FILE->R.string.sales_error_invalid;SalesUiError.WRONG_RESTAURANT->R.string.sales_error_restaurant;SalesUiError.UNKNOWN_MENU->R.string.sales_error_unknown_menu;SalesUiError.MENU_MISMATCH->R.string.sales_error_menu_mismatch;SalesUiError.ITEM_MISMATCH->R.string.sales_error_item_mismatch;SalesUiError.EXPORT_CONFLICT->R.string.sales_error_export_conflict;SalesUiError.HISTORY_CONFLICT->R.string.sales_error_history_conflict;SalesUiError.SAVE_FAILED->R.string.sales_error_save}
