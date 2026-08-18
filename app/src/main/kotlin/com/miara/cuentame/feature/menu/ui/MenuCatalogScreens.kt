package com.miara.cuentame.feature.menu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.MenuRecipeId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.app.ui.theme.AppSpacing
import com.miara.cuentame.core.designsystem.component.*
import com.miara.cuentame.core.model.menu.*
import com.miara.cuentame.feature.menu.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MenuCatalogListRoute(onBack:()->Unit,onOpen:(com.miara.cuentame.core.common.ids.MenuId)->Unit,vm:MenuCatalogListViewModel=hiltViewModel()){
 val s by vm.state.collectAsStateWithLifecycle();var create by remember{mutableStateOf(false)}
 LaunchedEffect(s.createdId){s.createdId?.let{create=false;vm.consumeCreated();onOpen(it)}}
 Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.catalog_menus))},navigationIcon={TextButton(onClick=onBack){Text(stringResource(R.string.action_back))}},actions={TextButton(onClick=vm::toggleArchived){Text(stringResource(if(s.includeArchived)R.string.catalog_hide_archived else R.string.menu_show_archived))}})},floatingActionButton={ExtendedFloatingActionButton(onClick={create=true},modifier=Modifier.testTag("catalog_create"),icon={Icon(Icons.Default.Add,null)},text={Text(stringResource(R.string.catalog_new_menu))})}){p->
  when{ s.loading->Centered(p){CircularProgressIndicator()};s.loadError->Centered(p){Button(onClick=vm::retry){Text(stringResource(R.string.action_retry_desc))}};s.rows.isEmpty()->Centered(p){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(AppSpacing.md)){Text(stringResource(R.string.catalog_empty_title),style=MaterialTheme.typography.headlineSmall);Text(stringResource(R.string.catalog_empty_body),color=MaterialTheme.colorScheme.onSurfaceVariant);AppPrimaryButton(onClick={create=true}){Icon(Icons.Default.Add,null);Spacer(Modifier.width(AppSpacing.sm));Text(stringResource(R.string.catalog_new_menu))}}};else->LazyColumn(Modifier.padding(p).adaptiveContentWidth(960.dp).testTag("catalog_list"),contentPadding=PaddingValues(AppSpacing.md),verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){items(s.rows,key={it.menu.id.value}){row->AppCard(Modifier.fillMaxWidth().clickable{onOpen(row.menu.id)}){Column(Modifier.padding(AppSpacing.md),verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(row.menu.name,style=MaterialTheme.typography.titleMedium);if(row.menu.archivedAt!=null)AppStatusChip(stringResource(R.string.catalog_archived),StatusTone.NEUTRAL)};Text(stringResource(R.string.catalog_counts,row.categoryCount,row.itemCount),color=MaterialTheme.colorScheme.onSurfaceVariant);Text(stringResource(R.string.catalog_default_discount,row.menu.defaultCashDiscountPercent.stripTrailingZeros().toPlainString()));AppStatusChip(if(row.menu.publicationRevision==0L)stringResource(R.string.catalog_not_published)else stringResource(R.string.catalog_published_revision,row.menu.publicationRevision),if(row.menu.publicationRevision==0L)StatusTone.NEUTRAL else StatusTone.SUCCESS)}}}}}
 }
 if(create)MenuCatalogDialog(null,s.busy,s.error,{create=false;vm.clearError()}){n,d,p->vm.create(n,d,p)}
}

