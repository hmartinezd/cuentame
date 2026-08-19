package com.miara.cuentame.feature.menu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.MenuRecipeId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.designsystem.util.Formatters
import com.miara.cuentame.app.ui.theme.AppSpacing
import com.miara.cuentame.core.designsystem.component.*
import com.miara.cuentame.core.model.menu.*
import com.miara.cuentame.feature.menu.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class) @Composable fun MenuListRoute(onBack:()->Unit,onOpen:(MenuRecipeId)->Unit,vm:MenuListViewModel=hiltViewModel()){
 val s by vm.state.collectAsStateWithLifecycle(); var create by remember{mutableStateOf(false)}
 LaunchedEffect(s.createdId){s.createdId?.let{create=false;vm.consumeCreated();onOpen(it)}}
 Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.menu_items_title))},navigationIcon={TextButton(onClick=onBack){Text(stringResource(R.string.action_back))}},actions={TextButton(onClick=vm::toggleArchived){Text(stringResource(if(s.includeArchived)R.string.menu_hide_archived else R.string.menu_show_archived))}})},floatingActionButton={ExtendedFloatingActionButton(onClick={create=true},modifier=Modifier.testTag("menu_create"),icon={Icon(Icons.Default.Add,null)},text={Text(stringResource(R.string.menu_new_item))})}){p->
  when{ s.loading->Box(Modifier.fillMaxSize().padding(p),contentAlignment=Alignment.Center){CircularProgressIndicator()};s.error->Box(Modifier.fillMaxSize().padding(p),contentAlignment=Alignment.Center){Button(onClick=vm::retry){Text(stringResource(R.string.action_retry_desc))}};s.rows.isEmpty()->Box(Modifier.fillMaxSize().padding(p).padding(AppSpacing.lg),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(AppSpacing.md)){Text(stringResource(R.string.menu_empty),style=MaterialTheme.typography.headlineSmall);Text(stringResource(R.string.menu_empty_body),color=MaterialTheme.colorScheme.onSurfaceVariant);AppPrimaryButton(onClick={create=true}){Icon(Icons.Default.Add,null);Spacer(Modifier.width(AppSpacing.sm));Text(stringResource(R.string.menu_new_item))}}};else->LazyColumn(Modifier.padding(p).adaptiveContentWidth(960.dp).testTag("menu_list"),contentPadding=PaddingValues(AppSpacing.md),verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){items(s.rows,key={it.first.id.value}){(r,c)->MenuRecipeRow(r,c,onOpen)}}}
 }
 if(create) MenuEditDialog(stringResource(R.string.menu_create_title),"","",s.isSaving,s.operationError,{vm.clearOperationError();create=false}){n,p->vm.create(n,p)}
}

