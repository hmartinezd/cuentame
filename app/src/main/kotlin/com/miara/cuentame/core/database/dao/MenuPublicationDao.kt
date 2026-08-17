package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.miara.cuentame.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuPublicationDao {
    @Query("SELECT * FROM menu_publications WHERE sourceMenuId=:menuId ORDER BY publicationRevision DESC, id")
    fun observePublications(menuId:String):Flow<List<MenuPublicationEntity>>
    @Query("SELECT * FROM menu_publications WHERE id=:id") fun observePublication(id:String):Flow<MenuPublicationEntity?>
    @Query("SELECT * FROM menu_publications WHERE id=:id") suspend fun getPublication(id:String):MenuPublicationEntity?
    @Query("SELECT * FROM menu_publications WHERE sourceMenuId=:menuId AND publicationRevision=:revision LIMIT 1") suspend fun getByMenuRevision(menuId:String,revision:Long):MenuPublicationEntity?
    @Query("SELECT * FROM menu_publication_categories WHERE publicationId=:id ORDER BY sortOrder, sourceMenuCategoryId") fun observeCategories(id:String):Flow<List<MenuPublicationCategoryEntity>>
    @Query("SELECT * FROM menu_publication_categories WHERE publicationId=:id ORDER BY sortOrder, sourceMenuCategoryId") suspend fun getCategories(id:String):List<MenuPublicationCategoryEntity>
    @Query("SELECT * FROM menu_publication_items WHERE publicationId=:id ORDER BY sortOrder, sourceMenuPlacementId") fun observeItems(id:String):Flow<List<MenuPublicationItemEntity>>
    @Query("SELECT * FROM menu_publication_items WHERE publicationId=:id ORDER BY sortOrder, sourceMenuPlacementId") suspend fun getItems(id:String):List<MenuPublicationItemEntity>
    @Query("SELECT c.* FROM menu_publication_item_components c JOIN menu_publication_items i ON i.id=c.publicationItemId WHERE i.publicationId=:id ORDER BY c.publicationItemId,c.sortOrder,c.sourceMenuRecipeComponentId") fun observeComponents(id:String):Flow<List<MenuPublicationItemComponentEntity>>
    @Query("SELECT c.* FROM menu_publication_item_components c JOIN menu_publication_items i ON i.id=c.publicationItemId WHERE i.publicationId=:id ORDER BY c.publicationItemId,c.sortOrder,c.sourceMenuRecipeComponentId") suspend fun getComponents(id:String):List<MenuPublicationItemComponentEntity>
    @Insert suspend fun insertPublication(row:MenuPublicationEntity)
    @Insert suspend fun insertCategories(rows:List<MenuPublicationCategoryEntity>)
    @Insert suspend fun insertItems(rows:List<MenuPublicationItemEntity>)
    @Insert suspend fun insertComponents(rows:List<MenuPublicationItemComponentEntity>)
}
