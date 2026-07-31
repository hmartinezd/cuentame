package com.miara.cuentame.core.presentation.navigation

import com.miara.cuentame.core.common.ids.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.URLEncoder

class AppRoutesTest {

    @Before
    fun setup() {
        AppRoutes.encoder = object : RouteEncoder {
            override fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        }
    }

    @Test
    fun `ingredientDetail produces correct route`() {
        val id = IngredientId("ing-123")
        assertEquals("inventory/ing-123", AppRoutes.ingredientDetail(id))
    }

    @Test
    fun `ingredientEdit produces correct route`() {
        val id = IngredientId("ing-123")
        assertEquals("inventory/ing-123/edit", AppRoutes.ingredientEdit(id))
    }

    @Test
    fun `stockCountDraft produces correct route`() {
        val id = StockCountId("count-123")
        assertEquals("count/count-123", AppRoutes.stockCountDraft(id))
    }

    @Test
    fun `stockCountDetail produces correct route`() {
        val id = StockCountId("count-123")
        assertEquals("count/count-123/detail", AppRoutes.stockCountDetail(id))
    }

    @Test
    fun `stockCountArea produces correct route`() {
        val cid = StockCountId("count-123")
        val aid = StockCountAreaId("area-456")
        assertEquals("count/count-123/area/area-456", AppRoutes.stockCountArea(cid, aid))
    }

    @Test
    fun `purchaseDraft produces correct route`() {
        val id = PurchaseReceiptId("rec-123")
        assertEquals("purchases/rec-123", AppRoutes.purchaseDraft(id))
    }

    @Test
    fun `purchaseDetail produces correct route`() {
        val id = PurchaseReceiptId("rec-123")
        assertEquals("purchases/rec-123/detail", AppRoutes.purchaseDetail(id))
    }

    @Test
    fun `purchaseLineCreate produces correct route`() {
        val id = PurchaseReceiptId("rec-123")
        assertEquals("purchases/rec-123/line", AppRoutes.purchaseLineCreate(id))
    }

    @Test
    fun `purchaseLineEdit produces correct route`() {
        val rid = PurchaseReceiptId("rec-123")
        val lid = PurchaseLineId("line-456")
        assertEquals("purchases/rec-123/line/line-456", AppRoutes.purchaseLineEdit(rid, lid))
    }

    @Test
    fun `wasteDraft produces correct route`() {
        val id = WasteEventId("waste-123")
        assertEquals("waste/draft/waste-123", AppRoutes.wasteDraft(id))
    }

    @Test
    fun `wasteEdit produces correct route`() {
        val id = WasteEventId("waste-123")
        assertEquals("waste/waste-123/edit", AppRoutes.wasteEdit(id))
    }

    @Test
    fun `wasteDetail produces correct route`() {
        val id = WasteEventId("waste-123")
        assertEquals("waste/waste-123", AppRoutes.wasteDetail(id))
    }

    @Test
    fun `supplierEdit produces correct route`() {
        val id = SupplierId("sup-123")
        assertEquals("suppliers/sup-123/edit", AppRoutes.supplierEdit(id))
    }

    @Test
    fun `reportPurchaseDetail produces correct route`() {
        assertEquals("reports/purchases?range=LAST_30_DAYS", AppRoutes.reportPurchaseDetail("LAST_30_DAYS"))
    }

    @Test
    fun `reportWasteDetail produces correct route`() {
        assertEquals("reports/waste?range=LAST_30_DAYS", AppRoutes.reportWasteDetail("LAST_30_DAYS"))
    }

    @Test
    fun `encoding special characters`() {
        val id = IngredientId("item/one?#%")
        val encoded = AppRoutes.ingredientDetail(id)
        assertEquals("inventory/item%2Fone%3F%23%25", encoded)
    }

    @Test
    fun `encoding space`() {
        val id = IngredientId("item one")
        val encoded = AppRoutes.ingredientDetail(id)
        assertEquals("inventory/item%20one", encoded)
    }
}
