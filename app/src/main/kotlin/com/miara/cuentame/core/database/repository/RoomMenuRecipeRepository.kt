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
        dao.insertRecipe(MenuRecipeEntity(id=id.value,restaurantId=restaurantId.value,name=name.trim(),normalizedName=name.normalizeName(),sellingPrice=sellingPrice,
            notes=notes?.trim()?.takeIf(String::isNotEmpty),cashDiscountBehavior=CashDiscountBehavior.APPLY_DEFAULT,commercialRevision=0,consumptionRevision=0,
            archivedAt=null,createdAt=now,updatedAt=now)); return id
    }
    override suspend fun update(id: MenuRecipeId, name: String, sellingPrice: BigDecimal?, notes: String?) = db.withTransaction {
        validateBasics(name,sellingPrice); val old=dao.getRecipe(id.value) ?: return@withTransaction; if(old.archivedAt==null&&dao.activeNameCount(old.restaurantId,name.normalizeName(),id.value)>0)throw MenuRecipeValidationException.DuplicateName()
        val priceChanged = !decimalEquivalent(old.sellingPrice, sellingPrice)
        dao.updateRecipe(old.copy(name=name.trim(),normalizedName=name.normalizeName(),sellingPrice=sellingPrice,notes=notes?.trim()?.takeIf(String::isNotEmpty),
            commercialRevision=old.commercialRevision + if(priceChanged) 1 else 0,updatedAt=System.currentTimeMillis()))
    }
    override suspend fun setCashDiscountBehavior(id: MenuRecipeId, behavior: CashDiscountBehavior) = db.withTransaction {
        val old=dao.getRecipe(id.value) ?: return@withTransaction
        if(old.cashDiscountBehavior != behavior) dao.updateRecipe(old.copy(cashDiscountBehavior=behavior,
            commercialRevision=old.commercialRevision+1,updatedAt=System.currentTimeMillis()))
    }
    override suspend fun saveComponent(recipeId: MenuRecipeId, componentId: MenuRecipeComponentId?, ingredientId: IngredientId, optionId: IngredientUnitOptionId, quantityEntered: BigDecimal, sortOrder: Int): MenuRecipeComponentId = db.withTransaction {
        if(quantityEntered<=BigDecimal.ZERO) throw MenuRecipeValidationException.InvalidQuantity()
        val recipe=dao.getRecipe(recipeId.value) ?: throw MenuRecipeValidationException.OwnershipMismatch()
        val ingredient=ingredientDao.getById(ingredientId.value)
        if(ingredient?.restaurantId!=recipe.restaurantId) throw MenuRecipeValidationException.OwnershipMismatch()
        val option=optionDao.getById(optionId.value) ?: throw MenuRecipeValidationException.UnitOptionMismatch()
        if(option.ingredientId!=ingredientId.value) throw MenuRecipeValidationException.UnitOptionMismatch()
        if(!option.isActive || option.deletedAt!=null) throw MenuRecipeValidationException.InactiveUnitOption()
        val id=componentId ?: MenuRecipeComponentId(UUID.randomUUID().toString()); val old=componentId?.let { dao.getComponent(it.value) }
        if(old!=null && old.menuRecipeId!=recipeId.value) throw MenuRecipeValidationException.OwnershipMismatch()
        if(dao.componentCount(recipeId.value,ingredientId.value,id.value)>0) throw MenuRecipeValidationException.DuplicateComponent()
        val quantityBase=quantityEntered.multiply(option.factorToBase)
        val consumptionChanged = old == null || old.ingredientId != ingredientId.value || old.ingredientUnitOptionId != optionId.value ||
            old.quantityEntered.compareTo(quantityEntered) != 0 || old.quantityBase.compareTo(quantityBase) != 0
        val now=System.currentTimeMillis(); dao.upsertComponent(MenuRecipeComponentEntity(id.value,recipeId.value,ingredientId.value,optionId.value,quantityEntered,
            quantityBase,sortOrder,old?.createdAt?:now,now))
        if(consumptionChanged) dao.updateRecipe(recipe.copy(consumptionRevision=recipe.consumptionRevision+1,updatedAt=now))
        id
    }
    override suspend fun removeComponent(recipeId: MenuRecipeId, componentId: MenuRecipeComponentId) = db.withTransaction {
        val recipe=dao.getRecipe(recipeId.value) ?: return@withTransaction
        if(dao.deleteComponent(recipeId.value,componentId.value)>0) dao.updateRecipe(recipe.copy(
            consumptionRevision=recipe.consumptionRevision+1,updatedAt=System.currentTimeMillis()))
    }
    override suspend fun setArchived(id: MenuRecipeId, archived: Boolean) { val old=dao.getRecipe(id.value)?:return;if(!archived&&dao.activeNameCount(old.restaurantId,old.normalizedName,id.value)>0)throw MenuRecipeValidationException.DuplicateName();val now=System.currentTimeMillis(); dao.setArchived(id.value,if(archived)now else null,now) }
    private fun validateBasics(name:String, price:BigDecimal?) { if(name.isBlank()) throw MenuRecipeValidationException.InvalidName(); if(price!=null&&price<BigDecimal.ZERO) throw MenuRecipeValidationException.InvalidSellingPrice() }
    private fun decimalEquivalent(a: BigDecimal?, b: BigDecimal?) = a == null && b == null || a != null && b != null && a.compareTo(b) == 0
}
private fun MenuRecipeEntity.domain()=MenuRecipe(MenuRecipeId(id),RestaurantId(restaurantId),name,normalizedName,sellingPrice,notes,
    cashDiscountBehavior,commercialRevision,consumptionRevision,archivedAt?.let(Instant::ofEpochMilli),Instant.ofEpochMilli(createdAt),Instant.ofEpochMilli(updatedAt))
private fun MenuRecipeComponentEntity.domain()=MenuRecipeComponent(MenuRecipeComponentId(id),MenuRecipeId(menuRecipeId),IngredientId(ingredientId),IngredientUnitOptionId(ingredientUnitOptionId),quantityEntered,quantityBase,sortOrder,Instant.ofEpochMilli(createdAt),Instant.ofEpochMilli(updatedAt))
