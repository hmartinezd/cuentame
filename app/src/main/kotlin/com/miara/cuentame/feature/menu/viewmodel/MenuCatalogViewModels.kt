package com.miara.cuentame.feature.menu.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.menu.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class MenuCatalogUiError { INVALID_VALUES, DUPLICATE_MENU_NAME, DUPLICATE_CATEGORY_NAME, ITEM_ALREADY_IN_MENU, OWNERSHIP_ERROR, SAVE_FAILED }

private fun Throwable.catalogUiError() = when (this) {
    is MenuCatalogPersistenceException.InvalidCatalog -> MenuCatalogUiError.INVALID_VALUES
    is MenuCatalogPersistenceException.DuplicateName -> MenuCatalogUiError.DUPLICATE_MENU_NAME
    is MenuCatalogPersistenceException.DuplicateCategoryName -> MenuCatalogUiError.DUPLICATE_CATEGORY_NAME
    is MenuCatalogPersistenceException.DuplicateMenuRecipePlacement -> MenuCatalogUiError.ITEM_ALREADY_IN_MENU
    is MenuCatalogPersistenceException.OwnershipMismatch -> MenuCatalogUiError.OWNERSHIP_ERROR
    else -> MenuCatalogUiError.SAVE_FAILED
}

data class MenuCatalogRow(val menu: Menu, val categoryCount: Int, val itemCount: Int)
data class MenuCatalogListState(val loading: Boolean = true, val rows: List<MenuCatalogRow> = emptyList(), val includeArchived: Boolean = false,
    val loadError: Boolean = false, val busy: Boolean = false, val error: MenuCatalogUiError? = null, val createdId: MenuId? = null)

@HiltViewModel @OptIn(ExperimentalCoroutinesApi::class)
class MenuCatalogListViewModel @Inject constructor(private val catalogs: MenuCatalogRepository, private val restaurants: RestaurantRepository) : ViewModel() {
    private val archived = MutableStateFlow(false); private val retry = MutableStateFlow(0); private val op = MutableStateFlow(MenuCatalogListState(loading=false))
    private val content = combine(restaurants.observeRestaurant(), archived, retry) { r, a, _ -> r to a }.flatMapLatest { (restaurant, include) ->
        if (restaurant == null) flowOf(MenuCatalogListState(false, loadError=true, includeArchived=include)) else catalogs.observeMenus(restaurant.id, include).flatMapLatest { menus ->
            if (menus.isEmpty()) flowOf(MenuCatalogListState(false, includeArchived=include)) else combine(menus.map { menu ->
                combine(catalogs.observeCategories(menu.id), catalogs.observePlacements(menu.id)) { c, p -> MenuCatalogRow(menu,c.size,p.size) }
            }) { it.toList() }.map { MenuCatalogListState(false,it,include) }
        }.onStart { emit(MenuCatalogListState(includeArchived=include)) }.catch { emit(MenuCatalogListState(false,loadError=true,includeArchived=include)) }
    }
    val state = combine(content,op) { c,o -> c.copy(busy=o.busy,error=o.error,createdId=o.createdId) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),MenuCatalogListState())
    fun toggleArchived(){archived.value=!archived.value}; fun retry(){retry.value++}; fun clearError(){op.value=op.value.copy(error=null)}; fun consumeCreated(){op.value=op.value.copy(createdId=null)}
    fun create(name:String,description:String,discount:String){ save(null,name,description,discount) }
    private fun save(id:MenuId?,name:String,description:String,discount:String){
        if(op.value.busy)return; val pct=discount.trim().toBigDecimalOrNull()
        if(name.isBlank()||pct==null||pct<BigDecimal.ZERO||pct>=BigDecimal("100")){op.value=op.value.copy(error=MenuCatalogUiError.INVALID_VALUES);return}
        viewModelScope.launch { op.value=op.value.copy(busy=true,error=null,createdId=null); try { val restaurant=restaurants.getRestaurant()?:throw MenuCatalogPersistenceException.OwnershipMismatch(); val created=id?:catalogs.createMenu(restaurant.id,name,description.trim().ifBlank{null},pct); if(id!=null)catalogs.updateMenu(id,name,description.trim().ifBlank{null},pct); op.value=op.value.copy(busy=false,createdId=created) } catch(e:CancellationException){throw e}catch(e:Exception){op.value=op.value.copy(busy=false,error=e.catalogUiError())} }
    }
    fun setArchived(menu:Menu){ if(op.value.busy)return; viewModelScope.launch { op.value=op.value.copy(busy=true,error=null);try{catalogs.setArchived(menu.id,menu.archivedAt==null);op.value=op.value.copy(busy=false)}catch(e:CancellationException){throw e}catch(e:Exception){op.value=op.value.copy(busy=false,error=e.catalogUiError())} } }
}

