package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.menu.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMenuRecipeRepository @Inject constructor(private val db: RestaurantInventoryDatabase, private val dao: MenuRecipeDao,
    private val ingredientDao: IngredientDao, private val optionDao: IngredientUnitOptionDao) : MenuRecipeRepository {
    override fun observeRecipes(restaurantId: RestaurantId, includeArchived: Boolean) = dao.observeRecipes(restaurantId.value, includeArchived).map { it.map(MenuRecipeEntity::domain) }
    override fun observeRecipe(id: MenuRecipeId) = dao.observeRecipe(id.value).map { it?.domain() }
    override fun observeComponents(id: MenuRecipeId) = dao.observeComponents(id.value).map { it.map(MenuRecipeComponentEntity::domain) }
    override suspend fun create(restaurantId: RestaurantId, name: String, sellingPrice: BigDecimal?, notes: String?): MenuRecipeId {
        validateBasics(name,sellingPrice); if(dao.activeNameCount(restaurantId.value,name.normalizeName(),"")>0)throw MenuRecipeValidationException.DuplicateName(); val now=System.currentTimeMillis(); val id=MenuRecipeId(UUID.randomUUID().toString())
        dao.insertRecipe(MenuRecipeEntity(id.value,restaurantId.value,name.trim(),name.normalizeName(),sellingPrice,notes?.trim()?.takeIf(String::isNotEmpty),null,now,now)); return id
    }
    override suspend fun update(id: MenuRecipeId, name: String, sellingPrice: BigDecimal?, notes: String?) {
        validateBasics(name,sellingPrice); val old=dao.getRecipe(id.value) ?: return; if(old.archivedAt==null&&dao.activeNameCount(old.restaurantId,name.normalizeName(),id.value)>0)throw MenuRecipeValidationException.DuplicateName()
        dao.updateRecipe(old.copy(name=name.trim(),normalizedName=name.normalizeName(),sellingPrice=sellingPrice,notes=notes?.trim()?.takeIf(String::isNotEmpty),updatedAt=System.currentTimeMillis()))
    }
    override suspend fun saveComponent(recipeId: MenuRecipeId, componentId: MenuRecipeComponentId?, ingredientId: IngredientId, optionId: IngredientUnitOptionId, quantityEntered: BigDecimal, sortOrder: Int): MenuRecipeComponentId = db.withTransaction {
        if(quantityEntered<=BigDecimal.ZERO) throw MenuRecipeValidationException.InvalidQuantity()
        val recipe=dao.getRecipe(recipeId.value) ?: throw MenuRecipeValidationException.OwnershipMismatch()
        val ingredient=ingredientDao.getById(ingredientId.value); val option=optionDao.getById(optionId.value)
        if(ingredient?.restaurantId!=recipe.restaurantId || option?.ingredientId!=ingredientId.value || !option.isActive || option.deletedAt!=null) throw MenuRecipeValidationException.OwnershipMismatch()
        val id=componentId ?: MenuRecipeComponentId(UUID.randomUUID().toString()); val old=componentId?.let { dao.getComponent(it.value) }
        if(old!=null && old.menuRecipeId!=recipeId.value) throw MenuRecipeValidationException.OwnershipMismatch()
        val now=System.currentTimeMillis(); dao.upsertComponent(MenuRecipeComponentEntity(id.value,recipeId.value,ingredientId.value,optionId.value,quantityEntered,
            quantityEntered.multiply(option.factorToBase),sortOrder,old?.createdAt?:now,now)); id
    }
    override suspend fun removeComponent(recipeId: MenuRecipeId, componentId: MenuRecipeComponentId)=dao.deleteComponent(recipeId.value,componentId.value)
    override suspend fun setArchived(id: MenuRecipeId, archived: Boolean) { val old=dao.getRecipe(id.value)?:return;if(!archived&&dao.activeNameCount(old.restaurantId,old.normalizedName,id.value)>0)throw MenuRecipeValidationException.DuplicateName();val now=System.currentTimeMillis(); dao.setArchived(id.value,if(archived)now else null,now) }
    private fun validateBasics(name:String, price:BigDecimal?) { if(name.isBlank()) throw MenuRecipeValidationException.InvalidName(); if(price!=null&&price<BigDecimal.ZERO) throw MenuRecipeValidationException.InvalidSellingPrice() }
}
private fun MenuRecipeEntity.domain()=MenuRecipe(MenuRecipeId(id),RestaurantId(restaurantId),name,normalizedName,sellingPrice,notes,archivedAt?.let(Instant::ofEpochMilli),Instant.ofEpochMilli(createdAt),Instant.ofEpochMilli(updatedAt))
private fun MenuRecipeComponentEntity.domain()=MenuRecipeComponent(MenuRecipeComponentId(id),MenuRecipeId(menuRecipeId),IngredientId(ingredientId),IngredientUnitOptionId(ingredientUnitOptionId),quantityEntered,quantityBase,sortOrder,Instant.ofEpochMilli(createdAt),Instant.ofEpochMilli(updatedAt))
