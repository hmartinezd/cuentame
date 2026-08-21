package com.venkoi.restaurantops.feature.menu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venkoi.restaurantops.R
import com.venkoi.restaurantops.app.ui.theme.AppSpacing
import com.venkoi.restaurantops.core.designsystem.component.*
import com.venkoi.restaurantops.core.designsystem.util.Formatters
import com.venkoi.restaurantops.core.model.menu.CashDiscountBehavior
import com.venkoi.restaurantops.feature.menu.viewmodel.CreateMenuItemViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMenuItemRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onInventory: () -> Unit,
    onImport: () -> Unit,
    vm: CreateMenuItemViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.catalog_create_menu_item)) }, navigationIcon = { TextButton(onClick = onBack, enabled = !state.saving) { Text(stringResource(R.string.action_cancel)) } }) },
        bottomBar = { Surface(shadowElevation = 8.dp) { Row(Modifier.fillMaxWidth().adaptiveContentWidth(760.dp).padding(AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End)) { TextButton(onClick = onBack, enabled = !state.saving) { Text(stringResource(R.string.action_cancel)) }; AppPrimaryButton(onClick = { vm.save(name, price) }, enabled = !state.saving && name.isNotBlank(), modifier = Modifier.testTag("catalog_save_item")) { if (state.saving) CircularProgressIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.menu_save_item)) } } } }
    ) { padding ->
        if (state.loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("create_menu_item_screen"), contentPadding = PaddingValues(vertical = AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            item { CenteredCreateContent { AppSectionHeader(stringResource(R.string.menu_basic_information)) } }
            item { CenteredCreateContent { AppCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.menu_item_name)) }, modifier = Modifier.fillMaxWidth().testTag("catalog_new_item_name"), enabled = !state.saving); OutlinedTextField(price, { price = it }, label = { Text(stringResource(R.string.menu_selling_price)) }, modifier = Modifier.fillMaxWidth().testTag("catalog_new_item_price"), enabled = !state.saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) } } } }
            item { CenteredCreateContent { AppSectionHeader(stringResource(R.string.menu_cash_discount)) } }
            item { CenteredCreateContent { AppCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AppSpacing.md)) { CashChoice(state.cashDiscountBehavior == CashDiscountBehavior.APPLY_DEFAULT, stringResource(R.string.menu_cash_use_default_percent, state.defaultDiscountPercent.stripTrailingZeros().toPlainString()), !state.saving) { vm.setCashDiscountBehavior(CashDiscountBehavior.APPLY_DEFAULT) }; CashChoice(state.cashDiscountBehavior == CashDiscountBehavior.NONE, stringResource(R.string.menu_cash_none), !state.saving) { vm.setCashDiscountBehavior(CashDiscountBehavior.NONE) } } } } }
            item { CenteredCreateContent { AppSectionHeader(stringResource(R.string.menu_recipe_components), trailing = { if (state.ingredients.isNotEmpty()) AppPrimaryButton(onClick = { vm.openComponent() }, enabled = !state.saving, modifier = Modifier.testTag("menu_add_component")) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(AppSpacing.xs)); Text(stringResource(R.string.menu_add_component)) } }) } }
            if (state.ingredients.isEmpty()) item { CenteredCreateContent { AppCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) { Text(stringResource(R.string.menu_no_ingredients), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.menu_no_ingredients_body), color = MaterialTheme.colorScheme.onSurfaceVariant); Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) { OutlinedButton(onClick = onInventory) { Text(stringResource(R.string.menu_go_inventory)) }; TextButton(onClick = onImport) { Text(stringResource(R.string.menu_import_ingredients)) } } } } } }
            else if (state.components.isEmpty()) item { CenteredCreateContent { AppCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) { Text(stringResource(R.string.menu_recipe_not_configured), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.menu_recipe_not_configured_body), color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            items(state.components, key = { it.draftId }) { component -> CenteredCreateContent { AppCard(Modifier.fillMaxWidth().clickable { vm.openComponent(component) }) { Row(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(component.ingredientName, style = MaterialTheme.typography.titleMedium); Text("${Formatters.formatQuantity(component.quantity)} ${component.unitLabel}");if(state.ingredients.firstOrNull{it.id==component.ingredientId}?.defaultAreaId==null)Text(stringResource(R.string.menu_inventory_area_required),color=MaterialTheme.colorScheme.error) }; TextButton(onClick = { vm.removeComponent(component) }, enabled = !state.saving) { Text(stringResource(R.string.menu_remove_component)) } } } } }
            state.error?.let { error -> item { CenteredCreateContent { Text(operationErrorLabel(error), color = MaterialTheme.colorScheme.error) } } }
        }
    }
    if (state.editor.isOpen) ComponentDialog(state.editor, state.ingredients, vm::dismissComponent, vm::selectIngredient, vm::selectUnit, vm::updateQuantity, vm::saveComponent, null, false)
}

@Composable
private fun CenteredCreateContent(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)) {
        Box(Modifier.adaptiveContentWidth(760.dp)) { content() }
    }
}
