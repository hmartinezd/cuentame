package com.venkoi.restaurantops.core.database.repository

import com.venkoi.restaurantops.core.common.ids.*
import com.venkoi.restaurantops.core.database.dao.*
import com.venkoi.restaurantops.core.database.entity.*
import com.venkoi.restaurantops.core.domain.repository.PreparationCostRepository
import com.venkoi.restaurantops.core.domain.repository.PriceIntelligenceRepository
import com.venkoi.restaurantops.core.domain.service.*
import com.venkoi.restaurantops.core.model.ingredient.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RoomPreparationCostRepository @Inject constructor(
    private val recipeDao: PreparationRecipeDao,
    private val ingredientDao: IngredientDao,
    private val optionDao: IngredientUnitOptionDao,
    private val costDao: IngredientCostProjectionDao,
    private val unitDao: UnitDao,
    private val batchDao: ProductionBatchDao,
    private val restaurantDao: RestaurantDao,
    private val priceRepository: PriceIntelligenceRepository,
    private val calculator: PreparationCostCalculator
) : PreparationCostRepository {

    override fun observeRecipeCost(recipeId: PreparationRecipeId): Flow<PreparationRecipeCost?> =
        recipeDao.observeById(recipeId.value).flatMapLatest { recipe ->
            if (recipe == null) flowOf(null)
            else combine(graph(recipe.restaurantId), batchDao.observeLatestPostedForRecipe(recipe.id), restaurantDao.observeById(recipe.restaurantId)) { graph, batch, restaurant ->
                calculator.calculate(recipeId, graph.recipes, graph.ingredients)?.copy(
                    lastProduction = batch?.let {
                        HistoricalPreparationCost(ProductionBatchId(it.id), Instant.ofEpochMilli(it.effectiveAt),
                            it.totalComponentCostSnapshot?.toBigDecimalOrNull(), it.outputUnitCostBaseSnapshot?.toBigDecimalOrNull())
                    }, currencyCode = restaurant?.currencyCode ?: "USD"
                )
            }
        }

    override fun observeRecipeCostSummaries(restaurantId: RestaurantId): Flow<List<PreparationRecipeCostSummary>> =
        graph(restaurantId.value).map { graph ->
            graph.recipes.mapNotNull { recipe ->
                calculator.calculate(recipe.id, graph.recipes, graph.ingredients)?.let {
                    PreparationRecipeCostSummary(recipe.id, it.status, it.totalBatchCost)
                }
            }
        }

    override fun observeActivePreparationCostsByOutput(restaurantId: RestaurantId): Flow<Map<IngredientId, PreparationRecipeCost>> =
        graph(restaurantId.value).map { graph ->
            graph.recipes.asSequence()
                .filter { it.status == PreparationRecipeStatus.ACTIVE }
                .mapNotNull { recipe -> calculator.calculate(recipe.id, graph.recipes, graph.ingredients)?.let { recipe.outputIngredientId to it } }
                .toMap()
        }

    private fun graph(restaurantId: String): Flow<Graph> {
        val structural = combine(
            recipeDao.observeAllRecipesForRestaurant(restaurantId),
            recipeDao.observeAllComponentsForRestaurant(restaurantId),
            ingredientDao.observeAllIngredients(restaurantId),
            optionDao.observeAllForRestaurant(restaurantId),
            costDao.observeAllForRestaurant(restaurantId)
        ) { recipes, components, ingredients, options, costs -> Structural(recipes, components, ingredients, options, costs) }
        return combine(structural, unitDao.observeAll()) { s, units -> s to units }
            .flatMapLatest { (s, units) ->
                val ids = s.ingredients.map { IngredientId(it.id) }.toSet()
                priceRepository.observePriceComparisons(RestaurantId(restaurantId), ids)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(emptyMap())
                    }.map { comparisons ->
                    val options = s.options.associateBy { it.id }
                    val unitSymbols = units.associate { it.id to it.symbol }
                    val costs = s.costs.associateBy { it.ingredientId }
                    val deltas = comparisons.mapValues { it.value.absoluteChange }
                    val ingredients = s.ingredients.associate { entity ->
                        val projection = costs[entity.id]
                        val currentCost = when {
                            projection?.averageUnitCostBase == null -> CurrentIngredientCost.Missing
                            else -> projection.averageUnitCostBase.toBigDecimalOrNull()
                                ?.takeIf { it >= java.math.BigDecimal.ZERO }
                                ?.let(CurrentIngredientCost::Available)
                                ?: CurrentIngredientCost.Invalid
                        }
                        IngredientId(entity.id) to PreparationCostIngredientInput(
                            IngredientId(entity.id), entity.name, unitSymbols[entity.baseUnitId].orEmpty(), currentCost)
                    }
                    val components = s.components.groupBy { it.recipeId }
                    val recipeInputs = s.recipes.map { recipe ->
                        PreparationCostRecipeInput(
                            PreparationRecipeId(recipe.id), IngredientId(recipe.outputIngredientId),
                            runCatching { PreparationRecipeStatus.valueOf(recipe.status) }.getOrDefault(PreparationRecipeStatus.UNKNOWN),
                            recipe.standardYieldQuantity, recipe.standardYieldQuantityBase,
                            recipe.yieldUnitOptionId?.let { options[it]?.displayName },
                            components[recipe.id].orEmpty().map { component ->
                                PreparationCostComponentInput(
                                    PreparationRecipeComponentId(component.id), IngredientId(component.componentIngredientId),
                                    component.quantityEntered, options[component.unitOptionId]?.displayName,
                                    component.quantityBase, deltas[IngredientId(component.componentIngredientId)]
                                )
                            }
                        )
                    }
                    Graph(recipeInputs, ingredients)
                }
            }
    }

    private data class Structural(
        val recipes: List<PreparationRecipeEntity>, val components: List<PreparationRecipeComponentEntity>,
        val ingredients: List<IngredientEntity>, val options: List<IngredientUnitOptionEntity>,
        val costs: List<IngredientCostProjectionEntity>
    )
    private data class Graph(
        val recipes: List<PreparationCostRecipeInput>,
        val ingredients: Map<IngredientId, PreparationCostIngredientInput>
    )
}
