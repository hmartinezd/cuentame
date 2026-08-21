package com.venkoi.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.domain.repository.MenuPublicationRepository
import com.venkoi.cuentame.core.model.menu.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class MenuPackageExporterTest {
    @Test fun `prepare reads immutable repository and returns stable utf8 export`()=runTest{
        val snapshot=snapshot();val repository=FakeRepository(snapshot);val exporter=MenuPackageExporter(repository)
        val first=exporter.prepare(snapshot.publication.id);val second=exporter.prepare(snapshot.publication.id)
        assertThat(first.suggestedFileName).isEqualTo("dinner-menu-r2.cuentame-menu.json")
        assertThat(first.mimeType).isEqualTo("application/json");assertThat(first.bytes).isEqualTo(second.bytes)
        assertThat(first.bytes.toString(Charsets.UTF_8)).contains("\"packageId\": \"publication\"")
    }
    @Test fun `filename sanitization is deterministic and safe`() { assertThat(MenuPackageExporter(FakeRepository(null)).suggestedName("  Dinner / Café: Late!  ",7)).isEqualTo("dinner-caf-late-r7.cuentame-menu.json") }
    private class FakeRepository(private val snapshot:MenuPublicationSnapshot?):MenuPublicationRepository{
        override fun observePublications(menuId:MenuId)=flowOf(emptyList<MenuPublication>())
        override fun observePublication(publicationId:MenuPublicationId)=flowOf(snapshot)
        override suspend fun getPublication(publicationId:MenuPublicationId)=snapshot
        override suspend fun publish(menuId:MenuId)=error("not used")
    }
    private fun snapshot():MenuPublicationSnapshot{val pid=MenuPublicationId("publication");val category=MenuPublicationCategory(MenuPublicationCategoryId("pc"),pid,MenuCategoryId("category"),"Entrees",0);return MenuPublicationSnapshot(MenuPublication(pid,RestaurantId("restaurant"),MenuId("menu"),2,"Dinner Menu",null,BigDecimal("3"),"USD",Instant.parse("2026-08-16T17:30:00Z")),listOf(category),listOf(MenuPublicationItem(MenuPublicationItemId("item"),pid,category.id,MenuPlacementId("placement"),MenuRecipeId("burger"),"Burger",BigDecimal("13"),CashDiscountBehavior.APPLY_DEFAULT,1,2,0)),emptyList())}
}
