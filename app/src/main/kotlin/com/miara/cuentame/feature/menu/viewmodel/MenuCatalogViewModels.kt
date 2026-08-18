package com.miara.cuentame.feature.menu.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.menu.*
import com.miara.cuentame.core.domain.service.MenuPackageExport
import com.miara.cuentame.core.domain.service.MenuPackageExporter
import com.miara.cuentame.core.domain.service.MenuPackageExportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class MenuCatalogUiError { INVALID_VALUES, DUPLICATE_MENU_NAME, DUPLICATE_CATEGORY_NAME, ITEM_ALREADY_IN_MENU, OWNERSHIP_ERROR, PLACEMENT_FAILED, SAVE_FAILED }

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
sealed interface MenuPublicationUiError { data object MenuArchived:MenuPublicationUiError;data object MenuEmpty:MenuPublicationUiError;data object NoItems:MenuPublicationUiError;data class ItemArchived(val name:String):MenuPublicationUiError;data class ItemPriceMissing(val name:String):MenuPublicationUiError;data class IngredientAreaMissing(val name:String):MenuPublicationUiError;data object CatalogBroken:MenuPublicationUiError;data object Ownership:MenuPublicationUiError;data object SaveFailed:MenuPublicationUiError }
enum class MenuExportUiError { PUBLICATION_NOT_FOUND,MALFORMED_PACKAGE,WRITE_FAILED }
sealed interface MenuExportEvent { data class CreateDocument(val export:MenuPackageExport):MenuExportEvent }
data class MenuCatalogDetailState(val loading:Boolean=true,val menu:Menu?=null,val categories:List<MenuCategoryUiModel> = emptyList(),val availableItems:List<MenuRecipe> = emptyList(),val currencyCode:String="",val loadError:Boolean=false,val busy:Boolean=false,val error:MenuCatalogUiError?=null,val createdMenuItemId:MenuRecipeId?=null,val publications:List<MenuPublication> = emptyList(),val publicationError:MenuPublicationUiError?=null,val publishedRevision:Long?=null,val exportError:MenuExportUiError?=null,val exportSucceeded:Boolean=false)