@Composable private fun MenuRecipeRow(r:MenuRecipe,c:MenuRecipeCost?,onOpen:(MenuRecipeId)->Unit){AppCard(Modifier.fillMaxWidth().clickable{onOpen(r.id)}){Column(Modifier.padding(AppSpacing.md),verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){Text(r.name,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));Text(r.sellingPrice?.let{Formatters.formatCurrency(it,c?.currencyCode?:"USD")}?:stringResource(R.string.menu_selling_not_set),style=MaterialTheme.typography.titleMedium)};Row(horizontalArrangement=Arrangement.spacedBy(AppSpacing.sm)){c?.let{AppStatusChip(stringResource(when(it.status){MenuRecipeCostStatus.FULLY_COSTED->R.string.menu_fully_costed;MenuRecipeCostStatus.PARTIALLY_COSTED->R.string.menu_partially_costed_status;MenuRecipeCostStatus.UNCOSTED->R.string.menu_uncosted_status}),when(it.status){MenuRecipeCostStatus.FULLY_COSTED->StatusTone.SUCCESS;MenuRecipeCostStatus.PARTIALLY_COSTED->StatusTone.WARNING;MenuRecipeCostStatus.UNCOSTED->StatusTone.NEUTRAL})};if(r.archivedAt!=null)AppStatusChip(stringResource(R.string.menu_archived_status),StatusTone.NEUTRAL)};CostSummary(c)}}}
@Composable private fun CostSummary(c:MenuRecipeCost?){if(c==null)return;Text(when(c.status){MenuRecipeCostStatus.FULLY_COSTED->stringResource(R.string.menu_plate_and_food_cost,Formatters.formatCurrency(c.currentPlateCost!!,c.currencyCode),c.sellingMetrics.foodCostPercent?.let(Formatters::formatPercent)?:stringResource(R.string.menu_unavailable));MenuRecipeCostStatus.PARTIALLY_COSTED->stringResource(R.string.menu_partially_costed,c.costedComponentCount,c.componentCount);MenuRecipeCostStatus.UNCOSTED->stringResource(R.string.menu_uncosted)},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuDetailRoute(onBack: () -> Unit, vm: MenuDetailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf(false) }
    val r = s.recipe
    val c = s.cost
    LaunchedEffect(s.infoSaveSucceeded) {
        if (s.infoSaveSucceeded) {
            edit = false
            vm.consumeInfoSaveSuccess()
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(r?.name ?: stringResource(R.string.menu_item_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            actions = { if (r != null) TextButton(onClick = { edit = true; vm.clearInfoError() }) { Text(stringResource(R.string.action_edit)) } }
        )
    }) { padding ->
        when {
            s.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            s.error || r == null || c == null -> Box(Modifier.fillMaxSize().padding(padding).padding(AppSpacing.lg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    Text(stringResource(R.string.menu_detail_load_failed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = vm::retry) { Text(stringResource(R.string.action_retry_desc)) }
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag("menu_detail_scroll_page"),
                contentPadding = PaddingValues(vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                item {
                    MenuDetailContent {
                        AppSectionHeader(stringResource(R.string.menu_item_overview))
                        AppCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(r.name, style = MaterialTheme.typography.titleLarge)
                                    if (r.archivedAt != null) AppStatusChip(stringResource(R.string.menu_archived_status), StatusTone.NEUTRAL)
                                }
                                Text(r.sellingPrice?.let { Formatters.formatCurrency(it, c.currencyCode) } ?: stringResource(R.string.menu_selling_not_set), style = MaterialTheme.typography.headlineSmall)
                                Text(stringResource(R.string.menu_cash_discount), style = MaterialTheme.typography.titleSmall)
                                CashChoice(r.cashDiscountBehavior == CashDiscountBehavior.APPLY_DEFAULT, stringResource(R.string.menu_cash_use_default), !s.isSavingInfo) { vm.setCashDiscountBehavior(CashDiscountBehavior.APPLY_DEFAULT) }
                                CashChoice(r.cashDiscountBehavior == CashDiscountBehavior.NONE, stringResource(R.string.menu_cash_none), !s.isSavingInfo) { vm.setCashDiscountBehavior(CashDiscountBehavior.NONE) }
                                TextButton(onClick = vm::archive, enabled = !s.isArchiving) { Text(if (r.archivedAt == null) stringResource(R.string.menu_archive) else stringResource(R.string.menu_restore)) }
                                s.infoError?.let { Text(operationErrorLabel(it), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                item {
                    MenuDetailContent {
                        AppSectionHeader(stringResource(R.string.menu_cost_profitability))
                        AppCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) { CostStatus(c); CostDetail(c) } }
                    }
                }
                item {
                    MenuDetailContent {
                        AppSectionHeader(stringResource(R.string.menu_recipe_components), trailing = {
                            AppPrimaryButton(onClick = { vm.openComponent(null) }, modifier = Modifier.testTag("menu_add_component")) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(AppSpacing.sm)); Text(stringResource(R.string.menu_add_component))
                            }
                        })
                    }
                }
                items(c.components) { component ->
                    MenuDetailContent {
                        AppCard(Modifier.fillMaxWidth().clickable { s.components.firstOrNull { it.id == component.componentId }?.let(vm::openComponent) }) {
                            Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Text(component.ingredientName, style = MaterialTheme.typography.titleMedium)
                                Text("${Formatters.formatQuantity(component.quantityEntered)} ${component.enteredUnitLabel.orEmpty()}")
                                when (component.source) {
                                    MenuCostSource.ACTIVE_PREPARATION_RECIPE -> Text(stringResource(R.string.cost_source_preparation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    MenuCostSource.INGREDIENT_AVERAGE_COST -> Text(stringResource(R.string.cost_source_average), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    null -> Unit
                                }
                                Text(component.currentCost?.let { Formatters.formatCurrency(it, c.currencyCode) } ?: missingLabel(component.missingReason))
                            }
                        }
                    }
                }
            }
        }
    }
    if (edit && r != null) MenuEditDialog(stringResource(R.string.action_edit), r.name, r.sellingPrice?.toPlainString().orEmpty(), s.isSavingInfo, s.infoError, { vm.clearInfoError(); edit = false }, vm::save)
    if (s.editor.isOpen) ComponentDialog(s.editor, s.ingredients, vm::dismissComponent, vm::selectIngredient, vm::selectUnit, vm::updateComponentQuantity, vm::saveComponent, if (s.editor.existing != null) vm::removeComponent else null, s.isRemovingComponent)
}

@Composable
private fun MenuDetailContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md).adaptiveContentWidth(960.dp).testTag("menu_detail_content"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        content = content
    )
}
@Composable internal fun CashChoice(selected:Boolean,label:String,enabled:Boolean,onClick:()->Unit)=Row(Modifier.fillMaxWidth().clickable(enabled=enabled,onClick=onClick),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=selected,onClick=onClick,enabled=enabled);Text(label)}
@Composable private fun CostStatus(c:MenuRecipeCost)=AppStatusChip(stringResource(when(c.status){MenuRecipeCostStatus.FULLY_COSTED->R.string.menu_fully_costed;MenuRecipeCostStatus.PARTIALLY_COSTED->R.string.menu_partially_costed_status;MenuRecipeCostStatus.UNCOSTED->R.string.menu_uncosted_status}),when(c.status){MenuRecipeCostStatus.FULLY_COSTED->StatusTone.SUCCESS;MenuRecipeCostStatus.PARTIALLY_COSTED->StatusTone.WARNING;MenuRecipeCostStatus.UNCOSTED->StatusTone.NEUTRAL})
@Composable private fun CostDetail(c:MenuRecipeCost){when(c.status){MenuRecipeCostStatus.FULLY_COSTED->{Text(stringResource(R.string.menu_plate_cost,Formatters.formatCurrency(c.currentPlateCost!!,c.currencyCode)),Modifier.testTag("plate_cost"));Text(c.sellingMetrics.foodCostPercent?.let{stringResource(R.string.menu_food_cost,Formatters.formatPercent(it))}?:stringResource(R.string.menu_food_cost_unavailable));Text(c.sellingMetrics.grossProfitBeforeLaborAndOverhead?.let{stringResource(R.string.menu_gross_profit,Formatters.formatCurrency(it,c.currencyCode))}?:stringResource(R.string.menu_gross_profit_unavailable))};MenuRecipeCostStatus.PARTIALLY_COSTED->{Text(stringResource(R.string.menu_partially_costed,c.costedComponentCount,c.componentCount));Text(stringResource(R.string.menu_known_subtotal,Formatters.formatCurrency(c.knownCostSubtotal,c.currencyCode)),Modifier.testTag("known_subtotal"));Text(stringResource(R.string.menu_food_cost_unavailable));Text(stringResource(R.string.menu_gross_profit_unavailable))};MenuRecipeCostStatus.UNCOSTED->Text(stringResource(R.string.menu_uncosted))};if(c.priceImpact.coveredLeafCount>0)Text(stringResource(R.string.menu_vendor_impact,Formatters.formatCurrency(c.priceImpact.knownSubtotal,c.currencyCode),c.priceImpact.coveredLeafCount,c.priceImpact.totalLeafCount))}
@Composable private fun missingLabel(r:MenuRecipeCostMissingReason?)=when(r){MenuRecipeCostMissingReason.INGREDIENT_COST_MISSING->stringResource(R.string.menu_missing_cost);MenuRecipeCostMissingReason.INGREDIENT_COST_INVALID->stringResource(R.string.menu_invalid_cost);MenuRecipeCostMissingReason.ACTIVE_PREPARATION_PARTIAL->stringResource(R.string.menu_preparation_partial);MenuRecipeCostMissingReason.ACTIVE_PREPARATION_UNCOSTED->stringResource(R.string.menu_preparation_uncosted);MenuRecipeCostMissingReason.ACTIVE_PREPARATION_YIELD_UNAVAILABLE->stringResource(R.string.menu_preparation_yield_missing);MenuRecipeCostMissingReason.PREPARATION_DEPENDENCY_CYCLE->stringResource(R.string.menu_preparation_cycle);null->stringResource(R.string.menu_unavailable)}
@Composable private fun MenuEditDialog(title:String,initialName:String,initialPrice:String,isSaving:Boolean,error:MenuOperationError?,onDismiss:()->Unit,onSave:(String,String)->Unit){var n by remember{mutableStateOf(initialName)};var p by remember{mutableStateOf(initialPrice)};AlertDialog(onDismissRequest={if(!isSaving)onDismiss()},title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){OutlinedTextField(n,{n=it},label={Text(stringResource(R.string.menu_name))},enabled=!isSaving);OutlinedTextField(p,{p=it},label={Text(stringResource(R.string.menu_selling_price))},enabled=!isSaving,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));error?.let{Text(operationErrorLabel(it),color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(onClick={onSave(n,p)},enabled=!isSaving&&n.isNotBlank()){if(isSaving)CircularProgressIndicator(Modifier.size(20.dp))else Text(stringResource(R.string.action_save))}},dismissButton={TextButton(onClick=onDismiss,enabled=!isSaving){Text(stringResource(R.string.action_cancel))}})}

@Composable internal fun ComponentDialog(state:ComponentEditorState,ingredients:List<com.miara.cuentame.core.model.ingredient.Ingredient>,onDismiss:()->Unit,onIngredient:(IngredientId)->Unit,onUnit:(com.miara.cuentame.core.common.ids.IngredientUnitOptionId)->Unit,onQuantity:(String)->Unit,onSave:()->Unit,onDelete:(()->Unit)?,isRemoving:Boolean){var ingredientExpanded by remember{mutableStateOf(false)};var unitExpanded by remember{mutableStateOf(false)};val busy=state.isSaving||isRemoving;AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(stringResource(if(state.existing==null)R.string.menu_add_component else R.string.menu_edit_component))},text={Column(verticalArrangement=Arrangement.spacedBy(AppSpacing.sm)){Box{OutlinedButton(onClick={ingredientExpanded=true},enabled=!busy){Text(ingredients.firstOrNull{it.id==state.selectedIngredientId}?.name?:stringResource(R.string.menu_choose_ingredient))};DropdownMenu(ingredientExpanded,onDismissRequest={ingredientExpanded=false}){ingredients.forEach{i->DropdownMenuItem(text={Text(i.name)},onClick={onIngredient(i.id);ingredientExpanded=false})}}};OutlinedTextField(state.quantity,onQuantity,label={Text(stringResource(R.string.menu_quantity))},enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));Box{OutlinedButton(onClick={unitExpanded=true},enabled=!busy&&!state.isLoadingUnits&&state.availableUnitOptions.isNotEmpty(),modifier=Modifier.testTag("menu_unit_selector")){Text(state.availableUnitOptions.firstOrNull{it.id==state.selectedUnitOptionId}?.displayName?:stringResource(R.string.menu_choose_unit))};DropdownMenu(unitExpanded,onDismissRequest={unitExpanded=false}){state.availableUnitOptions.forEach{o->DropdownMenuItem(text={Text("${o.displayName} (${o.shortLabel})")},onClick={onUnit(o.id);unitExpanded=false})}}};state.error?.let{Text(operationErrorLabel(it),color=MaterialTheme.colorScheme.error)};if(onDelete!=null)TextButton(onClick=onDelete,enabled=!busy){Text(stringResource(R.string.menu_remove_component))}}},confirmButton={Button(onClick=onSave,enabled=!busy&&state.selectedIngredientId!=null&&state.selectedUnitOptionId!=null&&state.quantity.toBigDecimalOrNull()?.let{it>java.math.BigDecimal.ZERO}==true){if(state.isSaving)CircularProgressIndicator(Modifier.size(20.dp))else Text(stringResource(R.string.action_save))}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text(stringResource(R.string.action_cancel))}})}

@Composable internal fun operationErrorLabel(error:MenuOperationError)=stringResource(when(error){MenuOperationError.NAME_REQUIRED->R.string.menu_error_name_required;MenuOperationError.PRICE_MALFORMED->R.string.menu_error_price_malformed;MenuOperationError.PRICE_NEGATIVE->R.string.menu_error_price_negative;MenuOperationError.DUPLICATE_NAME->R.string.menu_error_duplicate_name;MenuOperationError.DUPLICATE_COMPONENT->R.string.menu_error_duplicate_component;MenuOperationError.INVALID_QUANTITY->R.string.menu_error_invalid_quantity;MenuOperationError.UNIT_REQUIRED->R.string.menu_error_unit_required;MenuOperationError.UNIT_MISMATCH->R.string.menu_error_unit_mismatch;MenuOperationError.UNIT_INACTIVE->R.string.menu_error_unit_inactive;MenuOperationError.OWNERSHIP->R.string.menu_error_ownership;MenuOperationError.SAVE_FAILED->R.string.menu_error_save_failed})
