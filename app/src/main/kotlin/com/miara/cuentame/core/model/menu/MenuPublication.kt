package com.miara.cuentame.core.model.menu

import com.miara.cuentame.core.common.ids.*
import java.math.BigDecimal
import java.time.Instant

data class MenuPublication(
    val id: MenuPublicationId,
    val restaurantId: RestaurantId,
    val sourceMenuId: MenuId,
    val publicationRevision: Long,
    val menuNameSnapshot: String,
    val menuDescriptionSnapshot: String?,
    val defaultCashDiscountPercentSnapshot: BigDecimal,
    val currencyCodeSnapshot: String,
    val publishedAt: Instant
)

data class MenuPublicationCategory(
    val id: MenuPublicationCategoryId,
    val publicationId: MenuPublicationId,
    val sourceMenuCategoryId: MenuCategoryId,
    val nameSnapshot: String,
    val sortOrder: Int
)

data class MenuPublicationItem(
    val id: MenuPublicationItemId,
    val publicationId: MenuPublicationId,
    val publicationCategoryId: MenuPublicationCategoryId,
    val sourceMenuPlacementId: MenuPlacementId,
    val menuRecipeId: MenuRecipeId,
    val displayNameSnapshot: String,
    val sellingPriceSnapshot: BigDecimal,
    val cashDiscountBehaviorSnapshot: CashDiscountBehavior,
    val commercialRevision: Long,
    val consumptionRevision: Long,
    val sortOrder: Int
)

data class MenuPublicationItemComponent(
    val id: MenuPublicationItemComponentId,
    val publicationItemId: MenuPublicationItemId,
    val sourceMenuRecipeComponentId: MenuRecipeComponentId,
    val ingredientId: IngredientId,
    val ingredientUnitOptionId: IngredientUnitOptionId,
    val inventoryAreaIdSnapshot: InventoryAreaId,
    val quantityEnteredSnapshot: BigDecimal,
    val quantityBaseSnapshot: BigDecimal,
    val sortOrder: Int
)

data class MenuPublicationSnapshot(
    val publication: MenuPublication,
    val categories: List<MenuPublicationCategory>,
    val items: List<MenuPublicationItem>,
    val components: List<MenuPublicationItemComponent>
)