@Composable private fun Centered(p:PaddingValues,content:@Composable () -> Unit)=Box(Modifier.fillMaxSize().padding(p).padding(24.dp),contentAlignment=Alignment.Center){content()}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MenuCatalogDetailRoute(onBack:()->Unit,onOpenMenuItem:(MenuRecipeId)->Unit,vm:MenuCatalogDetailViewModel=hiltViewModel()){
 val s by vm.state.collectAsStateWithLifecycle();var editMenu by remember{mutableStateOf(false)};var categoryEdit by remember{mutableStateOf<MenuCategory?>(null)};var addCategory by remember{mutableStateOf(false)};var picker by remember{mutableStateOf<MenuCategory?>(null)};var createItemFor by remember{mutableStateOf<MenuCategory?>(null)};var deleteCategory by remember{mutableStateOf<MenuCategoryUiModel?>(null)};var removeItem by remember{mutableStateOf<MenuPlacementUiModel?>(null)};var moveItem by remember{mutableStateOf<MenuPlacementUiModel?>(null)};var confirmPublish by remember{mutableStateOf(false)}
 val context=LocalContext.current;var pendingExport by remember{mutableStateOf<com.miara.cuentame.core.domain.service.MenuPackageExport?>(null)}
 val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->val export=pendingExport;pendingExport=null;if(uri==null){vm.exportPickerCancelled()}else{val success=runCatching{context.contentResolver.openOutputStream(uri)?.use{it.write(requireNotNull(export).bytes)}?:error("Unable to open document")}.isSuccess;vm.exportWriteFinished(success)}}
 LaunchedEffect(Unit){vm.exportEvents.collect{event->when(event){is MenuExportEvent.CreateDocument->{pendingExport=event.export;exportLauncher.launch(event.export.suggestedFileName)}}}}
 LaunchedEffect(s.createdMenuItemId){s.createdMenuItemId?.let{createItemFor=null;picker=null;vm.consumeCreatedMenuItem()}}
 LaunchedEffect(s.busy,s.error){if(!s.busy&&s.error==null){if(editMenu)editMenu=false;if(addCategory||categoryEdit!=null){addCategory=false;categoryEdit=null}}}
 val beginAddItem:(MenuCategory)->Unit={category->if(s.availableItems.isEmpty())createItemFor=category else picker=category}
 Scaffold(topBar={TopAppBar(title={Text(s.menu?.name?:stringResource(R.string.catalog_menu))},navigationIcon={TextButton(onClick=onBack){Text(stringResource(R.string.action_back))}},actions={if(s.menu!=null)TextButton(onClick={editMenu=true}){Text(stringResource(R.string.action_edit))}})}){p->when{ s.loading->Centered(p){CircularProgressIndicator()};s.loadError||s.menu==null->Centered(p){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(AppSpacing.md)){Text(stringResource(R.string.catalog_load_failed));Button(onClick=vm::retry){Text(stringResource(R.string.action_retry_desc))}}};else->BoxWithConstraints(Modifier.fillMaxSize().padding(p)){val wide=maxWidth>=720.dp;if(wide)Row(Modifier.fillMaxSize().adaptiveContentWidth(1200.dp).padding(AppSpacing.md),horizontalArrangement=Arrangement.spacedBy(AppSpacing.md)){Column(Modifier.width(320.dp),verticalArrangement=Arrangement.spacedBy(AppSpacing.md)){MenuSummary(s,{editMenu=true},vm::toggleArchived,{confirmPublish=true},Modifier.fillMaxWidth());PublicationHistory(s,vm::export,Modifier.fillMaxWidth())};CategoryList(s,{addCategory=true},{categoryEdit=it},{deleteCategory=it},beginAddItem,{item->onOpenMenuItem(item.recipe.id)},{removeItem=it},{moveItem=it},vm::moveCategory,vm::moveItem,Modifier.weight(1f))}else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(AppSpacing.md),verticalArrangement=Arrangement.spacedBy(AppSpacing.md)){item{MenuSummary(s,{editMenu=true},vm::toggleArchived,{confirmPublish=true},Modifier.fillMaxWidth())};item{CategoryList(s,{addCategory=true},{categoryEdit=it},{deleteCategory=it},beginAddItem,{item->onOpenMenuItem(item.recipe.id)},{removeItem=it},{moveItem=it},vm::moveCategory,vm::moveItem,Modifier.fillMaxWidth())};if(s.publications.isNotEmpty())item{PublicationHistory(s,vm::export,Modifier.fillMaxWidth())}}}}
 }
 if(editMenu)s.menu?.let{MenuCatalogDialog(it,s.busy,s.error,{editMenu=false;vm.clearError()}){n,d,p->vm.updateMenu(n,d,p)}}
 if(addCategory||categoryEdit!=null)NameDialog(categoryEdit?.name.orEmpty(),s.busy,s.error,{addCategory=false;categoryEdit=null;vm.clearError()}){vm.saveCategory(categoryEdit,it)}
 picker?.let{category->ItemPicker(category,s.availableItems,s.currencyCode,s.busy,{picker=null},{picker=null;createItemFor=category}){recipe->picker=null;vm.addItem(category,recipe)}}
 createItemFor?.let{category->CreateMenuItemDialog(category,s.busy,s.error,{createItemFor=null;vm.clearError()}){name,price->vm.createAndAddItem(category,name,price)}}
 deleteCategory?.let{model->Confirm(stringResource(R.string.catalog_delete_category_title,model.category.name),stringResource(R.string.catalog_delete_category_body,model.items.size),s.busy,{deleteCategory=null}){vm.removeCategory(model.category);deleteCategory=null}}
 removeItem?.let{item->Confirm(stringResource(R.string.catalog_remove_item_title,item.recipe.name,s.menu?.name.orEmpty()),stringResource(R.string.catalog_remove_item_body),s.busy,{removeItem=null}){vm.removeItem(item);removeItem=null}}
 moveItem?.let{item->MoveDialog(item,s.categories.map{it.category},s.busy,{moveItem=null}){vm.moveItem(item,it);moveItem=null}}
 if(confirmPublish)s.menu?.let{menu->AlertDialog(onDismissRequest={if(!s.busy)confirmPublish=false},title={Text(stringResource(R.string.catalog_publish_title,menu.name))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(stringResource(R.string.catalog_publish_body));Text(stringResource(R.string.catalog_publish_revision,menu.publicationRevision,menu.publicationRevision+1))}},confirmButton={Button(onClick={confirmPublish=false;vm.publish()},enabled=!s.busy){Text(stringResource(R.string.catalog_publish))}},dismissButton={TextButton(onClick={confirmPublish=false},enabled=!s.busy){Text(stringResource(R.string.action_cancel))}})}
}

