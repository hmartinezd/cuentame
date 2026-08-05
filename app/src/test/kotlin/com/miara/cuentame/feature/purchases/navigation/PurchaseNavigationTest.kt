package com.miara.cuentame.feature.purchases.navigation

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.presentation.navigation.RouteEncoder
import org.junit.Before
import org.junit.Test

class PurchaseNavigationTest {

    @Before
    fun setup() {
        // Use a simple encoder for testing routes
        AppRoutes.encoder = object : RouteEncoder {
            override fun encode(s: String): String = s
        }
    }

    @Test
    fun `DRAFT status routes to draft destination`() {
        val id = PurchaseReceiptId("p1")
        val route = getPurchaseNavigationRoute(id, DocumentStatus.DRAFT)
        assertThat(route).isEqualTo(AppRoutes.purchaseDraft(id))
    }

    @Test
    fun `POSTED status routes to detail destination`() {
        val id = PurchaseReceiptId("p1")
        val route = getPurchaseNavigationRoute(id, DocumentStatus.POSTED)
        assertThat(route).isEqualTo(AppRoutes.purchaseDetail(id))
    }

    @Test
    fun `VOIDED status routes to detail destination`() {
        val id = PurchaseReceiptId("p1")
        val route = getPurchaseNavigationRoute(id, DocumentStatus.VOIDED)
        assertThat(route).isEqualTo(AppRoutes.purchaseDetail(id))
    }

    @Test
    fun `UNKNOWN status routes to detail destination`() {
        val id = PurchaseReceiptId("p1")
        val route = getPurchaseNavigationRoute(id, DocumentStatus.UNKNOWN)
        assertThat(route).isEqualTo(AppRoutes.purchaseDetail(id))
    }
}
