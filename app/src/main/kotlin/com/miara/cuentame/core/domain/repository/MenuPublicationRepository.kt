package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.MenuId
import com.miara.cuentame.core.common.ids.MenuPublicationId
import com.miara.cuentame.core.model.menu.MenuPublication
import com.miara.cuentame.core.model.menu.MenuPublicationSnapshot
import kotlinx.coroutines.flow.Flow

sealed class MenuPublicationException(message: String) : Exception(message) {
    class MenuNotFound : MenuPublicationException("Menu not found")
    class MenuArchived : MenuPublicationException("Archived menus cannot be published")
    class MenuEmpty : MenuPublicationException("Menu has no categories")
    class NoItems : MenuPublicationException("Menu has no items")
    class ItemArchived(val itemName: String) : MenuPublicationException("$itemName is archived")
    class ItemPriceMissing(val itemName: String) : MenuPublicationException("$itemName has no selling price")
    class BrokenCatalogRelationship : MenuPublicationException("Menu catalog relationship is broken")
    class OwnershipMismatch : MenuPublicationException("Menu catalog ownership mismatch")
    class PersistenceFailure(cause: Throwable) : MenuPublicationException("Publication could not be saved") { init { initCause(cause) } }
}

interface MenuPublicationRepository {
    fun observePublications(menuId: MenuId): Flow<List<MenuPublication>>
    fun observePublication(publicationId: MenuPublicationId): Flow<MenuPublicationSnapshot?>
    suspend fun getPublication(publicationId: MenuPublicationId): MenuPublicationSnapshot?
    suspend fun publish(menuId: MenuId): MenuPublicationId
}
