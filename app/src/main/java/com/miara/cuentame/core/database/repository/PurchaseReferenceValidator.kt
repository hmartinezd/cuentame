package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.IngredientUnitOptionDao
import com.miara.cuentame.core.database.dao.InventoryAreaDao
import com.miara.cuentame.core.database.dao.PurchaseDao
import com.miara.cuentame.core.database.dao.SupplierDao
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.SupplierEntity
import com.miara.cuentame.core.domain.validation.ValidationError
import java.math.BigDecimal
import javax.inject.Inject

data class ValidatedPurchaseReferences(
    val receipt: PurchaseReceiptEntity,
    val supplier: SupplierEntity? = null,
    val restaurant: RestaurantEntity
)

data class ValidatedPurchaseLineReferences(
    val ingredient: IngredientEntity,
    val area: InventoryAreaEntity,
    val unitOption: IngredientUnitOptionEntity
)

class PurchaseReferenceValidator @Inject constructor(
    private val purchaseDao: PurchaseDao,
    private val supplierDao: SupplierDao,
    private val ingredientDao: IngredientDao,
    private val areaDao: InventoryAreaDao,
    private val unitOptionDao: IngredientUnitOptionDao
) {
    suspend fun validateReceiptAndRestaurant(
        receiptId: PurchaseReceiptId,
        activeRestaurant: RestaurantEntity
    ): ValidatedPurchaseReferences {
        val receipt = purchaseDao.getReceiptById(receiptId.value)
            ?: throw ValidationError.PurchaseNotFound
        
        if (receipt.restaurantId != activeRestaurant.id) {
            throw ValidationError.PurchaseOwnershipMismatch
        }

        val supplier = receipt.supplierId?.let { sid ->
            val s = supplierDao.getById(sid) ?: throw ValidationError.SupplierNotFound
            if (s.restaurantId != activeRestaurant.id) throw ValidationError.SupplierOwnershipMismatch
            if (!s.isActive || s.deletedAt != null) throw ValidationError.SupplierArchived
            s
        }

        return ValidatedPurchaseReferences(receipt, supplier, activeRestaurant)
    }

    suspend fun validateLineReferences(
        restaurantId: String,
        ingredientId: IngredientId,
        areaId: InventoryAreaId,
        unitOptionId: IngredientUnitOptionId
    ): ValidatedPurchaseLineReferences {
        val ingredient = ingredientDao.getById(ingredientId.value)
            ?: throw ValidationError.IngredientNotFound
        if (ingredient.restaurantId != restaurantId) throw ValidationError.IngredientOwnershipMismatch
        if (!ingredient.isActive || ingredient.deletedAt != null) throw ValidationError.ArchivedReference

        val area = areaDao.getById(areaId.value)
            ?: throw ValidationError.RecordNotFound
        if (area.restaurantId != restaurantId) throw ValidationError.InvalidPurchaseArea
        if (!area.isActive || area.deletedAt != null) throw ValidationError.ArchivedReference

        val option = unitOptionDao.getById(unitOptionId.value)
            ?: throw ValidationError.UnitOptionNotFound
        if (option.ingredientId != ingredientId.value) throw ValidationError.InvalidPurchaseUnitOption
        if (!option.isActive || option.deletedAt != null) throw ValidationError.ArchivedReference
        if (option.factorToBase <= BigDecimal.ZERO) throw ValidationError.InvalidUnitFactor

        return ValidatedPurchaseLineReferences(ingredient, area, option)
    }
}
