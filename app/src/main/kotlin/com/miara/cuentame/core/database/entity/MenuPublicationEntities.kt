package com.miara.cuentame.core.database.entity

import androidx.room.*
import com.miara.cuentame.core.model.menu.CashDiscountBehavior
import java.math.BigDecimal

@Entity(tableName = "menu_publications", foreignKeys = [ForeignKey(entity=RestaurantEntity::class,parentColumns=["id"],childColumns=["restaurantId"],onDelete=ForeignKey.CASCADE)], indices=[Index("restaurantId"),Index(value=["sourceMenuId","publicationRevision"],unique=true)])
data class MenuPublicationEntity(@PrimaryKey val id:String,val restaurantId:String,val sourceMenuId:String,val publicationRevision:Long,val menuNameSnapshot:String,val menuDescriptionSnapshot:String?,val defaultCashDiscountPercentSnapshot:BigDecimal,val currencyCodeSnapshot:String,val publishedAt:Long)

@Entity(tableName="menu_publication_categories",foreignKeys=[ForeignKey(entity=MenuPublicationEntity::class,parentColumns=["id"],childColumns=["publicationId"],onDelete=ForeignKey.CASCADE)],indices=[Index("publicationId"),Index(value=["publicationId","sourceMenuCategoryId"],unique=true)])
data class MenuPublicationCategoryEntity(@PrimaryKey val id:String,val publicationId:String,val sourceMenuCategoryId:String,val nameSnapshot:String,val sortOrder:Int)

@Entity(tableName="menu_publication_items",foreignKeys=[ForeignKey(entity=MenuPublicationEntity::class,parentColumns=["id"],childColumns=["publicationId"],onDelete=ForeignKey.CASCADE),ForeignKey(entity=MenuPublicationCategoryEntity::class,parentColumns=["id"],childColumns=["publicationCategoryId"],onDelete=ForeignKey.CASCADE)],indices=[Index("publicationId"),Index("publicationCategoryId"),Index(value=["publicationId","sourceMenuPlacementId"],unique=true),Index(value=["publicationId","menuRecipeId"],unique=true)])
data class MenuPublicationItemEntity(@PrimaryKey val id:String,val publicationId:String,val publicationCategoryId:String,val sourceMenuPlacementId:String,val menuRecipeId:String,val displayNameSnapshot:String,val sellingPriceSnapshot:BigDecimal,val cashDiscountBehaviorSnapshot:CashDiscountBehavior,val commercialRevision:Long,val consumptionRevision:Long,val sortOrder:Int)

@Entity(tableName="menu_publication_item_components",foreignKeys=[ForeignKey(entity=MenuPublicationItemEntity::class,parentColumns=["id"],childColumns=["publicationItemId"],onDelete=ForeignKey.CASCADE)],indices=[Index("publicationItemId"),Index(value=["publicationItemId","sourceMenuRecipeComponentId"],unique=true)])
data class MenuPublicationItemComponentEntity(@PrimaryKey val id:String,val publicationItemId:String,val sourceMenuRecipeComponentId:String,val ingredientId:String,val ingredientUnitOptionId:String,val quantityEnteredSnapshot:BigDecimal,val quantityBaseSnapshot:BigDecimal,val sortOrder:Int)
