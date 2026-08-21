package com.venkoi.cuentame.feature.purchases.navigation

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.presentation.navigation.AppRoutes
import com.venkoi.cuentame.core.presentation.navigation.RouteEncoder
import org.junit.After
import org.junit.Before
import org.junit.Test

class PurchaseNavigationTest {

    private lateinit var originalEncoder: RouteEncoder

    @Before
    fun setup() {
        originalEncoder = AppRoutes.encoder
        // Use a simple encoder for testing routes
        AppRoutes.encoder = object : RouteEncoder {
            override fun encode(s: String): String = s
        }
    }

    @After
    fun tearDown() {
        AppRoutes.encoder = originalEncoder
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
