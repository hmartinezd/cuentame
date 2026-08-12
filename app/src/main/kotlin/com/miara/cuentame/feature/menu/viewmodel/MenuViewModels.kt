package com.miara.cuentame.feature.menu.viewmodel

import androidx.lifecycle.*
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.menu.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class MenuListState(val loading:Boolean=true,val rows: List<Pair<MenuRecipe, MenuRecipeCost?>> = emptyList(),val error:Boolean=false,val includeArchived:Boolean=false)
@HiltViewModel class MenuListViewModel @Inject constructor(private val recipes:MenuRecipeRepository,private val costs:MenuCostRepository,private val restaurants:RestaurantRepository):ViewModel(){
    private val archived=MutableStateFlow(false); private val retry=MutableStateFlow(0)
    val state=combine(restaurants.observeRestaurant(),archived,retry){r,a,_->r to a}.flatMapLatest{(r,a)->if(r==null) flowOf(MenuListState(false,error=true)) else combine(recipes.observeRecipes(r.id,a),costs.observeCosts(r.id,a)){rs,cs->MenuListState(false,rs.map{it to cs.firstOrNull{c->c.menuRecipeId==it.id}},includeArchived=a)}.catch{emit(MenuListState(false,error=true,includeArchived=a))}.onStart{emit(MenuListState(includeArchived=a))}}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),MenuListState())
    fun create(name:String,price:String,onCreated:(MenuRecipeId)->Unit)=viewModelScope.launch{val r=restaurants.getRestaurant()?:return@launch; runCatching{recipes.create(r.id,name,price.trim().takeIf{it.isNotEmpty()}?.toBigDecimalOrNull(),null)}.onSuccess(onCreated)}
    fun toggleArchived(){archived.value=!archived.value}; fun retry(){retry.value++}
}
data class MenuDetailState(val loading:Boolean=true,val recipe:MenuRecipe?=null,val cost:MenuRecipeCost?=null,val components:List<MenuRecipeComponent> = emptyList(),val ingredients:List<Ingredient> = emptyList(),val error:Boolean=false)
@HiltViewModel class MenuDetailViewModel @Inject constructor(saved:SavedStateHandle,private val recipes:MenuRecipeRepository,private val costs:MenuCostRepository,private val ingredientRepository:IngredientRepository):ViewModel(){
    private val id=MenuRecipeId(requireNotNull(saved["menuRecipeId"])); val state=recipes.observeRecipe(id).flatMapLatest { recipe ->
        if(recipe==null) flowOf(MenuDetailState(false,error=true)) else combine(costs.observeCost(id),recipes.observeComponents(id),ingredientRepository.observeIngredients(recipe.restaurantId,false)){cost,components,ingredients->MenuDetailState(false,recipe,cost,components,ingredients,cost==null)}
    }.catch{emit(MenuDetailState(false,error=true))}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),MenuDetailState())
    fun save(name:String,price:String)=viewModelScope.launch{val r=state.value.recipe?:return@launch; recipes.update(id,name,price.trim().takeIf{it.isNotEmpty()}?.toBigDecimalOrNull(),r.notes)}
    fun archive()=viewModelScope.launch{val r=state.value.recipe?:return@launch;recipes.setArchived(id,r.archivedAt==null)}
    fun saveComponent(existing:MenuRecipeComponent?,ingredientId:IngredientId,quantity:String)=viewModelScope.launch{
        val q=quantity.toBigDecimalOrNull()?:return@launch;val option=if(existing!=null&&existing.ingredientId==ingredientId) ingredientRepository.getUnitOption(existing.ingredientUnitOptionId) else ingredientRepository.getUnitOptions(ingredientId).firstOrNull()
        if(option!=null)recipes.saveComponent(id,existing?.id,ingredientId,option.id,q,existing?.sortOrder?:state.value.components.size)
    }
    fun removeComponent(component:MenuRecipeComponent)=viewModelScope.launch{recipes.removeComponent(id,component.id)}
}
