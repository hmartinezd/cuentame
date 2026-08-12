package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.service.*
import com.miara.cuentame.core.model.menu.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RoomMenuCostRepository @Inject constructor(private val menuDao: MenuRecipeDao, private val ingredientDao: IngredientDao,
    private val optionDao: IngredientUnitOptionDao, private val costDao: IngredientCostProjectionDao, private val unitDao: UnitDao,
    private val restaurantDao: RestaurantDao, private val preparations: PreparationCostRepository,
    private val prices: PriceIntelligenceRepository, private val calculator: MenuCostCalculator) : MenuCostRepository {
    override fun observeCost(id: MenuRecipeId): Flow<MenuRecipeCost?> = menuDao.observeRecipe(id.value).flatMapLatest { recipe ->
        if(recipe==null) flowOf(null) else graph(recipe.restaurantId, true).map { g -> g.recipes.firstOrNull { it.id==id.value }?.let { calculate(it,g) } }
    }
    override fun observeCosts(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<MenuRecipeCost>> = graph(restaurantId.value,includeArchived).map { g -> g.recipes.map { calculate(it,g) } }

    private fun graph(restaurantId:String, includeArchived:Boolean):Flow<Graph> {
        val structural=combine(menuDao.observeRecipes(restaurantId,includeArchived),menuDao.observeAllComponents(restaurantId),
            ingredientDao.observeAllIngredients(restaurantId),optionDao.observeAllForRestaurant(restaurantId),costDao.observeAllForRestaurant(restaurantId)) { r,c,i,o,cost -> Structural(r,c,i,o,cost) }
        return combine(structural,unitDao.observeAll(),restaurantDao.observeById(restaurantId),preparations.observeActivePreparationCostsByOutput(RestaurantId(restaurantId))) { s,u,r,p -> arrayOf(s,u,r,p) }
            .flatMapLatest { values ->
                @Suppress("UNCHECKED_CAST") val s=values[0] as Structural
                @Suppress("UNCHECKED_CAST") val units=values[1] as List<com.miara.cuentame.core.database.entity.UnitEntity>
                val restaurant=values[2] as RestaurantEntity?
                @Suppress("UNCHECKED_CAST") val prep=values[3] as Map<IngredientId,com.miara.cuentame.core.model.ingredient.PreparationRecipeCost>
                prices.observePriceComparisons(RestaurantId(restaurantId),s.ingredients.map { IngredientId(it.id) }.toSet())
                    .catch { emit(emptyMap()) }.map { comparisons ->
                    val projections=s.costs.associateBy { it.ingredientId }; val symbols=units.associate { it.id to it.symbol }; val options=s.options.associateBy { it.id }
                    val ingredients=s.ingredients.associate { e -> IngredientId(e.id) to MenuCostIngredientInput(IngredientId(e.id),e.name,symbols[e.baseUnitId].orEmpty(),
                        projections[e.id]?.averageUnitCostBase?.let { raw -> raw.toBigDecimalOrNull()?.takeIf { it>=java.math.BigDecimal.ZERO }?.let(CurrentIngredientCost::Available) ?: CurrentIngredientCost.Invalid } ?: CurrentIngredientCost.Missing) }
                    Graph(s.recipes,s.components.groupBy { it.menuRecipeId },ingredients,options,comparisons.mapValues { it.value.absoluteChange },prep,restaurant?.currencyCode?:"USD")
                }
            }
    }
    private fun calculate(r:MenuRecipeEntity,g:Graph)=calculator.calculate(MenuRecipeId(r.id),r.sellingPrice,g.components[r.id].orEmpty().map { c ->
        MenuCostComponentInput(MenuRecipeComponentId(c.id),IngredientId(c.ingredientId),c.quantityEntered,g.options[c.ingredientUnitOptionId]?.displayName,c.quantityBase,g.deltas[IngredientId(c.ingredientId)])
    },g.ingredients,g.preparations,g.currency)
    private data class Structural(val recipes:List<MenuRecipeEntity>,val components:List<MenuRecipeComponentEntity>,val ingredients:List<IngredientEntity>,val options:List<IngredientUnitOptionEntity>,val costs:List<IngredientCostProjectionEntity>)
    private data class Graph(val recipes:List<MenuRecipeEntity>,val components:Map<String,List<MenuRecipeComponentEntity>>,val ingredients:Map<IngredientId,MenuCostIngredientInput>,val options:Map<String,IngredientUnitOptionEntity>,val deltas:Map<IngredientId,java.math.BigDecimal?>,val preparations:Map<IngredientId,com.miara.cuentame.core.model.ingredient.PreparationRecipeCost>,val currency:String)
}