data class MenuPlacementUiModel(val placement:MenuPlacement,val recipe:MenuRecipe)
data class MenuCategoryUiModel(val category:MenuCategory,val items:List<MenuPlacementUiModel>)
data class MenuCatalogDetailState(val loading:Boolean=true,val menu:Menu?=null,val categories:List<MenuCategoryUiModel> = emptyList(),val availableItems:List<MenuRecipe> = emptyList(),val loadError:Boolean=false,val busy:Boolean=false,val error:MenuCatalogUiError?=null)

@HiltViewModel @OptIn(ExperimentalCoroutinesApi::class)
class MenuCatalogDetailViewModel @Inject constructor(saved:SavedStateHandle,private val catalogs:MenuCatalogRepository,private val recipes:MenuRecipeRepository):ViewModel(){
    private val id=MenuId(requireNotNull(saved["menuId"])); private val op=MutableStateFlow(MenuCatalogDetailState(loading=false))
    private val content=catalogs.observeMenu(id).flatMapLatest { menu -> if(menu==null) flowOf(MenuCatalogDetailState(false,loadError=true)) else combine(catalogs.observeCategories(id),catalogs.observePlacements(id),recipes.observeRecipes(menu.restaurantId,false)){categories,placements,allRecipes ->
        val byId=allRecipes.associateBy{it.id}; val placed=placements.map{it.menuRecipeId}.toSet()
        MenuCatalogDetailState(false,menu,categories.map{cat->MenuCategoryUiModel(cat,placements.filter{it.categoryId==cat.id}.mapNotNull{p->byId[p.menuRecipeId]?.let{MenuPlacementUiModel(p,it)}})},allRecipes.filter{it.id !in placed})
    }}.catch{emit(MenuCatalogDetailState(false,loadError=true))}
    val state=combine(content,op){c,o->c.copy(busy=o.busy,error=o.error)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),MenuCatalogDetailState())
    fun clearError(){op.value=op.value.copy(error=null)}
    private fun run(block:suspend()->Unit){if(op.value.busy)return;viewModelScope.launch{op.value=op.value.copy(busy=true,error=null);try{block();op.value=op.value.copy(busy=false)}catch(e:CancellationException){throw e}catch(e:Exception){op.value=op.value.copy(busy=false,error=e.catalogUiError())}}}
    fun updateMenu(name:String,description:String,discount:String){val pct=discount.trim().toBigDecimalOrNull();if(name.isBlank()||pct==null||pct<BigDecimal.ZERO||pct>=BigDecimal("100")){op.value=op.value.copy(error=MenuCatalogUiError.INVALID_VALUES);return};run{catalogs.updateMenu(id,name,description.trim().ifBlank{null},pct)}}
    fun toggleArchived() { val menu=state.value.menu?:return;run{catalogs.setArchived(id,menu.archivedAt==null)} }
    fun saveCategory(existing:MenuCategory?,name:String)=run{catalogs.saveCategory(id,existing?.id,name,existing?.sortOrder?:((state.value.categories.maxOfOrNull{it.category.sortOrder}?:-10)+10))}
    fun removeCategory(category:MenuCategory)=run{catalogs.removeCategory(id,category.id)}
    fun addItem(category:MenuCategory,recipe:MenuRecipe)=run{catalogs.savePlacement(id,null,category.id,recipe.id,(state.value.categories.flatMap{it.items}.maxOfOrNull{it.placement.sortOrder}?:-10)+10)}
    fun removeItem(item:MenuPlacementUiModel)=run{catalogs.removePlacement(id,item.placement.id)}
    fun moveItem(item:MenuPlacementUiModel,to:MenuCategory)=run{catalogs.savePlacement(id,item.placement.id,to.id,item.recipe.id,item.placement.sortOrder)}
    fun moveCategory(index:Int,delta:Int){val ordered=state.value.categories.map{it.category.id}.toMutableList();val target=index+delta;if(target !in ordered.indices)return;val v=ordered.removeAt(index);ordered.add(target,v);run{catalogs.reorderCategories(id,ordered)}}
    fun moveItem(category:MenuCategoryUiModel,index:Int,delta:Int){val target=index+delta;if(target !in category.items.indices)return;val all=state.value.categories.flatMap{it.items}.map{it.placement.id}.toMutableList();val current=all.indexOf(category.items[index].placement.id);val swap=all.indexOf(category.items[target].placement.id);val v=all[current];all[current]=all[swap];all[swap]=v;run{catalogs.reorderPlacements(id,all)}}
}