@Composable private fun MenuSummary(s:MenuCatalogDetailState,onEdit:()->Unit,onArchive:()->Unit,onPublish:()->Unit,modifier:Modifier){val menu=s.menu?:return;Column(modifier,verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){AppSectionHeader(stringResource(R.string.catalog_overview));AppCard(Modifier.fillMaxWidth()){Column(Modifier.padding(AppSpacing.md),verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(menu.name,style=MaterialTheme.typography.headlineSmall);if(menu.archivedAt!=null)AppStatusChip(stringResource(R.string.catalog_archived),StatusTone.NEUTRAL)};menu.description?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text(stringResource(R.string.catalog_default_discount,menu.defaultCashDiscountPercent.stripTrailingZeros().toPlainString()));AppStatusChip(if(menu.publicationRevision==0L)stringResource(R.string.catalog_not_published)else stringResource(R.string.catalog_published_revision,menu.publicationRevision),if(menu.publicationRevision==0L)StatusTone.NEUTRAL else StatusTone.SUCCESS);s.publishedRevision?.let{AppStatusChip(stringResource(R.string.catalog_publish_success,it),StatusTone.SUCCESS)};s.publicationError?.let{AppStatusChip(publicationError(it),StatusTone.ERROR)};AppPrimaryButton(onClick=onPublish,modifier=Modifier.fillMaxWidth(),enabled=!s.busy&&menu.archivedAt==null){Text(stringResource(R.string.catalog_publish))};OutlinedButton(onClick=onEdit,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.action_edit))};TextButton(onClick=onArchive,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text(stringResource(if(menu.archivedAt==null)R.string.catalog_archive else R.string.catalog_restore))}}}}
}
@Composable private fun PublicationHistory(s:MenuCatalogDetailState,onExport:(com.miara.cuentame.core.common.ids.MenuPublicationId)->Unit,modifier:Modifier){if(s.publications.isEmpty())return;Column(modifier,verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){AppSectionHeader(stringResource(R.string.catalog_publication_history));AppCard(Modifier.fillMaxWidth()){Column(Modifier.padding(AppSpacing.md)){s.exportError?.let{Text(exportError(it),color=MaterialTheme.colorScheme.error)};if(s.exportSucceeded)Text(stringResource(R.string.catalog_export_success),color=MaterialTheme.colorScheme.primary);s.publications.forEach{p->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.catalog_history_row,p.publicationRevision,java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withZone(java.time.ZoneId.systemDefault()).format(p.publishedAt)),Modifier.weight(1f));TextButton(onClick={onExport(p.id)},enabled=!s.busy){Text(stringResource(R.string.catalog_export))}}}}}}
}
@Composable private fun CategoryList(s:MenuCatalogDetailState,onAdd:()->Unit,onEdit:(MenuCategory)->Unit,onDelete:(MenuCategoryUiModel)->Unit,onAddItem:(MenuCategory)->Unit,onOpen:(MenuPlacementUiModel)->Unit,onRemove:(MenuPlacementUiModel)->Unit,onMove:(MenuPlacementUiModel)->Unit,onMoveCategory:(Int,Int)->Unit,onMoveItem:(MenuCategoryUiModel,Int,Int)->Unit,modifier:Modifier){
 Column(modifier,verticalArrangement=Arrangement.spacedBy(AppSpacing.md)){
  AppSectionHeader(stringResource(R.string.catalog_categories),trailing={AppPrimaryButton(onClick=onAdd,enabled=!s.busy){Icon(Icons.Default.Add,null);Spacer(Modifier.width(AppSpacing.xs));Text(stringResource(R.string.catalog_add_category))}})
  s.error?.let{Text(catalogError(it),color=MaterialTheme.colorScheme.error)}
  if(s.categories.isEmpty())Text(stringResource(R.string.catalog_no_categories),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
  s.categories.forEachIndexed{ci,model->CategoryCard(model,ci,s.categories.lastIndex,s.currencyCode,s.busy,onEdit,onDelete,onAddItem,onOpen,onRemove,onMove,onMoveCategory,onMoveItem)}
 }
}

@Composable private fun CategoryCard(model:MenuCategoryUiModel,categoryIndex:Int,lastCategoryIndex:Int,currencyCode:String,busy:Boolean,onEdit:(MenuCategory)->Unit,onDelete:(MenuCategoryUiModel)->Unit,onAddItem:(MenuCategory)->Unit,onOpen:(MenuPlacementUiModel)->Unit,onRemove:(MenuPlacementUiModel)->Unit,onMove:(MenuPlacementUiModel)->Unit,onMoveCategory:(Int,Int)->Unit,onMoveItem:(MenuCategoryUiModel,Int,Int)->Unit){
 var actionsExpanded by remember{mutableStateOf(false)}
 AppCard(Modifier.fillMaxWidth()){Column(Modifier.padding(AppSpacing.md),verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Text(model.category.name,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
   IconButton(onClick={onMoveCategory(categoryIndex,-1)},enabled=!busy&&categoryIndex>0){Icon(Icons.Default.ArrowUpward,stringResource(R.string.catalog_move_up,model.category.name))}
   IconButton(onClick={onMoveCategory(categoryIndex,1)},enabled=!busy&&categoryIndex<lastCategoryIndex){Icon(Icons.Default.ArrowDownward,stringResource(R.string.catalog_move_down,model.category.name))}
   Box{IconButton(onClick={actionsExpanded=true},enabled=!busy){Icon(Icons.Default.MoreVert,stringResource(R.string.catalog_more_actions,model.category.name))};DropdownMenu(actionsExpanded,{actionsExpanded=false}){DropdownMenuItem(text={Text(stringResource(R.string.action_edit))},onClick={actionsExpanded=false;onEdit(model.category)});DropdownMenuItem(text={Text(stringResource(R.string.action_delete),color=MaterialTheme.colorScheme.error)},onClick={actionsExpanded=false;onDelete(model)})}}
  }
  model.items.forEachIndexed{ii,item->MenuPlacementRow(item,model,ii,currencyCode,busy,onOpen,onRemove,onMove,onMoveItem)}
  TextButton(onClick={onAddItem(model.category)},enabled=!busy){Icon(Icons.Default.Add,null);Spacer(Modifier.width(AppSpacing.xs));Text(stringResource(R.string.catalog_add_item))}
 }}
}

@Composable private fun MenuPlacementRow(item:MenuPlacementUiModel,category:MenuCategoryUiModel,index:Int,currencyCode:String,busy:Boolean,onOpen:(MenuPlacementUiModel)->Unit,onRemove:(MenuPlacementUiModel)->Unit,onMove:(MenuPlacementUiModel)->Unit,onMoveItem:(MenuCategoryUiModel,Int,Int)->Unit){
 var actionsExpanded by remember{mutableStateOf(false)}
 Row(Modifier.fillMaxWidth().clickable{onOpen(item)}.padding(vertical=AppSpacing.xs),verticalAlignment=Alignment.CenterVertically){
  Column(Modifier.weight(1f)){Text(item.recipe.name,style=MaterialTheme.typography.bodyLarge);if(item.recipe.archivedAt!=null)Text(stringResource(R.string.catalog_item_archived),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  Text(item.recipe.sellingPrice?.let{Formatters.formatCurrency(it,currencyCode)}?:stringResource(R.string.menu_selling_not_set),style=MaterialTheme.typography.bodyMedium)
  IconButton(onClick={onMoveItem(category,index,-1)},enabled=!busy&&index>0){Icon(Icons.Default.ArrowUpward,stringResource(R.string.catalog_move_up,item.recipe.name))}
  IconButton(onClick={onMoveItem(category,index,1)},enabled=!busy&&index<category.items.lastIndex){Icon(Icons.Default.ArrowDownward,stringResource(R.string.catalog_move_down,item.recipe.name))}
  Box{IconButton(onClick={actionsExpanded=true},enabled=!busy){Icon(Icons.Default.MoreVert,stringResource(R.string.catalog_more_actions,item.recipe.name))};DropdownMenu(actionsExpanded,{actionsExpanded=false}){DropdownMenuItem(text={Text(stringResource(R.string.catalog_move))},onClick={actionsExpanded=false;onMove(item)});DropdownMenuItem(text={Text(stringResource(R.string.catalog_remove),color=MaterialTheme.colorScheme.error)},onClick={actionsExpanded=false;onRemove(item)})}}
 }
}

@Composable private fun MenuCatalogDialog(menu:Menu?,busy:Boolean,error:MenuCatalogUiError?,dismiss:()->Unit,save:(String,String,String)->Unit){var n by remember(menu){mutableStateOf(menu?.name.orEmpty())};var d by remember(menu){mutableStateOf(menu?.description.orEmpty())};var p by remember(menu){mutableStateOf(menu?.defaultCashDiscountPercent?.toPlainString()?:"0")};AlertDialog(onDismissRequest=dismiss,title={Text(stringResource(if(menu==null)R.string.catalog_create else R.string.catalog_edit))},text={Column(verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){OutlinedTextField(n,{n=it},label={Text(stringResource(R.string.catalog_menu_name))});OutlinedTextField(d,{d=it},label={Text(stringResource(R.string.catalog_description))});OutlinedTextField(p,{p=it},label={Text(stringResource(R.string.catalog_discount_percent))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));error?.let{Text(catalogError(it),color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(onClick={save(n,d,p)},enabled=!busy&&n.isNotBlank()){Text(stringResource(R.string.action_save))}},dismissButton={TextButton(onClick=dismiss,enabled=!busy){Text(stringResource(R.string.action_cancel))}})}
@Composable private fun NameDialog(initial:String,busy:Boolean,error:MenuCatalogUiError?,dismiss:()->Unit,save:(String)->Unit){var n by remember{mutableStateOf(initial)};AlertDialog(onDismissRequest=dismiss,title={Text(stringResource(R.string.catalog_category_name))},text={Column{OutlinedTextField(n,{n=it},label={Text(stringResource(R.string.category_name))});error?.let{Text(catalogError(it),color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(onClick={save(n)},enabled=!busy&&n.isNotBlank()){Text(stringResource(R.string.action_save))}},dismissButton={TextButton(onClick=dismiss){Text(stringResource(R.string.action_cancel))}})}
@Composable
private fun ItemPicker(category:MenuCategory,items:List<MenuRecipe>,currencyCode:String,busy:Boolean,dismiss:()->Unit,create:()->Unit,select:(MenuRecipe)->Unit) {
 var q by remember { mutableStateOf("") }
 val shown=items.filter { it.name.contains(q,true) }
 AlertDialog(
  onDismissRequest=dismiss,
  title={Text(stringResource(R.string.catalog_add_to,category.name))},
  text={
   Column(verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)) {
    Button(onClick=create,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("catalog_create_item")) {
     Icon(Icons.Default.Add,null);Spacer(Modifier.width(AppSpacing.xs));Text(stringResource(R.string.catalog_create_menu_item))
    }
    if(items.isEmpty()) {
     Text(stringResource(R.string.catalog_no_existing_items))
    } else {
     OutlinedTextField(q,{q=it},label={Text(stringResource(R.string.catalog_search_items))},modifier=Modifier.testTag("catalog_item_search"))
     LazyColumn(Modifier.heightIn(max=360.dp)) {
      items(shown,key={it.id.value}) { r->
       ListItem(headlineContent={Text(r.name)},trailingContent={Text(r.sellingPrice?.let{Formatters.formatCurrency(it,currencyCode)}.orEmpty())},modifier=Modifier.clickable(enabled=!busy){select(r)})
      }
     }
     if(shown.isEmpty()) Text(stringResource(R.string.catalog_no_available_items))
    }
   }
  },
  confirmButton={},
  dismissButton={TextButton(onClick=dismiss){Text(stringResource(R.string.action_cancel))}}
 )
}
@Composable private fun CreateMenuItemDialog(category:MenuCategory,busy:Boolean,error:MenuCatalogUiError?,dismiss:()->Unit,save:(String,String)->Unit){var name by remember{mutableStateOf("")};var price by remember{mutableStateOf("")};AlertDialog(onDismissRequest=dismiss,title={Text(stringResource(R.string.catalog_create_item_in,category.name))},text={Column(verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){OutlinedTextField(name,{name=it},label={Text(stringResource(R.string.menu_name))},modifier=Modifier.testTag("catalog_new_item_name"));OutlinedTextField(price,{price=it},label={Text(stringResource(R.string.menu_selling_price))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.testTag("catalog_new_item_price"));error?.let{Text(catalogError(it),color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(onClick={save(name,price)},enabled=!busy&&name.isNotBlank()){Text(stringResource(R.string.action_save))}},dismissButton={TextButton(onClick=dismiss,enabled=!busy){Text(stringResource(R.string.action_cancel))}})}
@Composable private fun Confirm(title:String,body:String,busy:Boolean,dismiss:()->Unit,confirm:()->Unit)=AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Text(body)},confirmButton={Button(onClick=confirm,enabled=!busy){Text(stringResource(R.string.action_delete))}},dismissButton={TextButton(onClick=dismiss){Text(stringResource(R.string.action_cancel))}})
@Composable private fun MoveDialog(item:MenuPlacementUiModel,categories:List<MenuCategory>,busy:Boolean,dismiss:()->Unit,move:(MenuCategory)->Unit)=AlertDialog(onDismissRequest=dismiss,title={Text(stringResource(R.string.catalog_move_item,item.recipe.name))},text={Column{categories.filter{it.id!=item.placement.categoryId}.forEach{c->TextButton(onClick={move(c)},enabled=!busy){Text(c.name)}}}},confirmButton={},dismissButton={TextButton(onClick=dismiss){Text(stringResource(R.string.action_cancel))}})
@Composable private fun catalogError(e:MenuCatalogUiError)=stringResource(when(e){MenuCatalogUiError.INVALID_VALUES->R.string.catalog_error_invalid;MenuCatalogUiError.DUPLICATE_MENU_NAME->R.string.catalog_error_duplicate_menu;MenuCatalogUiError.DUPLICATE_CATEGORY_NAME->R.string.catalog_error_duplicate_category;MenuCatalogUiError.ITEM_ALREADY_IN_MENU->R.string.catalog_error_duplicate_item;MenuCatalogUiError.OWNERSHIP_ERROR->R.string.catalog_error_ownership;MenuCatalogUiError.PLACEMENT_FAILED->R.string.catalog_error_placement_failed;MenuCatalogUiError.SAVE_FAILED->R.string.menu_error_save_failed})
@Composable private fun publicationError(e:MenuPublicationUiError)=when(e){MenuPublicationUiError.MenuArchived->stringResource(R.string.catalog_publish_error_archived);MenuPublicationUiError.MenuEmpty,MenuPublicationUiError.NoItems->stringResource(R.string.catalog_publish_error_empty);is MenuPublicationUiError.ItemArchived->stringResource(R.string.catalog_publish_error_item_archived,e.name);is MenuPublicationUiError.ItemPriceMissing->stringResource(R.string.catalog_publish_error_price,e.name);is MenuPublicationUiError.IngredientAreaMissing->stringResource(R.string.catalog_publish_error_ingredient_area,e.name);MenuPublicationUiError.CatalogBroken,MenuPublicationUiError.Ownership->stringResource(R.string.catalog_publish_error_catalog);MenuPublicationUiError.SaveFailed->stringResource(R.string.catalog_publish_error_save)}
@Composable private fun exportError(e:MenuExportUiError)=stringResource(when(e){MenuExportUiError.PUBLICATION_NOT_FOUND->R.string.catalog_export_error_not_found;MenuExportUiError.MALFORMED_PACKAGE->R.string.catalog_export_error_malformed;MenuExportUiError.WRITE_FAILED->R.string.catalog_export_error_write})