@HiltViewModel @OptIn(ExperimentalCoroutinesApi::class)
class MenuCatalogDetailViewModel @Inject constructor(saved:SavedStateHandle,private val catalogs:MenuCatalogRepository,private val recipes:MenuRecipeRepository,private val restaurants:RestaurantRepository,private val publications:MenuPublicationRepository,private val packageExporter:MenuPackageExporter):ViewModel(){
    private val id=MenuId(requireNotNull(saved["menuId"])); private val retry=MutableStateFlow(0);private val op=MutableStateFlow(MenuCatalogDetailState(loading=false));private val publicationOp=MutableStateFlow(PublicationOperationState());private val exportOp=MutableStateFlow(ExportOperationState());private val _exportEvents=MutableSharedFlow<MenuExportEvent>(extraBufferCapacity=1);val exportEvents=_exportEvents.asSharedFlow()
    private val content=retry.flatMapLatest { catalogs.observeMenu(id).flatMapLatest { menu -> if(menu==null) flowOf(MenuCatalogDetailState(false,loadError=true)) else combine(catalogs.observeCategories(id),catalogs.observePlacements(id),recipes.observeRecipes(menu.restaurantId,true),restaurants.observeRestaurant()){categories,placements,allRecipes,restaurant ->
        if(restaurant?.id!=menu.restaurantId) return@combine MenuCatalogDetailState(false,loadError=true)
        val byId=allRecipes.associateBy{it.id}; val placed=placements.map{it.menuRecipeId}.toSet()
        val projected=categories.map{cat->MenuCategoryUiModel(cat,placements.filter{it.categoryId==cat.id}.map{p->MenuPlacementUiModel(p,checkNotNull(byId[p.menuRecipeId]){"Menu placement references a missing MenuRecipe: ${p.menuRecipeId.value}"})})}
        MenuCatalogDetailState(false,menu,projected,allRecipes.filter{it.archivedAt==null&&it.id !in placed},restaurant.currencyCode)
    }}.catch{emit(MenuCatalogDetailState(false,loadError=true))} }
    val state=combine(content,op,publications.observePublications(id),publicationOp,exportOp){c,o,history,p,x->c.copy(busy=o.busy||p.busy||x.busy,error=o.error,createdMenuItemId=o.createdMenuItemId,publications=history,publicationError=p.error,publishedRevision=p.publishedRevision,exportError=x.error,exportSucceeded=x.succeeded)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),MenuCatalogDetailState())
    fun clearError(){op.value=op.value.copy(error=null)}
    fun consumeCreatedMenuItem(){op.value=op.value.copy(createdMenuItemId=null)}
    fun retry(){retry.value++}
    private fun run(block:suspend()->Unit){if(op.value.busy)return;viewModelScope.launch{op.value=op.value.copy(busy=true,error=null);try{block();op.value=op.value.copy(busy=false)}catch(e:CancellationException){throw e}catch(e:Exception){op.value=op.value.copy(busy=false,error=e.catalogUiError())}}}
    fun updateMenu(name:String,description:String,discount:String){val pct=discount.trim().toBigDecimalOrNull();if(name.isBlank()||pct==null||pct<BigDecimal.ZERO||pct>=BigDecimal("100")){op.value=op.value.copy(error=MenuCatalogUiError.INVALID_VALUES);return};run{catalogs.updateMenu(id,name,description.trim().ifBlank{null},pct)}}
    fun toggleArchived() { val menu=state.value.menu?:return;run{catalogs.setArchived(id,menu.archivedAt==null)} }
    fun saveCategory(existing:MenuCategory?,name:String)=run{catalogs.saveCategory(id,existing?.id,name,existing?.sortOrder?:((state.value.categories.maxOfOrNull{it.category.sortOrder}?:-10)+10))}
    fun removeCategory(category:MenuCategory)=run{catalogs.removeCategory(id,category.id)}
    fun addItem(category:MenuCategory,recipe:MenuRecipe)=run{catalogs.savePlacement(id,null,category.id,recipe.id,(state.value.categories.flatMap{it.items}.maxOfOrNull{it.placement.sortOrder}?:-10)+10)}
    fun createAndAddItem(category:MenuCategory,name:String,priceText:String){
        if(op.value.busy)return
        val price=if(priceText.isBlank())null else priceText.trim().toBigDecimalOrNull()
        if(name.isBlank()||(priceText.isNotBlank()&&(price==null||price<BigDecimal.ZERO))){op.value=op.value.copy(error=MenuCatalogUiError.INVALID_VALUES);return}
        viewModelScope.launch{
            op.value=op.value.copy(busy=true,error=null,createdMenuItemId=null)
            try{
                val restaurant=restaurants.getRestaurant()?:throw MenuRecipeValidationException.OwnershipMismatch()
                val recipeId=recipes.create(restaurant.id,name,price,null)
                try{
                    val sortOrder=(state.value.categories.flatMap{it.items}.maxOfOrNull{it.placement.sortOrder}?:-10)+10
                    catalogs.savePlacement(id,null,category.id,recipeId,sortOrder)
                    op.value=op.value.copy(busy=false,createdMenuItemId=recipeId)
                }catch(e:CancellationException){throw e}catch(e:Exception){
                    // The recipe remains reusable and will appear in the existing-item picker.
                    op.value=op.value.copy(busy=false,error=MenuCatalogUiError.PLACEMENT_FAILED)
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){op.value=op.value.copy(busy=false,error=e.catalogUiError())}
        }
    }
    fun removeItem(item:MenuPlacementUiModel)=run{catalogs.removePlacement(id,item.placement.id)}
    fun moveItem(item:MenuPlacementUiModel,to:MenuCategory)=run{catalogs.savePlacement(id,item.placement.id,to.id,item.recipe.id,item.placement.sortOrder)}
    fun moveCategory(index:Int,delta:Int){val ordered=state.value.categories.map{it.category.id}.toMutableList();val target=index+delta;if(target !in ordered.indices)return;val v=ordered.removeAt(index);ordered.add(target,v);run{catalogs.reorderCategories(id,ordered)}}
    fun moveItem(category:MenuCategoryUiModel,index:Int,delta:Int){val target=index+delta;if(target !in category.items.indices)return;val all=state.value.categories.flatMap{it.items}.map{it.placement.id}.toMutableList();val current=all.indexOf(category.items[index].placement.id);val swap=all.indexOf(category.items[target].placement.id);val v=all[current];all[current]=all[swap];all[swap]=v;run{catalogs.reorderPlacements(id,all)}}
    fun publish(){if(publicationOp.value.busy)return;val nextRevision=state.value.menu?.publicationRevision?.plus(1);viewModelScope.launch{publicationOp.value=PublicationOperationState(busy=true);try{publications.publish(id);publicationOp.value=PublicationOperationState(publishedRevision=nextRevision)}catch(e:CancellationException){throw e}catch(e:MenuPublicationException){publicationOp.value=PublicationOperationState(error=when(e){is MenuPublicationException.MenuArchived->MenuPublicationUiError.MenuArchived;is MenuPublicationException.MenuEmpty->MenuPublicationUiError.MenuEmpty;is MenuPublicationException.NoItems->MenuPublicationUiError.NoItems;is MenuPublicationException.ItemArchived->MenuPublicationUiError.ItemArchived(e.itemName);is MenuPublicationException.ItemPriceMissing->MenuPublicationUiError.ItemPriceMissing(e.itemName);is MenuPublicationException.ComponentDefaultAreaMissing->MenuPublicationUiError.IngredientAreaMissing(e.ingredientName);is MenuPublicationException.ComponentIngredientNotFound,is MenuPublicationException.ComponentIngredientOwnershipMismatch,is MenuPublicationException.ComponentAreaInvalid->MenuPublicationUiError.CatalogBroken;is MenuPublicationException.OwnershipMismatch->MenuPublicationUiError.Ownership;is MenuPublicationException.BrokenCatalogRelationship,is MenuPublicationException.MenuNotFound->MenuPublicationUiError.CatalogBroken;is MenuPublicationException.PersistenceFailure->MenuPublicationUiError.SaveFailed})}}}
    fun clearPublicationFeedback(){publicationOp.value=PublicationOperationState()}
    fun export(publicationId:MenuPublicationId){if(exportOp.value.busy)return;viewModelScope.launch{exportOp.value=ExportOperationState(busy=true);try{_exportEvents.emit(MenuExportEvent.CreateDocument(packageExporter.prepare(publicationId)))}catch(e:CancellationException){throw e}catch(e:MenuPackageExportException){exportOp.value=ExportOperationState(error=when(e){is MenuPackageExportException.PublicationNotFound->MenuExportUiError.PUBLICATION_NOT_FOUND;is MenuPackageExportException.MalformedPublication,is MenuPackageExportException.ValidationFailed->MenuExportUiError.MALFORMED_PACKAGE})}}}
    fun exportPickerCancelled(){exportOp.value=ExportOperationState()}
    fun exportWriteFinished(success:Boolean){exportOp.value=if(success)ExportOperationState(succeeded=true)else ExportOperationState(error=MenuExportUiError.WRITE_FAILED)}
}

private data class PublicationOperationState(val busy:Boolean=false,val error:MenuPublicationUiError?=null,val publishedRevision:Long?=null)
private data class ExportOperationState(val busy:Boolean=false,val error:MenuExportUiError?=null,val succeeded:Boolean=false)
