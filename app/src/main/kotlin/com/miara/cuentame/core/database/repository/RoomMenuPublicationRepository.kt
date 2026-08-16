package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.menu.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMenuPublicationRepository @Inject constructor(
    private val database:RestaurantInventoryDatabase,
    private val publicationDao:MenuPublicationDao,
    private val catalogDao:MenuCatalogDao,
    private val recipeDao:MenuRecipeDao,
    private val restaurantDao:RestaurantDao
):MenuPublicationRepository {
    override fun observePublications(menuId:MenuId)=publicationDao.observePublications(menuId.value).map { it.map(MenuPublicationEntity::domain) }

    override fun observePublication(publicationId:MenuPublicationId):Flow<MenuPublicationSnapshot?> =
        combine(publicationDao.observePublication(publicationId.value),publicationDao.observeCategories(publicationId.value),publicationDao.observeItems(publicationId.value),publicationDao.observeComponents(publicationId.value)){p,c,i,x->
            p?.let { MenuPublicationSnapshot(it.domain(),c.map(MenuPublicationCategoryEntity::domain),i.map(MenuPublicationItemEntity::domain),x.map(MenuPublicationItemComponentEntity::domain)) }
        }

    override suspend fun getPublication(publicationId:MenuPublicationId):MenuPublicationSnapshot?=database.withTransaction{
        val p=publicationDao.getPublication(publicationId.value)?:return@withTransaction null
        MenuPublicationSnapshot(p.domain(),publicationDao.getCategories(p.id).map(MenuPublicationCategoryEntity::domain),publicationDao.getItems(p.id).map(MenuPublicationItemEntity::domain),publicationDao.getComponents(p.id).map(MenuPublicationItemComponentEntity::domain))
    }

    override suspend fun publish(menuId:MenuId):MenuPublicationId = try {
        database.withTransaction {
            val menu=catalogDao.getMenu(menuId.value)?:throw MenuPublicationException.MenuNotFound()
            if(menu.archivedAt!=null)throw MenuPublicationException.MenuArchived()
            val restaurant=restaurantDao.getById(menu.restaurantId)?:throw MenuPublicationException.OwnershipMismatch()
            val categories=catalogDao.getCategories(menu.id)
            if(categories.isEmpty())throw MenuPublicationException.MenuEmpty()
            val placements=catalogDao.getPlacements(menu.id)
            if(placements.isEmpty())throw MenuPublicationException.NoItems()
            val categoryIds=categories.mapTo(hashSetOf()){it.id}
            if(placements.any{it.categoryId !in categoryIds})throw MenuPublicationException.BrokenCatalogRelationship()
            val recipes=placements.map { placement ->
                val recipe=recipeDao.getRecipe(placement.menuRecipeId)?:throw MenuPublicationException.BrokenCatalogRelationship()
                if(recipe.restaurantId!=menu.restaurantId)throw MenuPublicationException.OwnershipMismatch()
                if(recipe.archivedAt!=null)throw MenuPublicationException.ItemArchived(recipe.name)
                if(recipe.sellingPrice==null)throw MenuPublicationException.ItemPriceMissing(recipe.name)
                placement to recipe
            }
            val id=MenuPublicationId(UUID.randomUUID().toString());val revision=menu.publicationRevision+1;val now=System.currentTimeMillis()
            publicationDao.insertPublication(MenuPublicationEntity(id.value,menu.restaurantId,menu.id,revision,menu.name,menu.description,menu.defaultCashDiscountPercent,restaurant.currencyCode,now))
            val categorySnapshotIds=categories.associate { it.id to MenuPublicationCategoryId(UUID.randomUUID().toString()) }
            publicationDao.insertCategories(categories.map{MenuPublicationCategoryEntity(categorySnapshotIds.getValue(it.id).value,id.value,it.id,it.name,it.sortOrder)})
            val itemRows=recipes.map{(placement,recipe)->MenuPublicationItemEntity(UUID.randomUUID().toString(),id.value,categorySnapshotIds.getValue(placement.categoryId).value,placement.id,recipe.id,recipe.name,checkNotNull(recipe.sellingPrice),recipe.cashDiscountBehavior,recipe.commercialRevision,recipe.consumptionRevision,placement.sortOrder)}
            publicationDao.insertItems(itemRows)
            val components=recipes.zip(itemRows).flatMap{(source,item)->recipeDao.getComponents(source.second.id).map{component->MenuPublicationItemComponentEntity(UUID.randomUUID().toString(),item.id,component.id,component.ingredientId,component.ingredientUnitOptionId,component.quantityEntered,component.quantityBase,component.sortOrder)}}
            if(components.isNotEmpty())publicationDao.insertComponents(components)
            if(catalogDao.advancePublicationRevision(menu.id,menu.publicationRevision,revision,now)!=1)throw MenuPublicationException.PersistenceFailure(IllegalStateException("Concurrent publication revision allocation"))
            id
        }
    } catch(e:CancellationException){throw e}catch(e:MenuPublicationException){throw e}catch(e:Exception){throw MenuPublicationException.PersistenceFailure(e)}
}

private fun MenuPublicationEntity.domain()=MenuPublication(MenuPublicationId(id),RestaurantId(restaurantId),MenuId(sourceMenuId),publicationRevision,menuNameSnapshot,menuDescriptionSnapshot,defaultCashDiscountPercentSnapshot,currencyCodeSnapshot,Instant.ofEpochMilli(publishedAt))
private fun MenuPublicationCategoryEntity.domain()=MenuPublicationCategory(MenuPublicationCategoryId(id),MenuPublicationId(publicationId),MenuCategoryId(sourceMenuCategoryId),nameSnapshot,sortOrder)
private fun MenuPublicationItemEntity.domain()=MenuPublicationItem(MenuPublicationItemId(id),MenuPublicationId(publicationId),MenuPublicationCategoryId(publicationCategoryId),MenuPlacementId(sourceMenuPlacementId),MenuRecipeId(menuRecipeId),displayNameSnapshot,sellingPriceSnapshot,cashDiscountBehaviorSnapshot,commercialRevision,consumptionRevision,sortOrder)
private fun MenuPublicationItemComponentEntity.domain()=MenuPublicationItemComponent(MenuPublicationItemComponentId(id),MenuPublicationItemId(publicationItemId),MenuRecipeComponentId(sourceMenuRecipeComponentId),IngredientId(ingredientId),IngredientUnitOptionId(ingredientUnitOptionId),quantityEnteredSnapshot,quantityBaseSnapshot,sortOrder)
